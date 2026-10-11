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
import com.qtekfun.ultimategadget.mapsdl.agnss.AgnssDownloader

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
}
