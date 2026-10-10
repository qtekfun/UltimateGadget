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

/** Small GNSS time helpers (no java.time, so it works on minSdk 24 without desugaring). */
object GnssTime {

    private const val SECONDS_PER_WEEK = 604800L

    /** Days since 1970-01-01 for a civil date (proleptic Gregorian, Howard Hinnant's algorithm). */
    fun daysFromCivil(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = (yy - era * 400).toLong()
        val doy = ((153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1).toLong()
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097 + doe - 719468
    }

    /** Absolute seconds since the Unix epoch for a UTC/GPS civil timestamp. */
    fun unixSeconds(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long =
        daysFromCivil(y, mo, d) * 86400L + h * 3600L + mi * 60L + s

    // GPS epoch 1980-01-06 00:00:00.
    private val GPS_EPOCH_UNIX = unixSeconds(1980, 1, 6, 0, 0, 0)

    /** Seconds of week for a timestamp, using GPS-aligned (Sunday 00:00) week boundaries. */
    fun gpsSecondsOfWeek(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        val gps = unixSeconds(y, mo, d, h, mi, s) - GPS_EPOCH_UNIX
        return ((gps % SECONDS_PER_WEEK) + SECONDS_PER_WEEK) % SECONDS_PER_WEEK
    }

    /** Full GPS week number for a timestamp. */
    fun gpsWeek(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        val gps = unixSeconds(y, mo, d, h, mi, s) - GPS_EPOCH_UNIX
        return gps / SECONDS_PER_WEEK
    }

    fun compareEpoch(a: RinexNav.Eph, b: RinexNav.Eph): Int {
        val ta = unixSeconds(a.year, a.month, a.day, a.hour, a.minute, a.second)
        val tb = unixSeconds(b.year, b.month, b.day, b.hour, b.minute, b.second)
        return ta.compareTo(tb)
    }
}
