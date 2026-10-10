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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * UltimateGadget A-GNSS bridge. Gadgetbridge has no internet, so the map companion
 * (com.qtekfun.ultimategadget.mapsdl) downloads public broadcast ephemeris and returns the ready
 * RTCM 3.3 bytes over a signature-protected bound service. We cache those bytes and assemble the
 * ephemeris.zip the watch pulls (via HuaweiEphemerisManager), re-stamping its "time" so it passes
 * the freshness check whenever the watch asks.
 *
 * Nothing is ever pushed to the watch here: the watch pulls the file on its own schedule.
 */
object HuaweiAgnssHelper {
    private val LOG = LoggerFactory.getLogger(HuaweiAgnssHelper::class.java)

    const val COMPANION_PKG = "com.qtekfun.ultimategadget.mapsdl"
    private const val HELPER_ACTION = "com.qtekfun.ultimategadget.agnss.HELPER"

    // Must match AgnssHelperService in the companion.
    private const val MSG_GENERATE = 1
    private const val MSG_RESULT = 2
    private const val KEY_WIFI_ONLY = "wifiOnly"
    private const val KEY_OK = "ok"
    private const val KEY_RTCM = "rtcm"
    private const val KEY_VALID_UNTIL = "validUntil"
    private const val KEY_SOURCE = "source"
    private const val KEY_ERROR = "error"
    private const val KEY_GPS = "gps"
    private const val KEY_GLONASS = "glonass"
    private const val KEY_GALILEO = "galileo"
    private const val KEY_BEIDOU = "beidou"

    // A-GNSS protocol framing the watch expects (verified on a GT Runner 2).
    private const val DOWNLOAD_TAG = "higeo/v1/gnssinfo?type=0x0004/HW_AGNSS"
    private const val UUID = "ug-agnss"
    private const val RTCM_NAME = "HW_AGNSS_RTCM_33"

    const val PREFS = "huawei_agnss"
    const val PREF_ENABLED = "agnss_auto_on_connect"
    const val PREF_WIFI_ONLY = "agnss_wifi_only"
    private const val PREF_FETCHED_AT = "agnss_fetched_at"
    private const val PREF_VALID_UNTIL = "agnss_valid_until"
    private const val PREF_SOURCE = "agnss_source"
    private const val PREF_GPS = "agnss_gps"
    private const val PREF_GLONASS = "agnss_glonass"
    private const val PREF_GALILEO = "agnss_galileo"
    private const val PREF_BEIDOU = "agnss_beidou"

    private const val CACHE_FILE = "agnss_rtcm.bin"
    private const val SERVED_FILE = "ephemeris.zip"
    private const val REQUEST_TIMEOUT_MS = 90_000L

    fun interface ResultCallback {
        fun onResult(ok: Boolean, message: String)
    }

    data class Status(
        val hasData: Boolean,
        val fetchedAt: Long,
        val validUntil: Long,
        val source: String,
        val gps: Int, val glonass: Int, val galileo: Int, val beidou: Int,
    )

