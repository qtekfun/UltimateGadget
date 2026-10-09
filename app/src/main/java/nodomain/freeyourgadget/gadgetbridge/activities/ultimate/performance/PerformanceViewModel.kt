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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.performance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PerformanceUiState(
    val loading: Boolean = true,
    val data: PerformanceData? = null,
)

/**
 * State holder for the Rendimiento screen. Application-only constructor so the default
 * AndroidViewModel factory (viewModel()) can build it — same contract as DashboardViewModel; do not
 * add parameters. All work runs off the main thread in [PerformanceRepository].
 */
class PerformanceViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PerformanceRepository(app)
    private val _state = MutableStateFlow(PerformanceUiState())
    val state: StateFlow<PerformanceUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.value = PerformanceUiState(loading = true, data = null)
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { repository.load() }
            _state.value = PerformanceUiState(loading = false, data = data)
        }
    }
}
