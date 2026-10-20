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

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Minimal RFC 4180 style CSV reader: quotes, escaped quotes, newlines inside quotes, CRLF, BOM. */
object CsvParser {
    fun parse(input: String, delimiter: Char = ','): List<List<String>> {
        val text = input.removePrefix("﻿")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var i = 0
        fun endField() { row.add(field.toString()); field.setLength(0); fieldStarted = false }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row) // skip blank lines
            row = ArrayList()
        }
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when {
                c == '"' && !fieldStarted -> { inQuotes = true; fieldStarted = true }
                c == delimiter -> endField()
                c == '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> { field.append(c); fieldStarted = true }
            }
            i++
        }
        if (field.isNotEmpty() || fieldStarted || row.isNotEmpty()) endRow()
        return rows
    }

    /** Picks `;` over `,` when the header line only splits that way (some spreadsheet exports). */
    fun detectDelimiter(text: String): Char {
        val first = text.removePrefix("﻿").lineSequence().firstOrNull().orEmpty()
        return if (first.count { it == ';' } > first.count { it == ',' }) ';' else ','
    }
}

enum class WeightUnit { KG, LB, UNKNOWN }
enum class DistanceUnit { KM, MILES, UNKNOWN }

/** A set as written in the file; weight and distance are still in the file's unit. */
data class HevySet(
    val kind: SetKind,
    val weight: Double?,
    val reps: Int?,
    val distance: Double?,
    val durationS: Int?,
    val rpe: Double?,
)

data class HevyExercise(val title: String, val supersetId: Int?, val notes: String, val sets: List<HevySet>)

data class HevyWorkout(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val description: String,
    val exercises: List<HevyExercise>,
)

data class HevyParsed(
    val workouts: List<HevyWorkout>,
    val weightUnit: WeightUnit,
    val distanceUnit: DistanceUnit,
    /** Data rows that were ignored (no exercise or an unreadable start time). */
    val skippedRows: Int,
) {
    val totalSets: Int get() = workouts.sumOf { w -> w.exercises.sumOf { it.sets.size } }
}

/** Reads a Hevy "export workout data" CSV into [HevyParsed]. */
object HevyCsv {
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val ENGLISH = Regex("""^(\d{1,2})\s+([A-Za-z]{3,9})\.?,?\s+(\d{4}),?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([AaPp][Mm])?$""")
    private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*(Z|[+-]\d{2}:?\d{2})?$""")

    /** Parses a date from the file; local time unless the text carries an offset. Null when unreadable. */
    fun parseDate(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        ENGLISH.matchEntire(s)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[2].lowercase(Locale.ROOT).take(3)) + 1
            if (month == 0) return null
            var hour = m.groupValues[4].toInt()
            when (m.groupValues[7].lowercase(Locale.ROOT)) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            return runCatching {
                LocalDateTime.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt(), hour, m.groupValues[5].toInt(),
                    m.groupValues[6].ifEmpty { "0" }.toInt()).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }
        ISO.matchEntire(s)?.let { m ->
            return runCatching {
                val local = LocalDateTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                    m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].ifEmpty { "0" }.toInt())
                val off = m.groupValues[7]
                if (off.isEmpty()) local.atZone(zone).toInstant().toEpochMilli()
                else {
                    val o = if (off == "Z") ZoneOffset.UTC else ZoneOffset.of(if (off.contains(':')) off else off.substring(0, 3) + ":" + off.substring(3))
                    OffsetDateTime.of(local, o).toInstant().toEpochMilli()
                }
            }.getOrNull()
        }
        return null
    }

    private fun kindOf(raw: String): SetKind {
        val t = raw.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")
        return when {
            t.startsWith("warm") -> SetKind.WARMUP
            t.startsWith("drop") -> SetKind.DROP
            t.startsWith("fail") -> SetKind.FAILURE
            else -> SetKind.NORMAL
        }
    }

    private fun number(raw: String?): Double? = raw?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private class Group(val title: String, val startMs: Long, val endMs: Long, val description: String) {
        class Ex(val title: String, val superset: Int?, var notes: String, val sets: MutableList<HevySet> = ArrayList())
        val exercises = ArrayList<Ex>()
    }

    /** @throws IllegalArgumentException when the text is not a Hevy export (no recognisable header). */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): HevyParsed {
        val rows = CsvParser.parse(text, CsvParser.detectDelimiter(text))
        require(rows.isNotEmpty()) { "empty file" }
        val header = rows[0].map { it.trim().lowercase(Locale.ROOT) }
        fun col(vararg names: String) = header.indexOfFirst { it in names }
        val cTitle = col("title"); val cStart = col("start_time"); val cEnd = col("end_time")
        val cDesc = col("description"); val cEx = col("exercise_title"); val cSuper = col("superset_id")
        val cNotes = col("exercise_notes"); val cType = col("set_type")
        val cWeight = header.indexOfFirst { it.startsWith("weight") }
        val cReps = col("reps"); val cDist = header.indexOfFirst { it.startsWith("distance") }
        val cDur = col("duration_seconds", "duration"); val cRpe = col("rpe")
        require(cTitle >= 0 && cStart >= 0 && cEx >= 0) { "not a Hevy workout export" }

        val weightUnit = when {
            cWeight < 0 -> WeightUnit.UNKNOWN
            header[cWeight].contains("lb") -> WeightUnit.LB
            header[cWeight].contains("kg") -> WeightUnit.KG
            else -> WeightUnit.UNKNOWN
        }
        val distanceUnit = when {
            cDist < 0 -> DistanceUnit.UNKNOWN
            header[cDist].contains("mi") -> DistanceUnit.MILES
            header[cDist].contains("km") -> DistanceUnit.KM
            else -> DistanceUnit.UNKNOWN
        }

        val groups = LinkedHashMap<String, Group>()
        var skipped = 0
        for (r in rows.drop(1)) {
            fun f(i: Int) = if (i in r.indices) r[i].trim() else ""
            val title = f(cTitle); val exTitle = f(cEx)
            val start = parseDate(f(cStart), zone)
            if (exTitle.isEmpty() || start == null) { skipped++; continue }
            val g = groups.getOrPut("$title\u0000$start") {
                Group(title.ifEmpty { exTitle }, start, parseDate(f(cEnd), zone)?.takeIf { it > start } ?: 0L, f(cDesc))
            }
            val superset = f(cSuper).takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: (it.hashCode() and 0x7fffffff) }
            val ex = g.exercises.lastOrNull()?.takeIf { it.title == exTitle }
                ?: Group.Ex(exTitle, superset, "").also { g.exercises.add(it) }
            if (ex.notes.isEmpty()) ex.notes = f(cNotes)
            ex.sets.add(HevySet(
                kind = kindOf(f(cType)), weight = number(f(cWeight)), reps = number(f(cReps))?.toInt(),
                distance = number(f(cDist)), durationS = number(f(cDur))?.toInt(), rpe = number(f(cRpe)),
            ))
        }
        val workouts = groups.values.map { g ->
            HevyWorkout(g.title, g.startMs, g.endMs, g.description,
                g.exercises.map { HevyExercise(it.title, it.superset, it.notes, it.sets) })
        }
        return HevyParsed(workouts, weightUnit, distanceUnit, skipped)
    }
}
