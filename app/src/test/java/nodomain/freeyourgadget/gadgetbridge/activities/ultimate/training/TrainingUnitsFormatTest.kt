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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TrainingUnitsFormatTest {
    private val spanish = Locale("es", "ES")
    private var saved: Locale = Locale.getDefault()

    @Before fun remember() { saved = Locale.getDefault() }
    @After fun restore() { Locale.setDefault(saved) }

    @Test fun spanishUsesTheDecimalComma() {
        val u = TrainingUnits(false, spanish)
        assertEquals("122,5 kg", u.weightWithUnit(122.5, "kg"))
        assertEquals("80 kg", u.weightWithUnit(80.0, "kg"))
        assertEquals("82,25 kg", u.weightWithUnit(82.25, "kg"))
    }

    @Test fun englishKeepsTheDecimalPoint() {
        assertEquals("122.5 kg", TrainingUnits(false, Locale.US).weightWithUnit(122.5, "kg"))
    }

    @Test fun bigNumbersHaveNoGroupingSeparator() {
        assertEquals("12500 kg", TrainingUnits(false, spanish).weightWithUnit(12500.0, "kg"))
        assertEquals("12500 kg", TrainingUnits(false, Locale.US).weightWithUnit(12500.0, "kg"))
    }

    @Test fun poundsAreConvertedAndFormatted() {
        assertEquals("220,46 lb", TrainingUnits(true, spanish).weightWithUnit(100.0, "lb"))
    }

    @Test fun distanceUsesTheLocaleToo() {
        assertEquals("1,5 km", TrainingUnits(false, spanish).distanceWithUnit(1500.0, "km"))
        assertEquals("1.5 km", TrainingUnits(false, Locale.US).distanceWithUnit(1500.0, "km"))
    }

    @Test fun defaultFollowsTheDeviceLocaleAtCallTime() {
        val u = TrainingUnits(false)
        Locale.setDefault(spanish)
        assertEquals("97,5 kg", u.weightWithUnit(97.5, "kg"))
        Locale.setDefault(Locale.US)
        assertEquals("97.5 kg", u.weightWithUnit(97.5, "kg"))
    }

    @Test fun describePastSetUsesTheFormattedWeight() {
        val u = TrainingUnits(false, spanish)
        val set = SessionSet(reps = 5, weightKg = 122.5, done = true)
        assertEquals("122,5 kg x 5", TrainingMath.describe(set, Measure.REPS_WEIGHT) { u.weightWithUnit(it, "kg") })
    }

    @Test fun inputFieldsKeepTheDotAndParseBothSeparators() {
        val u = TrainingUnits(false, spanish)
        assertEquals("122.5", u.weightText(122.5))
        assertEquals(122.5, u.parseWeight("122,5")!!, 0.0001)
        assertEquals(122.5, u.parseWeight("122.5")!!, 0.0001)
    }
}
