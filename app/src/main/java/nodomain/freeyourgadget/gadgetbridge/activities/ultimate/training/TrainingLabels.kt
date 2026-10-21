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

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import java.util.Locale

/**
 * Localized labels for the muscle, equipment and category names that come (in English) from the
 * exercise packs and the built-in list. Unknown text, such as the user's own entries, is shown as is.
 */
object TrainingLabels {
    private val RES: Map<String, Int> = mapOf(
        "quads" to R.string.ult_train_label_quads,
        "quadriceps" to R.string.ult_train_label_quads,
        "glutes" to R.string.ult_train_label_glutes,
        "lats" to R.string.ult_train_label_lats,
        "trapezius" to R.string.ult_train_label_trapezius,
        "obliquus externus abdominis" to R.string.ult_train_label_obliques,
        "serratus anterior" to R.string.ult_train_label_serratus,
        "soleus" to R.string.ult_train_label_soleus,
        "brachialis" to R.string.ult_train_label_brachialis,
        "biceps" to R.string.ult_train_label_biceps,
        "triceps" to R.string.ult_train_label_triceps,
        "chest" to R.string.ult_train_label_chest,
        "abs" to R.string.ult_train_label_abs,
        "calves" to R.string.ult_train_label_calves,
        "hamstrings" to R.string.ult_train_label_hamstrings,
        "shoulders" to R.string.ult_train_label_shoulders,
        "back" to R.string.ult_train_label_back,
        "cardio" to R.string.ult_train_label_cardio,
        "legs" to R.string.ult_train_label_legs,
        "arms" to R.string.ult_train_label_arms,
        "dumbbell" to R.string.ult_train_label_dumbbell,
        "cable machine" to R.string.ult_train_label_cable,
        "cable" to R.string.ult_train_label_cable,
        "barbell" to R.string.ult_train_label_barbell,
        "bench" to R.string.ult_train_label_bench,
        "pull-up bar" to R.string.ult_train_label_pullup_bar,
        "resistance band" to R.string.ult_train_label_band,
        "incline bench" to R.string.ult_train_label_incline_bench,
        "sz-bar" to R.string.ult_train_label_sz_bar,
        "kettlebell" to R.string.ult_train_label_kettlebell,
        "gym mat" to R.string.ult_train_label_mat,
        "swiss ball" to R.string.ult_train_label_swiss_ball,
        "bodyweight" to R.string.ult_train_label_bodyweight,
        "none (bodyweight exercise)" to R.string.ult_train_label_bodyweight,
        "none" to R.string.ult_train_label_none,
        "machine" to R.string.ult_train_label_machine,
        "bike" to R.string.ult_train_label_bike,
        "rope" to R.string.ult_train_label_rope,
    )

    @StringRes
    fun resFor(term: String): Int? = RES[term.trim().lowercase()]

    /** Splits a stored list such as "quads, glutes" into its terms. */
    fun terms(list: String): List<String> = list.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun label(context: Context, term: String): String = resFor(term)?.let { context.getString(it) } ?: term.trim()

    /** "quads, glutes" becomes "cuádriceps, glúteos" in the app language. */
    fun labels(context: Context, list: String): String = terms(list).joinToString(", ") { label(context, it) }

    /**
     * Builds a function that returns, for a stored list, its labels in English and in Spanish, so a
     * search finds an exercise whichever of the two the user types.
     */
    fun searchText(context: Context): (String) -> String {
        val contexts = listOf(Locale.ENGLISH, Locale("es")).map { l ->
            context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(l) })
        }
        return { list -> terms(list).joinToString("\n") { t -> contexts.joinToString("\n") { label(it, t) } } }
    }
}
