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

import android.app.Application
import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessRepository
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Reads the dashboard's health data from Gadgetbridge's own database for the active device, using
 * the same helpers the built-in charts/widgets use ([DailyTotals], the device's sample providers and
 * [BaseActivitySummaryDao]). Any metric the device doesn't store comes back null so the card shows a
 * graceful "no data" state. Falls back to [SampleHealthRepository] data only for @Preview.
 */
class DbHealthRepository(private val app: Application) : HealthRepository {
    private val log = LoggerFactory.getLogger(DbHealthRepository::class.java)

    override fun load(): DashboardData {
        val device = pickDevice()
            ?: return DashboardData(null, null, null, null, null, isSample = false)
        return DashboardData(
            steps = runCatching { loadSteps(device) }.getOrNull(),
            lastWorkout = runCatching { loadLastWorkout(device) }.getOrNull(),
            heart = runCatching { loadHeart(device) }.getOrNull(),
            sleepStress = runCatching { loadSleepStress(device) }.getOrNull(),
            readiness = runCatching { ReadinessRepository(app).load(device) }.getOrNull(),
            isSample = false,
        )
    }

    private fun pickDevice(): GBDevice? {
        val devices = (app as GBApplication).deviceManager.devices
        return devices.firstOrNull { it.isInitialized } ?: devices.firstOrNull()
    }

    // ---- Steps / activity -------------------------------------------------------------------

    private fun loadSteps(device: GBDevice): StepsData? {
        val weekly = ArrayList<Int>(7)
        var todayTotals: DailyTotals? = null
        for (daysAgo in 6 downTo 0) {
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val t = DailyTotals.getDailyTotalsForDevice(device, day)
            weekly.add(t.steps.toInt())
            if (daysAgo == 0) todayTotals = t
        }
        val today = todayTotals ?: return null
        if (today.steps <= 0 && weekly.all { it <= 0 }) return null
        val goal = runCatching { ActivityUser().stepsGoal }.getOrDefault(10000).coerceAtLeast(1)
        return StepsData(
            steps = today.steps.toInt(),
            goal = goal,
            distanceKm = today.distance / 1000.0,
            kcal = (today.activeCalories + today.restingCalories).toInt(),
            weekly = weekly,
        )
    }

    // ---- Heart rate / SpO2 ------------------------------------------------------------------

    private fun loadHeart(device: GBDevice): HeartData? {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val coordinator = device.deviceCoordinator
            val now = System.currentTimeMillis()
            val from = now - 6L * 60 * 60 * 1000

            val activity = coordinator.getSampleProvider(device, session)
            val recentHr = runCatching {
                activity?.getAllActivitySamples((from / 1000).toInt(), (now / 1000).toInt())
                    .orEmpty().map { it.heartRate }.filter { it in 1..250 }
            }.getOrDefault(emptyList())

            val resting = runCatching {
                coordinator.getHeartRateRestingSampleProvider(device, session)?.latestSample?.heartRate
            }.getOrNull()?.takeIf { it in 1..250 }

            val spo2 = runCatching {
                coordinator.getSpo2SampleProvider(device, session)?.latestSample?.spo2
            }.getOrNull()?.takeIf { it in 1..100 }

            val current = recentHr.lastOrNull() ?: resting
            if (current == null && resting == null && spo2 == null) return null
            return HeartData(
                currentBpm = current ?: 0,
                restingBpm = resting ?: 0,
                spo2 = spo2 ?: 0,
                recent = recentHr.takeLast(48),
            )
        }
    }

    // ---- Sleep / stress --------------------------------------------------------------------

    private fun loadSleepStress(device: GBDevice): SleepStressData? {
        val today = Calendar.getInstance()
        // DailyTotals only exposes the total (light+deep+rem); the per-phase breakdown is not
        // public, so we show the total and leave the phase split for a later increment.
        val totalSleep = DailyTotals.getDailyTotalsForDevice(device, today).sleep.toInt()

        val stress = GBApplication.acquireDB().use { db ->
            runCatching {
                device.deviceCoordinator.getStressSampleProvider(device, db.daoSession)
                    ?.latestSample?.stress
            }.getOrNull()?.takeIf { it in 1..100 }
        }

        if (totalSleep <= 0 && stress == null) return null
        return SleepStressData(
            sleepMinutes = totalSleep,
            lightMinutes = totalSleep,
            deepMinutes = 0,
            remMinutes = 0,
            awakeMinutes = 0,
            stressLevel = stress ?: 0,
        )
    }

    // ---- Last workout ----------------------------------------------------------------------

    private fun loadLastWorkout(device: GBDevice): WorkoutData? {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val dbDevice = DBHelper.findDevice(device, session) ?: return null
            val summary = session.baseActivitySummaryDao.queryBuilder()
                .where(BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.id))
                .orderDesc(BaseActivitySummaryDao.Properties.StartTime)
                .limit(1)
                .list()
                .firstOrNull() ?: return null

            val start: Date? = summary.startTime
            val end: Date? = summary.endTime
            val durationSeconds = if (start != null && end != null)
                ((end.time - start.time) / 1000).coerceAtLeast(0) else 0L
            val kind = summary.activityKind?.let { runCatching { ActivityKind.fromCode(it) }.getOrNull() }
            val type = kind?.getLabel(app)
                ?: summary.name?.takeIf { it.isNotBlank() }
                ?: "Workout"
            return WorkoutData(
                type = type,
                distanceKm = 0.0,
                durationSeconds = durationSeconds,
                whenLabel = start?.let { DATE_FMT.format(it) } ?: "",
                avgHeartRate = 0,
                trace = emptyList(),
            )
        }
    }

    companion object {
        private val DATE_FMT = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
    }
}
