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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.performance

/**
 * Immutable results of the local performance computations. Every block is nullable so a metric the
 * device does not provide the inputs for degrades to a "sin datos" card instead of showing an
 * invented value. All of this is derived on-device from the Gadgetbridge database — no network.
 */
data class PerformanceData(
    val pai: PaiData?,
    val load: TrainingLoadData?,
    val vo2max: Vo2MaxData?,
    val hrv: HrvData?,
    /** Estimated maximal heart rate (220 − age), shown as the basis for PAI/VO2max. */
    val hrMax: Int,
    /** Age used for [hrMax]; 0 if the user never set a birth date. */
    val age: Int,
    /** Resting HR baseline (bpm) used by TRIMP/VO2max, null if the device stores none. */
    val restingHr: Int?,
)

/**
 * Personal Activity Intelligence, HUNT-style (Nes et al. 2017). Points accrue for time spent with an
 * elevated heart rate over a rolling 7-day window; 100 is the weekly target.
 */
data class PaiData(
    val total: Int,
    /** PAI earned per day, oldest→newest, 7 entries. */
    val daily: List<Int>,
    /** Minutes in the moderate zone over the 7-day window. */
    val moderateMinutes: Int,
    /** Minutes in the vigorous zone over the 7-day window. */
    val vigorousMinutes: Int,
)

/**
 * Banister TRIMP training load. Acute = last 7 days; chronic = average week over the last 28 days;
 * ACWR = acute / chronic (Gabbett's acute:chronic workload ratio).
 */
data class TrainingLoadData(
    val acute: Int,
    val chronic: Int,
    val acwr: Double?,
    /** Daily TRIMP over the last 28 days, oldest→newest (28 entries), for the trend chart. */
    val daily: List<Double>,
    val workoutsCounted: Int,
    /** True when TRIMP used measured heart rate; false when it fell back to a duration estimate. */
    val usedHeartRate: Boolean,
)

enum class Vo2Method { HR_RATIO, RUNNING_VDOT }

/** Estimated VO2max (ml·kg⁻¹·min⁻¹). Always flagged as an estimate; method says how it was derived. */
data class Vo2MaxData(
    val value: Double,
    val method: Vo2Method,
    val detail: String,
)

/** Heart-rate variability, read straight from the device's HRV summary provider when it has one. */
data class HrvData(
    /** Last-night rMSSD average (ms), null if unknown. */
    val lastNight: Int?,
    /** Device weekly rMSSD average (ms), null if unknown. */
    val weekly: Int?,
    /** Device HRV status label (e.g. BALANCED), null if unknown. */
    val status: String?,
    /** Last-night averages over the recent window, oldest→newest, for the trend chart. */
    val trend: List<Int>,
)
