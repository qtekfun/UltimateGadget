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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.sleep

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SleepUiState(
    val loading: Boolean = true,
    val dayStartMillis: Long = SleepRepository.today(),
    val dayLabel: String = "",
    val isToday: Boolean = true,
    val data: SleepDayData? = null,
)

/**
 * Advanced sleep screen state. Application-only constructor so the default AndroidViewModel factory
 * (viewModel()) can create it — same contract as DashboardViewModel; do not add parameters. Pass the
 * initial day through [setDay] from the activity instead.
 */
class SleepViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SleepRepository(app)
    private val _state = MutableStateFlow(SleepUiState(dayStartMillis = SleepRepository.today()))
    val state: StateFlow<SleepUiState> = _state.asStateFlow()

    init {
        reload(_state.value.dayStartMillis)
    }

    /** Set the day to show (any millis within that day). Clamped to not go into the future. */
    fun setDay(millis: Long) {
        val day = SleepRepository.dayStart(millis).coerceAtMost(SleepRepository.today())
        reload(day)
    }

    fun previousDay() = reload(_state.value.dayStartMillis - DAY_MS)

    fun nextDay() {
        val next = _state.value.dayStartMillis + DAY_MS
        if (next <= SleepRepository.today()) reload(next)
    }

    private fun reload(day: Long) {
        _state.value = SleepUiState(
            loading = true,
            dayStartMillis = day,
            dayLabel = SleepRepository.dayLabel(day),
            isToday = day >= SleepRepository.today(),
            data = null,
        )
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { repository.load(day) }
            if (_state.value.dayStartMillis == day) {
                _state.value = _state.value.copy(loading = false, data = data)
            }
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
