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

import android.content.Context

/** One card's state in the user's layout. */
data class CardConfig(val id: DashboardCardId, val enabled: Boolean)

/**
 * Persists which dashboard cards are shown and in which order, per device/profile-agnostic, in a
 * small SharedPreferences file. The stored value is a CSV of "ID:0|1" entries in display order;
 * unknown/removed ids are dropped and newly-added cards are appended with their default state, so
 * the config survives app updates that add cards.
 */
class DashboardConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): List<CardConfig> {
        val stored = prefs.getString(KEY_LAYOUT, null)
        val all = DashboardCardId.entries
        if (stored.isNullOrBlank()) return all.map { CardConfig(it, it.defaultEnabled) }

        val parsed = stored.split('|').mapNotNull { token ->
            val parts = token.split(':')
            val id = parts.getOrNull(0)?.let { name -> all.firstOrNull { it.name == name } } ?: return@mapNotNull null
            CardConfig(id, parts.getOrNull(1) != "0")
        }
        val seen = parsed.map { it.id }.toSet()
        val appended = all.filter { it !in seen }.map { CardConfig(it, it.defaultEnabled) }
        return parsed + appended
    }

    fun save(configs: List<CardConfig>) {
        val csv = configs.joinToString("|") { "${it.id.name}:${if (it.enabled) 1 else 0}" }
        prefs.edit().putString(KEY_LAYOUT, csv).apply()
    }

    var amoled: Boolean
        get() = prefs.getBoolean(KEY_AMOLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED, value).apply()

    companion object {
        private const val FILE = "ultimate_dashboard"
        private const val KEY_LAYOUT = "card_layout"
        private const val KEY_AMOLED = "amoled"
    }
}
