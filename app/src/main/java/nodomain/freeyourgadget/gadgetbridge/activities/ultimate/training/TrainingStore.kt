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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Own SQLite database for the workouts feature, kept apart from the Gadgetbridge greenDAO schema so
 * the core stays untouched. Every entity is stored as its JSON (see [TrainingJson]) with the few
 * columns needed to order and filter; sessions are written after every edit so a session in
 * progress survives the process being killed.
 */
class TrainingStore(context: Context, name: String? = DB_NAME) : SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE exercise (id TEXT PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE routine (id TEXT PRIMARY KEY, name TEXT NOT NULL, updated INTEGER NOT NULL, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE session (id TEXT PRIMARY KEY, start INTEGER NOT NULL, status TEXT NOT NULL, json TEXT NOT NULL)")
        db.execSQL("CREATE INDEX session_start ON session(start)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 so far; later versions migrate here without dropping user data.
    }

    // ---- exercises (custom ones only; the built-in list lives in code) -----------------------

    fun customExercises(): List<Exercise> = readableDatabase.rawQuery("SELECT json FROM exercise", null).use { c ->
        buildList { while (c.moveToNext()) add(TrainingJson.decodeExercise(JSONObject(c.getString(0)))) }
    }.sortedBy { it.displayName().lowercase() }

    fun saveExercise(e: Exercise) = put("exercise", e.id, ContentValues().apply {
        put("json", TrainingJson.encode(e).toString())
    })

    fun deleteExercise(id: String) { writableDatabase.delete("exercise", "id=?", arrayOf(id)) }

    // ---- routines ----------------------------------------------------------------------------

    fun routines(): List<Routine> = readableDatabase.rawQuery("SELECT json FROM routine ORDER BY updated DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(TrainingJson.decodeRoutine(JSONObject(c.getString(0)))) }
    }

    fun routine(id: String): Routine? = readableDatabase.rawQuery("SELECT json FROM routine WHERE id=?", arrayOf(id)).use { c ->
        if (c.moveToFirst()) TrainingJson.decodeRoutine(JSONObject(c.getString(0))) else null
    }

    fun saveRoutine(r: Routine, nowMs: Long = System.currentTimeMillis()) {
        val stamped = r.copy(updatedMs = nowMs)
        put("routine", r.id, ContentValues().apply {
            put("name", r.name); put("updated", nowMs); put("json", TrainingJson.encode(stamped).toString())
        })
    }

    fun deleteRoutine(id: String) { writableDatabase.delete("routine", "id=?", arrayOf(id)) }

    // ---- sessions ----------------------------------------------------------------------------

    fun saveSession(s: Session) = put("session", s.id, ContentValues().apply {
        put("start", s.startMs); put("status", s.status.name); put("json", TrainingJson.encode(s).toString())
    })

    fun session(id: String): Session? = readableDatabase.rawQuery("SELECT json FROM session WHERE id=?", arrayOf(id)).use { c ->
        if (c.moveToFirst()) TrainingJson.decodeSession(JSONObject(c.getString(0))) else null
    }

    /** The session in progress, if any (there is at most one). */
    fun activeSession(): Session? = readableDatabase.rawQuery(
        "SELECT json FROM session WHERE status='ACTIVE' ORDER BY start DESC LIMIT 1", null,
    ).use { c -> if (c.moveToFirst()) TrainingJson.decodeSession(JSONObject(c.getString(0))) else null }

    /** Finished sessions, newest first. */
    fun finishedSessions(limit: Int = Int.MAX_VALUE): List<Session> = readableDatabase.rawQuery(
        "SELECT json FROM session WHERE status='DONE' ORDER BY start DESC LIMIT $limit", null,
    ).use { c -> buildList { while (c.moveToNext()) add(TrainingJson.decodeSession(JSONObject(c.getString(0)))) } }

    fun deleteSession(id: String) { writableDatabase.delete("session", "id=?", arrayOf(id)) }

    // ---- backup / exchange -------------------------------------------------------------------

    fun exportDocument() = TrainingJson.Document(customExercises(), routines(), finishedSessions() + listOfNotNull(activeSession()))

    /**
     * Imports a parsed document. With [replace] everything is wiped first; otherwise entries whose id
     * already exists are kept and the incoming ones are skipped. Returns how many were added.
     */
    fun importDocument(doc: TrainingJson.Document, replace: Boolean): ImportResult {
        val db = writableDatabase
        var added = 0
        var skipped = 0
        db.beginTransaction()
        try {
            if (replace) { db.delete("exercise", null, null); db.delete("routine", null, null); db.delete("session", null, null) }
            fun exists(table: String, id: String) = db.rawQuery("SELECT 1 FROM $table WHERE id=?", arrayOf(id)).use { it.moveToFirst() }
            for (e in doc.exercises) if (exists("exercise", e.id)) skipped++ else { saveExercise(e); added++ }
            for (r in doc.routines) if (exists("routine", r.id)) skipped++ else { saveRoutine(r, r.updatedMs.takeIf { it > 0 } ?: System.currentTimeMillis()); added++ }
            for (s in doc.sessions) if (exists("session", s.id)) skipped++ else { saveSession(s); added++ }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportResult(added, skipped)
    }

    data class ImportResult(val added: Int, val skipped: Int)

    private fun put(table: String, id: String, values: ContentValues) {
        values.put("id", id)
        writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        const val DB_NAME = "ultimate_training.db"
        private const val VERSION = 1

        @Volatile private var instance: TrainingStore? = null
        /** Drops the cached instance so a test (or a restore) reopens the database. */
        fun reset() { synchronized(this) { instance?.close(); instance = null } }

        fun get(context: Context): TrainingStore =
            instance ?: synchronized(this) { instance ?: TrainingStore(context).also { instance = it } }
    }
}
