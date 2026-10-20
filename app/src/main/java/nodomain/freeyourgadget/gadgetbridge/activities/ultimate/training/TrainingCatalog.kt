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

/** The exercises the user can pick: the built-in starter list plus their own (packs join in Phase 2). */
class TrainingCatalog(private val store: TrainingStore) {
    private var cache: List<Exercise>? = null

    fun all(): List<Exercise> = cache ?: (BuiltinExercises.all + store.customExercises()).also { cache = it }

    fun find(id: String): Exercise? = all().firstOrNull { it.id == id }

    fun invalidate() { cache = null }

    fun search(query: String, locale: java.util.Locale = java.util.Locale.getDefault()): List<Exercise> {
        val q = query.trim().lowercase()
        val list = all().sortedBy { it.displayName(locale).lowercase() }
        if (q.isEmpty()) return list
        return list.filter { e -> e.names.values.any { it.lowercase().contains(q) } || e.muscle.contains(q) || e.equipment.contains(q) }
    }
}
