package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health

import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgDataSample
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgSummarySample
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanWatchMeasurement
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.ScanWatchMeasurementStore
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.StoredMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's common view of ECG and SpO2 readings: brand adapters and what the home tile shows. */
class HealthMeasurementsTest {

    private fun ecg(ts: Long, address: String = "A") =
        EcgRecord(address, "w", ts, "Test", 30, 300, FloatArray(0), 70, EcgClassification.NORMAL, null)

    private fun spo2(ts: Long, tenths: Int?, conclusive: Boolean = tenths != null) =
        Spo2Record("A", "w", ts, "Test", tenths, conclusive)

    @Test
    fun theSummaryHoldsTheNewestEcgAndTheNewestSpo2ThatHasAValue() {
        val summary = HealthMeasurements.summary(
            listOf(ecg(100), ecg(300), ecg(200), spo2(250, 951), spo2(400, null, false), spo2(260, 940)),
        )
        assertEquals(300L, summary.lastEcg!!.timestampSec)
        // The newest SpO2 (400) was inconclusive, so the newest usable one is shown.
        assertEquals(260L, summary.lastSpo2!!.timestampSec)
        assertEquals(940, summary.lastSpo2!!.tenthsOfPercent)
    }

    @Test
    fun theSummaryOfNothingIsEmpty() {
        val summary = HealthMeasurements.summary(emptyList())
        assertNull(summary.lastEcg)
        assertNull(summary.lastSpo2)
    }

    @Test
    fun aWithingsEcgBecomesAnEcgRecordWithTheWatchsOwnConclusion() {
        // 0x80 + 0x077A = 1914, then +10 and -7.
        val signal = byteArrayOf(0x80.toByte(), 0x07, 0x7A, 0x0A, 0xF9.toByte())
        val m = WithingsScanWatchMeasurement(
            1_000L, 1L, StoredMeasurement.KIND_ECG, 1L, StoredMeasurement.RESULT_NORMAL_SINUS, 80, 300, 30, "130:0:0", signal,
        )
        val record = WithingsHealthSource.convert("AA", "ScanWatch", m) as EcgRecord
        assertEquals(1_000L, record.timestampSec)
        assertEquals(300, record.sampleRate)
        assertEquals(30, record.durationSeconds)
        assertEquals(80, record.averageHeartRate)
        assertEquals(EcgClassification.NORMAL, record.classification)
        assertNull(record.rawCode)
        assertEquals(listOf(1914f, 1924f, 1917f), record.samples.toList())
        assertEquals("Withings", record.source)
    }

    @Test
    fun anUnknownWithingsResultKeepsItsCode() {
        val m = WithingsScanWatchMeasurement(1L, 1L, StoredMeasurement.KIND_ECG, 1L, 0x1234L, 0, 300, 30, "", null)
        val record = WithingsHealthSource.convert("AA", "w", m) as EcgRecord
        assertEquals(EcgClassification.UNKNOWN, record.classification)
        assertEquals(0x1234L, record.rawCode)
        assertNull(record.averageHeartRate)
        assertEquals(0, record.samples.size)
    }

    @Test
    fun aWithingsSpo2ThatWasInconclusiveHasNoValue() {
        val m = WithingsScanWatchMeasurement(
            1L, 1L, StoredMeasurement.KIND_SPO2, 1L, StoredMeasurement.RESULT_SPO2_INCONCLUSIVE, -1, 25, 19, "54:1000:-1", null,
        )
        val record = WithingsHealthSource.convert("AA", "w", m) as Spo2Record
        assertFalse(record.conclusive)
        assertNull(record.tenthsOfPercent)
    }

    @Test
    fun aWithingsSpo2WithAnotherStatusKeepsItsValue() {
        val m = WithingsScanWatchMeasurement(1L, 1L, StoredMeasurement.KIND_SPO2, 1L, 0x02010000L, -1, 25, 19, "54:941:-1", null)
        val record = WithingsHealthSource.convert("AA", "w", m) as Spo2Record
        assertTrue(record.conclusive)
        assertEquals(941, record.tenthsOfPercent)
    }

    @Test
    fun otherWithingsKindsAreNotShown() {
        val m = WithingsScanWatchMeasurement(1L, 1L, 0x77, 1L, 0L, 0, 0, 0, "", null)
        assertNull(WithingsHealthSource.convert("AA", "w", m))
    }

    @Test
    fun aHuaweiEcgBecomesAnEcgRecord() {
        val summary = HuaweiEcgSummarySample(7L, 1L, 1L, 5_000_000L, 5_002_000L, "app", 66, 3L, 0L)
        val data = listOf(HuaweiEcgDataSample(7L, 0, 1.5f), HuaweiEcgDataSample(7L, 1, -2f), HuaweiEcgDataSample(7L, 2, 0.5f), HuaweiEcgDataSample(7L, 3, 0f))
        val record = HuaweiHealthSource.convert("BB", "Huawei", summary, data)
        assertEquals(5_000L, record.timestampSec)
        assertEquals(2, record.durationSeconds)
        assertEquals(2, record.sampleRate)
        assertEquals(66, record.averageHeartRate)
        assertEquals(listOf(1.5f, -2f, 0.5f, 0f), record.samples.toList())
        // No Huawei result code has been verified against a real recording, so none is given a name.
        assertEquals(EcgClassification.UNKNOWN, record.classification)
        assertEquals(3L, record.rawCode)
        assertEquals("Huawei", record.source)
    }

    @Test
    fun aHuaweiEcgWithNoDurationHasNoRate() {
        val summary = HuaweiEcgSummarySample(7L, 1L, 1L, 5_000_000L, 5_000_000L, "app", 0, 0L, 0L)
        val record = HuaweiHealthSource.convert("BB", "H", summary, emptyList())
        assertEquals(0, record.sampleRate)
        assertNull(record.averageHeartRate)
    }

    @Test
    fun onlyAMeasuredSpo2GoesToTheHealthCharts() {
        fun reading(tenths: String, result: Long) = StoredMeasureOf(tenths, result)
        assertTrue(ScanWatchMeasurementStore.isChartableSpo2(reading("54:941:-1", 0x02010000L)))
        // The inconclusive reading seen on a real watch: a placeholder 100.0 and the status code.
        assertFalse(ScanWatchMeasurementStore.isChartableSpo2(reading("54:1000:-1", StoredMeasurement.RESULT_SPO2_INCONCLUSIVE)))
        // Even with another status, exactly 100 % is how an unusable reading looked.
        assertFalse(ScanWatchMeasurementStore.isChartableSpo2(reading("54:1000:-1", 0L)))
        // Implausibly low values are not charted either.
        assertFalse(ScanWatchMeasurementStore.isChartableSpo2(reading("54:690:-1", 0L)))
        assertNotNull(reading("54:941:-1", 0L))
    }

    private fun StoredMeasureOf(readings: String, result: Long) = StoredMeasurement(
        1L, StoredMeasurement.KIND_SPO2, result, -1, 25, 19, readings, ByteArray(0), null,
    )
}
