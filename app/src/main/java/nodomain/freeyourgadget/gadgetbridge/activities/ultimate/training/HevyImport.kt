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

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Matches exercise titles from other apps to catalog exercises by normalised name (EN and ES). */
class ExerciseMatcher(exercises: List<Exercise>) {
    private val exact = HashMap<String, Exercise>()
    private val tokens = HashMap<String, Exercise>()
    private val coreTokens = HashMap<String, Exercise>()

    init {
        for (e in exercises) for (name in e.names.values) {
            exact.putIfAbsent(normalise(name), e)
            tokens.putIfAbsent(tokenKey(name, keepParens = true), e)
            coreTokens.putIfAbsent(tokenKey(name, keepParens = false), e)
        }
    }

    fun match(title: String): Exercise? =
        exact[normalise(title)] ?: tokens[tokenKey(title, true)] ?: coreTokens[tokenKey(title, false)]

    companion object {
        private val PARENS = Regex("""\([^)]*\)|\[[^]]*]""")
        private val MARKS = Regex("\\p{M}+")

        /** Lowercase, no accents or punctuation, single spaces, parenthesised text kept as words. */
        fun normalise(name: String): String = words(name, keepParens = true).joinToString(" ")

        private fun words(name: String, keepParens: Boolean): List<String> {
            val base = if (keepParens) name else name.replace(PARENS, " ")
            val plain = Normalizer.normalize(base, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)
            return plain.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        }

        /** Order-independent key: "Bench Press (Barbell)" and "Barbell bench press" agree. */
        private fun tokenKey(name: String, keepParens: Boolean): String =
            words(name, keepParens).map { singular(it) }.sorted().joinToString(" ")

        private fun singular(w: String): String = when {
            w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
            w.length > 4 && w.endsWith("sses") -> w.dropLast(2)
            w.length >= 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") -> w.dropLast(1)
            else -> w
        }
    }
}

/** An exercise title of the file that matched nothing in the catalog. */
data class HevyUnmatched(val title: String, val measure: Measure, val sets: Int)

data class HevyPlan(
    val matched: Map<String, Exercise>,
    val unmatched: List<HevyUnmatched>,
    val workouts: Int,
    val totalSets: Int,
    val firstMs: Long,
    val lastMs: Long,
)

data class HevyBuilt(val newExercises: List<Exercise>, val sessions: List<Session>)

data class HevyApplied(val sessionsAdded: Int, val sessionsSkipped: Int, val exercisesCreated: Int)

object HevyImport {
    fun plan(parsed: HevyParsed, catalog: List<Exercise>): HevyPlan {
        val matcher = ExerciseMatcher(catalog)
        val matched = LinkedHashMap<String, Exercise>()
        val byTitle = LinkedHashMap<String, MutableList<HevySet>>()
        for (w in parsed.workouts) for (e in w.exercises) byTitle.getOrPut(e.title) { ArrayList() }.addAll(e.sets)
        val unmatched = ArrayList<HevyUnmatched>()
        for ((title, sets) in byTitle) {
            val hit = matcher.match(title)
            if (hit != null) matched[title] = hit else unmatched.add(HevyUnmatched(title, inferMeasure(sets), sets.size))
        }
        return HevyPlan(
            matched, unmatched.sortedBy { it.title.lowercase(Locale.ROOT) }, parsed.workouts.size, parsed.totalSets,
            parsed.workouts.minOfOrNull { it.startMs } ?: 0L, parsed.workouts.maxOfOrNull { it.startMs } ?: 0L,
        )
    }

    fun inferMeasure(sets: List<HevySet>): Measure {
        val weight = sets.any { (it.weight ?: 0.0) > 0 }
        val reps = sets.any { (it.reps ?: 0) > 0 }
        val dist = sets.any { (it.distance ?: 0.0) > 0 }
        val time = sets.any { (it.durationS ?: 0) > 0 }
        return when {
            dist && time -> Measure.DISTANCE_TIME
            dist -> Measure.DISTANCE
            reps && weight -> Measure.REPS_WEIGHT
            reps -> Measure.REPS
            time -> Measure.TIME
            weight -> Measure.REPS_WEIGHT
            else -> Measure.REPS_WEIGHT
        }
    }

