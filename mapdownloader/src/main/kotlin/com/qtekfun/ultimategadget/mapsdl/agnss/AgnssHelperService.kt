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
package com.qtekfun.ultimategadget.mapsdl.agnss

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

/**
 * Bound helper that UltimateGadget (which deliberately has no internet) binds to in order to get
 * fresh A-GNSS data. It downloads public broadcast ephemeris and returns the ready RTCM bytes;
 * UltimateGadget assembles and serves the ephemeris.zip to the watch.
 *
 * The service is protected by a signature-level permission, so only apps signed with the same key
 * (i.e. UltimateGadget) can bind. The download runs on a background thread.
 */
class AgnssHelperService : Service() {

    companion object {
        const val ACTION = "com.qtekfun.ultimategadget.agnss.HELPER"
        const val MSG_GENERATE = 1
        const val MSG_RESULT = 2
        const val KEY_WIFI_ONLY = "wifiOnly"
        const val KEY_OK = "ok"
        const val KEY_RTCM = "rtcm"
        const val KEY_VALID_UNTIL = "validUntil"
        const val KEY_SOURCE = "source"
        const val KEY_ERROR = "error"
        const val KEY_GPS = "gps"
        const val KEY_GLONASS = "glonass"
        const val KEY_GALILEO = "galileo"
        const val KEY_BEIDOU = "beidou"
    }

    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("agnss-helper").apply { start() }
        messenger = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what != MSG_GENERATE) {
                    super.handleMessage(msg); return
                }
                val reply = msg.replyTo
                val wifiOnly = msg.data?.getBoolean(KEY_WIFI_ONLY, false) ?: false
                val out = Message.obtain(null, MSG_RESULT)
                val b = Bundle()
                try {
                    if (wifiOnly && !isOnWifi()) throw IllegalStateException("Sin Wi-Fi")
                    val res = AgnssDownloader.generate()
                    b.putBoolean(KEY_OK, true)
                    b.putByteArray(KEY_RTCM, res.rtcm)
                    b.putLong(KEY_VALID_UNTIL, res.validUntilMs)
                    b.putString(KEY_SOURCE, res.source)
                    b.putInt(KEY_GPS, res.counts['G'] ?: 0)
                    b.putInt(KEY_GLONASS, res.counts['R'] ?: 0)
                    b.putInt(KEY_GALILEO, res.counts['E'] ?: 0)
                    b.putInt(KEY_BEIDOU, res.counts['C'] ?: 0)
                } catch (e: Exception) {
                    b.putBoolean(KEY_OK, false)
                    b.putString(KEY_ERROR, e.message ?: "error")
                }
                out.data = b
                try {
                    reply?.send(out)
                } catch (_: RemoteException) {
                    // client gone; nothing to do
                }
            }
        })
    }

    private fun isOnWifi(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
    }
}
