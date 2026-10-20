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

import kotlin.math.roundToInt

/** Pure calculations used by the logger and the history; no Android types. */
object TrainingMath {
    private const val KG_PER_LB = 0.45359237
    private const val M_PER_KM = 1000.0
    private const val M_PER_MI = 1609.344

    fun kgToLb(kg: Double) = kg / KG_PER_LB
    fun lbToKg(lb: Double) = lb * KG_PER_LB
    fun metresToMiles(m: Double) = m / M_PER_MI
    fun milesToMetres(mi: Double) = mi * M_PER_MI
    fun metresToKm(m: Double) = m / M_PER_KM

    /** Warm-up sets do not count towards volume, records or the "last time" values. */
    fun counts(set: SessionSet) = set.done && set.kind != SetKind.WARMUP

    /** Sum of weight x reps over the sets that count (kg). */
    fun volumeKg(exercise: SessionExercise): Double =
        exercise.sets.filter { counts(it) }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

    fun volumeKg(session: Session): Double = session.exercises.sumOf { volumeKg(it) }

    fun doneSets(session: Session): Int = session.exercises.sumOf { e -> e.sets.count { counts(it) } }

    /** Epley estimate of the one-rep max; 0 when it cannot be computed. */
    fun estimatedOneRepMaxKg(set: SessionSet): Double {
        val w = set.weightKg ?: return 0.0
        val r = set.reps ?: return 0.0
        if (w <= 0.0 || r <= 0) return 0.0
        return if (r == 1) w else w * (1.0 + r / 30.0)
    }

    /** Records reached by [session] against everything in [history] (finished sessions, any order). */
    fun personalRecords(session: Session, history: List<Session>): List<RecordHit> {
        val hits = mutableListOf<RecordHit>()
        for (ex in session.exercises) {
            val before = history.filter { it.id != session.id && it.status == SessionStatus.DONE && it.startMs < session.startMs }
                .flatMap { s -> s.exercises.filter { it.exerciseId == ex.exerciseId } }
                .flatMap { it.sets.filter { st -> counts(st) } }
            val now = ex.sets.filter { counts(it) }
            if (now.isEmpty()) continue
            val bestWeight = now.maxOfOrNull { it.weightKg ?: 0.0 } ?: 0.0
            if (bestWeight > 0 && bestWeight > (before.maxOfOrNull { it.weightKg ?: 0.0 } ?: 0.0) && before.isNotEmpty())
                hits += RecordHit(ex.exerciseId, RecordKind.WEIGHT, bestWeight)
            val best1rm = now.maxOf { estimatedOneRepMaxKg(it) }
            if (best1rm > 0 && before.isNotEmpty() && best1rm > before.maxOf { estimatedOneRepMaxKg(it) })
                hits += RecordHit(ex.exerciseId, RecordKind.ONE_REP_MAX, best1rm)
            val bestVolume = now.maxOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
            if (bestVolume > 0 && before.isNotEmpty() && bestVolume > before.maxOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) })
                hits += RecordHit(ex.exerciseId, RecordKind.SET_VOLUME, bestVolume)
            val bestReps = now.maxOf { it.reps ?: 0 }
            if (bestReps > 0 && (ex.measure == Measure.REPS) && before.isNotEmpty() && bestReps > before.maxOf { it.reps ?: 0 })
                hits += RecordHit(ex.exerciseId, RecordKind.REPS, bestReps.toDouble())
        }
        return hits
    }

    /** The sets of [exerciseId] in the most recent finished session before [beforeMs] that had it. */
    fun lastTime(exerciseId: String, history: List<Session>, beforeMs: Long = Long.MAX_VALUE): List<SessionSet> =
        history.filter { it.status == SessionStatus.DONE && it.startMs < beforeMs }
            .sortedByDescending { it.startMs }
            .firstNotNullOfOrNull { s ->
                s.exercises.firstOrNull { it.exerciseId == exerciseId && it.sets.any { st -> counts(st) } }
                    ?.sets?.filter { counts(it) }
            } ?: emptyList()

    /** "80 x 8" style text for a past set. */
    fun describe(set: SessionSet, measure: Measure, weightText: (Double) -> String): String = when {
        measure.hasWeight && measure.hasReps -> "${weightText(set.weightKg ?: 0.0)} x ${set.reps ?: 0}"
        measure.hasReps -> "${set.reps ?: 0}"
        measure.hasDistance && measure.hasTime -> "${(set.distanceM ?: 0.0).roundToInt()} m / ${formatDuration(set.durationS ?: 0)}"
        measure.hasDistance -> "${(set.distanceM ?: 0.0).roundToInt()} m"
        else -> formatDuration(set.durationS ?: 0)
    }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

enum class RecordKind { WEIGHT, ONE_REP_MAX, SET_VOLUME, REPS }

data class RecordHit(val exerciseId: String, val kind: RecordKind, val value: Double)
