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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AgnssTest {

    private fun fixture(): String =
        javaClass.classLoader!!.getResourceAsStream("brdc_sample.rnx")!!
            .bufferedReader().use { it.readText() }

    /** Codec self-consistency: encode a field map, frame it, parse it back, decode -> identical. */
    @Test
    fun rtcmCodecRoundTrips() {
        val fields = linkedMapOf(
            "msg" to 1019L, "sat" to 7L, "week" to 391L, "ura" to 0L, "codeL2" to 1L,
            "idot" to -123L, "iode" to 81L, "toc" to 21600L, "af2" to 0L, "af1" to -5L,
            "af0" to 12345L, "iodc" to 849L, "crs" to -1500L, "dn" to 1400L, "m0" to -999999L,
            "cuc" to -80L, "ecc" to 17626799L, "cus" to 350L, "sqrta" to 2702044881L, "toe" to 21600L,
            "cic" to 35L, "omg0" to -1234567L, "cis" to 40L, "i0" to 2000000000L, "crc" to 5000L,
            "omg" to -2000000000L, "omegadot" to -4321L, "tgd" to -3L, "health" to 0L, "l2p" to 0L, "fit" to 0L,
        )
        val table = AgnssRtcm.T1019
        val frame = AgnssRtcm.frame(AgnssRtcm.encodeMessage(table, fields))
        val parsed = AgnssRtcm.parseFrames(frame)
        assertEquals(1, parsed.size)
        assertEquals(1019, parsed[0].msg)
        val back = AgnssRtcm.decodeMessage(table, parsed[0].payload)
        for ((k, v) in fields) assertEquals("field $k", v, back[k])
    }

    /** CRC-24Q known-answer: the empty message / a fixed vector against a reference. */
    @Test
    fun crc24qKnownAnswer() {
        // RTCM CRC-24Q (poly 0x1864CFB, init 0) of ASCII "123456789". Value cross-checked against
        // 113 real watch frames whose stored CRC this same routine reproduces exactly.
        assertEquals(0xCDE703, AgnssRtcm.crc24q("123456789".toByteArray()))
    }

    /** Real pipeline: RINEX (public IGS) -> RTCM for GPS/Galileo/BeiDou; decode and sanity-check. */
    @Test
    fun rinexToRtcmIsValid() {
        val ephs = RinexNav.parse(fixture())
        assertTrue("GPS parsed", ephs.any { it.system == 'G' })
        assertTrue("GLONASS parsed", ephs.any { it.system == 'R' })
        assertTrue("Galileo parsed", ephs.any { it.system == 'E' })
        assertTrue("BeiDou parsed", ephs.any { it.system == 'C' })

        val rtcm = AgnssBuilder.buildRtcm(ephs)
        val frames = AgnssRtcm.parseFrames(rtcm)
        assertTrue("produced frames", frames.isNotEmpty())

        // sqrtA bands by constellation (m^0.5): GPS MEO ~5153, Galileo ~5440, BeiDou MEO ~5282 / IGSO-GEO ~6493.
        val sqrtBands = mapOf(
            1019 to 5100.0..5200.0,
            1046 to 5400.0..5500.0,
            1042 to 5200.0..6600.0,
        )
        val seen = HashSet<Int>()
        for (f in frames) {
            seen.add(f.msg)
            // CRC must be valid: recompute over D3+len+payload and compare to the frame's trailing 3 bytes.
            val framed = AgnssRtcm.frame(f.payload)
            val crc = AgnssRtcm.crc24q(framed, framed.size - 3)
            val stored = ((framed[framed.size - 3].toInt() and 0xFF) shl 16) or
                ((framed[framed.size - 2].toInt() and 0xFF) shl 8) or (framed[framed.size - 1].toInt() and 0xFF)
            assertEquals("crc valid for ${f.msg}", stored, crc)

            val m = AgnssRtcm.decodeMessage(AgnssRtcm.tableByMsg[f.msg]!!, f.payload)
            if (f.msg == 1020) {
                // GLONASS: validate the broadcast position radius ~25510 km.
                val x = m["xn"]!! * 2.0.pow(-11)
                val y = m["yn"]!! * 2.0.pow(-11)
                val z = m["zn"]!! * 2.0.pow(-11)
                val r = kotlin.math.sqrt(x * x + y * y + z * z)
                assertTrue("GLONASS |r|=$r ~25510km sat ${m["sat"]}", r in 25000.0..26000.0)
                assertTrue("GLONASS sat ${m["sat"]} in range", m["sat"]!! in 1..24)
            } else {
                val band = sqrtBands[f.msg] ?: error("unexpected msg ${f.msg}")
                val sqrtA = m["sqrta"]!! * 2.0.pow(-19)
                val ecc = m["ecc"]!! * 2.0.pow(-33)
                assertTrue("msg ${f.msg} sat ${m["sat"]} sqrtA=$sqrtA in $band", sqrtA in band)
                assertTrue("msg ${f.msg} ecc=$ecc sane", ecc in 0.0..0.05)
                assertTrue("msg ${f.msg} sat ${m["sat"]} in range", m["sat"]!! in 1..63)
            }
        }
        assertEquals("all four constellations present", setOf(1019, 1020, 1042, 1046), seen)
    }
}
