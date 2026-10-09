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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.sleep

/** The four sleep stages Gadgetbridge classifies, ordered from deepest to most awake. */
enum class SleepPhase { DEEP, LIGHT, REM, AWAKE }

/**
 * One contiguous stretch of a single [phase] inside the main sleep session, measured from the
 * session start. Used to draw the hypnogram.
 */
data class SleepSegment(
    val phase: SleepPhase,
    val startOffsetMin: Int,
    val durationMin: Int,
)

/** A daytime sleep session separate from the main night. */
data class NapInfo(
    val startLabel: String,
    val endLabel: String,
    val durationMin: Int,
)

/**
 * Everything the advanced sleep screen shows for one calendar day, read in a single pass from the
 * local Gadgetbridge database. [hasData] is false when the device stored no sleep for the day.
 */
data class SleepDayData(
    val dayStartMillis: Long,
    val hasData: Boolean,
    val sleepStartLabel: String,
    val sleepEndLabel: String,
    val sessionStartMillis: Long,
    val sessionEndMillis: Long,
    val totalMin: Int,
    val deepMin: Int,
    val lightMin: Int,
    val remMin: Int,
    val awakeMin: Int,
    val awakenings: Int,
    /** 0..100. Device-native when [scoreNative], otherwise the local heuristic (see SleepRepository). */
    val score: Int,
    val scoreNative: Boolean,
    val segments: List<SleepSegment>,
    /** Nocturnal SpO2: 0 when the device stores none. */
    val spo2Min: Int,
    val spo2Avg: Int,
    /** Down-sampled nocturnal SpO2 readings for the bar chart. */
    val spo2Samples: List<Int>,
    val naps: List<NapInfo>,
) {
    companion object {
        fun empty(dayStartMillis: Long) = SleepDayData(
            dayStartMillis = dayStartMillis,
            hasData = false,
            sleepStartLabel = "", sleepEndLabel = "",
            sessionStartMillis = 0L, sessionEndMillis = 0L,
            totalMin = 0, deepMin = 0, lightMin = 0, remMin = 0, awakeMin = 0,
            awakenings = 0, score = 0, scoreNative = false,
            segments = emptyList(), spo2Min = 0, spo2Avg = 0,
            spo2Samples = emptyList(), naps = emptyList(),
        )
    }
}
