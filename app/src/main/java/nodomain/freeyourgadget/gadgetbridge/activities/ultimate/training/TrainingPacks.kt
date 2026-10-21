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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** Where the exercise pack is published (a release asset of the data repository). Single place to change. */
object TrainingPackSource {
    const val URL = "https://github.com/qtekfun/ultimate-workouts-data/releases/latest/download/pack-wger.zip"
}

/** The pack's `manifest.json`. */
data class PackManifest(
    val schema: Int,
    val id: String,
    val version: String,
    val languages: List<String>,
    val exercises: Int,
    val withMedia: Int,
    val licenses: List<String>,
    val attribution: String,
)

class PackException(message: String) : IOException(message)

/**
 * Installs and reads exercise resource packs. A pack is a zip with `manifest.json`, `exercises.json`
 * and a `media` folder; it is extracted to `<root>/<pack id>/` so everything stays on disk and a newer
 * version of the same pack replaces the older one.
 */
object TrainingPacks {
    /** Newest pack format this app reads; schema 1 packs (no summary/steps/tips/animations) still load. */
    const val SCHEMA = 2
    private val SUPPORTED_SCHEMAS = 1..SCHEMA
    const val MAX_ENTRIES = 5000
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    const val MAX_JSON_BYTES = 16L * 1024 * 1024
    private val ID_RE = Regex("[a-z0-9][a-z0-9_-]{0,39}")
    private val MEDIA_RE = Regex("media/[A-Za-z0-9._-]{1,200}\\.(webp|png|jpg|jpeg)")

    fun rootDir(context: Context) = File(context.filesDir, "training/packs")

    // ---- install -----------------------------------------------------------------------------

    /** Reads the zip from [input], validates it and installs it under [root]. Throws [PackException] on any problem. */
    @Throws(IOException::class)
    fun install(root: File, input: InputStream): PackManifest {
        root.mkdirs()
        val tmp = File(root, ".incoming-" + UUID.randomUUID())
        try {
            if (!tmp.mkdirs()) throw PackException("Cannot create the staging folder")
            extract(input, tmp)
            val manifest = readManifest(File(tmp, "manifest.json"))
            validateExercises(File(tmp, "exercises.json"))
            val target = File(root, manifest.id)
            val old = File(root, ".old-" + UUID.randomUUID())
            if (target.exists() && !target.renameTo(old)) throw PackException("Cannot replace the installed pack")
            if (!tmp.renameTo(target)) {
                if (old.exists()) old.renameTo(target)
                throw PackException("Cannot install the pack")
            }
            old.deleteRecursively()
            return manifest
        } finally {
            if (tmp.exists()) tmp.deleteRecursively()
        }
    }

