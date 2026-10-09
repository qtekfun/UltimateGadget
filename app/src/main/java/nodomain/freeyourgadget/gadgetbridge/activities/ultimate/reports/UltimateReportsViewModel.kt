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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser

data class ReportsUiState(
    val loading: Boolean = true,
    val data: ReportData? = null,
    val period: ReportPeriod = ReportPeriod.WEEK,
)

/**
 * Backs both the Reports and the Goals screens: they share one [ReportData] load. The constructor
 * takes only [Application] so the default factory (viewModel()) can create it — same reason as
 * DashboardViewModel; do not add parameters.
 */
class UltimateReportsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ReportsRepository(app)
    private val store = GoalsStore(app)
    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val goal = effectiveStepGoal()
            val data = withContext(Dispatchers.IO) { repository.load(goal) }
            _state.value = _state.value.copy(loading = false, data = data)
        }
    }

    fun setPeriod(period: ReportPeriod) {
        _state.value = _state.value.copy(period = period)
    }

    /** Persist a user step-goal override (0 clears it, falling back to Gadgetbridge's goal) and reload. */
    fun setStepGoal(goal: Int) {
        if (goal <= 0) store.clearStepGoalOverride() else store.stepGoalOverride = goal
        refresh()
    }

    private fun effectiveStepGoal(): Int {
        val override = store.stepGoalOverride
        if (override > 0) return override
        return runCatching { ActivityUser().stepsGoal }.getOrDefault(10000).coerceAtLeast(1)
    }
}
