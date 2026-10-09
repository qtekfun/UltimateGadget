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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness

import android.app.Application
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import java.util.Calendar
import kotlin.math.sqrt

/**
 * Gathers the readiness inputs from Gadgetbridge's DB for a device, merged with the user's manual
 * log, and runs [ReadinessEngine]. Everything missing is left null so the score degrades.
 */
class ReadinessRepository(private val app: Application) {
    private val manual = ManualLogStore(app)

    fun load(device: GBDevice?): ReadinessData {
        if (device == null) return ReadinessData(null, emptyList(), 0)
        val inputs = runCatching { gather(device) }.getOrElse { ReadinessInputs() }
        return ReadinessEngine.compute(inputs)
    }

    private fun gather(device: GBDevice): ReadinessInputs {
        var hrvToday: Double? = null
        var hrvMean: Double? = null
        var hrvSd: Double? = null
        var rhrToday: Double? = null
        var rhrMean: Double? = null
        var rhrSd: Double? = null
        var dbSleepHours: Double? = null
        var dbAcute: Double? = null
        var dbChronicWeek: Double? = null

        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val coordinator = device.deviceCoordinator
            val now = System.currentTimeMillis()

            // HRV: last-night average today + 7-day values for mean/sd.
            runCatching {
                val p = coordinator.getHrvSummarySampleProvider(device, session)
                val week = (p?.getAllSamples(now - 8L * 86400_000, now) ?: emptyList())
                    .mapNotNull { it.lastNightAverage?.toDouble() }
                if (week.isNotEmpty()) {
                    hrvToday = week.last()
                    hrvMean = week.average()
                    hrvSd = stdDev(week, hrvMean!!)
                }
            }

            // Resting HR: last ~10 days of resting samples for today + baseline.
            runCatching {
                val p = coordinator.getHeartRateRestingSampleProvider(device, session)
                val vals = (p?.getAllSamples(now - 10L * 86400_000, now) ?: emptyList())
                    .map { it.heartRate.toDouble() }.filter { it in 25.0..120.0 }
                if (vals.isNotEmpty()) {
                    rhrToday = vals.last()
                    rhrMean = vals.average()
                    rhrSd = stdDev(vals, rhrMean!!)
                }
            }

            // Sleep last night (minutes -> hours).
            runCatching {
                val mins = DailyTotals.getDailyTotalsForDevice(device, Calendar.getInstance()).sleep.toInt()
                if (mins > 0) dbSleepHours = mins / 60.0
            }

            // Training load from workouts: minutes * assumed moderate intensity (5), summed.
            runCatching {
                val dbDevice = DBHelper.findDevice(device, session)
                if (dbDevice != null) {
                    val from = now - 28L * 86400_000
                    val summaries = session.baseActivitySummaryDao.queryBuilder()
                        .where(
                            BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.id),
                            BaseActivitySummaryDao.Properties.StartTime.ge(java.util.Date(from)),
                        )
                        .list()
                    var acute = 0.0
                    var chronic = 0.0
                    for (s in summaries) {
                        val start = s.startTime ?: continue
                        val end = s.endTime ?: continue
                        val minutes = ((end.time - start.time) / 60000.0).coerceAtLeast(0.0)
                        val load = minutes * 5.0
                        chronic += load
                        if (start.time >= now - 7L * 86400_000) acute += load
                    }
                    if (chronic > 0) {
                        dbAcute = acute
                        dbChronicWeek = chronic / 4.0
                    }
                }
            }
        }

        // Merge manual log.
        val manualToday = manual.get()
        val sleepManual = manualToday.sleepHours != null
        val sleepHours = manualToday.sleepHours ?: dbSleepHours
        val manualAcute = manual.manualLoad(7)
        val manualChronic = manual.manualLoad(28) / 4.0
        val loadManual = manualAcute > 0.0
        val acute = (dbAcute ?: 0.0) + manualAcute
        val chronicWeek = (dbChronicWeek ?: 0.0) + manualChronic

        return ReadinessInputs(
            hrvToday = hrvToday, hrvMean = hrvMean, hrvSd = hrvSd,
            rhrToday = rhrToday, rhrMean = rhrMean, rhrSd = rhrSd,
            sleepHours = sleepHours, sleepNeedHours = 8.0,
            sleepQuality = manualToday.sleepQuality, sleepManual = sleepManual,
            acuteLoad = if (acute > 0.0) acute else null,
            chronicWeeklyLoad = if (chronicWeek > 0.0) chronicWeek else null,
            loadManual = loadManual,
        )
    }

    private fun stdDev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val v = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(v)
    }
}
