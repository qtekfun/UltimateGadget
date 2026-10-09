/*  Copyright (C) 2026 UltimateGadget contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.performance

import android.app.Application
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import java.util.Calendar
import java.util.Date
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Computes the Rendimiento (performance) metrics locally from Gadgetbridge's own database for the
 * active device — PAI, Banister training load/ACWR, estimated VO2max and HRV. Uses the same access
 * path as the dashboard (GBApplication.acquireDB() + the device coordinator's sample providers and
 * [BaseActivitySummaryDao]). 100% offline; anything the device does not store is returned null.
 *
 * Formulas are open and documented in place:
 *  - PAI: HUNT-style weekly points from minutes in heart-rate zones (Nes et al., 2017).
 *  - Training load: Banister TRIMP per workout, summed to acute(7d)/chronic(28d) and ACWR (Gabbett).
 *  - VO2max: Uth–Sørensen HR ratio (15.3·HRmax/HRrest), or Jack Daniels' VDOT from a recent run.
 *  - HRV: taken as-is from the device's HRV summary provider; no estimate is synthesised.
 */
class PerformanceRepository(private val app: Application) {

    fun load(): PerformanceData {
        val device = pickDevice()
        val user = runCatching { ActivityUser() }.getOrNull()
        val age = user?.age?.takeIf { it in 5..120 } ?: 0
        val male = user?.gender == ActivityUser.GENDER_MALE
        // Tanaka would be 208 − 0.7·age; the spec asks for the classic 220 − age, kept here.
        val hrMax = if (age > 0) 220 - age else DEFAULT_HR_MAX

        if (device == null) {
            return PerformanceData(null, null, null, null, hrMax, age, null)
        }

        val restingHr = runCatching { restingBaseline(device) }.getOrNull()
        val pai = runCatching { computePai(device, hrMax) }.getOrNull()
        val workouts = runCatching { loadWorkouts(device) }.getOrDefault(emptyList())
        val load = runCatching { computeLoad(workouts, restingHr, hrMax, male) }.getOrNull()
        val vo2 = runCatching { computeVo2Max(workouts, restingHr, hrMax) }.getOrNull()
        val hrv = runCatching { computeHrv(device) }.getOrNull()

        return PerformanceData(pai, load, vo2, hrv, hrMax, age, restingHr)
    }

    private fun pickDevice(): GBDevice? {
        val devices = (app as GBApplication).deviceManager.devices
        return devices.firstOrNull { it.isInitialized } ?: devices.firstOrNull()
    }

    // ---- Resting HR baseline ----------------------------------------------------------------

