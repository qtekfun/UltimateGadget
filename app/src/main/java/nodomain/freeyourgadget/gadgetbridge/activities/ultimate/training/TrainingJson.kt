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

/**
 * The app's own, versioned JSON format for exercises, routines and sessions. It is the backup and
 * exchange format: every list is optional on import and unknown fields are ignored.
 */
object TrainingJson {
    const val SCHEMA = 1
    const val FORMAT = "ultimategadget-training"

    // ---- encode ------------------------------------------------------------------------------

    private fun JSONObject.putOpt2(key: String, v: Any?): JSONObject { if (v != null) put(key, v); return this }

    fun encode(e: Exercise) = JSONObject()
        .put("id", e.id).put("names", JSONObject(e.names as Map<*, *>))
        .put("measure", e.measure.name).put("muscle", e.muscle).put("equipment", e.equipment)
        .put("notes", e.notes)

    fun encode(s: PlannedSet) = JSONObject().put("kind", s.kind.name)
        .putOpt2("reps", s.reps).putOpt2("repsMax", s.repsMax).putOpt2("weightKg", s.weightKg)
        .putOpt2("durationS", s.durationS).putOpt2("distanceM", s.distanceM).putOpt2("rir", s.rir)

    fun encode(r: Routine) = JSONObject().put("id", r.id).put("name", r.name).put("notes", r.notes)
        .put("updatedMs", r.updatedMs)
        .put("exercises", JSONArray().also { arr ->
            r.exercises.forEach { e ->
                arr.put(JSONObject().put("exerciseId", e.exerciseId).put("restSeconds", e.restSeconds)
                    .putOpt2("supersetGroup", e.supersetGroup).put("notes", e.notes)
                    .put("sets", JSONArray().also { a -> e.sets.forEach { a.put(encode(it)) } }))
            }
        })

    fun encode(s: SessionSet) = JSONObject().put("kind", s.kind.name).put("done", s.done).put("doneAtMs", s.doneAtMs)
        .putOpt2("reps", s.reps).putOpt2("weightKg", s.weightKg).putOpt2("durationS", s.durationS)
        .putOpt2("distanceM", s.distanceM).putOpt2("rir", s.rir)

    fun encode(s: Session) = JSONObject().put("id", s.id).put("title", s.title)
        .putOpt2("routineId", s.routineId).put("startMs", s.startMs).put("endMs", s.endMs)
        .put("status", s.status.name).put("notes", s.notes).put("restEndsAtMs", s.restEndsAtMs)
        .put("exercises", JSONArray().also { arr ->
            s.exercises.forEach { e ->
                arr.put(JSONObject().put("exerciseId", e.exerciseId).put("names", JSONObject(e.exerciseNames as Map<*, *>))
                    .put("measure", e.measure.name).put("restSeconds", e.restSeconds)
                    .putOpt2("supersetGroup", e.supersetGroup).put("notes", e.notes)
                    .put("sets", JSONArray().also { a -> e.sets.forEach { a.put(encode(it)) } }))
            }
        })

    // ---- decode ------------------------------------------------------------------------------

    private fun JSONObject.intOrNull(k: String) = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.doubleOrNull(k: String) = if (has(k) && !isNull(k)) getDouble(k) else null
    private inline fun <reified T : Enum<T>> JSONObject.enumOr(k: String, d: T): T =
        runCatching { enumValueOf<T>(getString(k)) }.getOrDefault(d)

    private fun names(o: JSONObject?): Map<String, String> {
        val out = linkedMapOf<String, String>()
        o?.keys()?.forEach { k -> out[k] = o.getString(k) }
        return out
    }

    private fun <T> JSONArray?.map(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

    fun decodeExercise(o: JSONObject) = Exercise(
        id = o.getString("id"), names = names(o.optJSONObject("names")),
        measure = o.enumOr("measure", Measure.REPS_WEIGHT), muscle = o.optString("muscle"),
        equipment = o.optString("equipment"), custom = true, notes = o.optString("notes"),
    )

    fun decodePlannedSet(o: JSONObject) = PlannedSet(
        kind = o.enumOr("kind", SetKind.NORMAL), reps = o.intOrNull("reps"), repsMax = o.intOrNull("repsMax"),
        weightKg = o.doubleOrNull("weightKg"), durationS = o.intOrNull("durationS"),
        distanceM = o.doubleOrNull("distanceM"), rir = o.intOrNull("rir"),
    )

    fun decodeRoutine(o: JSONObject) = Routine(
        id = o.getString("id"), name = o.getString("name"), notes = o.optString("notes"),
        updatedMs = o.optLong("updatedMs"),
        exercises = o.optJSONArray("exercises").map { e ->
            RoutineExercise(
                exerciseId = e.getString("exerciseId"), restSeconds = e.optInt("restSeconds", 90),
                supersetGroup = e.intOrNull("supersetGroup"), notes = e.optString("notes"),
                sets = e.optJSONArray("sets").map { decodePlannedSet(it) },
            )
        },
    )

    fun decodeSessionSet(o: JSONObject) = SessionSet(
        kind = o.enumOr("kind", SetKind.NORMAL), reps = o.intOrNull("reps"), weightKg = o.doubleOrNull("weightKg"),
        durationS = o.intOrNull("durationS"), distanceM = o.doubleOrNull("distanceM"), rir = o.intOrNull("rir"),
        done = o.optBoolean("done"), doneAtMs = o.optLong("doneAtMs"),
    )

    fun decodeSession(o: JSONObject) = Session(
        id = o.getString("id"), title = o.getString("title"),
        routineId = if (o.isNull("routineId")) null else o.optString("routineId", "").ifEmpty { null },
        startMs = o.getLong("startMs"), endMs = o.optLong("endMs"),
        status = o.enumOr("status", SessionStatus.DONE), notes = o.optString("notes"),
        restEndsAtMs = o.optLong("restEndsAtMs"),
        exercises = o.optJSONArray("exercises").map { e ->
            SessionExercise(
                exerciseId = e.getString("exerciseId"), exerciseNames = names(e.optJSONObject("names")),
                measure = e.enumOr("measure", Measure.REPS_WEIGHT), restSeconds = e.optInt("restSeconds", 90),
                supersetGroup = e.intOrNull("supersetGroup"), notes = e.optString("notes"),
                sets = e.optJSONArray("sets").map { decodeSessionSet(it) },
            )
        },
    )

    // ---- whole document ----------------------------------------------------------------------

    data class Document(
        val exercises: List<Exercise>,
        val routines: List<Routine>,
        val sessions: List<Session>,
    )

    fun export(doc: Document): String = JSONObject()
        .put("format", FORMAT).put("schema", SCHEMA)
        .put("exercises", JSONArray().also { a -> doc.exercises.filter { it.custom }.forEach { a.put(encode(it)) } })
        .put("routines", JSONArray().also { a -> doc.routines.forEach { a.put(encode(it)) } })
        .put("sessions", JSONArray().also { a -> doc.sessions.forEach { a.put(encode(it)) } })
        .toString(1)

    /** Parses a document; throws [IllegalArgumentException] if it is not ours or is from a newer schema. */
    fun import(text: String): Document {
        val o = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("not JSON", e) }
        require(o.optString("format") == FORMAT) { "not an UltimateGadget training file" }
        require(o.optInt("schema", 0) in 1..SCHEMA) { "unsupported schema ${o.optInt("schema")}" }
        return Document(
            exercises = o.optJSONArray("exercises").map { decodeExercise(it) },
            routines = o.optJSONArray("routines").map { decodeRoutine(it) },
            sessions = o.optJSONArray("sessions").map { decodeSession(it) },
        )
    }
}
