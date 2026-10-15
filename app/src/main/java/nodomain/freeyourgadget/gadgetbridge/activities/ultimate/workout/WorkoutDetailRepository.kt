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

import nodomain.freeyourgadget.gadgetbridge.R
import android.content.Context
import com.qtekfun.mapcore.LatLon
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.HealthUnits
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf.UltimateGolf
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts.DefaultWorkoutCharts
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryTableRowEntry
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries as E
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesResolver
import nodomain.freeyourgadget.gadgetbridge.model.workout.Workout
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Builds a [WorkoutDetail] for one workout, read-only, from what Gadgetbridge already has: the
 * device's summary parser (summary values, zones, splits) and its activity-track provider (the
 * per-sample heart rate, speed, cadence, altitude and GPS). Nothing is written back.
 */
class WorkoutDetailRepository(context: Context) {

    // Gadgetbridge applies its language override to the application context only.
    private val ctx: Context = GBApplication.getContext() ?: context.applicationContext

    fun load(summaryId: Long, device: GBDevice?): WorkoutDetail? {
        val summary = runCatching {
            GBApplication.acquireDB().use { it.daoSession.baseActivitySummaryDao.load(summaryId) }
        }.getOrNull() ?: return null

        val kind = kindOf(summary)
        val profile = WorkoutProfile.of(kind)
        val workout: Workout? = device?.let { dev ->
            runCatching { dev.deviceCoordinator.getActivitySummaryParser(dev, ctx).parseWorkout(summary, true) }
                .onFailure { LOG.warn("Failed to parse workout {}", summaryId, it) }
                .getOrNull()
        }
        val data = workout?.data ?: summaryData(summary)
        val points: List<ActivityPoint> = device?.let { dev ->
            runCatching {
                dev.deviceCoordinator.getActivityTrackProvider(dev, ctx).getActivityTrack(summary)?.allPoints
            }.onFailure { LOG.warn("Failed to load track for workout {}", summaryId, it) }.getOrNull()
        }.orEmpty().filter { it.time != null }.sortedBy { it.time }

        return Builder(summary, kind, profile, data, workout, points, device).build()
    }

    private inner class Builder(
        private val summary: BaseActivitySummary,
        private val kind: ActivityKind,
        private val profile: WorkoutProfile,
        private val data: ActivitySummaryData,
        private val workout: Workout?,
        private val points: List<ActivityPoint>,
        private val device: GBDevice?,
    ) {
        private val fmt = WorkoutValueFormatter(kind)
        private val consumed = HashSet<String>()
        private val startMs = summary.startTime?.time ?: points.firstOrNull()?.time?.time ?: 0L

        private fun entry(key: String): ActivitySummarySimpleEntry? = data.get(key) as? ActivitySummarySimpleEntry

        private fun number(key: String): Double? = (entry(key)?.value as? Number)?.toDouble()

        /**
         * The summary value [key] as a metric, or null when it is missing (or not positive, unless
         * [allowZero]). Every key looked at is marked used so ctx.getString(R.string.ult_more_data) does not repeat it.
         */
        private fun metric(key: String, label: String, allowZero: Boolean = false): WorkoutMetric? {
            consumed += key
            val e = entry(key) ?: return null
            val v = e.value ?: return null
            if (v is Number && (v.toDouble() < 0 || (!allowZero && v.toDouble() == 0.0))) return null
            return WorkoutMetric(label, fmt.formatTileValue(v, e.unit))
        }

        private fun firstMetric(label: String, vararg keys: String): WorkoutMetric? {
            var found: WorkoutMetric? = null
            for (k in keys) {
                val m = metric(k, label)
                if (found == null) found = m
            }
            return found
        }

        private val track: List<LatLon> = points.mapNotNull { p ->
            p.location?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }?.let { LatLon(it.latitude, it.longitude) }
        }

