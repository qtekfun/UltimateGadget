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

import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessData
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessFactor
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessFactorId

/**
 * Source of the dashboard's health data.
 *
 * The real implementation will read Gadgetbridge's database (DaoSession / activity samples,
 * [nodomain.freeyourgadget.gadgetbridge.GBApplication.acquireDB]), the same samples the existing
 * charts use. For now [SampleHealthRepository] returns clearly-marked placeholder data so the UI
 * can be designed and reviewed; swapping in a DbHealthRepository is a drop-in replacement.
 */
interface HealthRepository {
    fun load(): DashboardData
}

/**
 * TODO(data): replace with a DbHealthRepository backed by GBApplication.acquireDB():
 *   - steps/distance/kcal + weekly: ActivityAnalysis over the day's ActivitySample list.
 *   - lastWorkout: newest BaseActivitySummary for the device.
 *   - heart/spo2: latest HR + SpO2 samples; resting HR from the day's sleep window.
 *   - sleep/stress: sleep session (deep/light/rem/awake) + latest stress sample.
 * Everything below is synthetic sample data, flagged via DashboardData.isSample = true.
 */
class SampleHealthRepository : HealthRepository {
    override fun load(): DashboardData = DashboardData(
        steps = StepsData(
            steps = 8421, goal = 10000, distanceKm = 6.1, kcal = 412,
            weekly = listOf(9120, 7430, 11200, 6050, 12840, 8900, 8421),
        ),
        lastWorkout = WorkoutData(
            type = "Running", distanceKm = 5.2, durationSeconds = 28L * 60 + 14,
            whenLabel = "Today 07:39", avgHeartRate = 151,
            trace = listOf(3f, 5f, 6f, 5f, 7f, 8f, 6f, 7f, 9f, 6f, 5f, 7f),
        ),
        heart = HeartData(
            currentBpm = 72, restingBpm = 58, spo2 = 97,
            recent = listOf(64, 61, 72, 88, 76, 69, 65, 71, 74, 68, 72),
        ),
        sleepStress = SleepStressData(
            sleepMinutes = 7 * 60 + 12, deepMinutes = 96, lightMinutes = 258,
            remMinutes = 78, awakeMinutes = 0, stressLevel = 34,
        ),
        readiness = ReadinessData(
            score = 78,
            factors = listOf(
                ReadinessFactor(ReadinessFactorId.HRV, 82, 0.30, "rMSSD 58 ms vs media 7d 54 ms"),
                ReadinessFactor(ReadinessFactorId.RHR, 74, 0.20, "FC reposo 56 vs media 7d 58"),
                ReadinessFactor(ReadinessFactorId.SLEEP, 90, 0.30, "7,2 h de 8 h objetivo"),
                ReadinessFactor(ReadinessFactorId.LOAD, 65, 0.20, "ACWR 1,35 (ligero pico)"),
            ),
            confidence = 100,
        ),
        isSample = true,
    )
}