    private fun extract(input: InputStream, dest: File) {
        val canonicalDest = dest.canonicalFile
        var entries = 0
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw PackException("Too many entries")
                val name = entry.name
                if (name.startsWith("/") || name.contains("\\") || name.split('/').any { it == ".." }) {
                    throw PackException("Unsafe path in the pack: rejected")
                }
                if (entry.isDirectory) continue
                val isJson = name == "manifest.json" || name == "exercises.json"
                if (!isJson && !MEDIA_RE.matches(name)) continue // unknown files are ignored, never written
                val out = File(dest, name).canonicalFile
                if (!out.path.startsWith(canonicalDest.path + File.separator)) throw PackException("Unsafe path in the pack: rejected")
                out.parentFile?.mkdirs()
                val limit = if (isJson) MAX_JSON_BYTES else MAX_ENTRY_BYTES
                var size = 0L
                out.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        size += n; total += n
                        if (size > limit || total > MAX_TOTAL_BYTES) throw PackException("The pack is too large")
                        o.write(buf, 0, n)
                    }
                }
            }
        }
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    private fun readManifest(f: File): PackManifest {
        if (!f.isFile) throw PackException("manifest.json is missing")
        val o = try { JSONObject(f.readText()) } catch (e: Exception) { throw PackException("manifest.json is not valid") }
        val schema = o.optInt("schema", -1)
        if (schema !in SUPPORTED_SCHEMAS) throw PackException("Unsupported pack schema")
        val id = o.optString("id")
        if (!ID_RE.matches(id)) throw PackException("Invalid pack id")
        return PackManifest(
            schema, id, o.optString("version"), strings(o.optJSONArray("languages")),
            o.optInt("exercises"), o.optInt("withMedia"), strings(o.optJSONArray("licenses")), o.optString("attribution"),
        )
    }

    private fun validateExercises(f: File) {
        if (!f.isFile) throw PackException("exercises.json is missing")
        try {
            val o = JSONObject(f.readText())
            if (o.optInt("schema", -1) !in SUPPORTED_SCHEMAS) throw PackException("Unsupported exercises schema")
            val arr = o.getJSONArray("exercises")
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                if (e.getString("id").isBlank() || e.getJSONObject("name").length() == 0) throw PackException("Invalid exercise entry")
            }
        } catch (e: PackException) { throw e } catch (e: Exception) { throw PackException("exercises.json is not valid") }
    }

    // ---- read --------------------------------------------------------------------------------

    /** A pack on disk: its manifest and its exercises, already mapped to the catalog model. */
    class Installed(val manifest: PackManifest, val dir: File, val exercises: List<Exercise>) {
        fun mediaFile(rel: String): File? = File(dir, rel).takeIf { MEDIA_RE.matches(rel) && it.isFile }
    }

    /** All installed packs under [root]; broken ones are skipped. */
    fun load(root: File): List<Installed> =
        (root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()).sortedBy { it.name }
            .mapNotNull { runCatching { loadOne(it) }.getOrNull() }

    fun loadOne(dir: File): Installed {
        val manifest = readManifest(File(dir, "manifest.json"))
        val arr = JSONObject(File(dir, "exercises.json").readText()).getJSONArray("exercises")
        val list = ArrayList<Exercise>(arr.length())
        for (i in 0 until arr.length()) list += mapExercise(manifest.id, arr.getJSONObject(i))
        return Installed(manifest, dir, list)
    }

    private fun langMap(o: JSONObject?): Map<String, String> {
        val out = linkedMapOf<String, String>()
        o?.keys()?.forEach { k -> o.optString(k).takeIf { it.isNotBlank() }?.let { out[k] = it } }
        return out
    }

    private fun langLists(o: JSONObject?): Map<String, List<String>> {
        val out = linkedMapOf<String, List<String>>()
        o?.keys()?.forEach { k -> strings(o.optJSONArray(k)).map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { out[k] = it } }
        return out
    }

    private fun mapExercise(packId: String, o: JSONObject): Exercise {
        val equipment = strings(o.optJSONArray("equipment")).map { it.lowercase() }
            .map { if (it.startsWith("none")) "bodyweight" else it }.distinct()
        val measure = when {
            o.optString("category").equals("Cardio", true) -> Measure.TIME
            equipment.all { it == "bodyweight" || it == "gym mat" || it == "pull-up bar" } -> Measure.REPS
            else -> Measure.REPS_WEIGHT
        }
        val media = (o.optJSONArray("media") ?: JSONArray()).let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map {
                PackMedia(it.optString("file"), it.optBoolean("main"), it.optBoolean("ai"), it.optString("license"), strings(it.optJSONArray("authors")),
                    it.optBoolean("animated"), it.optBoolean("generated"))
            }
        }.sortedByDescending { it.main }
        return Exercise(
            id = o.getString("id"), names = langMap(o.optJSONObject("name")), measure = measure,
            muscle = strings(o.optJSONArray("muscles")).joinToString(", ") { it.lowercase() },
            equipment = equipment.joinToString(", "), custom = false,
            descriptions = langMap(o.optJSONObject("description")),
            musclesSecondary = strings(o.optJSONArray("musclesSecondary")).joinToString(", ") { it.lowercase() },
            category = o.optString("category"),
            summaries = langMap(o.optJSONObject("summary")),
            steps = langLists(o.optJSONObject("steps")),
            tips = langLists(o.optJSONObject("tips")),
            pack = PackInfo(packId, o.optString("license"), strings(o.optJSONArray("authors")), strings(o.optJSONArray("draftTranslations")), media),
        )
    }

    // ---- process-wide cache ------------------------------------------------------------------

    @Volatile private var cache: List<Installed>? = null

    /** Pack metadata is read from disk once and kept in memory; images are never loaded here. */
    fun installed(context: Context): List<Installed> =
        cache ?: synchronized(this) { cache ?: load(rootDir(context)).also { cache = it } }

    fun invalidate() { cache = null }

    fun exercises(context: Context): List<Exercise> = installed(context).flatMap { it.exercises }

    fun mediaFile(context: Context, packId: String, rel: String): File? =
        installed(context).firstOrNull { it.manifest.id == packId }?.mediaFile(rel)

    /** Installs [input] into the app's pack folder and refreshes the in-memory metadata. */
    fun install(context: Context, input: InputStream): PackManifest =
        install(rootDir(context), input).also { invalidate() }
}
