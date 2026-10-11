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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net

import android.content.Context

/** Ready-to-serve broadcast ephemeris, as produced by the A-GNSS downloader. */
class AgnssPayload(
    val rtcm: ByteArray,
    val validUntilMs: Long,
    val source: String,
    val gps: Int,
    val glonass: Int,
    val galileo: Int,
    val beidou: Int,
)

/**
 * Seam between the network-free core and the `play` flavor. Every flavor provides an object named
 * [InAppNetworkImpl] in this package: src/noplay (mainline, banglejs) is an inert stub, src/play
 * does the real downloads. Main code only ever talks to this contract.
 */
interface InAppNetwork {
    /** True when this build can reach the network itself (no companion app needed). */
    val available: Boolean

    /** Blocking. Downloads and assembles fresh A-GNSS data; throws on any failure. */
    @Throws(Exception::class)
    fun fetchAgnss(context: Context, wifiOnly: Boolean): AgnssPayload
}
