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

/** Flavors without INTERNET: downloads go through the companion app, nothing here. */
object InAppNetworkImpl : InAppNetwork {
    override val available: Boolean = false

    override fun fetchAgnss(context: Context, wifiOnly: Boolean): AgnssPayload =
        throw UnsupportedOperationException("This build has no network access")

    override fun fetchMapCatalog(): List<RemoteMap> =
        throw UnsupportedOperationException("This build has no network access")

    override fun downloadMap(context: Context, id: String, onProgress: (done: Long, total: Long) -> Unit): String =
        throw UnsupportedOperationException("This build has no network access")
}
