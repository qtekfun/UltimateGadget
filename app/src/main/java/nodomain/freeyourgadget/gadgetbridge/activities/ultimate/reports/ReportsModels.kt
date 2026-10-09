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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.reports

/** Which window a report covers. */
enum class ReportPeriod { WEEK, MONTH }

/** One point on a chart: a short axis label plus the metrics for that bucket (a day or a week). */
data class ReportPoint(
    val label: String,
    val steps: Int,
    val distanceKm: Double,
    val sleepMinutes: Int,
    val restingHr: Int,   // 0 == no data
    val goalMet: Boolean,
)

/**
 * A summarised report over a period: aggregate stats plus the per-bucket series used to draw the
 * bar charts. For WEEK the buckets are the 7 days; for MONTH they are grouped into weeks.
 */
data class ReportSummary(
    val period: ReportPeriod,
    val points: List<ReportPoint>,
    val totalSteps: Long,
    val avgSteps: Int,
    val totalDistanceKm: Double,
    val avgSleepMinutes: Int,
    val avgRestingHr: Int,   // 0 == no data
    val workoutCount: Int,
    val daysWithData: Int,
    val goalDays: Int,       // days in the period that met the step goal
)

/** A simple achievement computed locally from the data; [achieved] drives the medal grid state. */
data class Medal(
    val id: String,
    val title: String,
    val description: String,
    val achieved: Boolean,
)

/** Goals / streaks / medals, derived from the daily step series and the current step goal. */
data class GoalsData(
    val stepGoal: Int,
    val todaySteps: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val medals: List<Medal>,
)

/** Everything the reports screens show, loaded in one DB pass. */
data class ReportData(
    val weekly: ReportSummary?,
    val monthly: ReportSummary?,
    val goals: GoalsData,
    val hasDevice: Boolean,
) {
    companion object {
        fun empty(stepGoal: Int) = ReportData(
            weekly = null,
            monthly = null,
            goals = GoalsData(stepGoal, 0, 0, 0, emptyList()),
            hasDevice = false,
        )
    }
}
