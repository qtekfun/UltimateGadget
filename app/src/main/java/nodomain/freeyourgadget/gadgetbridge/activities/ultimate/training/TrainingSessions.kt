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

/**
 * Pure editing operations on a [Session]; every function returns a new session so the UI can keep an
 * immutable state and persist it after each change.
 */
object TrainingSessions {

    /** Starts an empty or routine-based session. Sets are prefilled from the plan, then from last time. */
    fun start(
        title: String,
        routine: Routine?,
        catalog: (String) -> Exercise?,
        history: List<Session>,
        nowMs: Long,
    ): Session {
        val exercises = routine?.exercises.orEmpty().mapNotNull { re ->
            val ex = catalog(re.exerciseId) ?: return@mapNotNull null
            val last = TrainingMath.lastTime(re.exerciseId, history, nowMs)
            val sets = re.sets.mapIndexed { i, p ->
                val ref = last.getOrNull(i) ?: last.lastOrNull()
                SessionSet(
                    kind = p.kind,
                    reps = p.reps ?: ref?.reps,
                    weightKg = p.weightKg ?: ref?.weightKg,
                    durationS = p.durationS ?: ref?.durationS,
                    distanceM = p.distanceM ?: ref?.distanceM,
                    rir = p.rir,
                )
            }
            SessionExercise(ex.id, ex.names, ex.measure, re.restSeconds, re.supersetGroup, sets, re.notes)
        }
        return Session(title = title, routineId = routine?.id, startMs = nowMs, exercises = exercises)
    }

    fun addExercise(s: Session, ex: Exercise, restSeconds: Int = 90, history: List<Session> = emptyList()): Session {
        val last = TrainingMath.lastTime(ex.id, history, s.startMs)
        val first = last.firstOrNull()
        val set = SessionSet(reps = first?.reps, weightKg = first?.weightKg, durationS = first?.durationS, distanceM = first?.distanceM)
        return s.copy(exercises = s.exercises + SessionExercise(ex.id, ex.names, ex.measure, restSeconds, null, listOf(set)))
    }

    fun removeExercise(s: Session, index: Int) = s.copy(exercises = s.exercises.filterIndexed { i, _ -> i != index })

    /** Adds a set copying the previous one (the usual next set), without the done flag. */
    fun addSet(s: Session, exIndex: Int): Session = mapExercise(s, exIndex) { e ->
        val prev = e.sets.lastOrNull()
        e.copy(sets = e.sets + (prev?.copy(done = false, doneAtMs = 0, kind = SetKind.NORMAL) ?: SessionSet()))
    }

    fun removeSet(s: Session, exIndex: Int, setIndex: Int): Session = mapExercise(s, exIndex) { e ->
        e.copy(sets = e.sets.filterIndexed { i, _ -> i != setIndex })
    }

    fun updateSet(s: Session, exIndex: Int, setIndex: Int, f: (SessionSet) -> SessionSet): Session = mapExercise(s, exIndex) { e ->
        e.copy(sets = e.sets.mapIndexed { i, st -> if (i == setIndex) f(st) else st })
    }

    /**
     * Marks or unmarks a set. Completing one starts the exercise's rest timer; unmarking clears it.
     * Returns the new session; [Session.restEndsAtMs] tells the caller whether to arm an alarm.
     */
    fun toggleDone(s: Session, exIndex: Int, setIndex: Int, nowMs: Long): Session {
        val ex = s.exercises.getOrNull(exIndex) ?: return s
        val set = ex.sets.getOrNull(setIndex) ?: return s
        val nowDone = !set.done
        val updated = updateSet(s, exIndex, setIndex) { it.copy(done = nowDone, doneAtMs = if (nowDone) nowMs else 0) }
        return updated.copy(restEndsAtMs = if (nowDone && ex.restSeconds > 0) nowMs + ex.restSeconds * 1000L else 0)
    }

    fun clearRest(s: Session) = s.copy(restEndsAtMs = 0)

    fun setRest(s: Session, exIndex: Int, seconds: Int) = mapExercise(s, exIndex) { it.copy(restSeconds = seconds.coerceAtLeast(0)) }

    /** Seconds left on the rest timer, 0 when none or already over. */
    fun restRemainingSeconds(s: Session, nowMs: Long): Int =
        if (s.restEndsAtMs <= nowMs) 0 else ((s.restEndsAtMs - nowMs + 999) / 1000).toInt()

    /**
     * Finishes the session: sets that were never completed are dropped, exercises left without sets
     * disappear, and the end time is stamped.
     */
    fun finish(s: Session, nowMs: Long): Session {
        val kept = s.exercises.map { e -> e.copy(sets = e.sets.filter { it.done }) }.filter { it.sets.isNotEmpty() }
        return s.copy(exercises = kept, endMs = nowMs, status = SessionStatus.DONE, restEndsAtMs = 0)
    }

    /** A routine built from what was actually done, to repeat a good session. */
    fun toRoutine(s: Session, name: String): Routine = Routine(
        name = name,
        exercises = s.exercises.map { e ->
            RoutineExercise(e.exerciseId, e.restSeconds, e.supersetGroup, e.sets.map {
                PlannedSet(it.kind, it.reps, null, it.weightKg, it.durationS, it.distanceM, it.rir)
            }, e.notes)
        },
    )

    private fun mapExercise(s: Session, index: Int, f: (SessionExercise) -> SessionExercise) =
        s.copy(exercises = s.exercises.mapIndexed { i, e -> if (i == index) f(e) else e })
}
