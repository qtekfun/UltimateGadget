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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/** Reads one brand's stored measurements as [HealthMeasurement]s. Add one per sensor family. */
interface HealthMeasurementSource {
    fun supports(device: GBDevice): Boolean
    fun load(db: DBHandler, device: GBDevice): List<HealthMeasurement>
}

/** Every known source, and the one entry point the screens use. */
object HealthMeasurements {
    private val sources: List<HealthMeasurementSource> = listOf(WithingsHealthSource, HuaweiHealthSource)

    fun supports(device: GBDevice): Boolean = sources.any { runCatching { it.supports(device) }.getOrDefault(false) }

    /** The measurements of one device, newest first. */
    fun load(device: GBDevice): List<HealthMeasurement> {
        val matching = sources.filter { runCatching { it.supports(device) }.getOrDefault(false) }
        if (matching.isEmpty()) return emptyList()
        return runCatching {
            GBApplication.acquireDB().use { db -> matching.flatMap { it.load(db, device) } }
        }.getOrDefault(emptyList()).sortedByDescending { it.timestampSec }
    }

    /** The measurements of every device that has some, newest first. */
    fun loadAll(context: Context): List<HealthMeasurement> {
        val app = context.applicationContext as GBApplication
        return app.deviceManager.devices.filter { supports(it) }.flatMap { load(it) }.sortedByDescending { it.timestampSec }
    }

    /** What the home tile shows: the newest ECG and the newest SpO2 reading that has a value. */
    fun summary(measurements: List<HealthMeasurement>): HealthSummary = HealthSummary(
        lastEcg = measurements.filterIsInstance<EcgRecord>().maxByOrNull { it.timestampSec },
        lastSpo2 = measurements.filterIsInstance<Spo2Record>()
            .filter { it.conclusive && it.tenthsOfPercent != null }.maxByOrNull { it.timestampSec },
    )
}

class HealthSummary(val lastEcg: EcgRecord?, val lastSpo2: Spo2Record?)
