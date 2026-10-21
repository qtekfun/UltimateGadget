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

import java.util.Locale
import java.util.UUID

/** What an exercise measures; decides which inputs a set shows. */
enum class Measure(val hasReps: Boolean, val hasWeight: Boolean, val hasTime: Boolean, val hasDistance: Boolean) {
    REPS_WEIGHT(true, true, false, false),
    REPS(true, false, false, false),
    TIME(false, false, true, false),
    DISTANCE(false, false, false, true),
    DISTANCE_TIME(false, false, true, true),
}

enum class SetKind { NORMAL, WARMUP, DROP, FAILURE }

/** An exercise of the catalog (built-in starter list, resource pack or created by the user). */
data class Exercise(
    val id: String,
    /** Names by language code ("en", "es"); [displayName] picks the best one. */
    val names: Map<String, String>,
    val measure: Measure = Measure.REPS_WEIGHT,
    val muscle: String = "",
    val equipment: String = "",
    val custom: Boolean = false,
    val notes: String = "",
    /** Descriptions by language code; only pack exercises have them (custom ones use [notes]). */
    val descriptions: Map<String, String> = emptyMap(),
    val musclesSecondary: String = "",
    /** Set for exercises that come from an installed resource pack: attribution and media. */
    val pack: PackInfo? = null,
    /** Pack category in English ("Legs", "Back"...); empty for built-in and custom exercises. */
    val category: String = "",
    /** Pack format v2: a one or two sentence summary by language code. */
    val summaries: Map<String, String> = emptyMap(),
    /** Pack format v2: the numbered steps by language code. */
    val steps: Map<String, List<String>> = emptyMap(),
    /** Pack format v2: form cues and common mistakes by language code. */
    val tips: Map<String, List<String>> = emptyMap(),
) {
    fun description(locale: Locale = Locale.getDefault()): String =
        descriptions[locale.language]?.takeIf { it.isNotBlank() } ?: descriptions["en"]?.takeIf { it.isNotBlank() }
            ?: descriptions.values.firstOrNull { it.isNotBlank() } ?: notes

    /** The description with hard-wrapped lines joined (schema 1 packs wrap their text). */
    fun cleanDescription(locale: Locale = Locale.getDefault()): String = TrainingText.clean(description(locale))

    fun summary(locale: Locale = Locale.getDefault()): String =
        pickByLanguage(summaries, locale) { it.isNotBlank() } ?: ""

    fun steps(locale: Locale = Locale.getDefault()): List<String> =
        pickByLanguage(steps, locale) { it.isNotEmpty() } ?: emptyList()

    fun tips(locale: Locale = Locale.getDefault()): List<String> =
        pickByLanguage(tips, locale) { it.isNotEmpty() } ?: emptyList()

    fun displayName(locale: Locale = Locale.getDefault()): String =
        names[locale.language] ?: names["en"] ?: names.values.firstOrNull() ?: id
}

/** Same fallback as the names: the locale language, then English, then anything non-empty. */
internal fun <T> pickByLanguage(m: Map<String, T>, locale: Locale, usable: (T) -> Boolean): T? =
    m[locale.language]?.takeIf(usable) ?: m["en"]?.takeIf(usable) ?: m.values.firstOrNull(usable)

/**
 * One picture or animation of a pack exercise; [file] is relative to the pack folder. [animated] items are
 * looping animated WebP files; [generated] ones were drawn for the app (CC0, no authors) rather than taken from a photo.
 */
data class PackMedia(
    val file: String, val main: Boolean, val ai: Boolean, val license: String, val authors: List<String>,
    val animated: Boolean = false, val generated: Boolean = false,
)

/** Where a pack exercise comes from: which pack, who wrote it, under which licence, its pictures. */
data class PackInfo(
    val packId: String,
    val license: String,
    val authors: List<String>,
    /** Language codes whose name/description are an unreviewed draft translation. */
    val draftTranslations: List<String>,
    val media: List<PackMedia>,
)

/** One planned set of a routine. Weight is always kilograms, distance metres, time seconds. */
data class PlannedSet(
    val kind: SetKind = SetKind.NORMAL,
    val reps: Int? = null,
    val repsMax: Int? = null,
    val weightKg: Double? = null,
    val durationS: Int? = null,
    val distanceM: Double? = null,
    val rir: Int? = null,
)

data class RoutineExercise(
    val exerciseId: String,
    val restSeconds: Int = 90,
    /** Exercises sharing a non-null group are a superset. */
    val supersetGroup: Int? = null,
    val sets: List<PlannedSet> = listOf(PlannedSet()),
    val notes: String = "",
)

data class Routine(
    val id: String = newId(),
    val name: String,
    val notes: String = "",
    val exercises: List<RoutineExercise> = emptyList(),
    val updatedMs: Long = 0,
)

/** A set as actually performed (or about to be) in a session. */
data class SessionSet(
    val kind: SetKind = SetKind.NORMAL,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationS: Int? = null,
    val distanceM: Double? = null,
    val rir: Int? = null,
    val done: Boolean = false,
    val doneAtMs: Long = 0,
)

data class SessionExercise(
    val exerciseId: String,
    /** Snapshot so a session stays readable if the catalog entry later disappears. */
    val exerciseNames: Map<String, String>,
    val measure: Measure,
    val restSeconds: Int = 90,
    val supersetGroup: Int? = null,
    val sets: List<SessionSet> = emptyList(),
    val notes: String = "",
) {
    fun displayName(locale: Locale = Locale.getDefault()): String =
        exerciseNames[locale.language] ?: exerciseNames["en"] ?: exerciseNames.values.firstOrNull() ?: exerciseId
}

enum class SessionStatus { ACTIVE, DONE }

data class Session(
    val id: String = newId(),
    val title: String,
    val routineId: String? = null,
    val startMs: Long,
    val endMs: Long = 0,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val notes: String = "",
    val exercises: List<SessionExercise> = emptyList(),
    /** Epoch millis when the running rest timer ends, 0 when none. */
    val restEndsAtMs: Long = 0,
) {
    val durationMs: Long get() = if (endMs > startMs) endMs - startMs else 0
}

fun newId(): String = UUID.randomUUID().toString()
