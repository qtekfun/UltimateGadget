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

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fetches public multi-GNSS broadcast ephemeris (RINEX 3 nav) from BKG's open IGS archive and
 * turns it into the `ephemeris.zip` that UltimateGadget serves to the watch for A-GNSS. No Huawei
 * servers or formats are involved; the only network host is a public GNSS data centre.
 */
object AgnssDownloader {

    /** The download tag and file name the Huawei watch asks for (verified on a GT Runner 2). */
    const val DOWNLOAD_TAG = "higeo/v1/gnssinfo?type=0x0004/HW_AGNSS"
    private const val UUID = "ug-agnss"
    private const val RTCM_NAME = "HW_AGNSS_RTCM_33"

    // BKG open archive: daily merged broadcast navigation (all constellations), ~1 MB gzipped.
    private const val BKG_BASE = "https://igs.bkg.bund.de/root_ftp/IGS/BRDC"

    data class Result(
        val zip: ByteArray,
        val counts: Map<Char, Int>,
        val validUntilMs: Long,
        val source: String,
    )

    private fun url(year: Int, doy: Int): String =
        "$BKG_BASE/$year/%03d/BRDC00IGS_R_$year%03d0000_01D_MN.rnx.gz".format(doy, doy)

    /** Download today's (or, as fallback, yesterday's) broadcast ephemeris and build ephemeris.zip. */
    fun generate(): Result {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        var lastError: Exception? = null
        // Try today then the previous two days (the current day's file fills in through the day).
        for (back in 0..2) {
            val y = cal.get(GregorianCalendar.YEAR)
            val doy = cal.get(GregorianCalendar.DAY_OF_YEAR)
            try {
                val rnx = String(gunzip(httpGet(url(y, doy))), Charsets.US_ASCII)
                val ephs = RinexNav.latestPerSat(RinexNav.parse(rnx))
                if (ephs.isNotEmpty()) return assemble(ephs, "BRDC $y/$doy (BKG)")
            } catch (e: Exception) {
                lastError = e
            }
            cal.add(GregorianCalendar.DAY_OF_YEAR, -1)
        }
        throw IllegalStateException("No se pudo obtener efemérides: ${lastError?.message ?: "sin datos"}")
    }

    private fun assemble(ephs: List<RinexNav.Eph>, source: String): Result {
        val rtcm = AgnssBuilder.buildRtcm(ephs)
        val counts = ephs.groupingBy { it.system }.eachCount()
        val now = System.currentTimeMillis()
        val config =
            """{"$DOWNLOAD_TAG":{"ver":3,"uuid":"$UUID","files":["$RTCM_NAME"]}}"""
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("time")); zip.write(now.toString().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("ephemeris_config.json")); zip.write(config.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("$UUID/$RTCM_NAME")); zip.write(rtcm); zip.closeEntry()
        }
        // Broadcast ephemeris is practically useful for a few hours; expose a conservative window.
        return Result(bos.toByteArray(), counts, now + 3 * 3600_000L, source)
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }

    private fun httpGet(url: String): ByteArray {
        var current = url
        repeat(5) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20000
                readTimeout = 60000
                setRequestProperty("User-Agent", "UltimateGadget-MapsDownloader")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location"); c.disconnect()
                requireNotNull(loc) { "Redirección sin destino" }
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                require(current.startsWith("https://")) { "Redirección no segura" }
                return@repeat
            }
            if (code !in 200..299) { c.disconnect(); throw IllegalStateException("HTTP $code") }
            try { return c.inputStream.readBytes() } finally { c.disconnect() }
        }
        throw IllegalStateException("Demasiadas redirecciones")
    }
}
