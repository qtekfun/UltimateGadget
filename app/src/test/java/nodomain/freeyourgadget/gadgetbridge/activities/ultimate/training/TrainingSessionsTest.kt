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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSessionsTest {
    private val squat = BuiltinExercises.find("builtin:squat")!!
    private val plank = BuiltinExercises.find("builtin:plank")!!
    private val catalog = { id: String -> BuiltinExercises.find(id) }

    private fun pastSquat(w: Double, r: Int, start: Long = 1_000) = Session(
        id = "p$start", title = "past", startMs = start, endMs = start + 1, status = SessionStatus.DONE,
        exercises = listOf(SessionExercise(squat.id, squat.names, squat.measure,
            sets = listOf(SessionSet(reps = r, weightKg = w, done = true), SessionSet(reps = r - 1, weightKg = w + 5, done = true)))),
    )

    @Test fun startFromRoutineUsesPlanThenLastTimeValues() {
        val routine = Routine(id = "r", name = "Legs", exercises = listOf(
            RoutineExercise(squat.id, 120, sets = listOf(PlannedSet(reps = 5), PlannedSet(reps = 5, weightKg = 200.0))),
            RoutineExercise("gone", sets = listOf(PlannedSet())),
        ))
        val s = TrainingSessions.start("Legs", routine, catalog, listOf(pastSquat(100.0, 8)), nowMs = 5_000)
        assertEquals(1, s.exercises.size)
        assertEquals("r", s.routineId)
        val sets = s.exercises[0].sets
        assertEquals(5, sets[0].reps)
        assertEquals(100.0, sets[0].weightKg!!, 0.0)
        assertEquals(200.0, sets[1].weightKg!!, 0.0)
        assertEquals(SessionStatus.ACTIVE, s.status)
        assertEquals(120, s.exercises[0].restSeconds)
    }

    @Test fun addExerciseAndSetsCopyThePreviousSet() {
        var s = TrainingSessions.start("t", null, catalog, emptyList(), 5_000)
        s = TrainingSessions.addExercise(s, squat, 90, listOf(pastSquat(100.0, 8)))
        assertEquals(100.0, s.exercises[0].sets[0].weightKg!!, 0.0)
        s = TrainingSessions.updateSet(s, 0, 0) { it.copy(reps = 6, weightKg = 110.0, done = true) }
        s = TrainingSessions.addSet(s, 0)
        assertEquals(2, s.exercises[0].sets.size)
        assertEquals(110.0, s.exercises[0].sets[1].weightKg!!, 0.0)
        assertFalse(s.exercises[0].sets[1].done)
        s = TrainingSessions.removeSet(s, 0, 1)
        assertEquals(1, s.exercises[0].sets.size)
    }

    @Test fun completingASetStartsTheRestTimerAndUnmarkingStopsIt() {
        var s = TrainingSessions.addExercise(TrainingSessions.start("t", null, catalog, emptyList(), 0), squat, 60)
        s = TrainingSessions.toggleDone(s, 0, 0, nowMs = 10_000)
        assertTrue(s.exercises[0].sets[0].done)
        assertEquals(70_000, s.restEndsAtMs)
        assertEquals(60, TrainingSessions.restRemainingSeconds(s, 10_000))
        assertEquals(1, TrainingSessions.restRemainingSeconds(s, 69_001))
        assertEquals(0, TrainingSessions.restRemainingSeconds(s, 70_000))
        s = TrainingSessions.toggleDone(s, 0, 0, nowMs = 11_000)
        assertFalse(s.exercises[0].sets[0].done)
        assertEquals(0, s.restEndsAtMs)
    }

    @Test fun noRestTimerWhenRestIsZero() {
        var s = TrainingSessions.addExercise(TrainingSessions.start("t", null, catalog, emptyList(), 0), plank, 0)
        s = TrainingSessions.toggleDone(s, 0, 0, 5_000)
        assertEquals(0, s.restEndsAtMs)
    }

    @Test fun finishDropsUndoneSetsAndEmptyExercises() {
        var s = TrainingSessions.start("t", null, catalog, emptyList(), 0)
        s = TrainingSessions.addExercise(s, squat)
        s = TrainingSessions.addExercise(s, plank)
        s = TrainingSessions.addSet(s, 0)
        s = TrainingSessions.toggleDone(s, 0, 0, 100)
        val f = TrainingSessions.finish(s, 9_000)
        assertEquals(SessionStatus.DONE, f.status)
        assertEquals(9_000, f.endMs)
        assertEquals(listOf(squat.id), f.exercises.map { it.exerciseId })
        assertEquals(1, f.exercises[0].sets.size)
        assertEquals(0, f.restEndsAtMs)
    }

    @Test fun finishedSessionCanBecomeARoutine() {
        var s = TrainingSessions.addExercise(TrainingSessions.start("t", null, catalog, emptyList(), 0), squat, 75)
        s = TrainingSessions.updateSet(s, 0, 0) { it.copy(reps = 5, weightKg = 100.0, done = true) }
        val r = TrainingSessions.toRoutine(TrainingSessions.finish(s, 1), "Copy")
        assertEquals("Copy", r.name)
        assertEquals(75, r.exercises[0].restSeconds)
        assertEquals(100.0, r.exercises[0].sets[0].weightKg!!, 0.0)
        assertNull(r.exercises[0].sets[0].repsMax)
    }

    @Test fun removeExerciseAndRestEdit() {
        var s = TrainingSessions.addExercise(TrainingSessions.addExercise(TrainingSessions.start("t", null, catalog, emptyList(), 0), squat), plank)
        s = TrainingSessions.setRest(s, 1, -5)
        assertEquals(0, s.exercises[1].restSeconds)
        s = TrainingSessions.removeExercise(s, 0)
        assertEquals(listOf(plank.id), s.exercises.map { it.exerciseId })
    }
}
