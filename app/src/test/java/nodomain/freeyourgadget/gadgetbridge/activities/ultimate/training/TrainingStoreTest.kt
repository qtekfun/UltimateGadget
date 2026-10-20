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

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrainingStoreTest {
    private fun store() = TrainingStore(ApplicationProvider.getApplicationContext(), name = null)

    private fun session(id: String, start: Long, status: SessionStatus) =
        Session(id = id, title = id, startMs = start, status = status,
            exercises = listOf(SessionExercise("builtin:squat", mapOf("en" to "Squat"), Measure.REPS_WEIGHT,
                sets = listOf(SessionSet(reps = 5, weightKg = 100.0, done = true)))))

    @Test fun activeSessionIsPersistedAndFoundAgain() {
        val s = store()
        assertNull(s.activeSession())
        s.saveSession(session("a", 10, SessionStatus.ACTIVE))
        assertEquals("a", s.activeSession()?.id)
        s.saveSession(session("a", 10, SessionStatus.ACTIVE).copy(title = "edited"))
        assertEquals("edited", s.activeSession()?.title)
        s.saveSession(session("a", 10, SessionStatus.DONE))
        assertNull(s.activeSession())
        assertEquals(listOf("a"), s.finishedSessions().map { it.id })
    }

    @Test fun finishedSessionsAreNewestFirst() {
        val s = store()
        s.saveSession(session("old", 1, SessionStatus.DONE))
        s.saveSession(session("new", 5, SessionStatus.DONE))
        s.saveSession(session("act", 9, SessionStatus.ACTIVE))
        assertEquals(listOf("new", "old"), s.finishedSessions().map { it.id })
        assertEquals(listOf("new"), s.finishedSessions(limit = 1).map { it.id })
    }

    @Test fun routinesAndCustomExercisesCrud() {
        val s = store()
        s.saveRoutine(Routine(id = "r", name = "A"), nowMs = 1)
        s.saveRoutine(Routine(id = "r2", name = "B"), nowMs = 2)
        assertEquals(listOf("r2", "r"), s.routines().map { it.id })
        s.deleteRoutine("r2")
        assertNotNull(s.routine("r"))
        assertNull(s.routine("r2"))

        s.saveExercise(Exercise("c", mapOf("en" to "Zeta"), custom = true))
        s.saveExercise(Exercise("d", mapOf("en" to "Alpha"), custom = true))
        assertEquals(listOf("d", "c"), s.customExercises().map { it.id })
        s.deleteExercise("c")
        assertEquals(listOf("d"), s.customExercises().map { it.id })
    }

    @Test fun importMergesWithoutOverwritingOrReplacesEverything() {
        val s = store()
        s.saveRoutine(Routine(id = "keep", name = "mine"), nowMs = 1)
        val doc = TrainingJson.Document(
            emptyList(),
            listOf(Routine(id = "keep", name = "theirs"), Routine(id = "new", name = "new")),
            listOf(session("x", 3, SessionStatus.DONE)),
        )
        val r = s.importDocument(doc, replace = false)
        assertEquals(2, r.added); assertEquals(1, r.skipped)
        assertEquals("mine", s.routine("keep")?.name)

        val r2 = s.importDocument(doc, replace = true)
        assertEquals(3, r2.added); assertEquals(0, r2.skipped)
        assertEquals("theirs", s.routine("keep")?.name)
    }

    @Test fun exportThenImportIntoEmptyStoreCopiesEverything() {
        val a = store()
        a.saveRoutine(Routine(id = "r", name = "A"), nowMs = 1)
        a.saveExercise(Exercise("c", mapOf("en" to "X"), custom = true))
        a.saveSession(session("s", 2, SessionStatus.DONE))
        val b = store()
        b.importDocument(TrainingJson.import(TrainingJson.export(a.exportDocument())), replace = true)
        assertEquals(a.routines().map { it.id }, b.routines().map { it.id })
        assertEquals(a.customExercises(), b.customExercises())
        assertEquals(a.finishedSessions(), b.finishedSessions())
    }
}
