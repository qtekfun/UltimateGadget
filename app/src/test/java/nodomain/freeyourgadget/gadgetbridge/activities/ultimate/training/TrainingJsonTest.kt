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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingJsonTest {
    private val custom = Exercise("c1", mapOf("en" to "Cable crunch", "es" to "Crunch en polea"), Measure.REPS_WEIGHT, "abs", "cable", custom = true, notes = "slow")
    private val routine = Routine(
        id = "r1", name = "Push", notes = "n", updatedMs = 5,
        exercises = listOf(
            RoutineExercise("builtin:bench_press", 120, supersetGroup = 1, sets = listOf(
                PlannedSet(SetKind.WARMUP, reps = 10, weightKg = 40.0),
                PlannedSet(SetKind.NORMAL, reps = 5, repsMax = 8, weightKg = 80.5, rir = 2),
            )),
            RoutineExercise("c1", sets = listOf(PlannedSet(durationS = 60))),
        ),
    )
    private val session = Session(
        id = "s1", title = "Push day", routineId = "r1", startMs = 1000, endMs = 9000, status = SessionStatus.DONE, notes = "good",
        exercises = listOf(
            SessionExercise("builtin:bench_press", mapOf("en" to "Barbell bench press"), Measure.REPS_WEIGHT, 120, 1,
                listOf(SessionSet(SetKind.NORMAL, 5, 80.5, null, null, 2, true, 4000))),
            SessionExercise("builtin:running", mapOf("en" to "Running"), Measure.DISTANCE_TIME, sets = listOf(SessionSet(distanceM = 5000.0, durationS = 1500, done = true))),
        ),
    )

    @Test fun documentRoundTripIsIdentical() {
        val text = TrainingJson.export(TrainingJson.Document(listOf(custom), listOf(routine), listOf(session)))
        val back = TrainingJson.import(text)
        assertEquals(listOf(custom), back.exercises)
        assertEquals(listOf(routine), back.routines)
        assertEquals(listOf(session), back.sessions)
        assertEquals(text, TrainingJson.export(back))
    }

    @Test fun exportSkipsNonCustomExercises() {
        val builtin = BuiltinExercises.all.first()
        val back = TrainingJson.import(TrainingJson.export(TrainingJson.Document(listOf(builtin, custom), emptyList(), emptyList())))
        assertEquals(listOf("c1"), back.exercises.map { it.id })
    }

    @Test fun nullableFieldsStayNull() {
        val s = TrainingJson.decodeSession(TrainingJson.encode(Session(id = "x", title = "t", startMs = 1)))
        assertNull(s.routineId)
        val set = TrainingJson.decodeSessionSet(TrainingJson.encode(SessionSet()))
        assertNull(set.reps); assertNull(set.weightKg)
    }

    @Test fun unknownFieldsAndMissingListsAreTolerated() {
        val text = """{"format":"ultimategadget-training","schema":1,"extra":true,"routines":[{"id":"r","name":"A","future":1}]}"""
        val doc = TrainingJson.import(text)
        assertEquals("A", doc.routines.single().name)
        assertEquals(emptyList<Session>(), doc.sessions)
    }

    @Test fun rejectsForeignAndNewerFiles() {
        assertThrows(IllegalArgumentException::class.java) { TrainingJson.import("not json") }
        assertThrows(IllegalArgumentException::class.java) { TrainingJson.import("""{"format":"other","schema":1}""") }
        assertThrows(IllegalArgumentException::class.java) { TrainingJson.import("""{"format":"ultimategadget-training","schema":99}""") }
    }

    @Test fun builtinIdsAreUniqueAndPrefixed() {
        val ids = BuiltinExercises.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("builtin:") })
        assertTrue(BuiltinExercises.all.all { it.names.containsKey("en") && it.names.containsKey("es") })
    }
}