    /** Stable id: importing the same workout twice yields the same session id. */
    fun sessionId(title: String, startMs: Long): String = "hevy:" + sha256("$title\n$startMs").take(32)

    fun customExerciseId(title: String): String = "custom:hevy:" + sha256(ExerciseMatcher.normalise(title)).take(24)

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /**
     * Turns the parsed file into sessions. Unmatched titles in [createTitles] become custom
     * exercises; the other unmatched ones are skipped (workouts left empty are dropped).
     */
    fun build(
        parsed: HevyParsed,
        plan: HevyPlan,
        createTitles: Set<String>,
        weightUnit: WeightUnit = parsed.weightUnit,
        distanceUnit: DistanceUnit = parsed.distanceUnit,
    ): HevyBuilt {
        val lb = weightUnit == WeightUnit.LB
        val miles = distanceUnit == DistanceUnit.MILES
        val created = LinkedHashMap<String, Exercise>()
        for (u in plan.unmatched) if (u.title in createTitles) {
            created[u.title] = Exercise(customExerciseId(u.title), mapOf("en" to u.title), u.measure, custom = true)
        }
        fun resolve(title: String): Exercise? = plan.matched[title] ?: created[title]

        val sessions = parsed.workouts.mapNotNull { w ->
            val done = if (w.endMs > 0) w.endMs else w.startMs
            val exercises = w.exercises.mapNotNull { e ->
                val ex = resolve(e.title) ?: return@mapNotNull null
                SessionExercise(
                    exerciseId = ex.id, exerciseNames = ex.names, measure = ex.measure, supersetGroup = e.supersetId,
                    notes = e.notes,
                    sets = e.sets.map { s ->
                        SessionSet(
                            kind = s.kind, reps = s.reps,
                            weightKg = s.weight?.takeIf { it > 0 }?.let { if (lb) TrainingMath.lbToKg(it) else it },
                            durationS = s.durationS?.takeIf { it > 0 },
                            distanceM = s.distance?.takeIf { it > 0 }?.let { if (miles) TrainingMath.milesToMetres(it) else it * 1000.0 },
                            rir = s.rpe?.let { (10 - it).toInt().coerceIn(0, 10) },
                            done = true, doneAtMs = done,
                        )
                    },
                )
            }
            if (exercises.isEmpty()) null else Session(
                id = sessionId(w.title, w.startMs), title = w.title, startMs = w.startMs, endMs = w.endMs,
                status = SessionStatus.DONE, notes = w.description, exercises = exercises,
            )
        }
        val usedIds = sessions.flatMap { s -> s.exercises.map { it.exerciseId } }.toSet()
        return HevyBuilt(created.values.filter { it.id in usedIds }, sessions)
    }

    /**
     * Writes the result into [store] in one transaction. Sessions and exercises whose id already
     * exists are left alone, so importing the same file again adds nothing.
     */
    fun apply(store: TrainingStore, built: HevyBuilt, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): HevyApplied {
        val db = store.writableDatabase
        val known = store.customExercises().map { it.id }.toHashSet()
        var created = 0; var added = 0; var skipped = 0
        val total = built.newExercises.size + built.sessions.size
        db.beginTransaction()
        try {
            for ((i, e) in built.newExercises.withIndex()) {
                if (known.add(e.id)) { store.saveExercise(e); created++ }
                onProgress(i + 1, total)
            }
            for ((i, s) in built.sessions.withIndex()) {
                if (store.session(s.id) == null) { store.saveSession(s); added++ } else skipped++
                onProgress(built.newExercises.size + i + 1, total)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return HevyApplied(added, skipped, created)
    }
}