        fun build(): WorkoutDetail {
            listOf(E.INTERNAL_HAS_GPS, E.TYPE, E.HR_ZONE_NA).forEach { consumed += it }
            val durationSeconds = number(E.ACTIVE_SECONDS)?.toLong()?.takeIf { it > 0 }
                ?: (((summary.endTime?.time ?: startMs) - startMs) / 1000)
            consumed += E.ACTIVE_SECONDS
            val duration = WorkoutMetric(ctx.getString(R.string.ult_wd_duration), WorkoutMath.clock(durationSeconds))

            val distance = metric(E.DISTANCE_METERS, ctx.getString(R.string.ult_wd_distance))
                ?: if (profile.leadsWithDistance && track.size >= 2) {
                    val m = HealthUnits.pathMeters(track.map { it.lat to it.lon })
                    if (m > 0) WorkoutMetric(ctx.getString(R.string.ult_wd_distance), fmt.formatTileValue(m, E.UNIT_METERS)) else null
                } else null
            val isPace = ActivityKind.isPaceActivity(kind) || profile == WorkoutProfile.SWIM
            val avgPace = if (isPace) metric(E.PACE_AVG_SECONDS_KM, ctx.getString(R.string.ult_wd_avg_pace)) else metric(E.SPEED_AVG, ctx.getString(R.string.ult_wd_avg_speed))
            val maxPace = if (isPace) metric(E.PACE_MAX, ctx.getString(R.string.ult_wd_best_pace)) else metric(E.SPEED_MAX, ctx.getString(R.string.ult_wd_max_speed))
            val kcal = metric(E.CALORIES_BURNT, ctx.getString(R.string.ult_wd_calories))
            val avgHr = metric(E.HR_AVG, ctx.getString(R.string.ult_wd_avg_hr))
            val total = firstMetric(cycleTotalLabel(), E.STEPS, E.STROKES, E.JUMPS)
            val cadence = firstMetric(if (profile == WorkoutProfile.SWIM) ctx.getString(R.string.ult_wd_avg_stroke) else ctx.getString(R.string.ult_wd_avg_cadence),E.CADENCE_AVG, E.STEP_RATE_AVG, E.STROKE_RATE_AVG, E.JUMP_RATE_AVG)
            val power = metric(E.CYCLING_POWER_AVERAGE, ctx.getString(R.string.ult_wd_avg_power))

            val (hero, secondary) = when (profile) {
                WorkoutProfile.OUTDOOR -> listOf(
                    distance, duration, avgPace, metric(E.ELEVATION_GAIN, ctx.getString(R.string.ult_wd_elev_gain)),
                ) to listOf(kcal, avgHr, total, cadence, maxPace, metric(E.ELEVATION_LOSS, ctx.getString(R.string.ult_wd_elev_loss)), power)
                WorkoutProfile.INDOOR -> listOf(
                    duration, kcal, avgHr,
                ) to listOf(total, cadence, distance, if (distance != null) avgPace else null, power)
                WorkoutProfile.SWIM -> listOf(
                    distance, duration, avgPace, metric(E.LAPS, ctx.getString(R.string.ult_wd_lengths)),
                ) to listOf(
                    metric(E.SWOLF_AVG, ctx.getString(R.string.ult_wd_avg_swolf)), cadence, total, metric(E.SWIM_STYLE, ctx.getString(R.string.ult_wd_style)),
                    metric(E.LANE_LENGTH, ctx.getString(R.string.ult_wd_pool_length)), kcal, avgHr,
                )
                WorkoutProfile.GENERIC, WorkoutProfile.GOLF -> listOf(
                    duration, kcal, avgHr,
                ) to listOf(total, distance, cadence)
            }

            val heart = heartSection()
            val effort = listOfNotNull(
                metric(E.WORKOUT_LOAD, ctx.getString(R.string.ult_training_load)),
                metric(E.TRAINING_EFFECT_AEROBIC, ctx.getString(R.string.ult_wd_aerobic), allowZero = true),
                metric(E.TRAINING_EFFECT_ANAEROBIC, ctx.getString(R.string.ult_wd_anaerobic), allowZero = true),
                metric(E.TRAINING_EFFECT_TOTAL, ctx.getString(R.string.ult_wd_training_effect), allowZero = true),
                metric(E.RECOVERY_TIME, ctx.getString(R.string.ult_wd_recovery_time)),
                metric(E.MAXIMUM_OXYGEN_UPTAKE, "VO₂max"),
            )

            val start = summary.startTime
            val end = summary.endTime
            val typeLabel = runCatching { kind.getLabel(ctx) }.getOrNull().orEmpty().ifEmpty { ctx.getString(R.string.ult_workout) }
            return WorkoutDetail(
                summaryId = summary.id ?: -1L,
                profile = profile,
                typeLabel = typeLabel,
                title = summary.name?.takeIf { it.isNotBlank() } ?: typeLabel,
                iconRes = kind.icon,
                whenLabel = buildString {
                    if (start != null) append(DAY_FMT.format(start)).append(" · ").append(TIME_FMT.format(start))
                    if (end != null) append("–").append(TIME_FMT.format(end))
                },
                hero = hero.filterNotNull(),
                secondary = secondary.filterNotNull().distinct(),
                track = if (profile.showsMap(track.size)) track else emptyList(),
                heart = heart,
                curves = curves(),
                tables = tables(),
                effort = effort,
                more = moreData(),
            )
        }

