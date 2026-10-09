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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.sleep

import android.app.Application
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.charts.SleepAnalysis
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reads advanced sleep data for one calendar day from Gadgetbridge's own database, reusing the same
 * building blocks the built-in sleep charts use: the device's [SleepAnalysis] over the activity
 * [ActivitySample] stream (phase classification), the SpO2 sample provider for nocturnal SpO2, and
 * the device's native sleep-score provider when it has one.
 *
 * The night attributed to day D is the one that ends on the morning of D: the sample window spans
 * from D-1 12:00 to D 18:00, which also captures daytime naps on D. [SleepAnalysis] splits that
 * window into sessions; the longest is the main night and the rest (that begin on day D) are naps.
 *
 * Sleep score: when the coordinator exposes a device-native score it is used verbatim
 * ([SleepDayData.scoreNative] = true). Otherwise a transparent local heuristic is computed, with no
 * invented data — only the phase durations, awakenings and total time actually in the database:
 *
 *   score = 0.50 * durationScore   (ideal 7–9 h asleep, linear falloff outside)
 *         + 0.20 * deepScore       (deep fraction vs an 18% target, capped at 100)
 *         + 0.20 * remScore        (REM fraction vs a 22% target, capped at 100)
 *         + 0.10 * continuityScore (100 − 8·awakenings − 0.5·awakeMinutes)
 *
 * All sub-scores are clamped to 0..100 and the result is rounded to an integer.
 */
class SleepRepository(private val app: Application) {
    private val log = LoggerFactory.getLogger(SleepRepository::class.java)

    fun load(dayStartMillis: Long): SleepDayData {
        val device = pickDevice() ?: return SleepDayData.empty(dayStartMillis)
        return runCatching { loadForDevice(device, dayStartMillis) }
            .onFailure { log.warn("Failed to load sleep data", it) }
            .getOrDefault(SleepDayData.empty(dayStartMillis))
    }

    private fun pickDevice(): GBDevice? {
        val devices = (app as GBApplication).deviceManager.devices
        return devices.firstOrNull { it.isInitialized } ?: devices.firstOrNull()
    }

