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

import android.app.Application
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Reads weekly/monthly aggregates for the Reports and Goals screens straight from Gadgetbridge's own
 * database, reusing the very same helpers the built-in charts use: [DailyTotals] for steps/distance/
 * sleep per day, the device's resting-heart-rate [nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider]
 * for resting HR, and [BaseActivitySummaryDao] for the workout count. 100% offline, no network.
 *
 * One pass builds a daily series over [HISTORY_DAYS]; the week/month summaries and the streaks/medals
 * are all derived from it so the whole screen loads from a single DB acquisition.
 */
class ReportsRepository(private val app: Application) {
    private val log = LoggerFactory.getLogger(ReportsRepository::class.java)

    /** One calendar day's aggregates, oldest..today in the series. */
    private data class DayRecord(
        val dayStartMs: Long,
        val steps: Int,
        val distanceKm: Double,
        val sleepMinutes: Int,
        val restingHr: Int,   // 0 == no data
    )

    fun load(stepGoal: Int): ReportData {
        val device = pickDevice() ?: return ReportData.empty(stepGoal)
        val series = runCatching { loadDailySeries(device) }.getOrDefault(emptyList())
        if (series.isEmpty()) return ReportData.empty(stepGoal).copy(hasDevice = true)

        val weekDays = series.takeLast(7)
        val monthDays = series.takeLast(30)
        val weekly = summarize(weekDays, ReportPeriod.WEEK, stepGoal, device)
        val monthly = summarize(monthDays, ReportPeriod.MONTH, stepGoal, device)
        val goals = computeGoals(series, stepGoal)
        return ReportData(weekly, monthly, goals, hasDevice = true)
    }

    private fun pickDevice(): GBDevice? {
        val devices = (app as GBApplication).deviceManager.devices
        return devices.firstOrNull { it.isInitialized } ?: devices.firstOrNull()
    }

    // ---- Daily series -----------------------------------------------------------------------

    private fun loadDailySeries(device: GBDevice): List<DayRecord> {
        GBApplication.acquireDB().use { db ->
            // Resting HR for the whole window in one query, bucketed by day below.
            val windowStart = startOfDay(daysAgo(HISTORY_DAYS - 1)).timeInMillis
            val restingByDay = loadRestingByDay(device, db, windowStart)

            val out = ArrayList<DayRecord>(HISTORY_DAYS)
            for (ago in (HISTORY_DAYS - 1) downTo 0) {
                val day = startOfDay(daysAgo(ago))
                val totals = runCatching { DailyTotals.getDailyTotalsForDevice(device, day, db) }.getOrNull()
                val steps = totals?.steps?.toInt() ?: 0
                val distanceKm = (totals?.distance ?: 0L) / 1000.0
                val sleep = totals?.sleep?.toInt() ?: 0
                out.add(
                    DayRecord(
                        dayStartMs = day.timeInMillis,
                        steps = steps,
                        distanceKm = distanceKm,
                        sleepMinutes = sleep,
                        restingHr = restingByDay[day.timeInMillis] ?: 0,
                    )
                )
            }
            return out
        }
    }

    /** Average resting HR per day over the window, keyed by that day's midnight millis. */
    private fun loadRestingByDay(device: GBDevice, db: DBHandler, windowStartMs: Long): Map<Long, Int> {
        return runCatching {
            val provider = device.deviceCoordinator.getHeartRateRestingSampleProvider(device, db.daoSession)
                ?: return emptyMap()
            val now = System.currentTimeMillis()
            val samples = provider.getAllSamples(windowStartMs, now)
            val sums = HashMap<Long, IntArray>() // dayMillis -> [sum, count]
            for (s in samples) {
                val hr = s.heartRate
                if (hr !in 1..250) continue
                val day = startOfDay(Calendar.getInstance().apply { timeInMillis = s.timestamp }).timeInMillis
                val acc = sums.getOrPut(day) { IntArray(2) }
                acc[0] += hr; acc[1] += 1
            }
            sums.mapValues { (_, acc) -> if (acc[1] > 0) acc[0] / acc[1] else 0 }
        }.getOrDefault(emptyMap())
    }

    // ---- Summaries --------------------------------------------------------------------------

    private fun summarize(
        days: List<DayRecord>,
        period: ReportPeriod,
        stepGoal: Int,
        device: GBDevice,
    ): ReportSummary {
        val withData = days.filter { it.steps > 0 || it.sleepMinutes > 0 || it.restingHr > 0 }
        val totalSteps = days.sumOf { it.steps.toLong() }
        val totalDistance = days.sumOf { it.distanceKm }
        val stepDays = days.count { it.steps > 0 }
        val sleepDays = days.filter { it.sleepMinutes > 0 }
        val hrDays = days.filter { it.restingHr > 0 }
        val goalDays = days.count { it.steps >= stepGoal }

        val points = when (period) {
            ReportPeriod.WEEK -> days.map { rec ->
                ReportPoint(
                    label = DOW_FMT.format(Date(rec.dayStartMs)),
                    steps = rec.steps,
                    distanceKm = rec.distanceKm,
                    sleepMinutes = rec.sleepMinutes,
                    restingHr = rec.restingHr,
                    goalMet = rec.steps >= stepGoal,
                )
            }
            ReportPeriod.MONTH -> groupIntoWeeks(days, stepGoal)
        }

        val periodStartMs = days.firstOrNull()?.dayStartMs ?: System.currentTimeMillis()
        val workoutCount = runCatching { countWorkouts(device, periodStartMs) }.getOrDefault(0)

        return ReportSummary(
            period = period,
            points = points,
            totalSteps = totalSteps,
            avgSteps = if (stepDays > 0) (totalSteps / stepDays).toInt() else 0,
            totalDistanceKm = totalDistance,
            avgSleepMinutes = if (sleepDays.isNotEmpty()) sleepDays.sumOf { it.sleepMinutes } / sleepDays.size else 0,
            avgRestingHr = if (hrDays.isNotEmpty()) hrDays.sumOf { it.restingHr } / hrDays.size else 0,
            workoutCount = workoutCount,
            daysWithData = withData.size,
            goalDays = goalDays,
        )
    }

