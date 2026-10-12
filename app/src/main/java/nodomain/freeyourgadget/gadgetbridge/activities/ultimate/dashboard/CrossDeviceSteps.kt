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

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import java.util.Calendar

/**
 * Daily steps summed over every watch, so wearing one in the morning and another in the afternoon
 * gives the day's real total instead of only the watch that happens to be selected.
 *
 * Samples are bucketed per minute and only the largest reading of each minute counts, so a
 * stretch where two watches were worn at once is not counted twice.
 */
object CrossDeviceSteps {

    data class Sample(val ts: Int, val steps: Int, val distanceCm: Int)

    data class Merged(val steps: Long, val distanceCm: Long)

    /** Per-minute maximum across [perDevice] sample lists; distance follows the winning sample. */
    fun merge(perDevice: List<List<Sample>>): Merged {
        val bestPerMinute = HashMap<Int, Sample>()
        for (samples in perDevice) {
            // A watch can report several samples within one minute: those add up.
            val ownMinutes = HashMap<Int, Sample>()
            for (s in samples) {
                if (s.steps <= 0) continue
                val minute = Math.floorDiv(s.ts, 60)
                val prev = ownMinutes[minute]
                ownMinutes[minute] = if (prev == null) s else Sample(
                    minute * 60,
                    prev.steps + s.steps,
                    if (prev.distanceCm < 0 && s.distanceCm < 0) -1
                    else maxOf(prev.distanceCm, 0) + maxOf(s.distanceCm, 0),
                )
            }
            for ((minute, s) in ownMinutes) {
                val best = bestPerMinute[minute]
                if (best == null || s.steps > best.steps) bestPerMinute[minute] = s
            }
        }
        var steps = 0L
        var distance = 0L
        for (s in bestPerMinute.values) {
            steps += s.steps
            if (s.distanceCm > 0) distance += s.distanceCm
        }
        return Merged(steps, distance)
    }

    /**
     * Totals for [day] over all devices. A single watch with data returns exactly what the
     * dashboard always showed for it ([DailyTotals]); with several, their samples are merged.
     * [primary] is used when no device has data. Null if nothing could be read.
     */
    fun dailyTotals(day: Calendar, primary: GBDevice): Merged? {
        val from = (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fromTs = (from.timeInMillis / 1000).toInt()
        val toTs = fromTs + 24 * 60 * 60 - 1

        val withData = ArrayList<Pair<GBDevice, List<Sample>>>()
        runCatching {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                for (device in GBApplication.app().deviceManager.devices) {
                    val provider = device.deviceCoordinator.getSampleProvider(device, session) ?: continue
                    val samples = provider.getAllActivitySamples(fromTs, toTs)
                        .map { Sample(it.timestamp, it.steps, it.distanceCm) }
                    if (samples.any { it.steps > 0 }) withData += device to samples
                }
            }
        }

        if (withData.size > 1) return merge(withData.map { it.second })

        val device = withData.firstOrNull()?.first ?: primary
        val totals = runCatching { DailyTotals.getDailyTotalsForDevice(device, day) }.getOrNull()
            ?: return null
        return Merged(totals.steps, totals.distance)
    }
}
