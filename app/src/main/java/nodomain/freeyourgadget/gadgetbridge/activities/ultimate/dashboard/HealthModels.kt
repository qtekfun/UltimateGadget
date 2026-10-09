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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard

/** The dashboard cards, each identified for persistence of the user's selection/order. */
enum class DashboardCardId(val defaultEnabled: Boolean) {
    STEPS(true),
    LAST_WORKOUT(true),
    HEART(true),
    SLEEP_STRESS(true);
}

data class StepsData(
    val steps: Int,
    val goal: Int,
    val distanceKm: Double,
    val kcal: Int,
    /** One value per day, oldest..today, for the weekly sparkline. */
    val weekly: List<Int>,
)

data class WorkoutData(
    val type: String,
    val distanceKm: Double,
    val durationSeconds: Long,
    val whenLabel: String,
    val avgHeartRate: Int,
    /** Pace/elevation-ish shape for the card sparkline. */
    val trace: List<Float>,
)

data class HeartData(
    val currentBpm: Int,
    val restingBpm: Int,
    val spo2: Int,
    /** Recent readings for the mini chart. */
    val recent: List<Int>,
)

data class SleepStressData(
    val sleepMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val stressLevel: Int,   // 0..100
)

/** Everything the dashboard shows, loaded in one pass. A null metric means "no data yet". */
data class DashboardData(
    val steps: StepsData?,
    val lastWorkout: WorkoutData?,
    val heart: HeartData?,
    val sleepStress: SleepStressData?,
    /** True while the values are placeholder samples, not real device data. */
    val isSample: Boolean,
)
