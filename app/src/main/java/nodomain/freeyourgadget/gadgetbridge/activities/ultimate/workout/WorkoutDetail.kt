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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout

import com.qtekfun.mapcore.LatLon
import java.util.Locale

/** One labelled, already formatted value ("Avg heart rate" / "121 bpm"). */
data class WorkoutMetric(val label: String, val value: String)

/** A point of a curve: [t] seconds from the workout start, [v] in the display unit. */
data class SeriesPoint(val t: Float, val v: Float)

/**
 * A curve to plot. [format] turns a value into its label (pace as m:ss, heart rate as an integer).
 * [inverted] draws lower values higher, which is how pace reads (a faster pace is a smaller number).
 */
data class WorkoutSeries(
    val title: String,
    val unit: String,
    val points: List<SeriesPoint>,
    val format: (Float) -> String,
    val inverted: Boolean = false,
    val stats: List<WorkoutMetric> = emptyList(),
)

/** Time spent in one heart-rate zone; [index] is 1 (easiest) to 5 (hardest). */
data class HrZoneTime(val index: Int, val label: String, val seconds: Double)

data class HeartSection(
    val stats: List<WorkoutMetric>,
    val curve: WorkoutSeries?,
    /** Lower bound in bpm of zones 1..5, to draw the bands behind the curve. */
    val zoneLowerBounds: List<Int>,
    val zones: List<HrZoneTime>,
    /** Heart rate after the end of the workout, one value per sample, oldest first. */
    val recovery: List<Float>,
)

/** A table the watch sent (km splits, swim lengths...), already formatted. */
data class WorkoutTable(val title: String, val header: List<String>, val rows: List<List<String>>)

data class WorkoutDetail(
    val summaryId: Long,
    val profile: WorkoutProfile,
    val typeLabel: String,
    val title: String,
    val iconRes: Int,
    val whenLabel: String,
    val hero: List<WorkoutMetric>,
    val secondary: List<WorkoutMetric>,
    val track: List<LatLon>,
    val heart: HeartSection?,
    /** Pace, speed, elevation, cadence and power curves, in the order the profile wants them. */
    val curves: List<WorkoutSeries>,
    val tables: List<WorkoutTable>,
    val effort: List<WorkoutMetric>,
    /** Every other summary value, so nothing the watch sent is hidden. */
    val more: List<WorkoutMetric>,
)

object WorkoutMath {

    /** Average [points] into at most [max] buckets so a long workout still draws quickly. */
    fun downsample(points: List<SeriesPoint>, max: Int = 240): List<SeriesPoint> {
        if (points.size <= max) return points
        val bucket = points.size.toDouble() / max
        return (0 until max).map { b ->
            val from = (b * bucket).toInt()
            val to = ((b + 1) * bucket).toInt().coerceAtMost(points.size).coerceAtLeast(from + 1)
            val slice = points.subList(from, to)
            SeriesPoint(slice.map { it.t }.average().toFloat(), slice.map { it.v }.average().toFloat())
        }
    }

    /**
     * Seconds spent in each zone 1..5 from a heart-rate curve, for watches that do not send their
     * own time-in-zone. Each sample counts until the next one; gaps longer than [maxGapSeconds]
     * (a paused or dropped-out stretch) count only [maxGapSeconds]. [lowerBounds] are the zone
     * floors in bpm; time below zone 1 is not counted.
     */
    fun secondsInZones(curve: List<SeriesPoint>, lowerBounds: List<Int>, maxGapSeconds: Float = 30f): DoubleArray {
        val out = DoubleArray(5)
        if (lowerBounds.size < 5) return out
        for (i in 0 until curve.size - 1) {
            val dt = (curve[i + 1].t - curve[i].t).coerceIn(0f, maxGapSeconds)
            val zone = zoneOf(curve[i].v, lowerBounds)
            if (zone > 0) out[zone - 1] += dt.toDouble()
        }
        return out
    }

    fun zoneOf(bpm: Float, lowerBounds: List<Int>): Int {
        for (z in 5 downTo 1) if (bpm >= lowerBounds[z - 1]) return z
        return 0
    }

    /** "1:07:16" or "47:15". */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    /** "1 h 7 min" or "47 min", for the workout list. */
    fun shortDuration(seconds: Long): String {
        val m = (seconds.coerceAtLeast(0) + 30) / 60
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    /**
     * The one-line summary under a workout in the list: distance first for distance sports,
     * otherwise duration, calories and average heart rate. Missing values are left out.
     */
    fun listLine(profile: WorkoutProfile, durationSeconds: Long, distance: String?, kcal: Int?, avgHr: Int?): String {
        val parts = mutableListOf<String>()
        if (profile.leadsWithDistance && distance != null) parts += distance
        parts += shortDuration(durationSeconds)
        if (kcal != null && kcal > 0) parts += "$kcal kcal"
        if (!profile.leadsWithDistance && avgHr != null && avgHr > 0) parts += "$avgHr bpm"
        return parts.joinToString(" · ")
    }
}