        private fun cycleTotalLabel(): String = when (ActivityKind.getCycleUnit(kind)) {
            ActivityKind.CycleUnit.STROKES -> ctx.getString(R.string.ult_wd_strokes)
            ActivityKind.CycleUnit.JUMPS -> ctx.getString(R.string.ult_wd_jumps)
            else -> ctx.getString(R.string.ult_wd_steps)
        }

        private fun t(p: ActivityPoint): Float = (p.time.time - startMs) / 1000f

        // ---- Heart rate ------------------------------------------------------------------

        private fun heartSection(): HeartSection? {
            val stats = listOfNotNull(
                metric(E.HR_AVG, ctx.getString(R.string.ult_wd_avg)), metric(E.HR_MAX, ctx.getString(R.string.ult_wd_max)), metric(E.HR_MIN, ctx.getString(R.string.ult_wd_min)),
            )
            val raw = points.filter { it.heartRate > 0 }.map { SeriesPoint(t(it), it.heartRate.toFloat()) }
            val zonesCfg = runCatching { HeartRateZonesResolver.resolve(data, device, ActivityUser()) }.getOrNull()
            val bounds = zonesCfg?.let { listOf(it.zone1, it.zone2, it.zone3, it.zone4, it.zone5) }.orEmpty()

            var zones = watchZones()
            if (zones.isEmpty() && raw.size >= 2 && bounds.size == 5) {
                val secs = WorkoutMath.secondsInZones(raw, bounds)
                zones = secs.mapIndexed { i, s -> HrZoneTime(i + 1, HeartRateZonesResolver.labelForZone(ctx, i + 1), s) }
            }
            val recovery = workout?.charts.orEmpty()
                .firstOrNull { it.group == E.GROUP_RECOVERY_HEART_RATE }
                ?.chartData?.dataSets.orEmpty()
                .flatMap { ds -> (0 until ds.entryCount).map { ds.getEntryForIndex(it).y } }
                .filter { it > 0f }

            if (stats.isEmpty() && raw.size < 2 && zones.isEmpty()) return null
            val curve = raw.takeIf { it.size >= 2 }?.let {
                WorkoutSeries(ctx.getString(R.string.ult_heart_rate), ctx.getString(R.string.ult_bpm), WorkoutMath.downsample(it), { v -> v.toInt().toString() })
            }
            return HeartSection(stats, curve, bounds, zones.takeIf { z -> z.any { it.seconds > 0 } }.orEmpty(), recovery)
        }

        /** Time in zone as the watch measured it, easiest zone first. */
        private fun watchZones(): List<HrZoneTime> {
            val present = ZONE_KEYS.filter { data.has(it) }
            ZONE_KEYS.forEach { consumed += it }
            return present.mapIndexedNotNull { i, key ->
                val secs = number(key) ?: return@mapIndexedNotNull null
                HrZoneTime((i + 1).coerceAtMost(5), fmt.getStringResourceByName(key), secs)
            }
        }

        // ---- Curves ----------------------------------------------------------------------

