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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.notifications

import nodomain.freeyourgadget.gadgetbridge.GBApplication

/**
 * Quiet-hours ("No molestar") rule for notifications, persisted in the shared app preferences
 * with UltimateGadget-owned keys. This is a phone-global rule (not per device).
 *
 * Keys written here (all in the default [GBApplication.getPrefs] SharedPreferences):
 *  - "ultimate_notif_quiet_enabled" : Boolean   — master switch for the quiet-hours rule.
 *  - "ultimate_notif_quiet_start"   : Int       — start of the window, minutes since midnight [0..1439].
 *  - "ultimate_notif_quiet_end"     : Int       — end of the window, minutes since midnight [0..1439].
 *  - "ultimate_notif_quiet_days"    : StringSet — active days, each "1".."7" (Calendar.DAY_OF_WEEK,
 *                                                 1 = Sunday … 7 = Saturday).
 *
 * The rule is persisted only; wiring it into the notification pipeline is left to the core service
 * and is out of scope for this UI. [isQuietNow] is provided so a future caller can evaluate it.
 */
object NotificationRulesStore {
    const val KEY_ENABLED = "ultimate_notif_quiet_enabled"
    const val KEY_START = "ultimate_notif_quiet_start"
    const val KEY_END = "ultimate_notif_quiet_end"
    const val KEY_DAYS = "ultimate_notif_quiet_days"

    private const val DEFAULT_START = 22 * 60 // 22:00
    private const val DEFAULT_END = 7 * 60    // 07:00

    /** Calendar.DAY_OF_WEEK values, Sunday..Saturday. */
    val ALL_DAYS: Set<Int> = (1..7).toSet()

    data class QuietHours(
        val enabled: Boolean,
        val startMinutes: Int,
        val endMinutes: Int,
        val days: Set<Int>,
    )

    fun load(): QuietHours {
        val prefs = GBApplication.getPrefs().preferences
        val days = prefs.getStringSet(KEY_DAYS, null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: ALL_DAYS
        return QuietHours(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            startMinutes = prefs.getInt(KEY_START, DEFAULT_START).coerceIn(0, 1439),
            endMinutes = prefs.getInt(KEY_END, DEFAULT_END).coerceIn(0, 1439),
            days = if (days.isEmpty()) ALL_DAYS else days,
        )
    }

    fun save(rule: QuietHours) {
        GBApplication.getPrefs().preferences.edit()
            .putBoolean(KEY_ENABLED, rule.enabled)
            .putInt(KEY_START, rule.startMinutes.coerceIn(0, 1439))
            .putInt(KEY_END, rule.endMinutes.coerceIn(0, 1439))
            .putStringSet(KEY_DAYS, rule.days.map { it.toString() }.toSet())
            .apply()
    }

    /**
     * Whether the given day-of-week + minute-of-day currently falls inside the quiet window.
     * Handles windows that wrap past midnight (e.g. 22:00 → 07:00). The day refers to the day the
     * window started on.
     */
    fun isQuietNow(rule: QuietHours, dayOfWeek: Int, minuteOfDay: Int): Boolean {
        if (!rule.enabled) return false
        if (dayOfWeek !in rule.days) return false
        return if (rule.startMinutes <= rule.endMinutes) {
            minuteOfDay in rule.startMinutes until rule.endMinutes
        } else {
            // wraps past midnight: active late evening OR early morning
            minuteOfDay >= rule.startMinutes || minuteOfDay < rule.endMinutes
        }
    }

    fun formatMinutes(minutes: Int): String {
        val m = minutes.coerceIn(0, 1439)
        return "%02d:%02d".format(m / 60, m % 60)
    }
}
