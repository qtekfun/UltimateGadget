package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard

import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.EcgClassification
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.EcgRecord
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.HealthSummary
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.Spo2Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeMeasurementsTest {
    private fun ecg(ts: Long) = EcgRecord("A", "w", ts, "Test", 30, 300, FloatArray(0), 70, EcgClassification.NORMAL, null)
    private fun spo2(tenths: Int) = Spo2Record("A", "w", 1L, "Test", tenths, true)
    private val pulse = HeartData(currentBpm = 63, restingBpm = 55, spo2 = 0, recent = listOf(60, 63))

    @Test
    fun nothingAtAllGivesNoTile() {
        assertNull(mergeMeasurements(null, null))
        assertNull(mergeMeasurements(null, HealthSummary(null, null)))
    }

    @Test
    fun anEcgAloneStillShowsTheTile() {
        val tile = mergeMeasurements(null, HealthSummary(ecg(500), null))!!
        assertEquals(500L, tile.ecg!!.timestampSec)
        assertEquals(EcgClassification.NORMAL, tile.ecg!!.classification)
        assertEquals(0, tile.currentBpm)
    }

    @Test
    fun theEcgIsAddedToThePulse() {
        val tile = mergeMeasurements(pulse, HealthSummary(ecg(700), null))!!
        assertEquals(63, tile.currentBpm)
        assertEquals(55, tile.restingBpm)
        assertEquals(700L, tile.ecg!!.timestampSec)
    }

    @Test
    fun theSpo2OfThePulseWatchWins() {
        val tile = mergeMeasurements(pulse.copy(spo2 = 97), HealthSummary(null, spo2(941)))!!
        assertEquals(97, tile.spo2)
    }

    @Test
    fun otherwiseTheNewestReadingIsRoundedToWholePercent() {
        assertEquals(94, mergeMeasurements(pulse, HealthSummary(null, spo2(941)))!!.spo2)
        assertEquals(95, mergeMeasurements(pulse, HealthSummary(null, spo2(945)))!!.spo2)
        assertEquals(0, mergeMeasurements(pulse, HealthSummary(null, null))!!.spo2)
    }
}
