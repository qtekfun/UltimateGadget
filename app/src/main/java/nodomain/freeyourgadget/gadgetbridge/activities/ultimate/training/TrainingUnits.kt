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
import java.util.Locale

/** Metric or imperial display; storage is always kg / metres. */
/** [locale] decides the decimal separator of the display texts; null follows the device language. */
class TrainingUnits(val imperial: Boolean, private val locale: Locale? = null) {
    val weightLabelRes get() = if (imperial) nodomain.freeyourgadget.gadgetbridge.R.string.ult_train_lb else nodomain.freeyourgadget.gadgetbridge.R.string.ult_train_kg
    val distanceLabelRes get() = if (imperial) nodomain.freeyourgadget.gadgetbridge.R.string.ult_train_mi else nodomain.freeyourgadget.gadgetbridge.R.string.ult_train_km

    fun weightValue(kg: Double) = if (imperial) TrainingMath.kgToLb(kg) else kg
    fun distanceValue(m: Double) = if (imperial) TrainingMath.metresToMiles(m) else TrainingMath.metresToKm(m)

    /** Number text for an input field: no trailing zeros, dot decimal separator. */
    fun weightText(kg: Double?): String = kg?.let { trim(weightValue(it)) } ?: ""
    fun distanceText(m: Double?): String = m?.let { trim(distanceValue(it)) } ?: ""

    fun parseWeight(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.let { if (imperial) TrainingMath.lbToKg(it) else it }
    fun parseDistance(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.let { if (imperial) TrainingMath.milesToMetres(it) else it * 1000.0 }

    /** "82.5 kg" style text for summaries, with the locale decimal separator ("82,5 kg" in Spanish). */
    fun weightWithUnit(kg: Double, unit: String) = display(weightValue(kg)) + " " + unit

    /** "1.5 km" style text, with the locale decimal separator. */
    fun distanceWithUnit(m: Double, unit: String) = display(distanceValue(m)) + " " + unit

    private fun display(v: Double): String =
        java.text.NumberFormat.getNumberInstance(locale ?: Locale.getDefault()).apply {
            maximumFractionDigits = 2; minimumFractionDigits = 0; isGroupingUsed = false
        }.format(v)

    private fun trim(v: Double): String {
        val r = Math.round(v * 100.0) / 100.0
        return if (r == Math.floor(r)) r.toLong().toString() else String.format(Locale.US, "%s", r)
    }

    companion object {
        private const val PREFS = "ultimate_training_prefs"
        private const val KEY = "imperial"
        fun load(context: Context) = TrainingUnits(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false))
        fun save(context: Context, imperial: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, imperial).apply()
        }
    }
}
