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

import java.text.Normalizer
import java.util.Locale

/**
 * The exercises the user can pick: the built-in starter list, the installed resource packs and
 * their own. Pack metadata comes from [packs] (read from disk once, kept in memory).
 */
class TrainingCatalog(
    private val store: TrainingStore,
    /** Extra search text (the labels in every supported language) for a stored muscle/equipment/category list. */
    private val labelText: (String) -> String = { "" },
    private val packs: () -> List<Exercise> = { emptyList() },
) {
    private var cache: List<Exercise>? = null
    private var index: Map<String, Exercise>? = null
    private var haystacks: Map<String, String>? = null
    private var sorted: Pair<String, List<Exercise>>? = null

    /** Built-in first, then pack, then custom; a later source never hides an earlier id. */
    fun all(): List<Exercise> = cache ?: (BuiltinExercises.all + packs() + store.customExercises())
        .distinctBy { it.id }.also { cache = it; index = it.associateBy { e -> e.id }; haystacks = null; sorted = null }

    fun find(id: String): Exercise? { all(); return index?.get(id) }

    fun invalidate() { cache = null; index = null; haystacks = null; sorted = null }

    private fun haystack(): Map<String, String> = haystacks ?: all().associate { e ->
        e.id to fold((e.names.values + e.muscle + e.musclesSecondary + e.equipment + e.category).joinToString("\n") + "\n" + labelText(e.muscle) + "\n" + labelText(e.musclesSecondary) + "\n" + labelText(e.equipment) + "\n" + labelText(e.category))
    }.also { haystacks = it }

    fun search(query: String, locale: Locale = Locale.getDefault()): List<Exercise> {
        val q = fold(query.trim())
        val lang = locale.language
        val ordered = sorted?.takeIf { it.first == lang && cache != null }?.second
            ?: all().sortedBy { fold(it.displayName(locale)) }.also { sorted = lang to it }
        if (q.isEmpty()) return ordered
        val h = haystack()
        return ordered.filter { h[it.id]?.contains(q) == true }
    }

    companion object {
        /** The catalog the screens use: built-in, installed packs and the user's own. */
        fun create(context: android.content.Context, store: TrainingStore) =
            TrainingCatalog(store, TrainingLabels.searchText(context)) { TrainingPacks.exercises(context) }

        private val MARKS = Regex("\\p{M}+")
        /** Lower-case and accent-free, so "dominadas" matches "Dominádas" and vice versa. */
        fun fold(s: String): String = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()
    }
}
