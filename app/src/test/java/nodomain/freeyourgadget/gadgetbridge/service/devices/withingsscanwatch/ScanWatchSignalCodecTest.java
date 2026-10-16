package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class ScanWatchSignalCodecTest {

    private static byte[] bytes(final int... values) {
        final byte[] out = new byte[values.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    public void anEscapeIsAnAbsoluteValueAndTheRestAreDeltas() {
        // The start of a real recording: 0x077A = 1914, then +10, -7.
        assertArrayEquals(new int[]{1914, 1924, 1917}, ScanWatchSignalCodec.decode(bytes(0x80, 0x07, 0x7A, 0x0A, 0xF9)));
    }

    @Test
    public void absoluteValuesAreSigned() {
        assertArrayEquals(new int[]{-10, -9}, ScanWatchSignalCodec.decode(bytes(0x80, 0xFF, 0xF6, 0x01)));
    }

    @Test
    public void aTruncatedEscapeEndsTheSignal() {
        assertArrayEquals(new int[]{0, 5}, ScanWatchSignalCodec.decode(bytes(0x80, 0x00, 0x00, 0x05, 0x80, 0x07)));
        assertEquals(0, ScanWatchSignalCodec.decode(new byte[0]).length);
    }

    @Test
    public void aThirtySecondStripAtThreeHundredHertzHasNineThousandSamples() {
        // Encode a synthetic ECG-like wave the way the watch does and decode it back.
        final int n = 9000;
        final int[] wave = new int[n];
        for (int i = 0; i < n; i++) {
            wave[i] = 2000 + (int) (300 * Math.sin(i / 40.0)) + ((i % 220 == 0) ? 700 : 0);
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previous = 0;
        for (int i = 0; i < n; i++) {
            final int delta = wave[i] - previous;
            if (i % 50 == 0 || delta < -127 || delta > 127) {
                out.write(0x80);
                out.write((wave[i] >> 8) & 0xff);
                out.write(wave[i] & 0xff);
            } else {
                out.write(delta & 0xff);
            }
            previous = wave[i];
        }
        final int[] decoded = ScanWatchSignalCodec.decode(out.toByteArray());
        assertEquals(n, decoded.length);
        assertArrayEquals(wave, decoded);
    }
}
