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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Manual sleep/workout entries, so readiness works even on days the watch was not worn.
 * Stored per day (yyyy-MM-dd) in SharedPreferences as small JSON. Local only.
 */
class ManualLogStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    data class ManualDay(
        val sleepHours: Double? = null,
        val sleepQuality: Int? = null,   // 0..100
        val workoutMinutes: Int? = null,
        val workoutRpe: Int? = null,     // 1..10 perceived exertion
    )

    fun get(cal: Calendar = Calendar.getInstance()): ManualDay {
        val raw = prefs.getString(key(cal), null) ?: return ManualDay()
        return runCatching {
            val o = JSONObject(raw)
            ManualDay(
                sleepHours = if (o.has("sh")) o.getDouble("sh") else null,
                sleepQuality = if (o.has("sq")) o.getInt("sq") else null,
                workoutMinutes = if (o.has("wm")) o.getInt("wm") else null,
                workoutRpe = if (o.has("wr")) o.getInt("wr") else null,
            )
        }.getOrDefault(ManualDay())
    }

    fun set(day: ManualDay, cal: Calendar = Calendar.getInstance()) {
        val o = JSONObject()
        day.sleepHours?.let { o.put("sh", it) }
        day.sleepQuality?.let { o.put("sq", it) }
        day.workoutMinutes?.let { o.put("wm", it) }
        day.workoutRpe?.let { o.put("wr", it) }
        prefs.edit().putString(key(cal), o.toString()).apply()
    }

    /** Sum of manual workout "load" (minutes * rpe) over the last [days] days. */
    fun manualLoad(days: Int): Double {
        var total = 0.0
        val cal = Calendar.getInstance()
        repeat(days) {
            val d = get(cal)
            val m = d.workoutMinutes
            if (m != null) total += m * (d.workoutRpe ?: 5)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return total
    }

    private fun key(cal: Calendar) = "d_" + fmt.format(cal.time)

    companion object {
        private const val FILE = "ultimate_manual_log"
        private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
