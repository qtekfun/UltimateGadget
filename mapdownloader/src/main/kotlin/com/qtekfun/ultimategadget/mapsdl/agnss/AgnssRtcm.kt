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
 * Minimal RTCM 3.3 codec for the GNSS ephemeris messages the Huawei watch's A-GNSS wants:
 * 1019 (GPS), 1020 (GLONASS), 1042 (BeiDou), 1046 (Galileo I/NAV). The watch asks only for
 * HW_AGNSS = a stream of these frames (verified on a GT Runner 2); no Huawei-proprietary format.
 *
 * The field tables below were validated byte-for-byte against a real HW_AGNSS packet captured from
 * the watch's own cache: decoding each frame to its fields and re-encoding reproduces the original
 * bytes exactly, and the decoded values (e.g. sqrtA) match the real orbital parameters.
 *
 * GLONASS (1020) uses sign-magnitude integers for several fields; [Field.signMag] marks them.
 */
object AgnssRtcm {

    /** A field in an RTCM message: name, bit width, and integer representation. */
    data class Field(
        val name: String,
        val bits: Int,
        val signed: Boolean = false,
        val signMag: Boolean = false,
    )

    private fun u(name: String, bits: Int) = Field(name, bits, signed = false)
    private fun s(name: String, bits: Int) = Field(name, bits, signed = true)
    private fun sm(name: String, bits: Int) = Field(name, bits, signMag = true)

    // --- Message field tables (order matters). Validated against real packets. ---

    val T1019: List<Field> = listOf(
        u("msg", 12), u("sat", 6), u("week", 10), u("ura", 4), u("codeL2", 2),
        s("idot", 14), u("iode", 8), u("toc", 16), s("af2", 8), s("af1", 16), s("af0", 22),
        u("iodc", 10), s("crs", 16), s("dn", 16), s("m0", 32), s("cuc", 16), u("ecc", 32),
        s("cus", 16), u("sqrta", 32), u("toe", 16), s("cic", 16), s("omg0", 32), s("cis", 16),
        s("i0", 32), s("crc", 16), s("omg", 32), s("omegadot", 24), s("tgd", 8), u("health", 6),
        u("l2p", 1), u("fit", 1),
    )

    val T1020: List<Field> = listOf(
        u("msg", 12), u("sat", 6), u("freq", 5), u("almH", 1), u("almHav", 1), u("P1", 2),
        u("tk", 12), u("Bn_msb", 1), u("P2", 1), u("tb", 7),
        sm("xnd", 24), sm("xn", 27), sm("xndd", 5),
        sm("ynd", 24), sm("yn", 27), sm("yndd", 5),
        sm("znd", 24), sm("zn", 27), sm("zndd", 5),
        u("P3", 1), sm("gn", 11), u("P", 2), u("ln3", 1), sm("taun", 22), sm("dtaun", 5),
        u("En", 5), u("P4", 1), u("FT", 4), u("NT", 11), u("M", 2), u("addAvail", 1),
        u("NA", 11), sm("tauc", 32), u("N4", 5), sm("tauGPS", 22), u("ln5", 1), u("resv", 7),
    )

    val T1042: List<Field> = listOf(
        u("msg", 12), u("sat", 6), u("week", 13), u("urai", 4), s("idot", 14), u("aode", 5),
        u("toc", 17), s("a2", 11), s("a1", 22), s("a0", 24), u("aodc", 5), s("crs", 18),
        s("dn", 16), s("m0", 32), s("cuc", 18), u("ecc", 32), s("cus", 18), u("sqrta", 32),
        u("toe", 17), s("cic", 18), s("omg0", 32), s("cis", 18), s("i0", 32), s("crc", 18),
        s("omg", 32), s("omegadot", 24), s("tgd1", 10), s("tgd2", 10), u("health", 1),
        u("resv", 1),
    )

    val T1046: List<Field> = listOf(
        u("msg", 12), u("sat", 6), u("week", 12), u("iodnav", 10), u("sisa", 8), s("idot", 14),
        u("toc", 14), s("af2", 6), s("af1", 21), s("af0", 31), s("crs", 16), s("dn", 16),
        s("m0", 32), s("cuc", 16), u("ecc", 32), s("cus", 16), u("sqrta", 32), u("toe", 14),
        s("cic", 16), s("omg0", 32), s("cis", 16), s("i0", 32), s("crc", 16), s("omg", 32),
        s("omegadot", 24), s("bgdE5aE1", 10), s("bgdE5bE1", 10), u("e5bHealth", 2), u("e5bDVS", 1),
        u("e1bHealth", 2), u("e1bDVS", 1), u("resv", 2),
    )