        private fun curves(): List<WorkoutSeries> {
            val speed = speedCurve()
            val elevation = elevationCurve()
            val cadence = cadenceCurve()
            val power = series(ctx.getString(R.string.ult_wd_power), "W", points.filter { it.power > 0 }.map { SeriesPoint(t(it), it.power) }) { "%.0f".format(it) }
            return when (profile) {
                WorkoutProfile.OUTDOOR -> listOf(speed, elevation, cadence, power)
                WorkoutProfile.INDOOR -> listOf(cadence, power, speed)
                WorkoutProfile.SWIM -> listOf(speed)
                WorkoutProfile.GENERIC -> listOf(speed, elevation, cadence, power)
                WorkoutProfile.GOLF -> emptyList()
            }.filterNotNull()
        }

        private fun series(
            title: String, unit: String, raw: List<SeriesPoint>, inverted: Boolean = false, format: (Float) -> String,
        ): WorkoutSeries? {
            if (raw.size < 2) return null
            val avg = raw.map { it.v }.average().toFloat()
            val best = if (inverted) raw.minOf { it.v } else raw.maxOf { it.v }
            return WorkoutSeries(
                title, unit, WorkoutMath.downsample(raw), format, inverted,
                listOf(WorkoutMetric(ctx.getString(R.string.ult_wd_avg), format(avg)), WorkoutMetric(if (inverted) ctx.getString(R.string.ult_wd_best) else ctx.getString(R.string.ult_wd_max), format(best))),
            )
        }

        private fun speedCurve(): WorkoutSeries? {
            val moving = points.filter { it.speed > 0f }
            if (moving.size < 2) return null
            if (profile == WorkoutProfile.SWIM || ActivityKind.isPaceActivity(kind)) {
                val swim = profile == WorkoutProfile.SWIM
                val (perMeters, unitToken) = if (swim) 100.0 to E.UNIT_SECONDS_PER_100_METERS else 1000.0 to E.UNIT_SECONDS_PER_KM
                // Drop standing-still samples: a walk paused at a crossing would be a 2 h/km pace.
                val raw = moving.filter { it.speed >= if (swim) 0.2f else 0.5f }.map { p ->
                    val c = fmt.convert(perMeters / p.speed, unitToken, true)
                    SeriesPoint(t(p), c.value.toFloat())
                }
                val unit = fmt.getUnitString(fmt.convert(0.0, unitToken, true).unit)
                return series(ctx.getString(R.string.ult_wd_pace), unit, raw, inverted = true) { v ->
                    val s = Math.round(v * 60)
                    String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
                }
            }
            val raw = moving.map { p -> SeriesPoint(t(p), fmt.convert(p.speed.toDouble(), E.UNIT_METERS_PER_SECOND, true).value.toFloat()) }
            val unit = fmt.getUnitString(fmt.convert(0.0, E.UNIT_METERS_PER_SECOND, true).unit)
            return series(ctx.getString(R.string.ult_wd_speed), unit, raw) { "%.1f".format(it) }
        }

        private fun elevationCurve(): WorkoutSeries? {
            val raw = points.filter { it.altitude > GPSCoordinate.UNKNOWN_ALTITUDE }
                .map { SeriesPoint(t(it), fmt.convert(it.altitude, E.UNIT_METERS, true).value.toFloat()) }
            // A barometer on a gym bike reports one constant altitude; that is not a profile.
            if (raw.size < 2 || raw.maxOf { it.v } - raw.minOf { it.v } < 3f) return null
            val unit = fmt.getUnitString(fmt.convert(0.0, E.UNIT_METERS, true).unit)
            return series(ctx.getString(R.string.ult_wd_elevation), unit, raw) { "%.0f".format(it) }?.let { s ->
                s.copy(stats = listOf(
                    WorkoutMetric(ctx.getString(R.string.ult_wd_min), s.format(raw.minOf { it.v })), WorkoutMetric(ctx.getString(R.string.ult_wd_max), s.format(raw.maxOf { it.v })),
                ))
            }
        }

        private fun cadenceCurve(): WorkoutSeries? {
            val raw = points.filter { it.cadence > 0 }.map { SeriesPoint(t(it), it.cadence.toFloat()) }
            val unit = fmt.getUnitString(DefaultWorkoutCharts.getCadenceUnit(ActivityKind.getCycleUnit(kind)))
            return series(ctx.getString(R.string.ult_wd_cadence), unit, raw) { it.toInt().toString() }
        }

