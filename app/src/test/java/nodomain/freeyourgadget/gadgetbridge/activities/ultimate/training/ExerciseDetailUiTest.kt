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

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** The exercise detail screen against a tiny pack installed from a fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseDetailUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var catalog: TrainingCatalog

    private fun png(color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        TrainingPacks.invalidate()
        TrainingPacks.rootDir(ctx).deleteRecursively()
        val media = mapOf(
            "media/uuid-squat_1.png" to png(0xFFFF0000.toInt()),
            "media/uuid-squat_2.png" to png(0xFF00FF00.toInt()),
            "media/uuid-lunge_1.png" to png(0xFF0000FF.toInt()),
        )
        TrainingPacks.install(ctx, PackFixture.zip(media = media).inputStream())
        TrainingStore.reset()
        catalog = TrainingCatalog.create(ctx, TrainingStore.get(ctx))
    }

    @After fun tearDown() {
        TrainingPacks.invalidate()
        TrainingStore.reset()
    }

    private fun show(id: String, best: String? = null) {
        rule.mainClock.autoAdvance = false
        rule.setContent { UltimateTheme { ExerciseDetail(catalog.find(id)!!, best, onDismiss = {}) } }
        // Pictures are decoded on a background thread; wait for the first one to appear.
        val tag = if (catalog.find(id)!!.pack!!.media.isEmpty()) "ex_icon" else "ex_image_0"
        rule.waitUntil(10_000) {
            rule.mainClock.advanceTimeBy(50)
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun showsDescriptionMusclesEquipmentAndBestSet() {
        show("uuid-squat", best = "5 x 60 kg")
        rule.onNodeWithText("Goblet Squat").assertExists()
        rule.onNodeWithText("About Goblet Squat").assertExists()
        rule.onNodeWithText("quads, glutes").assertExists()
        rule.onNodeWithText("calves").assertExists()
        rule.onNodeWithText("barbell").assertExists()
        rule.onNodeWithText("Best: 5 x 60 kg").assertExists()
    }

    @Test fun showsNotYetDoneWithoutHistory() {
        show("uuid-squat")
        rule.onNodeWithText("Not done yet").assertExists()
    }

    @Test fun twoImagesCrossFadeInALoop() {
        show("uuid-squat")
        rule.onNodeWithTag("ex_image_0").assertExists()
        rule.mainClock.advanceTimeBy(3500)
        rule.onNodeWithTag("ex_image_1").assertExists()
        rule.mainClock.advanceTimeBy(3500)
        rule.onNodeWithTag("ex_image_0").assertExists()
    }

    @Test fun oneImageStaysStill() {
        show("uuid-lunge")
        rule.onNodeWithTag("ex_image_0").assertExists()
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithTag("ex_image_0").assertExists()
    }

    @Test fun noImageShowsTheMuscleIcon() {
        show("uuid-plank")
        rule.onNodeWithTag("ex_icon").assertExists()
    }

    @Test fun attributionListsAuthorsLicensesAndAiImages() {
        show("uuid-squat")
        rule.onNodeWithText("Text: Ann, Bob (CC-BY-SA-4)").assertExists()
        rule.onNodeWithText("Image 1: Cy (CC-BY-SA-4.0)").assertExists()
        rule.onNodeWithText("Image 2: Cy (CC-BY-SA-4.0)").assertExists()
        rule.onNodeWithText("AI-generated image").assertExists() // only the second picture is flagged
    }

    @Test fun builtinExerciseHasNoAttributionOrImage() {
        rule.mainClock.autoAdvance = false
        rule.setContent { UltimateTheme { ExerciseDetail(BuiltinExercises.find("builtin:squat")!!, null, onDismiss = {}) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("ex_icon").assertExists()
        assertEquals(0, rule.onAllNodesCount("Attribution"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    @Test fun deleteOnlyForCustomExercises() {
        var deleted = false
        rule.mainClock.autoAdvance = false
        val custom = Exercise("c", mapOf("en" to "Mine"), custom = true, notes = "my notes")
        rule.setContent { UltimateTheme { ExerciseDetail(custom, null, onDismiss = {}, onDelete = { deleted = true }) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("my notes").assertExists()
        rule.onNodeWithText("Delete").performClick()
        assertEquals(true, deleted)
    }
}
