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

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutProfileTest {

    @Test
    fun gymMachinesAreIndoorAndNeverShowAMap() {
        for (kind in listOf(ActivityKind.ELLIPTICAL_TRAINER, ActivityKind.TREADMILL, ActivityKind.INDOOR_CYCLING,
            ActivityKind.ROWING_MACHINE, ActivityKind.INDOOR_WALKING)) {
            val p = WorkoutProfile.of(kind)
            assertEquals(kind.name, WorkoutProfile.INDOOR, p)
            assertFalse(kind.name, p.showsMap(500))
        }
    }

    @Test
    fun distanceSportsAreOutdoor() {
        for (kind in listOf(ActivityKind.RUNNING, ActivityKind.WALKING, ActivityKind.HIKING, ActivityKind.CYCLING)) {
            assertEquals(kind.name, WorkoutProfile.OUTDOOR, WorkoutProfile.of(kind))
        }
        assertTrue(WorkoutProfile.OUTDOOR.showsMap(2))
        assertFalse(WorkoutProfile.OUTDOOR.showsMap(1))
    }

    @Test
    fun swimGolfAndTheRest() {
        assertEquals(WorkoutProfile.SWIM, WorkoutProfile.of(ActivityKind.POOL_SWIM))
        assertEquals(WorkoutProfile.SWIM, WorkoutProfile.of(ActivityKind.SWIMMING_OPENWATER))
        assertEquals(WorkoutProfile.GOLF, WorkoutProfile.of(ActivityKind.GOLF))
        assertEquals(WorkoutProfile.GENERIC, WorkoutProfile.of(ActivityKind.STRENGTH_TRAINING))
        assertEquals(WorkoutProfile.GENERIC, WorkoutProfile.of(ActivityKind.UNKNOWN))
    }

    @Test
    fun listLineLeadsWithDistanceOnlyForDistanceSports() {
        assertEquals("5 km · 1 h 7 min · 380 kcal",
            WorkoutMath.listLine(WorkoutProfile.OUTDOOR, 4036, "5 km", 380, 120))
        // The elliptical of the bug report: no "0.0 km", heart rate instead.
        assertEquals("47 min · 393 kcal · 121 bpm",
            WorkoutMath.listLine(WorkoutProfile.INDOOR, 2815, null, 393, 121))
        assertEquals("47 min", WorkoutMath.listLine(WorkoutProfile.GENERIC, 2815, null, null, 0))
    }

    @Test
    fun clockFormats() {
        assertEquals("47:15", WorkoutMath.clock(2835))
        assertEquals("1:07:16", WorkoutMath.clock(4036))
    }

    @Test
    fun secondsInZonesCountsUntilNextSampleAndCapsGaps() {
        val bounds = listOf(100, 120, 140, 160, 180)
        val curve = listOf(
            SeriesPoint(0f, 90f),    // below zone 1: not counted
            SeriesPoint(5f, 110f),   // zone 1 for 5 s
            SeriesPoint(10f, 150f),  // zone 3, then a 100 s gap capped to 30 s
            SeriesPoint(110f, 185f), // last sample: no duration
        )
        assertArrayEquals(doubleArrayOf(5.0, 0.0, 30.0, 0.0, 0.0), WorkoutMath.secondsInZones(curve, bounds), 1e-6)
    }

    @Test
    fun downsampleKeepsShortSeriesAndAveragesLongOnes() {
        val short = (0 until 10).map { SeriesPoint(it.toFloat(), it.toFloat()) }
        assertEquals(short, WorkoutMath.downsample(short))
        val long = (0 until 1000).map { SeriesPoint(it.toFloat(), 100f) }
        val d = WorkoutMath.downsample(long, 100)
        assertEquals(100, d.size)
        assertTrue(d.all { it.v == 100f })
        assertTrue(d.zipWithNext().all { (a, b) -> b.t > a.t })
    }
}
