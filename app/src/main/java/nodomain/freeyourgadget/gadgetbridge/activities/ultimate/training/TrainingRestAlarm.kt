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

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Rest timer alarm. The timer end is stored in the session itself; this only wakes the app up when
 * it ends: a vibration when the workout screen is open, a notification when it is not.
 */
object TrainingRestAlarm {
    const val CHANNEL = "training_rest"
    private const val NOTIFICATION_ID = 41001
    private const val REQUEST = 41002

    /** Set by the workout screen while it is visible. */
    @Volatile var screenVisible = false

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, RestTimerReceiver::class.java).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context, endsAtMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Inexact-while-idle is plenty for a rest timer and needs no special permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, pending(context))
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
    }

    internal fun notify(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ult_train_channel), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, 0, TrainingSessionActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.ult_train_rest_over))
                .setContentText(context.getString(R.string.ult_train_rest_over_sub))
                .setContentIntent(open).setAutoCancel(true).setVibrate(longArrayOf(0, 250, 120, 250))
                .setCategory(NotificationCompat.CATEGORY_ALARM).build(),
        )
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Skip if the session was finished or the timer pushed back in the meantime.
        val s = TrainingStore.get(context).activeSession() ?: return
        if (s.restEndsAtMs == 0L || s.restEndsAtMs > System.currentTimeMillis() + 1500) return
        if (TrainingRestAlarm.screenVisible) TrainingRestAlarm.vibrate(context) else TrainingRestAlarm.notify(context)
    }
}
