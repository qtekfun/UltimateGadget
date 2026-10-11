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

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit handling for the health cards. Gadgetbridge stores sample distance in CENTIMETRES and the
 * Huawei protocol reports active calories in small calories (the app itself sends goals x1000),
 * so the raw totals cannot be shown as metres / kcal directly.
 */
object HealthUnits {
    /**
     * Distance in km from a day's raw total in centimetres. Devices that do not report distance
     * (total 0) fall back to steps x step length, like the steps chart does.
     */
    fun distanceKm(distanceCm: Long, steps: Long, stepLengthCm: Int): Double {
        val cm = if (distanceCm > 0) distanceCm.toDouble()
        else if (steps > 0 && stepLengthCm > 0) steps.toDouble() * stepLengthCm
        else 0.0
        return cm / 100_000.0
    }

    /** Active calories in kcal from the raw sample total; [smallCalories] for devices reporting cal. */
    fun activeKcal(raw: Long, smallCalories: Boolean): Double =
        if (smallCalories) raw / 1000.0 else raw.toDouble()

    /** Length in metres of a lat/lon path (haversine over consecutive points). */
    fun pathMeters(points: List<Pair<Double, Double>>): Double {
        var m = 0.0
        for (i in 1 until points.size) {
            val (la1, lo1) = points[i - 1]
            val (la2, lo2) = points[i]
            val dLat = Math.toRadians(la2 - la1)
            val dLon = Math.toRadians(lo2 - lo1)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(la1)) * cos(Math.toRadians(la2)) * sin(dLon / 2) * sin(dLon / 2)
            m += 2 * 6371000.0 * atan2(sqrt(h), sqrt(1 - h))
        }
        return m
    }
}
