package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.DataStructureFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;

/** The answer to "give me the stored ECG" of a real ScanWatch, as the structures the app receives. */
public class StoredMeasureCollectorTest {
    // First frame of the answer (header, values, extension and signal description of a real 30 s ECG
    // whose rhythm the watch classified as normal). The waveform chunks follow in other frames.
    private static final String HEAD = "01014700D001160017000000000103000000000000000000000000006AD23C1C011700080000000000820000014900080000008201020401014A000400000B7D09B900060001FFFFFFFF014300080001012C03020E010146000C0000001E00004650000000000147000CFFFFE0000000";

    private static byte[] hex(final String s) {
        final byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static List<WithingsStructure> structures(final String frame) {
        final byte[] bytes = hex(frame);
        final byte[] payload = Arrays.copyOfRange(bytes, 5, bytes.length);
        return new DataStructureFactory().createStructuresFromRawData(payload);
    }

    @Test
    public void theHeaderGivesTimeKindRhythmAndSignalDescription() {
        final StoredMeasureCollector collector = new StoredMeasureCollector();
        collector.accept(structures(HEAD));
        final List<StoredMeasurement> measurements = collector.finish();
        assertEquals(1, measurements.size());
        final StoredMeasurement m = measurements.get(0);
        assertEquals(0x6AD23C1CL, m.timestamp);
        assertTrue(m.isEcg());
        assertTrue(m.isNormalRhythm());
        assertEquals(300, m.sampleRate);
        assertEquals(30, m.durationSeconds);
        assertEquals(-1, m.heartRate);
        // The watch wants this echoed to delete the measurement.
        assertArrayEquals(hex("0001012c03020e01"), m.deleteKey);
        assertFalse(m.hasSignal());
    }

    @Test
    public void signalChunksAreJoinedInOrder() {
        final StoredMeasureCollector collector = new StoredMeasureCollector();
        collector.accept(structures(HEAD));
        // 0144 with a length byte of 3 followed by three bytes, twice.
        collector.accept(structures("0101470008" + "01440004" + "03" + "80077A"));
        collector.accept(structures("0101470008" + "01440004" + "03" + "0AF9FC"));
        final StoredMeasurement m = collector.finish().get(0);
        assertArrayEquals(hex("80077A0AF9FC"), m.signal);
        assertArrayEquals(new int[]{1914, 1924, 1917, 1913}, ScanWatchSignalCodec.decode(m.signal));
    }

    @Test
    public void aNewHeaderClosesThePreviousMeasurement() {
        final StoredMeasureCollector collector = new StoredMeasureCollector();
        collector.accept(structures(HEAD));
        collector.accept(structures(HEAD));
        assertEquals(2, collector.finish().size());
        assertEquals(0, collector.finish().size());
    }

    @Test
    public void structuresBeforeAnyHeaderAreIgnored() {
        final StoredMeasureCollector collector = new StoredMeasureCollector();
        collector.accept(structures("0101470008" + "01440004" + "03" + "80077A"));
        assertEquals(0, collector.finish().size());
    }
}
