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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dataio

import android.content.Context
import org.json.JSONObject
import org.slf4j.LoggerFactory
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.GBDatabaseManager
import nodomain.freeyourgadget.gadgetbridge.entities.DaoMaster
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full, portable UltimateGadget backup: it bundles everything needed to bring the app up on a new
 * phone in the same state, then restores it. The bundle is a ZIP (built in memory with
 * [java.util.zip], no extra libraries) which is afterwards encrypted by [UltimateBackupCrypto].
 *
 * What goes in the ZIP:
 * ```
 *   manifest.json             app version/flavor, DB schema version, date, package, entry counts
 *   db/Gadgetbridge           the whole SQLite DB (workouts incl. Huawei summary/sections/samples/
 *                             pace, SpO2, activity/sleep/HR, the DEVICE table, user, …)
 *   shared_prefs/<name>.xml   EVERY SharedPreferences file: the default <pkg>_preferences.xml,
 *                             the per-device devicesettings_<ADDRESS>.xml, widgetsettings_*,
 *                             and any coordinator/capability prefs the app keeps
 *   files/routes/<name>.gpx   saved routes (filesDir/routes)
 * ```
 * Maps (the `.pmtiles` tiles under filesDir/maps, and filesDir/mapcore) are deliberately excluded:
 * large and re-downloadable.
 *
 * What CANNOT be backed up: the Bluetooth bonding keys live in the Android OS, not in the app, so
 * on a new phone the watch must be re-paired/re-connected. Workouts, settings and the paired-device
 * row are all restored, so everything else comes back.
 */
object UltimateBackup {
    private val LOG = LoggerFactory.getLogger(UltimateBackup::class.java)

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_DB = "db/Gadgetbridge"
    private const val PREFIX_PREFS = "shared_prefs/"
    private const val PREFIX_ROUTES = "files/routes/"

    const val FILE_EXTENSION = "ugbak"

    /** Upper bound for a decompressed single entry, to defend against zip bombs on import. */
    private const val MAX_ENTRY_BYTES = 512L * 1024 * 1024

    data class ImportResult(val prefsFiles: Int, val routeFiles: Int)

    // ---------------------------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds the full backup ZIP in memory, encrypts it with [password] and writes it to [out].
     * [out] is owned by the caller.
     */
    fun exportEncrypted(context: Context, out: OutputStream, password: CharArray) {
        val zipBytes = buildZip(context)
        UltimateBackupCrypto.encrypt(zipBytes, password, out)
    }

    private fun buildZip(context: Context): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // db/Gadgetbridge – stream the official DB export straight into the entry.
            zos.putNextEntry(ZipEntry(ENTRY_DB))
            GBDatabaseManager.exportDB(NonClosingOutputStream(zos))
            zos.closeEntry()