        // ---- Tables and the rest ---------------------------------------------------------

        private fun tables(): List<WorkoutTable> {
            val groups = LinkedHashMap<String, MutableList<Pair<String, ActivitySummaryTableRowEntry>>>()
            for (key in data.keys) {
                val row = data.get(key) as? ActivitySummaryTableRowEntry ?: continue
                consumed += key
                groups.getOrPut(row.group ?: "") { mutableListOf() } += key to row
            }
            return groups.mapNotNull { (group, rows) ->
                val cells = rows.map { (_, r) -> r.isHeader to r.columns.map { c -> c.format(fmt) } }
                val header = cells.firstOrNull { it.first }?.second.orEmpty()
                val body = cells.filterNot { it.first }.map { it.second }
                if (body.isEmpty()) null
                else WorkoutTable(tableTitle(group), header, body)
            }
        }

        private fun tableTitle(group: String): String = when {
            profile == WorkoutProfile.SWIM -> ctx.getString(R.string.ult_wd_lengths)
            group == E.GROUP_PACE -> ctx.getString(R.string.ult_wd_splits)
            group.isEmpty() -> ctx.getString(R.string.ult_wd_laps)
            else -> fmt.getStringResourceByName(group)
        }

        private fun moreData(): List<WorkoutMetric> = data.keys
            .filterNot { it in consumed || it.startsWith("internal") || it.startsWith(E.HR_ZONE_BOUND_PREFIX) }
            .mapNotNull { key ->
                val e = entry(key) ?: return@mapNotNull null
                val v = e.value ?: return@mapNotNull null
                WorkoutMetric(fmt.getStringResourceByName(key), fmt.formatValue(v, e.unit))
            }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WorkoutDetailRepository::class.java)
        private val DAY_FMT = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
        private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())

        /** Zone keys from the easiest to the hardest, across the naming schemes devices use. */
        private val ZONE_KEYS = listOf(
            E.HR_ZONE_WARM_UP, E.HR_ZONE_EASY, E.HR_ZONE_FAT_BURN, E.HR_ZONE_AEROBIC,
            E.HR_ZONE_ANAEROBIC, E.HR_ZONE_THRESHOLD, E.HR_ZONE_EXTREME, E.HR_ZONE_MAXIMUM,
        )

        /** The workout's kind, with golf rounds (sent with a type upstream never mapped) as GOLF. */
        fun kindOf(summary: BaseActivitySummary): ActivityKind {
            val raw = runCatching { ActivityKind.fromCode(summary.activityKind ?: ActivityKind.UNKNOWN.code) }
                .getOrDefault(ActivityKind.UNKNOWN)
            return if (UltimateGolf.isGolf(raw.code, UltimateGolf.rawHuaweiType(summary), summary.name)) ActivityKind.GOLF else raw
        }

        private fun summaryData(summary: BaseActivitySummary): ActivitySummaryData =
            runCatching { ActivitySummaryData.fromJson(summary.summaryData) }.getOrNull() ?: ActivitySummaryData()

        /**
         * The short line under a workout in lists, from its stored summary only (no parsing):
         * "5.0 km · 1 h 7 min · 380 kcal" or "47 min · 393 kcal · 121 bpm".
         */
        fun listLine(summary: BaseActivitySummary, kind: ActivityKind = kindOf(summary)): String {
            val profile = WorkoutProfile.of(kind)
            val data = summaryData(summary)
            val start = summary.startTime?.time
            val end = summary.endTime?.time
            val duration = data.getNumber(E.ACTIVE_SECONDS, null)?.toLong()?.takeIf { it > 0 }
                ?: if (start != null && end != null) (end - start) / 1000 else 0L
            val meters = data.getNumber(E.DISTANCE_METERS, null)?.toDouble()?.takeIf { it > 0 }
            val distance = meters?.let { WorkoutValueFormatter(kind).formatTileValue(it, E.UNIT_METERS) }
            return WorkoutMath.listLine(
                profile, duration, distance,
                data.getNumber(E.CALORIES_BURNT, null)?.toInt(),
                data.getNumber(E.HR_AVG, null)?.toInt(),
            )
        }
    }
}