    /** Group the month's days into consecutive 7-day buckets ("S1".."S5"), oldest first. */
    private fun groupIntoWeeks(days: List<DayRecord>, stepGoal: Int): List<ReportPoint> {
        if (days.isEmpty()) return emptyList()
        val buckets = days.chunked(7)
        return buckets.mapIndexed { idx, week ->
            val stepDays = week.count { it.steps > 0 }
            val sleepDays = week.filter { it.sleepMinutes > 0 }
            val hrDays = week.filter { it.restingHr > 0 }
            val totalSteps = week.sumOf { it.steps }
            ReportPoint(
                label = "S${idx + 1}",
                steps = if (stepDays > 0) totalSteps / stepDays else 0, // avg daily steps that week
                distanceKm = week.sumOf { it.distanceKm },
                sleepMinutes = if (sleepDays.isNotEmpty()) sleepDays.sumOf { it.sleepMinutes } / sleepDays.size else 0,
                restingHr = if (hrDays.isNotEmpty()) hrDays.sumOf { it.restingHr } / hrDays.size else 0,
                goalMet = stepDays > 0 && (totalSteps / stepDays) >= stepGoal,
            )
        }
    }

    private fun countWorkouts(device: GBDevice, sinceMs: Long): Int {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val dbDevice = DBHelper.findDevice(device, session) ?: return 0
            return session.baseActivitySummaryDao.queryBuilder()
                .where(
                    BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.id),
                    BaseActivitySummaryDao.Properties.StartTime.ge(Date(sinceMs)),
                )
                .count()
                .toInt()
        }
    }

    // ---- Goals / streaks / medals -----------------------------------------------------------

    private fun computeGoals(series: List<DayRecord>, stepGoal: Int): GoalsData {
        val goal = stepGoal.coerceAtLeast(1)
        val todaySteps = series.lastOrNull()?.steps ?: 0

        // Current streak: consecutive days meeting the goal, counting back from today. Today not yet
        // meeting the goal does not break a streak that is still "in progress", so we start from the
        // most recent day that either met the goal or is today-still-open.
        var currentStreak = 0
        for (i in series.indices.reversed()) {
            val rec = series[i]
            val isToday = i == series.lastIndex
            if (rec.steps >= goal) {
                currentStreak++
            } else if (isToday) {
                // today is still open and hasn't hit the goal yet: don't count it, keep looking back
                continue
            } else {
                break
            }
        }

        // Best streak over the whole history.
        var best = 0
        var run = 0
        for (rec in series) {
            if (rec.steps >= goal) { run++; best = maxOf(best, run) } else run = 0
        }

        val medals = computeMedals(series, goal, currentStreak, best)
        return GoalsData(
            stepGoal = stepGoal,
            todaySteps = todaySteps,
            currentStreak = currentStreak,
            bestStreak = best,
            medals = medals,
        )
    }

    private fun computeMedals(series: List<DayRecord>, goal: Int, currentStreak: Int, bestStreak: Int): List<Medal> {
        val anyGoalDay = series.any { it.steps >= goal }
        val bestDay = series.maxOfOrNull { it.steps } ?: 0
        val bestWeekSteps = series.chunked(7).maxOfOrNull { wk -> wk.sumOf { it.steps } } ?: 0
        val totalSteps = series.sumOf { it.steps.toLong() }
        val totalDistance = series.sumOf { it.distanceKm }
        val streakBest = maxOf(currentStreak, bestStreak)

        return listOf(
            Medal("first_goal", "Primer objetivo", "Alcanza tu meta de pasos en un día", anyGoalDay),
            Medal("step_15k", "15 000 pasos", "15 000 pasos en un solo día", bestDay >= 15_000),
            Medal("streak_3", "Racha de 3", "3 días seguidos cumpliendo el objetivo", streakBest >= 3),
            Medal("streak_7", "Semana perfecta", "7 días seguidos cumpliendo el objetivo", streakBest >= 7),
            Medal("streak_30", "Mes de fuego", "30 días seguidos cumpliendo el objetivo", streakBest >= 30),
            Medal("week_70k", "70k en 7 días", "70 000 pasos en una semana", bestWeekSteps >= 70_000),
            Medal("dist_100k", "100 km", "100 km acumulados en el historial", totalDistance >= 100.0),
            Medal("steps_1m", "Millón de pasos", "1 000 000 de pasos acumulados", totalSteps >= 1_000_000L),
        )
    }

    // ---- date helpers -----------------------------------------------------------------------

    private fun daysAgo(n: Int): Calendar =
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }

    private fun startOfDay(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    companion object {
        /** How much history to scan for streaks/medals (also the longest report window + margin). */
        private const val HISTORY_DAYS = 180
        private val DOW_FMT = SimpleDateFormat("EEE", Locale.getDefault())
    }
}
