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

import androidx.compose.ui.graphics.Color

/**
 * Colour category of one hole's strokes on the scorecard, by difference against par.
 *
 * The scale is read off the Huawei Health "Detalles" capture (par 3: 4 = yellow, 5 = orange,
 * 7 and 9 = red; par 4: 7 = red), i.e. +1 yellow, +2 orange, +3 or worse red. The capture shows
 * nothing at or under par, so those are not split further: they share one neutral category
 * (no invented birdie/eagle colours). The app itself has never coloured them either.
 */
enum class ScoreCategory { UNPLAYED, PAR_OR_BETTER, BOGEY, DOUBLE_BOGEY, TRIPLE_OR_WORSE }

object GolfScoreStyle {
    /** Pure mapping (strokes, par) -> category. Missing/zero strokes mean the hole was not played. */
    fun category(score: Int?, par: Int?): ScoreCategory {
        if (score == null || score <= 0) return ScoreCategory.UNPLAYED
        if (par == null || par <= 0) return ScoreCategory.PAR_OR_BETTER
        return when (score - par) {
            in Int.MIN_VALUE..0 -> ScoreCategory.PAR_OR_BETTER
            1 -> ScoreCategory.BOGEY
            2 -> ScoreCategory.DOUBLE_BOGEY
            else -> ScoreCategory.TRIPLE_OR_WORSE
        }
    }

    /** Border colour for a category, or null for "no coloured border" (par or better, unplayed). */
    fun borderColor(category: ScoreCategory, light: Boolean): Color? = when (category) {
        ScoreCategory.BOGEY -> if (light) Color(0xFFB8860B) else Color(0xFFFFD54F)
        ScoreCategory.DOUBLE_BOGEY -> if (light) Color(0xFFD9650B) else Color(0xFFFF9800)
        ScoreCategory.TRIPLE_OR_WORSE -> if (light) Color(0xFFC62828) else Color(0xFFFF5A4D)
        ScoreCategory.PAR_OR_BETTER, ScoreCategory.UNPLAYED -> null
    }

    /** Green used for the par numbers, as in the reference. */
    fun parColor(light: Boolean): Color = if (light) Color(0xFF2E7D32) else Color(0xFF6FCF73)

    /** Delay before the n-th row starts its entrance, capped so long cards stay snappy. */
    fun staggerDelayMs(index: Int, stepMs: Int = 45, maxMs: Int = 600): Int =
        (index * stepMs).coerceIn(0, maxMs)

    /*
     * TRAJECTORY ANIMATION - documented proposal (not implemented).
     *
     * The Huawei Health "Trayectoria" tab draws each stroke as a curve (white -> orange gradient),
     * the distance on the curve ("68 yd") and a ball that ends the path. That needs per-stroke
     * start/end coordinates and distance, which the watch only gets in its golf mini-app and
     * sends to the Huawei cloud; the persisted HUAWEI_WORKOUT_SECTIONS rows carry only per-hole
     * par/score/putts/penalty/fairway/swing data, and the round map is a single GPS walk track
     * drawn by the mapcore engine (drawTrack). Without stroke geometry there is nothing real to
     * animate, and inventing shots would break the "never invented" rule of this screen.
     *
     * Plan once stroke positions exist (Phase 3): a Canvas over the hole map, one Path per stroke,
     * progress p in [0,1] via Animatable (stroke i starts when i-1 ends), PathMeasure.getSegment
     * to cut the path, Brush.linearGradient(White -> Orange) along it, ball Circle at
     * getPosition(p*length), and the distance label fading in at p == 1. Gate with the same
     * animations-enabled check used by the scorecard (ANIMATOR_DURATION_SCALE == 0 -> p = 1).
     */
}
