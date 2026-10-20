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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Drives the workout screens like a user would, against the real store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingScreensUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var store: TrainingStore

    @Before fun setUp() {
        TrainingStore.reset()
        store = TrainingStore.get(RuntimeEnvironment.getApplication())
    }

    @After fun tearDown() { TrainingStore.reset() }

    private fun activeSquat(sets: Int = 1): Session {
        val squat = BuiltinExercises.find("builtin:squat")!!
        return Session(
            id = "act", title = "Leg day", startMs = System.currentTimeMillis() - 60_000,
            exercises = listOf(SessionExercise(squat.id, squat.names, squat.measure, 90, null,
                List(sets) { SessionSet(reps = 5, weightKg = 100.0) })),
        ).also { store.saveSession(it) }
    }

    @Test fun homeStartsAnEmptyWorkoutAndOffersToResumeIt() {
        rule.setContent { UltimateTheme { TrainingHome() } }
        rule.onNodeWithText("Start empty workout").assertExists()
        rule.onNodeWithText("Start empty workout").performClick()
        assertNotNull("session was stored right away", store.activeSession())
        assertEquals("Workout", store.activeSession()!!.title)
    }

    @Test fun homeShowsRoutinesAndHistory() {
        store.saveRoutine(Routine(id = "r", name = "Push day", exercises = listOf(RoutineExercise("builtin:bench_press"))), nowMs = 1)
        rule.setContent { UltimateTheme { TrainingHome() } }
        rule.onNodeWithText("Push day").assertExists()
        rule.onNodeWithText("1 exercises").assertExists()
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Finished workouts will appear here.").assertExists()
    }

    @Test fun sessionScreenShowsRecoveredWorkoutAndAddsASetPersistently() {
        activeSquat()
        // A fresh composition reads only from disk, like after the process was killed.
        rule.setContent { UltimateTheme { SessionRoot(null, onClose = {}) } }
        rule.onNodeWithText("Leg day").assertExists()
        rule.onNodeWithText("Barbell back squat").assertExists()
        rule.onNodeWithText("Add set").performClick()
        assertEquals(2, store.activeSession()!!.exercises[0].sets.size)
    }

    @Test fun finishingStoresTheWorkoutAndClearsTheActiveOne() {
        val s = activeSquat()
        store.saveSession(TrainingSessions.toggleDone(s, 0, 0, System.currentTimeMillis()))
        rule.setContent { UltimateTheme { SessionRoot(null, onClose = {}) } }
        rule.onAllNodesWithText("Finish")[0].performClick()
        rule.onAllNodesWithText("Finish")[1].performClick()
        assertNull(store.activeSession())
        val done = store.finishedSessions().single()
        assertEquals(1, TrainingMath.doneSets(done))
        assertEquals(500.0, TrainingMath.volumeKg(done), 1e-9)
    }
}
