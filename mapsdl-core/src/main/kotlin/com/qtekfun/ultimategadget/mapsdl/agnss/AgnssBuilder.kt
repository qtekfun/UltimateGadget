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

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Builds the HW_AGNSS RTCM 3.3 stream the Huawei watch wants, from public broadcast ephemeris
 * (RINEX 3 nav). Maps each satellite's RINEX parameters to the RTCM scaled integers and emits the
 * framed messages (1019 GPS, 1042 BeiDou, 1046 Galileo, 1020 GLONASS).
 *
 * Angular Keplerian elements are in semicircles in RTCM (radians / pi); the harmonic correction
 * terms (Cuc/Cus/Cic/Cis) stay in radians and Crs/Crc in metres.
 */
object AgnssBuilder {

    /** int = round( (angular ? v/pi : v) / 2^exp ). */
    private fun q(v: Double, exp: Int, angular: Boolean = false): Long =
        ((if (angular) v / PI else v) / 2.0.pow(exp)).roundToLong()

    /** GPS URA metres -> 4-bit index (IS-GPS-200). */
    private fun gpsUraIndex(meters: Double): Long {
        val edges = doubleArrayOf(2.4, 3.4, 4.85, 6.85, 9.65, 13.65, 24.0, 48.0, 96.0, 192.0, 384.0, 768.0, 1536.0, 3072.0, 6144.0)
        for (i in edges.indices) if (meters <= edges[i]) return i.toLong()
        return 15
    }

    // Week-number conventions in the RTCM messages, derived from the epoch's full GPS week
    // (verified against a real watch packet): GPS = week % 1024, Galileo = week - 1024 (GST),
    // BeiDou = week - 1356 (BDT).
    private fun fullGpsWeek(e: RinexNav.Eph): Long =
        GnssTime.gpsWeek(e.year, e.month, e.day, e.hour, e.minute, e.second)

    /** RINEX GPS value indices (clock a0,a1,a2 then 7 orbit lines x4). */
    private object G {
        const val af0 = 0; const val af1 = 1; const val af2 = 2
        const val iode = 3; const val crs = 4; const val dn = 5; const val m0 = 6
        const val cuc = 7; const val ecc = 8; const val cus = 9; const val sqrta = 10
        const val toe = 11; const val cic = 12; const val omg0 = 13; const val cis = 14
        const val i0 = 15; const val crc = 16; const val omg = 17; const val omegadot = 18
        const val idot = 19; const val codesL2 = 20; const val week = 21; const val l2p = 22
        const val svacc = 23; const val health = 24; const val tgd = 25; const val iodc = 26
        const val fit = 28
    }

    fun gpsFields(e: RinexNav.Eph): Map<String, Long> {
        val v = e.values
        return linkedMapOf(
            "msg" to 1019L,
            "sat" to e.prn.toLong(),
            "week" to (fullGpsWeek(e) % 1024),
            "ura" to gpsUraIndex(v[G.svacc]),
            "codeL2" to v[G.codesL2].toLong(),
            "idot" to q(v[G.idot], -43, angular = true),
            "iode" to v[G.iode].toLong(),
            "toc" to (e.epochGpsSeconds() / 16),
            "af2" to q(v[G.af2], -55),
            "af1" to q(v[G.af1], -43),
            "af0" to q(v[G.af0], -31),
            "iodc" to v[G.iodc].toLong(),
            "crs" to q(v[G.crs], -5),
            "dn" to q(v[G.dn], -43, angular = true),
            "m0" to q(v[G.m0], -31, angular = true),
            "cuc" to q(v[G.cuc], -29),
            "ecc" to q(v[G.ecc], -33),
            "cus" to q(v[G.cus], -29),
            "sqrta" to q(v[G.sqrta], -19),
            "toe" to (v[G.toe] / 16.0).roundToLong(),
            "cic" to q(v[G.cic], -29),
            "omg0" to q(v[G.omg0], -31, angular = true),
            "cis" to q(v[G.cis], -29),
            "i0" to q(v[G.i0], -31, angular = true),
            "crc" to q(v[G.crc], -5),
            "omg" to q(v[G.omg], -31, angular = true),
            "omegadot" to q(v[G.omegadot], -43, angular = true),
            "tgd" to q(v[G.tgd], -31),
            "health" to v[G.health].toLong(),
            "l2p" to v[G.l2p].toLong(),
            "fit" to if (v[G.fit] > 4.0) 1L else 0L,
        )
    }

