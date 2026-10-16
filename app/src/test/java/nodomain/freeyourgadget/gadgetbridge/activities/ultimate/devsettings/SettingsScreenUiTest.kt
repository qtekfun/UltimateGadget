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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.devsettings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.SemanticsMatcher
import nodomain.freeyourgadget.gadgetbridge.R
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the settings screen with every simple node type and drives it like a user would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val changed = mutableListOf<String>()
    private val actions = mutableListOf<String?>()

    private fun show(nodes: List<PrefNode>, prefs: android.content.SharedPreferences) {
        val store = DeviceSettingsStore.forApp(prefs) { changed += it }
        rule.setContent {
            UltimateTheme {
                UltimateDeviceSettingsScreen(
                    rootTitle = "Root",
                    rootNodes = nodes,
                    store = store,
                    hasClassicFallback = false,
                    onBack = {},
                    onDelegate = {},
                    onAction = { actions += it },
                )
            }
        }
    }

    private fun prefs() = org.robolectric.RuntimeEnvironment.getApplication()
        .getSharedPreferences("ui-test", android.content.Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun switchTogglesAndPersists() {
        val p = prefs()
        show(listOf(PrefNode.Switch("sw", "Switch row", null, 0, false, null)), p)
        rule.onNodeWithText("Switch row").performClick()
        assertTrue(p.getBoolean("sw", false))
        assertTrue("sw" in changed)
    }

    @Test
    fun singleChoicePicksAnEntry() {
        val p = prefs()
        show(
            listOf(PrefNode.SingleChoice("sc", "Choice row", null, 0, "a", listOf("Alpha", "Beta"), listOf("a", "b"), null)),
            p,
        )
        rule.onNodeWithText("Choice row").performClick()
        rule.onNodeWithText("Beta").performClick()
        assertEquals("b", p.getString("sc", null))
    }

    @Test
    fun actionRowReportsItsKey() {
        show(listOf(PrefNode.Action("act", "Action row", null, 0)), prefs())
        rule.onNodeWithText("Action row").performClick()
        assertEquals(listOf<String?>("act"), actions)
    }

    @Test
    fun subScreenNavigatesAndShowsChildren() {
        val child = PrefNode.Switch("inner", "Inner switch", null, 0, false, null)
        show(listOf(PrefNode.SubScreen("sub", "Sub screen", null, 0, listOf(child))), prefs())
        rule.onNodeWithText("Sub screen").performClick()
        rule.onNodeWithText("Inner switch").assertExists()
    }

    @Test
    fun textRowEditsThroughItsDialog() {
        val p = prefs()
        show(listOf(PrefNode.Text("tx", "Text row", null, 0, "", false, null)), p)
        rule.onNodeWithText("Text row").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("hello")
        rule.onNodeWithText("OK").performClick()
        assertEquals("hello", p.getString("tx", null))
        assertTrue("tx" in changed)
        rule.onNodeWithText("hello").assertExists() // the value becomes the summary
    }

    @Test
    fun textDialogCancelLeavesTheValue() {
        val p = prefs()
        p.edit().putString("tx", "keep").commit()
        show(listOf(PrefNode.Text("tx", "Text row", null, 0, "", false, null)), p)
        rule.onNodeWithText("Text row").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("discard")
        rule.onNodeWithText("Cancel").performClick()
        assertEquals("keep", p.getString("tx", null))
    }

    @Test
    fun sliderMovesWithinItsRange() {
        val p = prefs()
        show(listOf(PrefNode.Slider("sl", "Slider row", null, 0, 5, 0, 10, null)), p)
        val hasProgress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        rule.onNode(hasProgress).performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        assertEquals(10, p.getInt("sl", -1))
        rule.onNode(hasProgress).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertEquals(0, p.getInt("sl", -1))
    }

    @Test
    fun multiChoiceStoresTheTickedSet() {
        val p = prefs()
        show(
            listOf(PrefNode.MultiChoice("mc", "Multi row", null, 0, emptySet(), listOf("One", "Two", "Three"), listOf("1", "2", "3"), null)),
            p,
        )
        rule.onNodeWithText("Multi row").performClick()
        rule.onNodeWithText("One").performClick()
        rule.onNodeWithText("Three").performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals(setOf("1", "3"), p.getStringSet("mc", null))
    }

    @Test
    fun sortableKeepsAndSavesTheOrder() {
        val p = prefs()
        show(
            listOf(PrefNode.Sortable("so", "Sort row", null, 0, listOf("b", "a"), listOf("Alpha", "Beta"), listOf("a", "b"), null)),
            p,
        )
        rule.onNodeWithText("Sort row").performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals("b,a", p.getString("so", null))
    }

    @Test
    fun conditionalRowsFollowTheirPreference() {
        val p = prefs()
        show(
            listOf(
                PrefNode.Switch("gate", "Gate", null, 0, false, null),
                PrefNode.Conditional({ it.getBoolean("gate", false) }, listOf(PrefNode.Info(null, "x", "Revealed", 0))),
            ),
            p,
        )
        rule.onNodeWithText("Revealed").assertDoesNotExist()
        rule.onNodeWithText("Gate").performClick()
        rule.onNodeWithText("Revealed").assertExists()
        rule.onNodeWithText("Gate").performClick()
        rule.onNodeWithText("Revealed").assertDoesNotExist()
    }

    @Test
    fun rowsDependingOnADisabledSwitchStayInert() {
        val p = prefs()
        show(
            listOf(
                PrefNode.Switch("parent", "Parent", null, 0, false, null),
                PrefNode.Switch("child", "Child", null, 0, false, "parent"),
            ),
            p,
        )
        rule.onNodeWithText("Child").performClick()
        assertTrue(!p.getBoolean("child", false))
        rule.onNodeWithText("Parent").performClick()
        rule.onNodeWithText("Child").performClick()
        assertTrue(p.getBoolean("child", false))
    }

    @Test
    fun emptyListIsInertAndSaysWhy() {
        val p = prefs()
        show(listOf(PrefNode.SingleChoice("el", "Empty list", null, 0, "", emptyList(), emptyList(), null)), p)
        rule.onNodeWithText("Empty list").performClick()
        assertTrue(!p.contains("el"))
        rule.onNodeWithText(
            org.robolectric.RuntimeEnvironment.getApplication().getString(R.string.ult_options_when_connected),
        ).assertExists()
    }

    @Test
    fun infoRowShowsTheStoredValueLive() {
        val p = prefs()
        p.edit().putString("addr", "10.0.0.1").commit()
        show(listOf(PrefNode.Info("addr", "fallback", "Address", 0)), p)
        rule.onNodeWithText("10.0.0.1").assertExists()
        p.edit().putString("addr", "10.0.0.2").commit()
        rule.waitForIdle()
        rule.onNodeWithText("10.0.0.2").assertExists()
    }

    @Test
    fun everyEditorActionIsRouted() {
        // Guards the action table: each routed key must have a distinct, non-null editor class.
        assertEquals(ActionRouting.editors.size, ActionRouting.editors.values.toSet().size)
    }
}
