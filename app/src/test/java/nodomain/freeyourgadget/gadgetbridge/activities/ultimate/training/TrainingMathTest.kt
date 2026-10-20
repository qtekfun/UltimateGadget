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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingMathTest {
    private fun ex(id: String, vararg sets: SessionSet) =
        SessionExercise(id, mapOf("en" to id), Measure.REPS_WEIGHT, sets = sets.toList())

    private fun done(w: Double, r: Int, kind: SetKind = SetKind.NORMAL) = SessionSet(kind, reps = r, weightKg = w, done = true)

    private fun session(id: String, start: Long, vararg ex: SessionExercise) =
        Session(id = id, title = id, startMs = start, endMs = start + 3_600_000, status = SessionStatus.DONE, exercises = ex.toList())

    @Test fun unitsRoundTrip() {
        assertEquals(100.0, TrainingMath.lbToKg(TrainingMath.kgToLb(100.0)), 1e-9)
        assertEquals(2.20462, TrainingMath.kgToLb(1.0), 1e-4)
        assertEquals(1.0, TrainingMath.metresToMiles(1609.344), 1e-9)
    }

    @Test fun volumeIgnoresWarmupAndUndoneSets() {
        val e = ex("a", done(60.0, 10, SetKind.WARMUP), done(100.0, 5), SessionSet(reps = 5, weightKg = 100.0, done = false))
        assertEquals(500.0, TrainingMath.volumeKg(e), 1e-9)
    }

    @Test fun epleyOneRepMax() {
        assertEquals(100.0, TrainingMath.estimatedOneRepMaxKg(done(100.0, 1)), 1e-9)
        assertEquals(133.333, TrainingMath.estimatedOneRepMaxKg(done(100.0, 10)), 1e-2)
        assertEquals(0.0, TrainingMath.estimatedOneRepMaxKg(SessionSet(reps = 5)), 1e-9)
    }

    @Test fun lastTimeUsesMostRecentFinishedSessionWithTheExercise() {
        val old = session("old", 1_000, ex("squat", done(80.0, 5)))
        val mid = session("mid", 2_000, ex("bench", done(60.0, 8)))
        val recent = session("recent", 3_000, ex("squat", done(90.0, 5), done(60.0, 10, SetKind.WARMUP)))
        val last = TrainingMath.lastTime("squat", listOf(old, mid, recent))
        assertEquals(listOf(90.0), last.map { it.weightKg })
        assertTrue(TrainingMath.lastTime("deadlift", listOf(old, mid, recent)).isEmpty())
        assertEquals(listOf(80.0), TrainingMath.lastTime("squat", listOf(old, mid, recent), beforeMs = 3_000).map { it.weightKg })
    }

    @Test fun personalRecordsNeedHistoryAndBeatIt() {
        val old = session("old", 1_000, ex("squat", done(100.0, 5)))
        val first = session("first", 500, ex("squat", done(120.0, 5)))
        assertTrue("no record without history", TrainingMath.personalRecords(first, listOf(first)).isEmpty())

        val better = session("better", 2_000, ex("squat", done(110.0, 5)))
        val kinds = TrainingMath.personalRecords(better, listOf(old, better)).map { it.kind }
        assertTrue(RecordKind.WEIGHT in kinds)
        assertTrue(RecordKind.ONE_REP_MAX in kinds)

        val worse = session("worse", 2_000, ex("squat", done(90.0, 5)))
        assertTrue(TrainingMath.personalRecords(worse, listOf(old, worse)).isEmpty())
    }

    @Test fun describeSets() {
        val w = { kg: Double -> "%.0f kg".format(kg) }
        assertEquals("80 kg x 8", TrainingMath.describe(done(80.0, 8), Measure.REPS_WEIGHT, w))
        assertEquals("12", TrainingMath.describe(SessionSet(reps = 12), Measure.REPS, w))
        assertEquals("1:05", TrainingMath.describe(SessionSet(durationS = 65), Measure.TIME, w))
        assertEquals("5000 m / 25:00", TrainingMath.describe(SessionSet(distanceM = 5000.0, durationS = 1500), Measure.DISTANCE_TIME, w))
    }

    @Test fun durationFormat() {
        assertEquals("0:09", TrainingMath.formatDuration(9))
        assertEquals("1:01:01", TrainingMath.formatDuration(3661))
    }
}
