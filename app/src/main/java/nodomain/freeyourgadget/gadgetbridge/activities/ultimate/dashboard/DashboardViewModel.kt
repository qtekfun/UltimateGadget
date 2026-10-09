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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val loading: Boolean = true,
    val data: DashboardData? = null,
    val cards: List<CardConfig> = emptyList(),
    val editing: Boolean = false,
    val amoled: Boolean = false,
)

class DashboardViewModel(
    app: Application,
    private val repository: HealthRepository = SampleHealthRepository(),
) : AndroidViewModel(app) {

    private val store = DashboardConfigStore(app)
    private val _state = MutableStateFlow(DashboardUiState(cards = store.load(), amoled = store.amoled))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { repository.load() }
            _state.value = _state.value.copy(loading = false, data = data)
        }
    }

    fun setEditing(editing: Boolean) {
        _state.value = _state.value.copy(editing = editing)
    }

    fun toggleCard(id: DashboardCardId) {
        val updated = _state.value.cards.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        persist(updated)
    }

    fun moveCard(from: Int, to: Int) {
        val list = _state.value.cards.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        persist(list)
    }

    fun setAmoled(amoled: Boolean) {
        store.amoled = amoled
        _state.value = _state.value.copy(amoled = amoled)
    }

    private fun persist(configs: List<CardConfig>) {
        store.save(configs)
        _state.value = _state.value.copy(cards = configs)
    }
}
