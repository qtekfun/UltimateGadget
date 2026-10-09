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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Transparent, local "readiness" (recovery) score, 0..100.
 *
 * There is no single validated formula that combines these inputs — commercial scores (Oura,
 * Garmin Body Battery, Whoop) are proprietary — so this is a documented heuristic built from
 * published, open components, meant to be read and tweaked, not a medical metric:
 *
 *  - HRV vs baseline: today's ln(rMSSD) compared to a 7-day rolling mean, scaled by its SD
 *    (Plews' rolling baseline + "smallest worthwhile change" approach). Higher = more recovered.
 *  - Resting HR vs baseline: today's RHR compared to the 7-day mean/SD. Lower = more recovered.
 *  - Sleep: last night's duration vs a target need (default 8 h), optional quality.
 *  - Training load (ACWR, Gabbett): acute (last 7 days) / chronic (avg week over ~28 days).
 *    A ratio in the ~0.8–1.3 "sweet spot" scores best; spikes above ~1.5 are penalised.
 *
 * Each present factor is scored 0..100 and combined with documented weights, renormalised over the
 * factors that actually have data, so the score degrades gracefully. `confidence` reflects how many
 * factors were available. See docs/readiness.md for the formula and sources.
 */
object ReadinessEngine {

    // Documented weights (sum = 1.0 when all present; renormalised over available factors).
    private const val W_HRV = 0.30
    private const val W_RHR = 0.20
    private const val W_SLEEP = 0.30
    private const val W_LOAD = 0.20

    fun compute(inputs: ReadinessInputs): ReadinessData {
        val factors = mutableListOf<ReadinessFactor>()

        hrvScore(inputs)?.let {
            factors += ReadinessFactor(ReadinessFactorId.HRV, it.first, W_HRV, it.second)
        }
        rhrScore(inputs)?.let {
            factors += ReadinessFactor(ReadinessFactorId.RHR, it.first, W_RHR, it.second)
        }
        sleepScore(inputs)?.let {
            factors += ReadinessFactor(ReadinessFactorId.SLEEP, it.first, W_SLEEP, it.second)
        }
        loadScore(inputs)?.let {
            factors += ReadinessFactor(ReadinessFactorId.LOAD, it.first, W_LOAD, it.second)
        }

        if (factors.isEmpty()) {
            return ReadinessData(score = null, factors = emptyList(), confidence = 0)
        }
        val totalW = factors.sumOf { it.weight }
        val score = factors.sumOf { it.score * it.weight } / totalW
        // Confidence: how many of the 4 factors we had, lightly favouring the heavier ones.
        val confidence = (factors.sumOf { it.weight } / (W_HRV + W_RHR + W_SLEEP + W_LOAD) * 100).toInt()
        return ReadinessData(
            score = score.toInt().coerceIn(0, 100),
            factors = factors,
            confidence = confidence.coerceIn(0, 100),
        )
    }

    /** z of today vs baseline, clamped; maps to a 0..100 score around 70 at baseline. */
    private fun zToScore(z: Double, higherIsBetter: Boolean): Int {
        val c = z.coerceIn(-1.5, 1.5)
        val signed = if (higherIsBetter) c else -c
        return (70 + 20 * signed).toInt().coerceIn(0, 100)
    }

    private fun hrvScore(i: ReadinessInputs): Pair<Int, String>? {
        val today = i.hrvToday ?: return null
        val mean = i.hrvMean ?: return null
        val sd = i.hrvSd?.takeIf { it > 0.0 } ?: return null
        val z = (ln(today) - ln(mean)) / (sd / mean)   // ~ln-space deviation
        val s = zToScore(z, higherIsBetter = true)
        return s to "rMSSD ${today.toInt()} ms vs media 7d ${mean.toInt()} ms"
    }

    private fun rhrScore(i: ReadinessInputs): Pair<Int, String>? {
        val today = i.rhrToday ?: return null
        val mean = i.rhrMean ?: return null
        val sd = i.rhrSd?.takeIf { it > 0.0 } ?: return null
        val z = (today - mean) / sd
        val s = zToScore(z, higherIsBetter = false)
        return s to "FC reposo ${today.toInt()} vs media 7d ${mean.toInt()}"
    }

    private fun sleepScore(i: ReadinessInputs): Pair<Int, String>? {
        val hours = i.sleepHours ?: return null
        val need = if (i.sleepNeedHours > 0) i.sleepNeedHours else 8.0
        val ratio = hours / need
        var s = (ratio * 100).toInt()
        i.sleepQuality?.let { q -> s = (s * (0.75 + 0.25 * (q / 100.0))).toInt() } // optional quality 0..100
        val src = if (i.sleepManual) " (manual)" else ""
        return s.coerceIn(0, 100) to "%.1f h de %.0f h objetivo%s".format(hours, need, src)
    }

    private fun loadScore(i: ReadinessInputs): Pair<Int, String>? {
        val acute = i.acuteLoad ?: return null
        val chronicWeek = i.chronicWeeklyLoad?.takeIf { it > 0.0 } ?: run {
            // No chronic history yet: neutral-ish based only on whether there was any load.
            return 70 to "Carga reciente ${acute.toInt()} (sin histórico)"
        }
        val acwr = acute / chronicWeek
        // Sweet spot 0.8..1.3 -> 100; outside drops; big spikes penalised.
        val s = when {
            acwr in 0.8..1.3 -> 100
            acwr < 0.8 -> (100 - (0.8 - acwr) * 60).toInt()        // undertraining, mild
            acwr <= 1.5 -> (100 - (acwr - 1.3) * 150).toInt()      // slight spike
            else -> max(20.0, 70 - (acwr - 1.5) * 80).toInt()      // strong spike
        }
        val manual = if (i.loadManual) " (incluye manual)" else ""
        return s.coerceIn(0, 100) to "ACWR %.2f (agudo/%.0f crónico)%s".format(acwr, chronicWeek, manual)
    }

    /** Label for a 0..100 score. */
    fun label(score: Int): String = when {
        score >= 75 -> "Alta"
        score >= 55 -> "Media"
        score >= 35 -> "Baja"
        else -> "Muy baja"
    }

    /** min/max exported for tests. */
    fun clamp01to100(v: Int) = min(100, max(0, v))
}

enum class ReadinessFactorId { HRV, RHR, SLEEP, LOAD }

data class ReadinessFactor(
    val id: ReadinessFactorId,
    val score: Int,        // 0..100
    val weight: Double,
    val detail: String,
)

data class ReadinessInputs(
    val hrvToday: Double? = null,
    val hrvMean: Double? = null,
    val hrvSd: Double? = null,
    val rhrToday: Double? = null,
    val rhrMean: Double? = null,
    val rhrSd: Double? = null,
    val sleepHours: Double? = null,
    val sleepNeedHours: Double = 8.0,
    val sleepQuality: Int? = null,
    val sleepManual: Boolean = false,
    val acuteLoad: Double? = null,
    val chronicWeeklyLoad: Double? = null,
    val loadManual: Boolean = false,
)

data class ReadinessData(
    val score: Int?,              // null = not enough data
    val factors: List<ReadinessFactor>,
    val confidence: Int,          // 0..100
)