    private fun loadForDevice(device: GBDevice, dayStartMillis: Long): SleepDayData {
        val windowStartSec = ((dayStartMillis - 12L * HOUR_MS) / 1000L).toInt()
        val windowEndSec = ((dayStartMillis + 18L * HOUR_MS) / 1000L).toInt()

        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val coordinator = device.deviceCoordinator

            val samples: List<ActivitySample> = runCatching {
                coordinator.getSampleProvider(device, session)
                    ?.getAllActivitySamples(windowStartSec, windowEndSec)
                    ?.let { ArrayList<ActivitySample>(it) }
            }.getOrNull().orEmpty()

            if (samples.isEmpty()) return SleepDayData.empty(dayStartMillis)

            val sessions = SleepAnalysis().calculateSleepSessions(samples)
            if (sessions.isEmpty()) return SleepDayData.empty(dayStartMillis)

            val main = sessions.maxByOrNull { sleepTotalSec(it) } ?: return SleepDayData.empty(dayStartMillis)

            val deepMin = (main.deepSleepDuration / 60L).toInt()
            val lightMin = (main.lightSleepDuration / 60L).toInt()
            val remMin = (main.remSleepDuration / 60L).toInt()
            val awakeMin = (main.awakeSleepDuration / 60L).toInt()
            val totalMin = deepMin + lightMin + remMin

            val sStartSec = (main.sleepStart.time / 1000L)
            val sEndSec = (main.sleepEnd.time / 1000L)
            val segments = buildSegments(samples, sStartSec, sEndSec)
            val awakenings = segments.count { it.phase == SleepPhase.AWAKE && it.durationMin >= WAKE_EPISODE_MIN }

            val (spo2Min, spo2Avg, spo2Samples) =
                loadSpo2(device, session, main.sleepStart.time, main.sleepEnd.time)

            val native = loadNativeScore(device, session, main.sleepStart.time, main.sleepEnd.time)
            val score = native ?: heuristicScore(totalMin, deepMin, remMin, awakenings, awakeMin)

            val naps = sessions
                .filter { it !== main && it.sleepStart.time >= dayStartMillis }
                .mapNotNull { nap ->
                    val durMin = (sleepTotalSec(nap) / 60L).toInt()
                    if (durMin <= 0) null
                    else NapInfo(TIME_FMT.format(nap.sleepStart), TIME_FMT.format(nap.sleepEnd), durMin)
                }
                .sortedBy { it.startLabel }

            return SleepDayData(
                dayStartMillis = dayStartMillis,
                hasData = true,
                sleepStartLabel = TIME_FMT.format(main.sleepStart),
                sleepEndLabel = TIME_FMT.format(main.sleepEnd),
                sessionStartMillis = main.sleepStart.time,
                sessionEndMillis = main.sleepEnd.time,
                totalMin = totalMin,
                deepMin = deepMin,
                lightMin = lightMin,
                remMin = remMin,
                awakeMin = awakeMin,
                awakenings = awakenings,
                score = score,
                scoreNative = native != null,
                segments = segments,
                spo2Min = spo2Min,
                spo2Avg = spo2Avg,
                spo2Samples = spo2Samples,
                naps = naps,
            )
        }
    }

    /** Build the hypnogram: merge consecutive same-phase samples inside the main session. */
    private fun buildSegments(samples: List<ActivitySample>, startSec: Long, endSec: Long): List<SleepSegment> {
        val inSession = samples.filter { it.timestamp in startSec..endSec }
        if (inSession.size < 2) return emptyList()
        val out = ArrayList<SleepSegment>()
        for (i in 1 until inSession.size) {
            val prev = inSession[i - 1]
            val cur = inSession[i]
            val durSec = cur.timestamp - prev.timestamp
            if (durSec <= 0) continue
            val phase = phaseOf(cur.kind)
            val durMin = max(1, (durSec / 60L).toInt())
            val offMin = ((prev.timestamp - startSec) / 60L).toInt().coerceAtLeast(0)
            val last = out.lastOrNull()
            if (last != null && last.phase == phase && last.startOffsetMin + last.durationMin >= offMin) {
                out[out.lastIndex] = last.copy(durationMin = last.durationMin + durMin)
            } else {
                out.add(SleepSegment(phase, offMin, durMin))
            }
        }
        return out
    }

    private fun phaseOf(kind: ActivityKind?): SleepPhase = when (kind) {
        ActivityKind.DEEP_SLEEP -> SleepPhase.DEEP
        ActivityKind.LIGHT_SLEEP -> SleepPhase.LIGHT
        ActivityKind.REM_SLEEP -> SleepPhase.REM
        else -> SleepPhase.AWAKE
    }

    private fun loadSpo2(
        device: GBDevice,
        session: nodomain.freeyourgadget.gadgetbridge.entities.DaoSession,
        fromMs: Long,
        toMs: Long,
    ): Triple<Int, Int, List<Int>> {
        val values = runCatching {
            device.deviceCoordinator.getSpo2SampleProvider(device, session)
                ?.getAllSamples(fromMs, toMs)
                ?.map { it.spo2 }
                ?.filter { it in 1..100 }
        }.getOrNull().orEmpty()
        if (values.isEmpty()) return Triple(0, 0, emptyList())
        val min = values.min()
        val avg = (values.sum().toDouble() / values.size).roundToInt()
        return Triple(min, avg, downsample(values, 48))
    }

    private fun loadNativeScore(
        device: GBDevice,
        session: nodomain.freeyourgadget.gadgetbridge.entities.DaoSession,
        fromMs: Long,
        toMs: Long,
    ): Int? = runCatching {
        if (!device.deviceCoordinator.supportsSleepScore(device)) return null
        device.deviceCoordinator.getSleepScoreProvider(device, session)
            ?.getAllSamples(fromMs, toMs)
            ?.lastOrNull { it.sleepScore > 0 }
            ?.sleepScore
    }.getOrNull()

    private fun heuristicScore(totalMin: Int, deepMin: Int, remMin: Int, awakenings: Int, awakeMin: Int): Int {
        if (totalMin <= 0) return 0
        // Duration: full marks inside 7–9 h, linear falloff to 0 at 3 h / 12 h.
        val hours = totalMin / 60.0
        val durationScore = when {
            hours in 7.0..9.0 -> 100.0
            hours < 7.0 -> (100.0 * ((hours - 3.0) / 4.0))
            else -> (100.0 * ((12.0 - hours) / 3.0))
        }.coerceIn(0.0, 100.0)
        val deepScore = (100.0 * (deepMin.toDouble() / totalMin) / 0.18).coerceIn(0.0, 100.0)
        val remScore = (100.0 * (remMin.toDouble() / totalMin) / 0.22).coerceIn(0.0, 100.0)
        val continuityScore = (100.0 - 8.0 * awakenings - 0.5 * awakeMin).coerceIn(0.0, 100.0)
        val score = 0.50 * durationScore + 0.20 * deepScore + 0.20 * remScore + 0.10 * continuityScore
        return score.roundToInt().coerceIn(0, 100)
    }

    private fun downsample(values: List<Int>, target: Int): List<Int> {
        if (values.size <= target) return values
        val step = values.size.toDouble() / target
        return (0 until target).map { values[(it * step).toInt().coerceIn(0, values.lastIndex)] }
    }

    private fun sleepTotalSec(s: SleepAnalysis.SleepSession): Long =
        s.lightSleepDuration + s.deepSleepDuration + s.remSleepDuration

    companion object {
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val WAKE_EPISODE_MIN = 5
        private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())

        /** Local midnight for the day that contains [millis]. */
        fun dayStart(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun today(): Long = dayStart(System.currentTimeMillis())

        private val DAY_FMT = SimpleDateFormat("EEEE d MMM", Locale.getDefault())
        fun dayLabel(dayStartMillis: Long): String = DAY_FMT.format(Date(dayStartMillis))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
