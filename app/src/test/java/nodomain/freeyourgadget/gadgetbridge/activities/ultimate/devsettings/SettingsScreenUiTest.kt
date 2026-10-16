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

import androidx.compose.ui.test.junit4.createComposeRule
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
    fun everyEditorActionIsRouted() {
        // Guards the action table: each routed key must have a distinct, non-null editor class.
        assertEquals(ActionRouting.editors.size, ActionRouting.editors.values.toSet().size)
    }
}
