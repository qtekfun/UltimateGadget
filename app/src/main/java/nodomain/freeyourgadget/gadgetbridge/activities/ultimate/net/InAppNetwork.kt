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

/** One map of the online catalog that can be downloaded into the phone map store. */
class RemoteMap(val id: String, val name: String, val parent: String, val sizeBytes: Long)

class GolfCountry(val id: String, val code: String, val name: String)
class GolfCity(val id: String, val name: String, val province: String)
class GolfCourseInfo(val id: Long, val name: String, val totalLength: Int, val version: String)

/** A golf course map already downloaded to [file], named like the companion app names them. */
class GolfCourseDownload(val courseId: Int, val version: Int, val friendlyName: String, val file: java.io.File)

/** One Amazfit/Zepp device of the account with its paste-ready Bluetooth auth key. */
class HuamiKey(val mac: String, val active: Boolean, val authKey: String)

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

    /** Blocking. Fetches the online base-map catalog; throws on failure. */
    @Throws(Exception::class)
    fun fetchMapCatalog(): List<RemoteMap>

    /**
     * Blocking. Downloads catalog map [id] into the phone map store (same place a manual import
     * lands), reporting `(done, total)` bytes. Returns the stored file name; throws on failure.
     */
    @Throws(Exception::class)
    fun downloadMap(context: Context, id: String, onProgress: (done: Long, total: Long) -> Unit): String

    /** Blocking. Huawei golf catalog: countries. */
    @Throws(Exception::class)
    fun golfCountries(): List<GolfCountry>

    @Throws(Exception::class)
    fun golfCities(country: GolfCountry): List<GolfCity>

    @Throws(Exception::class)
    fun golfCourses(country: GolfCountry, city: GolfCity): List<GolfCourseInfo>

    /** Blocking. Fetches the course map .bin of [course] into the app cache, ready to send to the watch. */
    @Throws(Exception::class)
    fun downloadGolfCourse(context: Context, country: GolfCountry, course: GolfCourseInfo): GolfCourseDownload

    /** Region labels accepted by [fetchHuamiKeys], in order. Empty when unsupported. */
    val huamiRegions: List<String>

    /**
     * Blocking. Logs in to Huami/Zepp over HTTPS and returns the account's devices with auth keys.
     * The credentials are used for this call only and never stored or logged.
     */
    @Throws(Exception::class)
    fun fetchHuamiKeys(email: String, password: String, regionIndex: Int): List<HuamiKey>
}
