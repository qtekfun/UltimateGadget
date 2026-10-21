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

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds tiny exercise packs in memory; no third-party data or media is committed. */
object PackFixture {
    fun manifest(id: String = "testpack", version: String = "1", schema: Int = 1) = JSONObject()
        .put("schema", schema).put("id", id).put("version", version)
        .put("languages", JSONArray(listOf("en", "es"))).put("exercises", 3).put("withMedia", 2)
        .put("licenses", JSONArray(listOf("CC0-1.0"))).put("attribution", "Test attribution text").toString()

    private fun ex(id: String, en: String, es: String?, media: List<JSONObject> = emptyList(), draft: List<String> = emptyList()) = JSONObject()
        .put("id", id)
        .put("name", JSONObject().put("en", en).also { if (es != null) it.put("es", es) })
        .put("description", JSONObject().put("en", "About $en").also { if (es != null) it.put("es", "Sobre $es") })
        .put("category", "Legs").put("muscles", JSONArray(listOf("Quads", "Glutes"))).put("musclesSecondary", JSONArray(listOf("Calves")))
        .put("equipment", JSONArray(listOf("Barbell"))).put("draftTranslations", JSONArray(draft))
        .put("license", "CC-BY-SA-4").put("authors", JSONArray(listOf("Ann", "Bob")))
        .put("media", JSONArray(media))

    fun media(file: String, main: Boolean = true, ai: Boolean = false) = JSONObject()
        .put("file", file).put("main", main).put("ai", ai).put("license", "CC-BY-SA-4.0").put("authors", JSONArray(listOf("Cy")))

    fun exercises(): String = JSONObject().put("schema", 1).put("source", "test").put("exercises", JSONArray(listOf(
        ex("uuid-squat", "Goblet Squat", "Sentadilla goblet", listOf(media("media/uuid-squat_1.png"), media("media/uuid-squat_2.png", main = false, ai = true)), draft = listOf("es")),
        ex("uuid-lunge", "Lunge", "Zancada", listOf(media("media/uuid-lunge_1.png"))),
        ex("uuid-plank", "Plank", null),
    ))).toString()

    private fun lang(en: Any, es: Any?) = JSONObject().put("en", en).also { if (es != null) it.put("es", es) }

    fun animMedia(file: String, generated: Boolean = true) = JSONObject()
        .put("file", file).put("main", true).put("ai", false).put("animated", true).put("generated", generated)
        .put("license", "CC0-1.0").put("authors", JSONArray())

    /** Pack format 2: summary, steps, tips and animated media next to schema 1 style entries. */
    fun exercisesV2(): String = JSONObject().put("schema", 2).put("source", "test").put("exercises", JSONArray(listOf(
        ex("uuid-squat", "Goblet Squat", "Sentadilla goblet", listOf(animMedia("media/uuid-squat.anim.webp"), media("media/uuid-squat_1.png", main = false)))
            .put("summary", lang("A squat holding a weight at the chest.", "Una sentadilla con peso al pecho."))
            .put("steps", lang(JSONArray(listOf("Hold the weight.", "Sit down.", "Stand up.")), JSONArray(listOf("Sujeta el peso.", "Baja.", "Sube."))))
            .put("tips", lang(JSONArray(listOf("Keep the chest up.")), JSONArray(listOf("Pecho arriba."))))
            .put("category", "Legs"),
        ex("uuid-lunge", "Lunge", "Zancada", listOf(animMedia("media/uuid-lunge.anim.webp")))
            .put("steps", JSONObject().put("en", JSONArray(listOf("Step forward.", "  ", "Return.")))),
        ex("uuid-plank", "Plank", null, listOf(animMedia("media/uuid-plank.anim.webp")))
            .put("description", JSONObject().put("en", "Line one\nwrapped line\n\nSecond paragraph.")),
    ))).toString()

    fun zipV2(): ByteArray = zip(
        manifest = manifest(schema = 2), exercises = exercisesV2(),
        media = mapOf(
            "media/uuid-squat.anim.webp" to byteArrayOf(1), "media/uuid-squat_1.png" to byteArrayOf(2),
            "media/uuid-lunge.anim.webp" to byteArrayOf(3), "media/uuid-plank.anim.webp" to byteArrayOf(4),
        ),
    )

    /** [extra] are additional raw entries (name to bytes), e.g. a zip-slip path. */
    fun zip(
        manifest: String? = manifest(), exercises: String? = exercises(),
        media: Map<String, ByteArray> = mapOf("media/uuid-squat_1.png" to byteArrayOf(1), "media/uuid-squat_2.png" to byteArrayOf(2), "media/uuid-lunge_1.png" to byteArrayOf(3)),
        extra: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(n: String, b: ByteArray) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
            manifest?.let { put("manifest.json", it.toByteArray()) }
            exercises?.let { put("exercises.json", it.toByteArray()) }
            media.forEach { (n, b) -> put(n, b) }
            extra.forEach { (n, b) -> put(n, b) }
        }
        return bos.toByteArray()
    }
}