    /** RINEX Galileo value indices (same Keplerian layout as GPS, different orbit5/6 contents). */
    private object E {
        const val af0 = 0; const val af1 = 1; const val af2 = 2
        const val iodnav = 3; const val crs = 4; const val dn = 5; const val m0 = 6
        const val cuc = 7; const val ecc = 8; const val cus = 9; const val sqrta = 10
        const val toe = 11; const val cic = 12; const val omg0 = 13; const val cis = 14
        const val i0 = 15; const val crc = 16; const val omg = 17; const val omegadot = 18
        const val idot = 19; const val sisa = 23; const val health = 24
        const val bgdE5aE1 = 25; const val bgdE5bE1 = 26
    }

    private fun galileoSisaIndex(meters: Double): Long {
        if (meters < 0) return 255 // NAPA / no accuracy prediction available
        val cm = (meters * 100.0).roundToLong()
        return cm.coerceIn(0, 254)
    }

    fun galileoFields(e: RinexNav.Eph): Map<String, Long> {
        val v = e.values
        val h = v[E.health].toLong()
        return linkedMapOf(
            "msg" to 1046L,
            "sat" to e.prn.toLong(),
            "week" to (fullGpsWeek(e) - 1024),
            "iodnav" to v[E.iodnav].toLong(),
            "sisa" to galileoSisaIndex(v[E.sisa]),
            "idot" to q(v[E.idot], -43, angular = true),
            "toc" to (e.epochGpsSeconds() / 60),
            "af2" to q(v[E.af2], -59),
            "af1" to q(v[E.af1], -46),
            "af0" to q(v[E.af0], -34),
            "crs" to q(v[E.crs], -5),
            "dn" to q(v[E.dn], -43, angular = true),
            "m0" to q(v[E.m0], -31, angular = true),
            "cuc" to q(v[E.cuc], -29),
            "ecc" to q(v[E.ecc], -33),
            "cus" to q(v[E.cus], -29),
            "sqrta" to q(v[E.sqrta], -19),
            "toe" to (v[E.toe] / 60.0).roundToLong(),
            "cic" to q(v[E.cic], -29),
            "omg0" to q(v[E.omg0], -31, angular = true),
            "cis" to q(v[E.cis], -29),
            "i0" to q(v[E.i0], -31, angular = true),
            "crc" to q(v[E.crc], -5),
            "omg" to q(v[E.omg], -31, angular = true),
            "omegadot" to q(v[E.omegadot], -43, angular = true),
            "bgdE5aE1" to q(v[E.bgdE5aE1], -32),
            "bgdE5bE1" to q(v[E.bgdE5bE1], -32),
            "e5bHealth" to ((h shr 7) and 3),
            "e5bDVS" to ((h shr 6) and 1),
            "e1bHealth" to ((h shr 1) and 3),
            "e1bDVS" to (h and 1),
            "resv" to 0L,
        )
    }

    /** RINEX BeiDou value indices. */
    private object C {
        const val af0 = 0; const val af1 = 1; const val af2 = 2
        const val aode = 3; const val crs = 4; const val dn = 5; const val m0 = 6
        const val cuc = 7; const val ecc = 8; const val cus = 9; const val sqrta = 10
        const val toe = 11; const val cic = 12; const val omg0 = 13; const val cis = 14
        const val i0 = 15; const val crc = 16; const val omg = 17; const val omegadot = 18
        const val idot = 19; const val svacc = 23; const val health = 24
        const val tgd1 = 25; const val tgd2 = 26; const val aodc = 28
    }

