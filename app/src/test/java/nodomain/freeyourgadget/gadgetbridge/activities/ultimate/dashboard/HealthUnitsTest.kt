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

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthUnitsTest {
    @Test
    fun distanceCentimetresToKm() {
        // 355,400 cm is 3.554 km, not 355.4 (the card used to divide by 1000).
        assertEquals(3.554, HealthUnits.distanceKm(355_400, 4719, 70), 1e-9)
    }

    @Test
    fun distanceFallsBackToStepLength() {
        assertEquals(3.3033, HealthUnits.distanceKm(0, 4719, 70), 1e-9)
    }

    @Test
    fun distanceZeroWithoutData() {
        assertEquals(0.0, HealthUnits.distanceKm(0, 0, 70), 0.0)
        assertEquals(0.0, HealthUnits.distanceKm(0, 100, 0), 0.0)
    }

    @Test
    fun activeKcalHonoursSmallCalories() {
        assertEquals(373.0, HealthUnits.activeKcal(373_000, true), 1e-9)
        assertEquals(373.0, HealthUnits.activeKcal(373, false), 1e-9)
    }

    @Test
    fun pathMetersOfOneDegreeOfLatitude() {
        // ~111.19 km per degree of latitude on the 6371 km sphere.
        val m = HealthUnits.pathMeters(listOf(40.0 to -3.0, 41.0 to -3.0))
        assertEquals(111_195.0, m, 100.0)
        assertEquals(0.0, HealthUnits.pathMeters(listOf(40.0 to -3.0)), 0.0)
    }
}
