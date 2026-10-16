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

/**
 * UltimateGadget's own view of the measurements a watch makes on demand, whatever the brand: every
 * sensor is adapted to these types and the app shows them in one list and on the home screen. A
 * brand keeps its data in its own tables; a [HealthMeasurementSource] reads them as these records.
 */
sealed interface HealthMeasurement {
    val deviceAddress: String
    val deviceName: String
    /** Seconds since the epoch. */
    val timestampSec: Long
}

/** What the watch itself concluded about an ECG (the app draws no conclusion of its own). */
enum class EcgClassification {
    NORMAL,
    ATRIAL_FIBRILLATION,
    OTHER,
    INCONCLUSIVE,
    /** The watch sent a result the app does not know how to name; see [EcgRecord.rawCode]. */
    UNKNOWN,
}

/**
 * One ECG strip. [samples] are in the watch's own units (not calibrated to millivolts), so a chart
 * should scale to the signal. [averageHeartRate] is null when the watch did not report one.
 */
class EcgRecord(
    override val deviceAddress: String,
    override val deviceName: String,
    override val timestampSec: Long,
    val source: String,
    val durationSeconds: Int,
    val sampleRate: Int,
    val samples: FloatArray,
    val averageHeartRate: Int?,
    val classification: EcgClassification,
    /** The watch's own result code, kept for results that have no [classification] name yet. */
    val rawCode: Long?,
) : HealthMeasurement

/**
 * One on-demand SpO2 reading. [tenthsOfPercent] is null when the watch gave no usable value (for
 * example an inconclusive reading, whose placeholder value must not be shown as a result).
 */
class Spo2Record(
    override val deviceAddress: String,
    override val deviceName: String,
    override val timestampSec: Long,
    val source: String,
    val tenthsOfPercent: Int?,
    val conclusive: Boolean,
) : HealthMeasurement