    /** Mean resting HR over the last ~10 days (falls back to the latest sample). */
    private fun restingBaseline(device: GBDevice): Int? {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val p = device.deviceCoordinator.getHeartRateRestingSampleProvider(device, session)
                ?: return null
            val now = System.currentTimeMillis()
            val vals = p.getAllSamples(now - 10L * DAY_MS, now)
                .map { it.heartRate }.filter { it in 25..120 }
            if (vals.isNotEmpty()) return vals.average().roundToInt()
            return p.latestSample?.heartRate?.takeIf { it in 25..120 }
        }
    }

    // ---- PAI (HUNT model) -------------------------------------------------------------------

    /**
     * Personal Activity Intelligence over the last 7 days. The HUNT algorithm (Nes et al., 2017,
     * *Am J Med*) accumulates points from time spent with an elevated heart rate; the exact,
     * sex/age-tuned coefficients are proprietary, so this is a transparent approximation:
     *
     *  - Continuous heart-rate samples for the window are read from the device sample provider; the
     *    duration each sample represents is the gap to the next sample, capped at [PAI_GAP_CAP_MIN]
     *    minutes so idle gaps are not counted as exercise.
     *  - Zones are set by %HRmax: moderate = 59–76 %, vigorous = ≥ 76 % (below 59 % earns nothing),
     *    matching the HUNT moderate/vigorous split.
     *  - Points per minute: moderate = [PAI_PTS_MODERATE], vigorous = [PAI_PTS_VIGOROUS] (≈2×),
     *    scaled so ~150 min moderate or ~75 min vigorous per week ≈ 100 PAI, i.e. the physical-
     *    activity guideline the HUNT target was built around. The absolute calibration is
     *    sex-independent here and therefore approximate.
     */
    private fun computePai(device: GBDevice, hrMax: Int): PaiData? {
        if (hrMax <= 0) return null
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val provider = device.deviceCoordinator.getSampleProvider(device, session) ?: return null
            val now = System.currentTimeMillis()
            val fromMs = now - 7L * DAY_MS
            val samples = provider.getAllActivitySamples((fromMs / 1000).toInt(), (now / 1000).toInt())
                .filter { it.heartRate in 30..230 }
                .sortedBy { it.timestamp }
            if (samples.size < 2) return null

            val perDayPai = DoubleArray(7)
            var moderateMin = 0.0
            var vigorousMin = 0.0
            val dayZero = fromMs

            for (i in 0 until samples.size - 1) {
                val s = samples[i]
                val tMs = s.timestamp.toLong() * 1000L
                val nextMs = samples[i + 1].timestamp.toLong() * 1000L
                val gapMin = ((nextMs - tMs) / 60000.0).coerceIn(0.0, PAI_GAP_CAP_MIN)
                if (gapMin <= 0.0) continue
                val pctMax = s.heartRate.toDouble() / hrMax
                val pts = when {
                    pctMax >= 0.76 -> { vigorousMin += gapMin; PAI_PTS_VIGOROUS }
                    pctMax >= 0.59 -> { moderateMin += gapMin; PAI_PTS_MODERATE }
                    else -> 0.0
                } * gapMin
                val dayIdx = (((tMs - dayZero) / DAY_MS).toInt()).coerceIn(0, 6)
                perDayPai[dayIdx] += pts
            }

            val total = perDayPai.sum().roundToInt()
            if (total <= 0 && moderateMin + vigorousMin <= 0.0) return null
            return PaiData(
                total = total,
                daily = perDayPai.map { it.roundToInt() },
                moderateMinutes = moderateMin.roundToInt(),
                vigorousMinutes = vigorousMin.roundToInt(),
            )
        }
    }

    // ---- Workouts (shared by training load + VO2max) ----------------------------------------

    private data class Workout(
        val startMs: Long,
        val durationMin: Double,
        val avgHr: Int?,
        val distanceMeters: Double?,
        val running: Boolean,
    )

    private fun loadWorkouts(device: GBDevice): List<Workout> {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val dbDevice = DBHelper.findDevice(device, session) ?: return emptyList()
            val now = System.currentTimeMillis()
            val from = now - 90L * DAY_MS // enough for a running VO2max estimate too
            val rows: List<BaseActivitySummary> = session.baseActivitySummaryDao.queryBuilder()
                .where(
                    BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.id),
                    BaseActivitySummaryDao.Properties.StartTime.ge(Date(from)),
                )
                .orderDesc(BaseActivitySummaryDao.Properties.StartTime)
                .list()
            return rows.mapNotNull { toWorkout(it) }
        }
    }

    private fun toWorkout(s: BaseActivitySummary): Workout? {
        val start = s.startTime ?: return null
        val end = s.endTime ?: return null
        val minutes = (end.time - start.time) / 60000.0
        if (minutes <= 0.0 || minutes > 24 * 60) return null
        val data = runCatching { ActivitySummaryData.fromJson(s.summaryData) }.getOrNull()
        val avgHr = data?.getNumber(ActivitySummaryEntries.HR_AVG, null)?.toInt()?.takeIf { it in 30..230 }
        val dist = data?.getNumber(ActivitySummaryEntries.DISTANCE_METERS, null)?.toDouble()?.takeIf { it > 0.0 }
        val kind = s.activityKind.let { runCatching { ActivityKind.fromCode(it) }.getOrNull() }
        val running = kind != null && kind.name.contains("RUNNING")
        return Workout(start.time, minutes, avgHr, dist, running)
    }

    // ---- Training load (Banister TRIMP + ACWR) ----------------------------------------------

    /**
     * Banister TRIMP per workout, then acute (7d sum), chronic (28d sum / 4 = average week) and
     * ACWR = acute/chronic (Gabbett). TRIMP = minutes · HRr · k · e^(b·HRr), with the heart-rate
     * reserve HRr = (HRavg − HRrest)/(HRmax − HRrest) and the sex-specific constants from Banister:
     * men k=0.64, b=1.92; women k=0.86, b=1.67. When a workout has no average HR it is estimated at
     * HRr≈0.65 (moderate). Without a resting-HR baseline TRIMP cannot be formed, so load falls back
     * to a plain duration proxy (minutes · 5) and [TrainingLoadData.usedHeartRate] is false.
     */
    private fun computeLoad(
        workouts: List<Workout>,
        restingHr: Int?,
        hrMax: Int,
        male: Boolean,
    ): TrainingLoadData? {
        val now = System.currentTimeMillis()
        val windowStart = now - 28L * DAY_MS
        val recent = workouts.filter { it.startMs >= windowStart }
        if (recent.isEmpty()) return null

        val usedHr = restingHr != null && hrMax > restingHr
        val daily = DoubleArray(28)
        var acute = 0.0
        var chronic = 0.0
        for (w in recent) {
            val load = if (usedHr) {
                val hrr = (((w.avgHr ?: ((restingHr!! + hrMax) * 0.65).toInt()).toDouble() - restingHr!!) /
                        (hrMax - restingHr)).coerceIn(0.0, 1.0)
                val k = if (male) 0.64 else 0.86
                val b = if (male) 1.92 else 1.67
                w.durationMin * hrr * k * exp(b * hrr)
            } else {
                w.durationMin * 5.0
            }
            chronic += load
            if (w.startMs >= now - 7L * DAY_MS) acute += load
            val dayIdx = (((w.startMs - windowStart) / DAY_MS).toInt()).coerceIn(0, 27)
            daily[dayIdx] += load
        }
        val chronicWeek = chronic / 4.0
        val acwr = if (chronicWeek > 0.0) acute / chronicWeek else null
        return TrainingLoadData(
            acute = acute.roundToInt(),
            chronic = chronicWeek.roundToInt(),
            acwr = acwr,
            daily = daily.toList(),
            workoutsCounted = recent.size,
            usedHeartRate = usedHr,
        )
    }

    // ---- VO2max estimate --------------------------------------------------------------------

    /**
     * Estimated VO2max. A recent run with distance + duration gives an individualised estimate via
     * Jack Daniels' VDOT (velocity→VO2 and a time→%VO2max decay); otherwise the Uth–Sørensen–
     * Overgaard–Pedersen (2004) heart-rate ratio VO2max = 15.3 · HRmax / HRrest is used. Both are
     * clearly flagged as estimates.
     */
    private fun computeVo2Max(workouts: List<Workout>, restingHr: Int?, hrMax: Int): Vo2MaxData? {
        val run = workouts.firstOrNull {
            it.running && it.distanceMeters != null && it.distanceMeters >= 1500.0 &&
                it.durationMin in 8.0..240.0
        }
        if (run != null) {
            val v = run.distanceMeters!! / run.durationMin // m/min
            val t = run.durationMin
            val vo2 = -4.60 + 0.182258 * v + 0.000104 * v * v
            val pct = 0.8 + 0.1894393 * exp(-0.012778 * t) + 0.2989558 * exp(-0.1932605 * t)
            val vdot = vo2 / pct
            if (vdot in 20.0..90.0) {
                return Vo2MaxData(
                    value = vdot,
                    method = Vo2Method.RUNNING_VDOT,
                    detail = "VDOT de carrera (%.1f km en %.0f min)".format(run.distanceMeters / 1000.0, t),
                )
            }
        }
        if (restingHr != null && restingHr in 25..120 && hrMax > 0) {
            val value = 15.3 * hrMax.toDouble() / restingHr
            if (value in 20.0..90.0) {
                return Vo2MaxData(
                    value = value,
                    method = Vo2Method.HR_RATIO,
                    detail = "Ratio FC (FCmáx $hrMax / FCreposo $restingHr)",
                )
            }
        }
        return null
    }

    // ---- HRV (device-provided only) ---------------------------------------------------------

    private fun computeHrv(device: GBDevice): HrvData? {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val p = device.deviceCoordinator.getHrvSummarySampleProvider(device, session) ?: return null
            val now = System.currentTimeMillis()
            val samples = p.getAllSamples(now - 14L * DAY_MS, now)
            if (samples.isEmpty()) return null
            val trend = samples.mapNotNull { it.lastNightAverage?.takeIf { v -> v > 0 } }
            val latest = samples.lastOrNull()
            val lastNight = latest?.lastNightAverage?.takeIf { it > 0 }
            val weekly = latest?.weeklyAverage?.takeIf { it > 0 }
            val status = latest?.status?.takeIf { it.getNum() > 0 }?.name
            if (lastNight == null && weekly == null && trend.isEmpty()) return null
            return HrvData(lastNight, weekly, status, trend)
        }
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val DEFAULT_HR_MAX = 190

        // PAI zone weighting — see computePai KDoc.
        private const val PAI_GAP_CAP_MIN = 5.0
        private const val PAI_PTS_MODERATE = 100.0 / 150.0  // ≈0.667 pts/min
        private const val PAI_PTS_VIGOROUS = 100.0 / 75.0   // ≈1.333 pts/min
    }
}