            // shared_prefs/*.xml – copy every preferences file verbatim.
            var prefCount = 0
            sharedPrefsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".xml") }
                ?.sortedBy { it.name }
                ?.forEach { xml ->
                    zos.putNextEntry(ZipEntry(PREFIX_PREFS + xml.name))
                    xml.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    prefCount++
                }

            // files/routes/*.gpx – saved routes.
            var routeCount = 0
            File(context.filesDir, "routes").listFiles { f -> f.isFile && f.name.endsWith(".gpx") }
                ?.sortedBy { it.name }
                ?.forEach { gpx ->
                    zos.putNextEntry(ZipEntry(PREFIX_ROUTES + gpx.name))
                    gpx.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    routeCount++
                }

            // manifest.json – written last (we now know the counts).
            val manifest = JSONObject()
                .put("format", "ultimategadget-backup")
                .put("formatVersion", 1)
                .put("appVersionName", BuildConfig.VERSION_NAME)
                .put("appVersionCode", BuildConfig.VERSION_CODE)
                .put("flavor", BuildConfig.FLAVOR)
                .put("packageName", context.packageName)
                .put("dbSchemaVersion", DaoMaster.SCHEMA_VERSION)
                .put("createdAt", System.currentTimeMillis())
                .put("prefsFiles", prefCount)
                .put("routeFiles", routeCount)
            zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zos.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    // ---------------------------------------------------------------------------------------------
    // Import / restore
    // ---------------------------------------------------------------------------------------------

    /**
     * Decrypts the [input] backup with [password], validates it and restores DB + SharedPreferences
     * + routes. The DB goes through the core importer ([GBDatabaseManager.importDB]) which validates
     * integrity; prefs .xml files are written back to shared_prefs. The app must be restarted
     * afterwards (see the activity) for the in-memory state to pick up the restored data.
     *
     * @throws UltimateBackupCrypto.WrongPasswordException wrong password / corrupted file
     * @throws IOException not a valid UltimateGadget backup, or a restore error
     */
    fun importEncrypted(context: Context, input: InputStream, password: CharArray): ImportResult {
        val zipBytes = UltimateBackupCrypto.decrypt(input, password)

        // Read every entry into memory first; the DB import reopens/validates the DB and we must not
        // leave the install half-restored because of a bad entry discovered mid-way.
        var dbBytes: ByteArray? = null
        val prefs = LinkedHashMap<String, ByteArray>()
        val routes = LinkedHashMap<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    when {
                        name == ENTRY_DB -> dbBytes = readEntry(zis)
                        name == ENTRY_MANIFEST -> readEntry(zis) // parsed for info only; skip
                        name.startsWith(PREFIX_PREFS) -> {
                            val leaf = safeLeaf(name.removePrefix(PREFIX_PREFS), ".xml")
                            if (leaf != null) prefs[leaf] = readEntry(zis)
                        }
                        name.startsWith(PREFIX_ROUTES) -> {
                            val leaf = safeLeaf(name.removePrefix(PREFIX_ROUTES), ".gpx")
                            if (leaf != null) routes[leaf] = readEntry(zis)
                        }
                        else -> LOG.warn("Ignoring unexpected backup entry: {}", name)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val db = dbBytes ?: throw IOException("La copia no contiene base de datos (db/Gadgetbridge)")

        // 1) Database – core importer: writes the file, reopens and checks integrity.
        GBDatabaseManager.importDB(ByteArrayInputStream(db))

        // 2) SharedPreferences – write the .xml back into shared_prefs.
        val prefsDir = sharedPrefsDir(context).apply { mkdirs() }
        for ((leaf, bytes) in prefs) {
            File(prefsDir, leaf).outputStream().use { it.write(bytes) }
        }

        // 3) Routes.
        if (routes.isNotEmpty()) {
            val routesDir = File(context.filesDir, "routes").apply { mkdirs() }
            for ((leaf, bytes) in routes) {
                File(routesDir, leaf).outputStream().use { it.write(bytes) }
            }
        }

        return ImportResult(prefsFiles = prefs.size, routeFiles = routes.size)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private fun sharedPrefsDir(context: Context): File =
        File(context.applicationInfo.dataDir, "shared_prefs")

    /**
     * Returns a safe single-segment file name with the expected [requiredSuffix], or null to skip
     * it. Guards against path traversal (`..`, slashes) so an import can only ever write inside the
     * target directory.
     */
    private fun safeLeaf(raw: String, requiredSuffix: String): String? {
        if (raw.isEmpty() || raw.contains('/') || raw.contains('\\') ||
            raw.contains("..") || !raw.endsWith(requiredSuffix)
        ) {
            LOG.warn("Rejecting unsafe backup entry name: {}", raw)
            return null
        }
        return raw
    }

    private fun readEntry(zis: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = zis.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IOException("Entrada de copia demasiado grande")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Wraps an OutputStream so that close() (called by the core DB exporter) does not close the ZIP. */
    private class NonClosingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() { /* ignore – the ZipOutputStream must stay open */ flush() }
    }
}
