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

/**
 * Minimal RINEX 3 navigation (ephemeris) parser for GPS (G), GLONASS (R), Galileo (E) and
 * BeiDou (C). Reads the broadcast-orbit records into a flat list of doubles per satellite epoch,
 * in the canonical RINEX order, so the per-constellation RTCM mapper can pick fields by index.
 *
 * This is public IGS/BKG data (not a capture), parsed as plain text. Fortran "D" exponents are
 * accepted. Satellite systems have a fixed number of broadcast-orbit lines: GPS/GAL/BDS use 7,
 * GLONASS uses 3.
 */
object RinexNav {

    /** One broadcast ephemeris record. [values] are the clock terms followed by the orbit terms,
     *  in RINEX order (clock: a0,a1,a2; then each orbit line's 4 values left-to-right). */
    data class Eph(
        val system: Char,
        val prn: Int,
        val year: Int, val month: Int, val day: Int,
        val hour: Int, val minute: Int, val second: Int,
        val values: DoubleArray,
    ) {
        /** Seconds of the record epoch within its constellation week (for toc). */
        fun epochGpsSeconds(): Long = GnssTime.gpsSecondsOfWeek(year, month, day, hour, minute, second)
    }

    private fun orbitLines(system: Char): Int = if (system == 'R' || system == 'S') 3 else 7

    private fun parseFortran(s: String): Double {
        val t = s.trim().replace('D', 'E').replace('d', 'E')
        if (t.isEmpty() || t == "-" || t == "+") return 0.0
        return t.toDoubleOrNull() ?: 0.0
    }

    /** Parse all ephemeris records of the given systems from RINEX 3 nav text. */
    fun parse(text: String, systems: Set<Char> = setOf('G', 'R', 'E', 'C')): List<Eph> {
        val lines = text.split('\n')
        var i = 0
        // skip header
        while (i < lines.size && !lines[i].contains("END OF HEADER")) i++
        if (i < lines.size) i++
        val out = ArrayList<Eph>()
        while (i < lines.size) {
            val head = lines[i]
            if (head.length < 23 || head[0] == ' ') { i++; continue }
            val sys = head[0]
            val prn = head.substring(1, 3).trim().toIntOrNull()
            if (prn == null) { i++; continue }
            val n = orbitLines(sys)
            if (i + n >= lines.size) break
            if (sys !in systems) { i += n + 1; continue }
            val year = head.substring(4, 8).trim().toIntOrNull() ?: 0
            val month = head.substring(9, 11).trim().toIntOrNull() ?: 0
            val day = head.substring(12, 14).trim().toIntOrNull() ?: 0
            val hour = head.substring(15, 17).trim().toIntOrNull() ?: 0
            val minute = head.substring(18, 20).trim().toIntOrNull() ?: 0
            val second = head.substring(21, 23).trim().toIntOrNull() ?: 0
            val vals = ArrayList<Double>()
            // three clock terms on the epoch line (cols 23.. in 19-char groups)
            for (k in 0 until 3) {
                val a = 23 + k * 19
                vals.add(if (head.length >= a + 19) parseFortran(head.substring(a, a + 19)) else 0.0)
            }
            for (ln in 1..n) {
                val l = lines[i + ln]
                for (k in 0 until 4) {
                    val a = 4 + k * 19
                    vals.add(if (l.length >= a + 19) parseFortran(l.substring(a, a + 19)) else 0.0)
                }
            }
            out.add(Eph(sys, prn, year, month, day, hour, minute, second, vals.toDoubleArray()))
            i += n + 1
        }
        return out
    }

    /** Keep only the newest record per satellite (latest epoch). */
    fun latestPerSat(ephs: List<Eph>): List<Eph> {
        val best = HashMap<String, Eph>()
        for (e in ephs) {
            val key = "${e.system}${e.prn}"
            val cur = best[key]
            if (cur == null || GnssTime.compareEpoch(e, cur) > 0) best[key] = e
        }
        return best.values.sortedWith(compareBy({ it.system }, { it.prn }))
    }
}
