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
 * Small text-only starter list so the logger is usable before any resource pack is installed.
 * Ids are prefixed so they can never clash with pack ids (wger uuids) or custom ones (random uuids).
 */
object BuiltinExercises {
    private fun e(id: String, en: String, es: String, muscle: String, equipment: String, measure: Measure) =
        Exercise("builtin:$id", mapOf("en" to en, "es" to es), measure, muscle, equipment)

    val all: List<Exercise> = listOf(
        e("bench_press", "Barbell bench press", "Press de banca con barra", "chest", "barbell", Measure.REPS_WEIGHT),
        e("incline_bench_press", "Incline barbell bench press", "Press de banca inclinado con barra", "chest", "barbell", Measure.REPS_WEIGHT),
        e("dumbbell_bench_press", "Dumbbell bench press", "Press de banca con mancuernas", "chest", "dumbbell", Measure.REPS_WEIGHT),
        e("chest_fly", "Dumbbell fly", "Aperturas con mancuernas", "chest", "dumbbell", Measure.REPS_WEIGHT),
        e("pec_deck", "Pec deck", "Contractora (pec deck)", "chest", "machine", Measure.REPS_WEIGHT),
        e("push_up", "Push-up", "Flexiones", "chest", "bodyweight", Measure.REPS),
        e("dip", "Dip", "Fondos", "chest", "bodyweight", Measure.REPS),
        e("overhead_press", "Overhead press", "Press militar", "shoulders", "barbell", Measure.REPS_WEIGHT),
        e("dumbbell_shoulder_press", "Dumbbell shoulder press", "Press de hombros con mancuernas", "shoulders", "dumbbell", Measure.REPS_WEIGHT),
        e("lateral_raise", "Lateral raise", "Elevaciones laterales", "shoulders", "dumbbell", Measure.REPS_WEIGHT),
        e("rear_delt_fly", "Rear delt fly", "Pájaros", "shoulders", "dumbbell", Measure.REPS_WEIGHT),
        e("face_pull", "Face pull", "Face pull", "shoulders", "cable", Measure.REPS_WEIGHT),
        e("pull_up", "Pull-up", "Dominadas", "back", "bodyweight", Measure.REPS),
        e("chin_up", "Chin-up", "Dominadas supinas", "back", "bodyweight", Measure.REPS),
        e("lat_pulldown", "Lat pulldown", "Jalón al pecho", "back", "cable", Measure.REPS_WEIGHT),
        e("barbell_row", "Barbell row", "Remo con barra", "back", "barbell", Measure.REPS_WEIGHT),
        e("dumbbell_row", "One-arm dumbbell row", "Remo a un brazo con mancuerna", "back", "dumbbell", Measure.REPS_WEIGHT),
        e("seated_cable_row", "Seated cable row", "Remo sentado en polea", "back", "cable", Measure.REPS_WEIGHT),
        e("deadlift", "Deadlift", "Peso muerto", "back", "barbell", Measure.REPS_WEIGHT),
        e("romanian_deadlift", "Romanian deadlift", "Peso muerto rumano", "hamstrings", "barbell", Measure.REPS_WEIGHT),
        e("squat", "Barbell back squat", "Sentadilla con barra", "quadriceps", "barbell", Measure.REPS_WEIGHT),
        e("front_squat", "Front squat", "Sentadilla frontal", "quadriceps", "barbell", Measure.REPS_WEIGHT),
        e("leg_press", "Leg press", "Prensa de piernas", "quadriceps", "machine", Measure.REPS_WEIGHT),
        e("lunge", "Walking lunge", "Zancadas caminando", "quadriceps", "dumbbell", Measure.REPS_WEIGHT),
        e("bulgarian_split_squat", "Bulgarian split squat", "Sentadilla búlgara", "quadriceps", "dumbbell", Measure.REPS_WEIGHT),
        e("leg_extension", "Leg extension", "Extensión de piernas", "quadriceps", "machine", Measure.REPS_WEIGHT),
        e("leg_curl", "Lying leg curl", "Curl femoral tumbado", "hamstrings", "machine", Measure.REPS_WEIGHT),
        e("hip_thrust", "Hip thrust", "Hip thrust", "glutes", "barbell", Measure.REPS_WEIGHT),
        e("calf_raise", "Standing calf raise", "Elevación de talones de pie", "calves", "machine", Measure.REPS_WEIGHT),
        e("biceps_curl", "Dumbbell biceps curl", "Curl de bíceps con mancuernas", "biceps", "dumbbell", Measure.REPS_WEIGHT),
        e("barbell_curl", "Barbell curl", "Curl con barra", "biceps", "barbell", Measure.REPS_WEIGHT),
        e("hammer_curl", "Hammer curl", "Curl martillo", "biceps", "dumbbell", Measure.REPS_WEIGHT),
        e("triceps_pushdown", "Triceps pushdown", "Extensión de tríceps en polea", "triceps", "cable", Measure.REPS_WEIGHT),
        e("skull_crusher", "Skull crusher", "Rompecráneos", "triceps", "barbell", Measure.REPS_WEIGHT),
        e("crunch", "Crunch", "Abdominales (crunch)", "abs", "bodyweight", Measure.REPS),
        e("hanging_leg_raise", "Hanging leg raise", "Elevación de piernas colgado", "abs", "bodyweight", Measure.REPS),
        e("plank", "Plank", "Plancha", "abs", "bodyweight", Measure.TIME),
        e("running", "Running", "Correr", "cardio", "none", Measure.DISTANCE_TIME),
        e("cycling", "Cycling", "Ciclismo", "cardio", "bike", Measure.DISTANCE_TIME),
        e("rowing_machine", "Rowing machine", "Máquina de remo", "cardio", "machine", Measure.DISTANCE_TIME),
        e("swimming", "Swimming", "Natación", "cardio", "none", Measure.DISTANCE_TIME),
        e("walking", "Walking", "Caminar", "cardio", "none", Measure.DISTANCE_TIME),
        e("jump_rope", "Jump rope", "Comba", "cardio", "rope", Measure.TIME),
    )

    private val byId = all.associateBy { it.id }
    fun find(id: String): Exercise? = byId[id]
}