    val tableByMsg: Map<Int, List<Field>> = mapOf(
        1019 to T1019, 1020 to T1020, 1042 to T1042, 1046 to T1046,
    )

    // --- Bit I/O ---

    class BitWriter {
        private val bits = ArrayList<Int>()
        fun put(value: Long, n: Int) {
            for (i in n - 1 downTo 0) bits.add(((value shr i) and 1L).toInt())
        }
        fun putField(f: Field, value: Long) {
            val v = when {
                f.signMag && value < 0 -> (1L shl (f.bits - 1)) or (-value)
                f.signed && value < 0 -> value + (1L shl f.bits)
                else -> value
            }
            put(v, f.bits)
        }
        fun toBytes(): ByteArray {
            val out = ByteArray((bits.size + 7) / 8)
            for (i in bits.indices) if (bits[i] != 0) out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            return out
        }
        fun bitLength(): Int = bits.size
    }

    class BitReader(private val data: ByteArray) {
        private var pos = 0
        fun get(n: Int): Long {
            var v = 0L
            repeat(n) {
                val bit = (data[pos ushr 3].toInt() ushr (7 - (pos and 7))) and 1
                v = (v shl 1) or bit.toLong()
                pos++
            }
            return v
        }
        fun getField(f: Field): Long {
            val raw = get(f.bits)
            return when {
                f.signMag -> {
                    val mag = raw and ((1L shl (f.bits - 1)) - 1)
                    if (raw and (1L shl (f.bits - 1)) != 0L) -mag else mag
                }
                f.signed && (raw and (1L shl (f.bits - 1))) != 0L -> raw - (1L shl f.bits)
                else -> raw
            }
        }
    }

    // --- CRC-24Q (RTCM), polynomial 0x1864CFB ---

    fun crc24q(data: ByteArray, len: Int = data.size): Int {
        var crc = 0
        for (i in 0 until len) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 16)
            repeat(8) {
                crc = crc shl 1
                if (crc and 0x1000000 != 0) crc = crc xor 0x1864CFB
            }
        }
        return crc and 0xFFFFFF
    }

    /** Encode one message's fields (in table order) to its payload bytes. */
    fun encodeMessage(table: List<Field>, fields: Map<String, Long>): ByteArray {
        val bw = BitWriter()
        for (f in table) bw.putField(f, fields[f.name] ?: 0L)
        return bw.toBytes()
    }

    /** Decode a payload to its fields (used for validation). */
    fun decodeMessage(table: List<Field>, payload: ByteArray): Map<String, Long> {
        val br = BitReader(payload)
        val m = LinkedHashMap<String, Long>()
        for (f in table) m[f.name] = br.getField(f)
        return m
    }

    /** Wrap a message payload in an RTCM3 frame: 0xD3 + 10-bit length + payload + CRC24Q. */
    fun frame(payload: ByteArray): ByteArray {
        require(payload.size < 1024) { "RTCM payload too long: ${payload.size}" }
        val out = ByteArray(3 + payload.size + 3)
        out[0] = 0xD3.toByte()
        out[1] = ((payload.size shr 8) and 0x03).toByte()
        out[2] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 3, payload.size)
        val crc = crc24q(out, 3 + payload.size)
        out[3 + payload.size] = ((crc shr 16) and 0xFF).toByte()
        out[3 + payload.size + 1] = ((crc shr 8) and 0xFF).toByte()
        out[3 + payload.size + 2] = (crc and 0xFF).toByte()
        return out
    }

    /** Parsed frames of a concatenated RTCM3 stream (for validation). */
    data class Frame(val msg: Int, val payload: ByteArray)

    fun parseFrames(data: ByteArray): List<Frame> {
        val frames = ArrayList<Frame>()
        var i = 0
        while (i + 3 <= data.size) {
            if (data[i].toInt() and 0xFF != 0xD3) { i++; continue }
            val len = ((data[i + 1].toInt() and 0x03) shl 8) or (data[i + 2].toInt() and 0xFF)
            if (i + 3 + len + 3 > data.size) break
            val payload = data.copyOfRange(i + 3, i + 3 + len)
            val msg = ((payload[0].toInt() and 0xFF) shl 4) or ((payload[1].toInt() and 0xFF) ushr 4)
            frames.add(Frame(msg, payload))
            i += 3 + len + 3
        }
        return frames
    }
}