    @JvmStatic
    fun isCompanionInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(COMPANION_PKG, 0); true
    }.getOrDefault(false)

    @JvmStatic
    fun status(context: Context): Status {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fetched = p.getLong(PREF_FETCHED_AT, 0)
        return Status(
            hasData = fetched > 0 && File(context.filesDir, CACHE_FILE).exists(),
            fetchedAt = fetched,
            validUntil = p.getLong(PREF_VALID_UNTIL, 0),
            source = p.getString(PREF_SOURCE, "") ?: "",
            gps = p.getInt(PREF_GPS, 0), glonass = p.getInt(PREF_GLONASS, 0),
            galileo = p.getInt(PREF_GALILEO, 0), beidou = p.getInt(PREF_BEIDOU, 0),
        )
    }

    /** Assemble the served ephemeris.zip from cached RTCM with a fresh "time". No network. */
    @JvmStatic
    fun writeServedZipFromCache(context: Context): Boolean {
        val cache = File(context.filesDir, CACHE_FILE)
        if (!cache.exists()) return false
        return runCatching {
            val rtcm = cache.readBytes()
            val config = """{"$DOWNLOAD_TAG":{"ver":3,"uuid":"$UUID","files":["$RTCM_NAME"]}}"""
            val out = File(context.getExternalFilesDir(null), SERVED_FILE)
            ZipOutputStream(out.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("time")); zip.write(System.currentTimeMillis().toString().toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("ephemeris_config.json")); zip.write(config.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("$UUID/$RTCM_NAME")); zip.write(rtcm); zip.closeEntry()
            }
            LOG.info("A-GNSS: wrote served ephemeris.zip ({} bytes RTCM)", rtcm.size)
            true
        }.getOrElse { LOG.error("A-GNSS: writeServedZipFromCache failed", it); false }
    }

    /** Whether the cached data is still within its validity window. */
    @JvmStatic
    fun cacheFresh(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return File(context.filesDir, CACHE_FILE).exists() &&
            System.currentTimeMillis() < p.getLong(PREF_VALID_UNTIL, 0)
    }

    /**
     * Called when a Huawei device connects. If auto-refresh is on: refresh from the companion when
     * the cache is stale, otherwise just re-stamp the served file so the watch gets a fresh "time".
     */
    @JvmStatic
    fun onDeviceConnected(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Off by default: the watch only ever receives A-GNSS once the user opts in.
        if (!p.getBoolean(PREF_ENABLED, false)) return
        if (cacheFresh(context)) {
            writeServedZipFromCache(context)
            return
        }
        if (isCompanionInstalled(context)) {
            refresh(context, p.getBoolean(PREF_WIFI_ONLY, false)) { ok, msg ->
                LOG.info("A-GNSS: auto-refresh on connect: {} {}", ok, msg)
            }
        }
    }

    private var inFlight = false

    /** Bind the companion, fetch fresh RTCM, cache it and write the served zip. Async. */
    @JvmStatic
    @Synchronized
    fun refresh(context: Context, wifiOnly: Boolean, callback: ResultCallback) {
        if (!isCompanionInstalled(context)) {
            callback.onResult(false, "La app de mapas no está instalada"); return
        }
        if (inFlight) {
            callback.onResult(false, "Ya hay una actualización en curso"); return
        }
        inFlight = true
        val appContext = context.applicationContext
        val thread = HandlerThread("agnss-client").apply { start() }
        val handler = Handler(thread.looper)

        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        lateinit var connection: ServiceConnection

        fun finish(ok: Boolean, message: String) {
            if (!done.compareAndSet(false, true)) return
            runCatching { appContext.unbindService(connection) }
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
            inFlight = false
            callback.onResult(ok, message)
        }

        val replyMessenger = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what != MSG_RESULT) { super.handleMessage(msg); return }
                val b = msg.data
                if (b == null || !b.getBoolean(KEY_OK, false)) {
                    finish(false, b?.getString(KEY_ERROR) ?: "Error desconocido"); return
                }
                val rtcm = b.getByteArray(KEY_RTCM)
                if (rtcm == null || rtcm.isEmpty()) { finish(false, "Respuesta vacía"); return }
                runCatching {
                    File(appContext.filesDir, CACHE_FILE).writeBytes(rtcm)
                    appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putLong(PREF_FETCHED_AT, System.currentTimeMillis())
                        .putLong(PREF_VALID_UNTIL, b.getLong(KEY_VALID_UNTIL, 0))
                        .putString(PREF_SOURCE, b.getString(KEY_SOURCE, ""))
                        .putInt(PREF_GPS, b.getInt(KEY_GPS, 0)).putInt(PREF_GLONASS, b.getInt(KEY_GLONASS, 0))
                        .putInt(PREF_GALILEO, b.getInt(KEY_GALILEO, 0)).putInt(PREF_BEIDOU, b.getInt(KEY_BEIDOU, 0))
                        .apply()
                    writeServedZipFromCache(appContext)
                }.onFailure { finish(false, "Error al guardar: ${it.message}"); return }
                val n = b.getInt(KEY_GPS, 0) + b.getInt(KEY_GLONASS, 0) + b.getInt(KEY_GALILEO, 0) + b.getInt(KEY_BEIDOU, 0)
                finish(true, "Actualizado: $n satélites (${b.getString(KEY_SOURCE, "")})")
            }
        })

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runCatching {
                    val out = Message.obtain(null, MSG_GENERATE)
                    out.replyTo = replyMessenger
                    out.data = Bundle().apply { putBoolean(KEY_WIFI_ONLY, wifiOnly) }
                    Messenger(binder).send(out)
                }.onFailure { finish(false, "No se pudo pedir al servicio: ${it.message}") }
            }
            override fun onServiceDisconnected(name: ComponentName?) { finish(false, "Servicio desconectado") }
        }

        handler.postDelayed({ finish(false, "Tiempo de espera agotado") }, REQUEST_TIMEOUT_MS)

        val intent = Intent(HELPER_ACTION).setPackage(COMPANION_PKG)
        val bound = runCatching { appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) finish(false, "No se pudo conectar con la app de mapas")
    }
}
