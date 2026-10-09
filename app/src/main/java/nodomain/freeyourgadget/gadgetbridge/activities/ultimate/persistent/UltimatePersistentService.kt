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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.UltimateHomeActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import org.slf4j.LoggerFactory
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Optional, Huawei-Health-style persistent (ongoing) notification.
 *
 * Shows today's step count (with goal, distance and calories when available) plus the active
 * watch's connection state and battery, and runs as a foreground service so the OS is less likely
 * to kill the app on aggressive OEM ROMs. Entirely opt-in via
 * [UltimatePersistentNotification.PREF_KEY]; tapping it opens [UltimateHomeActivity].
 *
 * It is a SEPARATE foreground service from the core [DeviceCommunicationService] and uses its own
 * notification channel and id, so it never interferes with Gadgetbridge's own connection
 * notification.
 *
 * Refresh cadence: rebuilt every [UPDATE_INTERVAL_MS] and also immediately whenever the core
 * broadcasts [GBApplication.ACTION_NEW_DATA] (activity sync finished) or
 * [GBDevice.ACTION_DEVICE_CHANGED] (connection/battery changed). All DB reads run off the main
 * thread.
 */
class UltimatePersistentService : Service() {
    private val log = LoggerFactory.getLogger(UltimatePersistentService::class.java)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private val tick = Runnable { refresh() }

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Must call startForeground promptly; show a placeholder, then fill it in asynchronously.
        startForegroundCompat(buildNotification(Content.loading(this)))

        val filter = IntentFilter().apply {
            addAction(GBApplication.ACTION_NEW_DATA)
            addAction(GBDevice.ACTION_DEVICE_CHANGED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(dataReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tick)
        runCatching { LocalBroadcastManager.getInstance(this).unregisterReceiver(dataReceiver) }
        io.shutdownNow()
        super.onDestroy()
    }

    /** Recompute content off the main thread, then update the notification and reschedule. */
    private fun refresh() {
        mainHandler.removeCallbacks(tick)
        io.execute {
            val content = runCatching { Content.read(this) }.getOrDefault(Content.loading(this))
            mainHandler.post {
                runCatching {
                    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(content))
                }.onFailure { log.warn("Could not post persistent notification", it) }
                mainHandler.postDelayed(tick, UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- Notification building ---------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pasos (notificación fija)",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notificación persistente opcional con los pasos del día"
                setShowBadge(false)
            }
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(c: Content): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, UltimateHomeActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.title)
            .setContentText(c.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.bigText))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (c.progressMax > 0) {
            builder.setProgress(c.progressMax, c.progress.coerceIn(0, c.progressMax), false)
        }
        return builder.build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    /** Immutable snapshot of what the notification should display. */
    private data class Content(
        val title: String,
        val text: String,
        val bigText: String,
        val progress: Int,
        val progressMax: Int,
    ) {
        companion object {
            fun loading(context: Context) = Content(
                title = context.getString(R.string.app_name),
                text = "Cargando pasos…",
                bigText = "Cargando pasos…",
                progress = 0,
                progressMax = 0,
            )

            /** Reads today's steps + the active device's status straight from the GB database. */
            fun read(context: Context): Content {
                val app = context.applicationContext as GBApplication
                val devices = app.deviceManager.devices
                val device: GBDevice? =
                    devices.firstOrNull { it.isInitialized } ?: devices.firstOrNull()

                val goal = runCatching { ActivityUser().stepsGoal }
                    .getOrDefault(10000).coerceAtLeast(1)

                var steps = 0
                var distanceKm = 0.0
                var kcal = 0
                if (device != null) {
                    runCatching {
                        val totals = DailyTotals.getDailyTotalsForDevice(device, Calendar.getInstance())
                        steps = totals.steps.toInt()
                        distanceKm = totals.distance / 1000.0
                        kcal = (totals.activeCalories + totals.restingCalories).toInt()
                    }
                }

                // Title is the watch's name; the steps live in the body. No connection-state / battery
                // line and no connect/sync actions — just name + steps.
                val title = device?.let { it.aliasOrName ?: it.name } ?: "UltimateGadget"
                val stepsLine = "Pasos: ${"%,d".format(Locale.getDefault(), steps)} / ${"%,d".format(Locale.getDefault(), goal)}"
                val metrics = buildList {
                    if (distanceKm > 0) add("%.2f km".format(Locale.getDefault(), distanceKm))
                    if (kcal > 0) add("$kcal kcal")
                }.joinToString(" · ")

                val big = buildString {
                    append(stepsLine)
                    if (metrics.isNotEmpty()) append('\n').append(metrics)
                }

                return Content(
                    title = title,
                    text = stepsLine,
                    bigText = big,
                    progress = steps,
                    progressMax = goal,
                )
            }
        }
    }

    companion object {
        /** Distinct from every core GB notification id (1,2,4,5,6,7,8,10,11,42). */
        const val NOTIFICATION_ID = 9090
        const val CHANNEL_ID = "ultimate_persistent_steps"
        const val ACTION_STOP = "nodomain.freeyourgadget.gadgetbridge.ultimate.PERSISTENT_STOP"

        /** How often the notification is rebuilt when idle (also refreshed on new data). */
        const val UPDATE_INTERVAL_MS = 15L * 60 * 1000
    }
}
