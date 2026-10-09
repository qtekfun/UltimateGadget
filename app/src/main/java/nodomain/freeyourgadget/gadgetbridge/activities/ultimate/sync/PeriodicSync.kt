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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes

/**
 * Periodically asks the connected watch to sync its recorded data, so the app's data stays fresh
 * without the user pulling to refresh. Gadgetbridge's built-in auto-fetch is event-driven (on unlock);
 * this adds a simple fixed-interval (5 min) sync via [AlarmManager], batched/inexact to spare battery.
 *
 * Scheduled from the launcher; it survives the activity, and each firing re-arms the next one so it
 * keeps going while the alarm is registered. It no-ops when no device is initialized.
 */
object PeriodicSync {
    const val INTERVAL_MS = 5 * 60 * 1000L
    private const val ACTION = "nodomain.freeyourgadget.gadgetbridge.ultimate.ACTION_PERIODIC_SYNC"
    private const val REQUEST = 0x75C9

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, UltimateSyncReceiver::class.java).setAction(ACTION)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST, intent, flags)
    }

    fun syncNow() {
        val connected = runCatching {
            GBApplication.app().deviceManager.devices.any { it.isInitialized }
        }.getOrDefault(false)
        if (connected) {
            runCatching {
                GBApplication.deviceService().onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
            }
        }
    }
}

/** Fires on each periodic alarm and triggers a sync of any connected device. */
class UltimateSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PeriodicSync.syncNow()
    }
}