    fun beidouFields(e: RinexNav.Eph): Map<String, Long> {
        val v = e.values
        return linkedMapOf(
            "msg" to 1042L,
            "sat" to e.prn.toLong(),
            "week" to (fullGpsWeek(e) - 1356),
            "urai" to gpsUraIndex(v[C.svacc]),
            "idot" to q(v[C.idot], -43, angular = true),
            "aode" to v[C.aode].toLong(),
            "toc" to (e.epochGpsSeconds() / 8),
            "a2" to q(v[C.af2], -66),
            "a1" to q(v[C.af1], -50),
            "a0" to q(v[C.af0], -33),
            "aodc" to v[C.aodc].toLong(),
            "crs" to q(v[C.crs], -6),
            "dn" to q(v[C.dn], -43, angular = true),
            "m0" to q(v[C.m0], -31, angular = true),
            "cuc" to q(v[C.cuc], -31),
            "ecc" to q(v[C.ecc], -33),
            "cus" to q(v[C.cus], -31),
            "sqrta" to q(v[C.sqrta], -19),
            "toe" to (v[C.toe] / 8.0).roundToLong(),
            "cic" to q(v[C.cic], -31),
            "omg0" to q(v[C.omg0], -31, angular = true),
            "cis" to q(v[C.cis], -31),
            "i0" to q(v[C.i0], -31, angular = true),
            "crc" to q(v[C.crc], -6),
            "omg" to q(v[C.omg], -31, angular = true),
            "omegadot" to q(v[C.omegadot], -43, angular = true),
            "tgd1" to (v[C.tgd1] / 1e-10).roundToLong(),
            "tgd2" to (v[C.tgd2] / 1e-10).roundToLong(),
            "health" to v[C.health].toLong(),
            "resv" to 0L,
        )
    }

    /** RINEX GLONASS value indices (3 clock terms, then 3 orbit lines of position/velocity/accel). */
    private object R {
        const val negTauN = 0; const val gammaN = 1; const val tk = 2
        const val x = 3; const val xv = 4; const val xa = 5; const val bn = 6
        const val y = 7; const val yv = 8; const val ya = 9; const val freq = 10
        const val z = 11; const val zv = 12; const val za = 13; const val age = 14
    }

    fun glonassFields(e: RinexNav.Eph): Map<String, Long> {
        val v = e.values
        // tb: ephemeris reference time index (15-min units) in GLONASS time = UTC + 3h.
        val secOfDayUtc = e.hour * 3600L + e.minute * 60L + e.second
        val tb = ((secOfDayUtc + 3 * 3600L) % 86400L) / 900L
        // NT: day number within the current GLONASS 4-year interval (started 1996 + k*4).
        val n4 = (e.year - 1996) / 4 + 1
        val intervalStartYear = 1996 + (n4 - 1) * 4
        val nt = GnssTime.daysFromCivil(e.year, e.month, e.day) -
            GnssTime.daysFromCivil(intervalStartYear, 1, 1) + 1
        val bn = v[R.bn].toLong()
        return linkedMapOf(
            "msg" to 1020L,
            "sat" to e.prn.toLong(),
            "freq" to (v[R.freq].toLong() + 7),
            "almH" to 0L, "almHav" to 0L, "P1" to 0L,
            "tk" to 0L, // frame reception time; not used for position (HH also sends 0)
            "Bn_msb" to ((bn shr 2) and 1),
            "P2" to 0L,
            "tb" to tb,
            "xnd" to q(v[R.xv], -20), "xn" to q(v[R.x], -11), "xndd" to q(v[R.xa], -30),
            "ynd" to q(v[R.yv], -20), "yn" to q(v[R.y], -11), "yndd" to q(v[R.ya], -30),
            "znd" to q(v[R.zv], -20), "zn" to q(v[R.z], -11), "zndd" to q(v[R.za], -30),
            "P3" to 0L,
            "gn" to q(v[R.gammaN], -40),
            "P" to 0L, "ln3" to 0L,
            "taun" to q(-v[R.negTauN], -30), // RINEX stores -TauN; broadcast tau_n = -(stored)
            "dtaun" to 0L,
            "En" to v[R.age].toLong(),
            "P4" to 0L, "FT" to 0L,
            "NT" to nt,
            "M" to 1L, // GLONASS-M
            "addAvail" to 0L, "NA" to 0L, "tauc" to 0L, "N4" to 0L, "tauGPS" to 0L, "ln5" to 0L,
            "resv" to 0L,
        )
    }

    /** Build the concatenated RTCM3 stream for the given ephemerides (GPS, GLONASS, Galileo, BeiDou). */
    fun buildRtcm(ephs: List<RinexNav.Eph>): ByteArray {
        val out = ArrayList<Byte>()
        for (e in RinexNav.latestPerSat(ephs)) {
            val fields = when (e.system) {
                'G' -> gpsFields(e)
                'R' -> glonassFields(e)
                'E' -> galileoFields(e)
                'C' -> beidouFields(e)
                else -> null
            } ?: continue
            val table = AgnssRtcm.tableByMsg[fields["msg"]!!.toInt()]!!
            val frame = AgnssRtcm.frame(AgnssRtcm.encodeMessage(table, fields))
            for (b in frame) out.add(b)
        }
        return out.toByteArray()
    }
}
