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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.route

import android.content.Context
import java.io.File

/**
 * Local registry of planned/sent GPX routes, kept in filesDir/routes. Sending a route to the watch is
 * one-way (the Huawei fitness upload has no "list/delete on watch" API), so this is the phone-side list
 * the user manages: open again in the planner, resend, or delete the local copy. Deleting here does not
 * remove the route already stored on the watch — that is done from the watch itself.
 *
 * File naming: "<epochMillis>__<sanitized name>.gpx" so the list shows a name and date with no sidecar.
 */
data class SavedRoute(val file: File, val name: String, val epochMillis: Long, val sizeBytes: Long)

object PhoneRouteStore {
    private const val DIR = "routes"

    fun routesDir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Writes the GPX under a timestamped, name-encoded file and returns it. */
    fun save(context: Context, name: String, gpx: String): File {
        val safe = sanitize(name).ifBlank { "ruta" }
        val file = File(routesDir(context), "${System.currentTimeMillis()}__$safe.gpx")
        file.writeText(gpx)
        return file
    }

    fun list(context: Context): List<SavedRoute> =
        routesDir(context).listFiles { f -> f.isFile && f.name.endsWith(".gpx") }
            ?.map { f ->
                val base = f.name.removeSuffix(".gpx")
                val sep = base.indexOf("__")
                val epoch = base.substringBefore("__").toLongOrNull() ?: f.lastModified()
                val name = if (sep >= 0) base.substring(sep + 2) else base
                SavedRoute(f, name, epoch, f.length())
            }
            ?.sortedByDescending { it.epochMillis }
            ?: emptyList()

    fun delete(file: File): Boolean = runCatching { file.isFile && file.delete() }.getOrDefault(false)

    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "").replace(' ', '_').take(60)
}
