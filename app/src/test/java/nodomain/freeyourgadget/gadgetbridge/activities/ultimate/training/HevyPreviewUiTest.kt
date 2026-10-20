package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HevyPreviewUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val csv = "title,start_time,exercise_title,weight,reps\n" +
        "W,2024-08-05 18:30,Deadlift,100,5\nW,2024-08-05 18:30,Zercher Squat,60,8\nW,2024-08-05 18:30,Odd Lift,10,8"

    private fun show(onConfirm: (Set<String>, WeightUnit, DistanceUnit) -> Unit, onDismiss: () -> Unit = {}) {
        val parsed = HevyCsv.parse(csv, ZoneId.of("UTC"))
        val plan = HevyImport.plan(parsed, BuiltinExercises.all)
        rule.setContent { UltimateTheme { HevyPreviewDialog(parsed, plan, onDismiss, onConfirm) } }
    }

    @Test fun showsCountsAndUnmatchedTitles() {
        show({ _, _, _ -> })
        rule.onNodeWithText("Import from Hevy").assertExists()
        rule.onNodeWithText("1 workouts", substring = true).assertExists()
        rule.onNodeWithText("3 sets", substring = true).assertExists()
        rule.onNodeWithText("1 exercises matched to the catalog").assertExists()
        rule.onNodeWithText("2 exercises are not in the catalog", substring = true).assertExists()
        rule.onNodeWithText("Zercher Squat").assertExists()
        rule.onNodeWithText("Odd Lift").assertExists()
        // The unit is not in the header, so the user is asked.
        rule.onNodeWithText("Weights are in:", substring = true).assertExists()
    }

    @Test fun confirmPassesTheChosenTitlesAndUnit() {
        var result: Triple<Set<String>, WeightUnit, DistanceUnit>? = null
        show({ c, w, d -> result = Triple(c, w, d) })
        // Both switches start on; pick pounds.
        rule.onNodeWithText("lb").performClick()
        rule.onNodeWithText("Import").performClick()
        assertEquals(setOf("Zercher Squat", "Odd Lift"), result!!.first)
        assertEquals(WeightUnit.LB, result!!.second)
    }

    @Test fun cancelDismissesWithoutConfirming() {
        var confirmed = false
        var dismissed = false
        show({ _, _, _ -> confirmed = true }, { dismissed = true })
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(true, dismissed)
        assertEquals(false, confirmed)
    }

    @Test fun togglingASwitchSkipsThatTitle() {
        var result: Set<String>? = null
        show({ c, _, _ -> result = c })
        // Rows are sorted by title, so the first switch belongs to "Odd Lift".
        rule.onAllNodes(androidx.compose.ui.test.isToggleable())[0].performClick()
        rule.onNodeWithText("Import").performClick()
        assertEquals(setOf("Zercher Squat"), result)
    }
}
