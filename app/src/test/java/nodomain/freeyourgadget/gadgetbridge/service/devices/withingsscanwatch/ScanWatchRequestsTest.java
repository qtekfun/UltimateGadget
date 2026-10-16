package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;

/** The data requests of a sync, compared with the frames the official app sent to a real ScanWatch. */
public class ScanWatchRequestsTest {
    // 2026-10-16 14:40:11 (the "from" of a captured request), as the unsigned 32-bit value in the frame.
    private static final long FROM = 0x6AD1C65EL;

    private static byte[] hex(final String s) {
        final byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    @Test
    public void activityRequestsAskForOneKindEach() {
        for (final int kind : new int[]{6, 5, 9, 8}) {
            assertArrayEquals("kind " + kind,
                    hex("0109780012" + "0506000" + "66AD1C65E0000" + "0515000400000" + "00" + kind),
                    ScanWatchRequests.activity(FROM, kind).getRawData());
        }
    }

    @Test
    public void movementRequestsUseTheVersionAndTheKind() {
        assertArrayEquals(hex("010506000F" + "05060006" + "6AD1C65E0000" + "0961000103"),
                ScanWatchRequests.movementByVersion(FROM, (byte) 3).getRawData());
        assertArrayEquals(hex("0105060012" + "05060006" + "6AD1C65E0000" + "0515000400000004"),
                ScanWatchRequests.movementByKind(FROM, 4).getRawData());
    }

    @Test
    public void heartRateRequestUsesTheDefaultVersion() {
        assertArrayEquals(hex("010928000F" + "05060006" + "6AD1C65E0000" + "0961000101"),
                ScanWatchRequests.heartRate(FROM).getRawData());
    }

    @Test
    public void storedSignalRequestCarriesOnlyTheKind() {
        assertArrayEquals(hex("010147000C" + "0143000800010000" + "00000000"),
                ScanWatchRequests.storedSignal(1).getRawData());
    }

    @Test
    public void aSyncAsksForEveryKindOnce() {
        final List<WithingsMessage> sync = ScanWatchRequests.sync(FROM);
        // 4 activity kinds, 2 movement, 1 heart rate, 3 stored signals.
        assertEquals(10, sync.size());
    }
}
