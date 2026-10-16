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

import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanWatchMeasurement
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.ScanWatchMeasurementStore
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.ScanWatchSignalCodec
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.StoredMeasurement

/** The ECG and SpO2 readings kept by a Withings ScanWatch. */
object WithingsHealthSource : HealthMeasurementSource {
    override fun supports(device: GBDevice): Boolean = device.type == DeviceType.WITHINGS_SCANWATCH

    override fun load(db: DBHandler, device: GBDevice): List<HealthMeasurement> =
        ScanWatchMeasurementStore.load(db, device).mapNotNull { convert(device.address, device.name.orEmpty(), it) }

    fun convert(address: String, name: String, m: WithingsScanWatchMeasurement): HealthMeasurement? = when (m.type) {
        StoredMeasurement.KIND_ECG -> {
            val raw = m.signal ?: ByteArray(0)
            val samples = ScanWatchSignalCodec.decode(raw).let { s -> FloatArray(s.size) { s[it].toFloat() } }
            EcgRecord(
                address, name, m.timestamp, "Withings",
                m.duration, if (m.sampleRate > 0) m.sampleRate else 300, samples,
                m.heartRate.takeIf { it > 0 },
                if (m.result == StoredMeasurement.RESULT_NORMAL_SINUS) EcgClassification.NORMAL else EcgClassification.UNKNOWN,
                m.result.takeIf { it >= 0 && it != StoredMeasurement.RESULT_NORMAL_SINUS },
            )
        }
        StoredMeasurement.KIND_SPO2 -> {
            val tenths = StoredMeasurement.spo2TenthsFrom(m.readings).takeIf { it > 0 }
            val conclusive = m.result != StoredMeasurement.RESULT_SPO2_INCONCLUSIVE && tenths != null
            Spo2Record(address, name, m.timestamp, "Withings", if (conclusive) tenths else null, conclusive)
        }
        else -> null
    }
}
