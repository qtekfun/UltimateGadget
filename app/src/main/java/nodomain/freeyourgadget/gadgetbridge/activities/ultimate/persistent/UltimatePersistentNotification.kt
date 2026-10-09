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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.persistent

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import org.slf4j.LoggerFactory

/**
 * Public control surface for the OPTIONAL Huawei-Health-style persistent notification: a single
 * UltimateGadget-owned preference plus the start/stop plumbing for [UltimatePersistentService].
 *
 * This is deliberately isolated from the Gadgetbridge core: the core already runs its own
 * foreground service ([nodomain.freeyourgadget.gadgetbridge.service.DeviceCommunicationService])
 * with a non-optional connection notification, which we do not touch. This feature adds a second,
 * user-toggleable foreground service whose only job is to show today's steps (plus the watch's
 * connection/battery) and to keep the process warm so aggressive OEM task killers (ColorOS/OPPO,
 * etc.) are less likely to kill the app.
 *
 * Preference (in the default [GBApplication.getPrefs] SharedPreferences):
 *  - "ultimate_persistent_notification" : Boolean — master switch, default false.
 *
 * Callers:
 *  - [UltimatePersistentActivity] reads/writes the pref via [isEnabled]/[setEnabled].
 *  - [syncFromPrefs] can be called from app/UI startup (e.g. GBApplication.onCreate or
 *    ControlCenterv2/UltimateHomeActivity.onCreate) to re-arm the service after a reboot/relaunch.
 *    Wiring that call is left to the integrator (reported, not done in core).
 */
object UltimatePersistentNotification {
    private val LOG = LoggerFactory.getLogger(UltimatePersistentNotification::class.java)

    const val PREF_KEY = "ultimate_persistent_notification"

    fun isEnabled(context: Context): Boolean =
        GBApplication.getPrefs().getBoolean(PREF_KEY, false)

    /** Persists the toggle and starts/stops the foreground service accordingly. */
    fun setEnabled(context: Context, enabled: Boolean) {
        GBApplication.getPrefs().preferences.edit()
            .putBoolean(PREF_KEY, enabled)
            .apply()
        if (enabled) start(context) else stop(context)
    }

    /** Start the service if the pref is on; stop it otherwise. Safe to call repeatedly. */
    fun syncFromPrefs(context: Context) {
        if (isEnabled(context)) start(context) else stop(context)
    }

    fun start(context: Context) {
        val intent = Intent(context.applicationContext, UltimatePersistentService::class.java)
        try {
            ContextCompat.startForegroundService(context.applicationContext, intent)
        } catch (e: Exception) {
            // e.g. background-start restrictions; the toggle still persists and will be honored
            // next time syncFromPrefs() runs from a valid (foreground) context.
            LOG.warn("Could not start persistent notification service", e)
        }
    }

    fun stop(context: Context) {
        val intent = Intent(context.applicationContext, UltimatePersistentService::class.java)
        intent.action = UltimatePersistentService.ACTION_STOP
        try {
            // Deliver a stop command so the service tears down its own foreground state cleanly.
            ContextCompat.startForegroundService(context.applicationContext, intent)
        } catch (e: Exception) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, UltimatePersistentService::class.java)
            )
        }
    }
}
