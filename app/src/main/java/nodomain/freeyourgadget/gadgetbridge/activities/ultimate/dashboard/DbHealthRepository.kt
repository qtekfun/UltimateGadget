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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard

import nodomain.freeyourgadget.gadgetbridge.R
import android.app.Application
import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout.WorkoutDetailRepository
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessRepository
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Reads the dashboard's health data from Gadgetbridge's own database for the active device, using
 * the same helpers the built-in charts/widgets use ([DailyTotals], the device's sample providers and
 * [BaseActivitySummaryDao]). Any metric the device doesn't store comes back null so the card shows a
 * graceful "no data" state. Falls back to [SampleHealthRepository] data only for @Preview.
 */
class DbHealthRepository(private val app: Application) : HealthRepository {
    private val log = LoggerFactory.getLogger(DbHealthRepository::class.java)

    override fun load(): DashboardData {
        val device = pickDevice()
            ?: return DashboardData(null, null, null, null, null, isSample = false)
        return DashboardData(
            steps = runCatching { loadSteps(device) }.getOrNull(),
            lastWorkout = runCatching { loadLastWorkout(device) }.getOrNull(),
            heart = runCatching { loadHeartAcrossDevices(device) }.onFailure { android.util.Log.w("DbHealthRepository", "heart tile failed", it) }.getOrNull(),
            sleepStress = runCatching { loadSleepStress(device) }.getOrNull(),
            readiness = runCatching { ReadinessRepository(app).load(device) }.getOrNull(),
            isSample = false,
        )
    }

    private fun pickDevice(): GBDevice? {
        val devices = (app as GBApplication).deviceManager.devices
        return devices.firstOrNull { it.isInitialized } ?: devices.firstOrNull()
    }

    // ---- Steps / activity -------------------------------------------------------------------

    private fun loadSteps(device: GBDevice): StepsData? {
        val weekly = ArrayList<Int>(7)
        var todayTotals: DailyTotals? = null
        var todaySteps = 0L
        var todayDistance = 0L
        for (daysAgo in 6 downTo 0) {
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            // Steps and distance add up across watches (one in the morning, another in the afternoon).
            val merged = CrossDeviceSteps.dailyTotals(day, device)
            weekly.add((merged?.steps ?: 0L).toInt())
            if (daysAgo == 0) {
                todayTotals = DailyTotals.getDailyTotalsForDevice(device, day)
                todaySteps = merged?.steps ?: todayTotals.steps
                todayDistance = merged?.distanceCm ?: todayTotals.distance
            }
        }
        val today = todayTotals ?: return null
        if (todaySteps <= 0 && weekly.all { it <= 0 }) return null
        val goal = runCatching { ActivityUser().stepsGoal }.getOrDefault(10000).coerceAtLeast(1)
        return StepsData(
            steps = todaySteps.toInt(),
            goal = goal,
            distanceKm = HealthUnits.distanceKm(todayDistance, todaySteps, ActivityUser().stepLengthCm),
            kcal = (
                HealthUnits.activeKcal(today.activeCalories, device.deviceCoordinator is HuaweiCoordinator) +
                    today.restingCalories
                ).toInt(),
            weekly = weekly,
        )
    }

    // ---- Heart rate / SpO2 ------------------------------------------------------------------

    /**
     * The heart tile is not about one watch: the pulse comes from the first watch that has one, and the
     * ECG and SpO2 lines from the measurements of every watch (in the app's common format).
     */
    private fun loadHeartAcrossDevices(picked: GBDevice): HeartData? {
        val devices = (app as GBApplication).deviceManager.devices
        val ordered = listOf(picked) + devices.filter { it !== picked }.sortedByDescending { it.isInitialized }
        val core = ordered.firstNotNullOfOrNull { d -> runCatching { loadHeart(d) }.getOrNull() }
        val measured = runCatching {
            nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.HealthMeasurements.summary(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.HealthMeasurements.loadAll(app),
            )
        }.getOrNull()
        return mergeMeasurements(core, measured)
    }


