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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.qtekfun.ultimategadget.mapsdl.Downloader
import com.qtekfun.ultimategadget.mapsdl.MapRegion
import com.qtekfun.ultimategadget.mapsdl.agnss.AgnssDownloader
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.phonemaps.PhoneMapStore
import java.io.File

/** `play` flavor: runs the shared :mapsdl-core downloaders in-process. */
object InAppNetworkImpl : InAppNetwork {
    override val available: Boolean = true

    override fun fetchAgnss(context: Context, wifiOnly: Boolean): AgnssPayload {
        if (wifiOnly && !isOnWifi(context)) throw IllegalStateException("Sin Wi-Fi")
        val res = AgnssDownloader.generate()
        return AgnssPayload(
            rtcm = res.rtcm,
            validUntilMs = res.validUntilMs,
            source = res.source,
            gps = res.counts['G'] ?: 0,
            glonass = res.counts['R'] ?: 0,
            galileo = res.counts['E'] ?: 0,
            beidou = res.counts['C'] ?: 0,
        )
    }

    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @Volatile
    private var catalog: Map<String, MapRegion> = emptyMap()

    override fun fetchMapCatalog(): List<RemoteMap> {
        val regions = Downloader.fetchCatalog()
        catalog = regions.associateBy { it.id }
        return regions.map { RemoteMap(it.id, it.name, it.parent, it.size) }
    }

    override fun downloadMap(context: Context, id: String, onProgress: (done: Long, total: Long) -> Unit): String {
        val region = catalog[id] ?: throw IllegalStateException("Mapa no encontrado en el catálogo")
        // The file name comes from the remote catalog: keep only the last path segment.
        val name = region.file.substringAfterLast('/').replace("\u0000", "")
            .let { if (it.endsWith(".pmtiles")) it else "$it.pmtiles" }
        Downloader.downloadToFile(region, File(PhoneMapStore.mapsDir(context), name), onProgress)
        return name
    }
}
