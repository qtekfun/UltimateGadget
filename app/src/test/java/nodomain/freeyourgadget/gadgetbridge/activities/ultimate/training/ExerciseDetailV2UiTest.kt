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
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** A drawable that records the playback calls, standing in for the platform's animated drawable. */
private class FakeAnimation : Drawable(), Animatable {
    var running = false
    var starts = 0
    override fun start() { running = true; starts++ }
    override fun stop() { running = false }
    override fun isRunning() = running
    override fun draw(canvas: android.graphics.Canvas) {}
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseDetailV2UiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var catalog: TrainingCatalog
    private val animations = HashMap<String, FakeAnimation>()
    private val decoder = AnimationDecoder { f -> if (f.name.contains("plank")) null else animations.getOrPut(f.name) { FakeAnimation() } }

    private fun png(): ByteArray {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFFFF0000.toInt()) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        TrainingPacks.invalidate()
        TrainingPacks.rootDir(ctx).deleteRecursively()
        val media = mapOf(
            "media/uuid-squat.anim.webp" to byteArrayOf(1), "media/uuid-squat_1.png" to png(),
            "media/uuid-lunge.anim.webp" to byteArrayOf(3), "media/uuid-plank.anim.webp" to byteArrayOf(4),
        )
        TrainingPacks.install(ctx, PackFixture.zip(manifest = PackFixture.manifest(schema = 2), exercises = PackFixture.exercisesV2(), media = media).inputStream())
        TrainingStore.reset()
        catalog = TrainingCatalog.create(ctx, TrainingStore.get(ctx))
    }

    @After fun tearDown() {
        TrainingPacks.invalidate()
        TrainingStore.reset()
    }

    private fun show(id: String, waitFor: String?) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            UltimateTheme {
                CompositionLocalProvider(LocalAnimationDecoder provides decoder) { ExerciseDetail(catalog.find(id)!!, null, onDismiss = {}) }
            }
        }
        if (waitFor != null) waitForTag(waitFor)
    }

    private fun waitForTag(tag: String) = rule.waitUntil(10_000) {
        rule.mainClock.advanceTimeBy(50)
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun v2ExerciseShowsSummaryStepsAndTipsInOrder() {
        show("uuid-squat", "ex_anim")
        rule.onNodeWithText("A squat holding a weight at the chest.").assertExists()
        rule.onNodeWithText("How to do it").assertExists()
        rule.onNodeWithTag("ex_step_0").assertExists()
        rule.onNodeWithText("Sit down.").assertExists()
        rule.onNodeWithText("2.").assertExists()
        rule.onNodeWithText("Tips").assertExists()
        rule.onNodeWithText("Keep the chest up.").assertExists()
        // The plain description is only the fallback when there are no steps.
        assertFalse(rule.onAllNodes(androidx.compose.ui.test.hasText("About Goblet Squat")).fetchSemanticsNodes().isNotEmpty())
        val top = { tag: String -> rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y }
        assertTrue(top("ex_summary") < top("ex_step_0"))
        assertTrue(top("ex_step_0") < top("ex_tip_0"))
    }

    @Test fun animationPlaysAndTheToggleSwitchesToThePhotoAndBack() {
        show("uuid-squat", "ex_anim")
        val anim = animations.getValue("uuid-squat.anim.webp")
        assertTrue(anim.running)
        assertFalse(exists("ex_image_0"))
        rule.onNodeWithTag("ex_media_toggle").performClick()
        waitForTag("ex_image_0")
        assertFalse(exists("ex_anim"))
        assertFalse(anim.running)
        rule.onNodeWithText("Animation").performClick()
        waitForTag("ex_anim")
        assertTrue(animations.getValue("uuid-squat.anim.webp").running)
    }

    @Test fun animationOnlyExerciseHasNoToggleAndOnlySkipsMissingSteps() {
        show("uuid-lunge", "ex_anim")
        assertFalse(exists("ex_media_toggle"))
        rule.onNodeWithText("Step forward.").assertExists()
        rule.onNodeWithText("Return.").assertExists()
        assertFalse(exists("ex_step_2"))
        assertEquals(true, animations.getValue("uuid-lunge.anim.webp").running)
    }

    @Test fun undecodableAnimationFallsBackToTheIconAndTheCleanedDescription() {
        show("uuid-plank", "ex_icon")
        rule.mainClock.advanceTimeBy(500)
        assertFalse(exists("ex_anim"))
        rule.onNodeWithText("Line one wrapped line\n\nSecond paragraph.").assertExists()
    }

    @Test fun attributionHasOneCc0LineForTheAnimationAndKeepsThePhotoCredit() {
        show("uuid-squat", "ex_anim")
        rule.onNodeWithText("Animation: generated for this app (CC0-1.0)").assertExists()
        rule.onNodeWithText("Image 1: Cy (CC-BY-SA-4.0)").assertExists()
        assertFalse(rule.onAllNodes(androidx.compose.ui.test.hasText("Image 2", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun playbackStopsWhenTheActivityPausesAndResumes() {
        show("uuid-squat", "ex_anim")
        val anim = animations.getValue("uuid-squat.anim.webp")
        assertTrue(anim.running)
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.mainClock.advanceTimeBy(100)
        assertFalse(anim.running)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeBy(100)
        assertTrue(anim.running)
    }

    @Test fun playbackStopsWhenTheScreenLeavesTheComposition() {
        var visible by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            UltimateTheme {
                CompositionLocalProvider(LocalAnimationDecoder provides decoder) {
                    if (visible) ExerciseDetail(catalog.find("uuid-squat")!!, null, onDismiss = {})
                }
            }
        }
        waitForTag("ex_anim")
        val anim = animations.getValue("uuid-squat.anim.webp")
        assertTrue(anim.running)
        rule.runOnUiThread { visible = false }
        rule.mainClock.advanceTimeBy(200)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertFalse(anim.running)
    }

    @Test @Config(qualifiers = "es") fun spanishTextsAndLabels() {
        show("uuid-squat", "ex_anim")
        rule.onNodeWithText("Una sentadilla con peso al pecho.").assertExists()
        rule.onNodeWithText("Cómo se hace").assertExists()
        rule.onNodeWithText("Pecho arriba.").assertExists()
        rule.onNodeWithText("cuádriceps, glúteos").assertExists()
        rule.onNodeWithText("gemelos").assertExists()
        rule.onNodeWithText("barra").assertExists()
        rule.onNodeWithText("piernas").assertExists()
    }
}
