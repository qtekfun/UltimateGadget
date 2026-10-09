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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.reports

import android.content.Context

/**
 * Small SharedPreferences store (file "ultimate_reports") for the reports/goals screens.
 *
 * Only holds UltimateGadget's own settings; it never touches Gadgetbridge's core preferences. The
 * daily step goal is an optional override: when unset ([stepGoalOverride] <= 0) the screens fall
 * back to Gadgetbridge's own `fitness_goal` (ActivityUser.stepsGoal), so the goal stays in sync with
 * the rest of the app until the user deliberately overrides it here.
 *
 * Keys:
 *  - "step_goal_override" (Int): user's own daily step goal; 0/absent means "use Gadgetbridge's".
 */
class GoalsStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 0 when the user has not set their own goal (fall back to ActivityUser.stepsGoal). */
    var stepGoalOverride: Int
        get() = prefs.getInt(KEY_STEP_GOAL, 0)
        set(value) = prefs.edit().putInt(KEY_STEP_GOAL, value.coerceAtLeast(0)).apply()

    fun clearStepGoalOverride() = prefs.edit().remove(KEY_STEP_GOAL).apply()

    companion object {
        private const val FILE = "ultimate_reports"
        private const val KEY_STEP_GOAL = "step_goal_override"
    }
}