    private fun loadHeart(device: GBDevice): HeartData? {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val coordinator = device.deviceCoordinator
            val now = System.currentTimeMillis()
            val from = now - 6L * 60 * 60 * 1000

            val activity = coordinator.getSampleProvider(device, session)
            val recentHr = runCatching {
                activity?.getAllActivitySamples((from / 1000).toInt(), (now / 1000).toInt())
                    .orEmpty().map { it.heartRate }.filter { it in 1..250 }
            }.getOrDefault(emptyList())

            val resting = runCatching {
                coordinator.getHeartRateRestingSampleProvider(device, session)?.latestSample?.heartRate
            }.getOrNull()?.takeIf { it in 1..250 }

            val providerSpo2 = runCatching {
                coordinator.getSpo2SampleProvider(device, session)?.latestSample?.spo2
            }.getOrNull()?.takeIf { it in 1..100 }

            // Nothing in the last 6h (a ring or watch that only measures periodically, or has not
            // synced recently) should still show the most recent reading on record instead of 0.
            // Find the device's latest sample and scan the day up to it for the last valid bpm.
            val lastKnownHr = recentHr.lastOrNull() ?: runCatching {
                val prov = activity ?: return@runCatching null
                val latest = prov.getLatestActivitySample() ?: return@runCatching null
                val endTs = latest.timestamp
                prov.getAllActivitySamples(endTs - 24 * 60 * 60, endTs)
                    .map { it.heartRate }.filter { it in 1..250 }.lastOrNull()
            }.getOrNull()

            val current = lastKnownHr ?: resting
            val spo2 = providerSpo2
            if (current == null && resting == null && spo2 == null) return null
            return HeartData(
                currentBpm = current ?: 0,
                restingBpm = resting ?: 0,
                spo2 = spo2 ?: 0,
                recent = recentHr.takeLast(48),
            )
        }
    }

    // ---- Sleep / stress --------------------------------------------------------------------

    private fun loadSleepStress(device: GBDevice): SleepStressData? {
        val today = Calendar.getInstance()
        // DailyTotals only exposes the total (light+deep+rem); the per-phase breakdown is not
        // public, so we show the total and leave the phase split for a later increment.
        val totalSleep = DailyTotals.getDailyTotalsForDevice(device, today).sleep.toInt()

        val stress = GBApplication.acquireDB().use { db ->
            runCatching {
                device.deviceCoordinator.getStressSampleProvider(device, db.daoSession)
                    ?.latestSample?.stress
            }.getOrNull()?.takeIf { it in 1..100 }
        }

        if (totalSleep <= 0 && stress == null) return null
        return SleepStressData(
            sleepMinutes = totalSleep,
            lightMinutes = totalSleep,
            deepMinutes = 0,
            remMinutes = 0,
            awakeMinutes = 0,
            stressLevel = stress ?: 0,
        )
    }

    // ---- Last workout ----------------------------------------------------------------------

    /**
     * Distance of a workout in km: the summary's own distance when the device sends one, otherwise
     * the length of its GPS track (golf rounds from Huawei carry the walk only as a track).
     */
    private fun workoutDistanceKm(device: GBDevice, summary: BaseActivitySummary): Double {
        val fromSummary = runCatching {
            ActivitySummaryData.fromJson(summary.summaryData)
                .getNumber(ActivitySummaryEntries.DISTANCE_METERS, null)?.toDouble()
        }.getOrNull()?.takeIf { it > 0.0 }
        if (fromSummary != null) return fromSummary / 1000.0
        val track = runCatching {
            val provider = device.deviceCoordinator.getActivityTrackProvider(device, app)
            provider.getActivityTrack(summary)?.allPoints.orEmpty()
                .mapNotNull { it.location }
                .map { it.latitude to it.longitude }
        }.getOrDefault(emptyList())
        return HealthUnits.pathMeters(track) / 1000.0
    }

    private fun loadLastWorkout(device: GBDevice): WorkoutData? {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val dbDevice = DBHelper.findDevice(device, session) ?: return null
            val summary = session.baseActivitySummaryDao.queryBuilder()
                .where(BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.id))
                .orderDesc(BaseActivitySummaryDao.Properties.StartTime)
                .limit(1)
                .list()
                .firstOrNull() ?: return null

            val start: Date? = summary.startTime
            val end: Date? = summary.endTime
            val durationSeconds = if (start != null && end != null)
                ((end.time - start.time) / 1000).coerceAtLeast(0) else 0L
            val kind = WorkoutDetailRepository.kindOf(summary)
            val type = kind.takeIf { it != ActivityKind.UNKNOWN }?.getLabel(app)
                ?: summary.name?.takeIf { it.isNotBlank() }
                ?: app.getString(R.string.ult_workout)
            return WorkoutData(
                type = type,
                distanceKm = workoutDistanceKm(device, summary),
                durationSeconds = durationSeconds,
                whenLabel = start?.let { DATE_FMT.format(it) } ?: "",
                avgHeartRate = 0,
                trace = emptyList(),
                line = WorkoutDetailRepository.listLine(summary, kind),
                iconRes = kind.icon,
            )
        }
    }

    companion object {
        private val DATE_FMT = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
    }
}

/**
 * Puts the on-demand readings of every watch (ECG, SpO2) on the heart tile: the SpO2 of the pulse watch
 * wins; otherwise the newest SpO2 reading that has a value (in whole percent) is used.
 */
internal fun mergeMeasurements(
    core: HeartData?,
    measured: nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.HealthSummary?,
): HeartData? {
    val spo2 = core?.spo2?.takeIf { it > 0 } ?: measured?.lastSpo2?.tenthsOfPercent?.let { (it + 5) / 10 }
    val ecg = measured?.lastEcg?.let { EcgTile(it.timestampSec, it.classification, it.rawCode) }
    if (core == null && spo2 == null && ecg == null) return null
    return (core ?: HeartData(0, 0, 0, emptyList())).copy(spo2 = spo2 ?: 0, ecg = ecg)
}
