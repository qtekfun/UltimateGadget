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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries

/**
 * Golf-specific helpers for the new UI.
 *
 * Golf rounds recorded on the Huawei Watch GT Runner 2 reach Gadgetbridge through the ordinary
 * workout-summary channel, but the summary "type" byte is not in upstream's `HuaweiActivityType`
 * table, so [HuaweiWorkoutGbParser.huaweiTypeToGbType] maps it to [ActivityKind.UNKNOWN] and the
 * round shows up as "actividad desconocida".
 *
 * The real, round-by-round golf stats (holes, par, score, putts, swing data, per-hole distances)
 * are NOT part of this channel: on Huawei they are produced by the watch golf mini-app together
 * with the Huawei cloud (proprietary format), which UltimateGadget deliberately does not talk to.
 * So what is actually available for a golf round is only the generic workout summary (duration,
 * calories, steps, heart-rate, etc.). [GolfRound.fromSummary] exposes exactly that and nothing it
 * cannot back up with real data.
 */
object UltimateGolf {

    /**
     * Raw Huawei workout-summary "type" bytes (0-255) that mean "golf" and must be shown as
     * [ActivityKind.GOLF]. Huawei's own universal sport-type id for golf is 263, but the on-wire
     * summary field is a single byte, so that id cannot be the value seen here; the real byte must
     * be read from an actual GT Runner 2 golf round.
     *
     * This was not captured during the golf spike (only the P2P golf-course mode was exercised, no
     * recorded round was synced), so the set is intentionally conservative: add the byte once it is
     * known. The golf detail screen prints the raw type of whatever workout opened it ("tipo Huawei
     * N"), so opening an unknown round reveals the value to put here. Keep this the single source of
     * truth together with `HuaweiWorkoutGbParser.HUAWEI_GOLF_WORKOUT_TYPES`.
     */
    val GOLF_WORKOUT_TYPES: Set<Int> = emptySet()

    /** The raw Huawei workout type byte (0-255) stored in the summary, or null if absent. */
    fun rawHuaweiType(summary: BaseActivitySummary?): Int? {
        summary ?: return null
        val data = runCatching { ActivitySummaryData.fromJson(summary.summaryData) }.getOrNull()
            ?: return null
        if (!data.has(ActivitySummaryEntries.TYPE)) return null
        return data.getNumber(ActivitySummaryEntries.TYPE, null)?.toInt()
    }

    /**
     * Whether a workout is golf. True when the stored [ActivityKind] is already GOLF, when the raw
     * Huawei type is a known golf byte, or when the device-provided name says so (some watches /
     * locales name the workout). Byte-independent name matching means golf is caught even before
     * [GOLF_WORKOUT_TYPES] is filled in.
     */
    fun isGolf(activityKindCode: Int?, rawHuaweiType: Int?, name: String?): Boolean {
        if (activityKindCode != null && activityKindCode == ActivityKind.GOLF.code) return true
        if (rawHuaweiType != null && rawHuaweiType in GOLF_WORKOUT_TYPES) return true
        val n = name?.trim()?.lowercase().orEmpty()
        return n.isNotEmpty() && (n == "golf" || n.contains("golf"))
    }

    fun isGolf(summary: BaseActivitySummary?): Boolean {
        summary ?: return false
        return isGolf(summary.activityKind, rawHuaweiType(summary), summary.name)
    }
}

/** One stat ready to render, with a localized label, a formatted value and whether it is real. */
data class GolfStat(val label: String, val value: String)

/**
 * The honest data of one golf round, built only from the real workout summary. Anything the watch
 * does not send is left null and surfaced as "no disponible" by the UI, never invented.
 */
