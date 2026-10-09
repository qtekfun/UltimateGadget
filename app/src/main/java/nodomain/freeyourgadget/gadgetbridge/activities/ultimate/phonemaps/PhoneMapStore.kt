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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.phonemaps

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Phone-side offline base maps for the MapLibre viewer: PMTiles files kept in filesDir/maps/
 * (the location the viewer reads). Pure file access, no network: the mainline build has no INTERNET
 * permission by design, so maps are IMPORTED (the user downloads a .pmtiles from the UltimateMaps-data
 * releases or UltimateMaps and picks it here) rather than fetched in-app.
 */
data class PhoneMap(val name: String, val sizeBytes: Long)

object PhoneMapStore {
    private const val DIR = "maps"

    fun mapsDir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun installed(context: Context): List<PhoneMap> =
        mapsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") }
            ?.sortedBy { it.name }
            ?.map { PhoneMap(it.name, it.length()) }
            ?: emptyList()

    /** Copies the picked document into filesDir/maps as a .pmtiles file. Returns the stored name. */
    fun importMap(context: Context, uri: Uri): Result<String> = runCatching {
        var name = queryDisplayName(context, uri) ?: "map-${System.currentTimeMillis()}.pmtiles"
        if (!name.endsWith(".pmtiles")) name = name.substringBeforeLast('.', name) + ".pmtiles"
        val dest = File(mapsDir(context), sanitize(name))
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open input stream" }
            dest.outputStream().use { out -> input.copyTo(out, 1 shl 16) }
        }
        dest.name
    }

    fun delete(context: Context, name: String): Boolean =
        File(mapsDir(context), sanitize(name)).takeIf { it.isFile }?.delete() ?: false

    private fun sanitize(name: String) = name.substringAfterLast('/').replace("\u0000", "")

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