data class GolfRound(
    val title: String,
    val whenLabel: String,
    val durationSeconds: Long,
    val rawHuaweiType: Int?,
    val stats: List<GolfStat>,
    /**
     * True only if the summary actually carried golf-specific per-hole/scoring fields. With the
     * current Huawei integration this is always false (that data is proprietary and not sent), and
     * the UI says so plainly.
     */
    val hasScorecard: Boolean,
) {
    companion object {
        private fun ActivitySummaryData.num(key: String): Double? =
            if (has(key)) getNumber(key, null)?.toDouble() else null

        /** Golf-specific summary keys we would show if a device ever sent them. */
        private val SCORECARD_KEYS = listOf(
            "holes", "golfHoles", "par", "golfPar", "score", "golfScore", "putts", "golfPutts",
            "swings", "golfSwingCount", "fairwayHits", "gir",
        )

        fun fromSummary(summary: BaseActivitySummary): GolfRound {
            val data = runCatching { ActivitySummaryData.fromJson(summary.summaryData) }.getOrNull()
            val start = summary.startTime
            val end = summary.endTime
            val dur = if (start != null && end != null)
                ((end.time - start.time) / 1000).coerceAtLeast(0L) else 0L

            val stats = mutableListOf<GolfStat>()
            // Duration is always meaningful for a round.
            stats += GolfStat("Duración", formatDuration(dur))

            if (data != null) {
                data.num(ActivitySummaryEntries.DISTANCE_METERS)?.takeIf { it > 0 }?.let {
                    stats += GolfStat("Distancia caminada", formatDistance(it))
                }
                data.num(ActivitySummaryEntries.STEPS)?.takeIf { it > 0 }?.let {
                    stats += GolfStat("Pasos", "%,d".format(it.toLong()))
                }
                data.num(ActivitySummaryEntries.CALORIES_BURNT)?.takeIf { it > 0 }?.let {
                    stats += GolfStat("Calorías", "%,d kcal".format(it.toLong()))
                }
                data.num(ActivitySummaryEntries.HR_AVG)?.takeIf { it > 0 }?.let {
                    stats += GolfStat("Pulso medio", "${it.toInt()} bpm")
                }
                val hrMin = data.num(ActivitySummaryEntries.HR_MIN)?.takeIf { it > 0 }
                val hrMax = data.num(ActivitySummaryEntries.HR_MAX)?.takeIf { it > 0 }
                if (hrMin != null || hrMax != null) {
                    stats += GolfStat(
                        "Pulso mín/máx",
                        "${hrMin?.toInt() ?: "—"} / ${hrMax?.toInt() ?: "—"} bpm",
                    )
                }
                data.num(ActivitySummaryEntries.WORKOUT_LOAD)?.takeIf { it > 0 }?.let {
                    stats += GolfStat("Carga de entreno", "${it.toInt()}")
                }
            }

            val hasScorecard = data != null && SCORECARD_KEYS.any { data.has(it) }
            if (hasScorecard) {
                // Defensive: if some future device/firmware does send scoring fields, surface them
                // instead of hiding them. Not expected with the current Huawei integration.
                data!!.num("holes")?.let { stats += GolfStat("Hoyos", "${it.toInt()}") }
                data.num("golfHoles")?.let { stats += GolfStat("Hoyos", "${it.toInt()}") }
                data.num("par")?.let { stats += GolfStat("Par", "${it.toInt()}") }
                data.num("golfPar")?.let { stats += GolfStat("Par", "${it.toInt()}") }
                data.num("score")?.let { stats += GolfStat("Score", "${it.toInt()}") }
                data.num("golfScore")?.let { stats += GolfStat("Score", "${it.toInt()}") }
                data.num("putts")?.let { stats += GolfStat("Putts", "${it.toInt()}") }
                data.num("golfPutts")?.let { stats += GolfStat("Putts", "${it.toInt()}") }
                data.num("golfSwingCount")?.let { stats += GolfStat("Golpes", "${it.toInt()}") }
            }

            return GolfRound(
                title = summary.name?.takeIf { it.isNotBlank() } ?: "Ronda de golf",
                whenLabel = start?.let { FMT.format(it) }.orEmpty(),
                durationSeconds = dur,
                rawHuaweiType = UltimateGolf.rawHuaweiType(summary),
                stats = stats,
                hasScorecard = hasScorecard,
            )
        }

        private val FMT = java.text.SimpleDateFormat("d MMM yyyy · HH:mm", java.util.Locale.getDefault())

        private fun formatDuration(sec: Long): String =
            String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)

        private fun formatDistance(meters: Double): String =
            if (meters >= 1000) String.format(java.util.Locale.getDefault(), "%.2f km", meters / 1000.0)
            else String.format(java.util.Locale.getDefault(), "%d m", meters.toInt())
    }
}
