package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;

/** The screen list, compared with the two messages the official app sent to a real ScanWatch. */
public class ScanWatchScreensTest {
    private static final int USER = 0x01020304;

    private static byte[] hex(final String s) {
        final byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** One entry as the watch expects it: id, user, two zero words, position on the device, type 1. */
    private static String entry(final int id, final int onDevice) {
        return "0516" + "0012" + String.format("%08x", id) + String.format("%08x", USER)
                + "00000000" + "00000000" + String.format("%02x", onDevice) + "01";
    }

    @Test
    public void theListMatchesWhatTheOfficialAppSent() {
        final List<WithingsMessage> messages = ScanWatchScreens.messages(USER, true, true);
        assertEquals(2, messages.size());
        // 8 entries (176 bytes), no end marker...
        assertArrayEquals(hex("0105" + "0c" + "00b0"
                + entry(0x84, 0x06) + entry(0x82, 0x04) + entry(0x7f, 0x01) + entry(0x80, 0x02)
                + entry(0x8c, 0x0c) + entry(0x8a, 0x09) + entry(0x8b, 0x0a) + entry(0x89, 0x0b)),
                messages.get(0).getRawData());
        // ... then 4 entries and the end marker (0x5c bytes).
        assertArrayEquals(hex("0105" + "0c" + "005c"
                + entry(0xa1, 0x12) + entry(0x96, 0x10) + entry(0x97, 0x11) + entry(0x160, 0x16)
                + "01000000"),
                messages.get(1).getRawData());
    }

    @Test
    public void ecgAndSpo2AreInTheListOnlyWhenTheirFeatureIsOn() {
        assertFalse(ScanWatchScreens.screens(false, false).contains(ScanWatchScreens.ECG));
        assertFalse(ScanWatchScreens.screens(false, false).contains(ScanWatchScreens.SPO2));
        assertTrue(ScanWatchScreens.screens(true, false).contains(ScanWatchScreens.ECG));
        assertTrue(ScanWatchScreens.screens(false, true).contains(ScanWatchScreens.SPO2));
        assertEquals(10, ScanWatchScreens.screens(false, false).size());
    }

    @Test
    public void everyScreenHasAPositionAndTheDateComesFirst() {
        final List<Integer> all = ScanWatchScreens.screens(true, true);
        assertEquals(Integer.valueOf(ScanWatchScreens.DATE), all.get(0));
        for (final int screen : all) {
            assertTrue("screen " + Integer.toHexString(screen), ScanWatchScreens.idOnDevice(screen) > 0);
        }
        assertEquals(12, all.size());
        assertEquals(-1, ScanWatchScreens.idOnDevice(0x3d)); // a Steel HR screen
    }

    @Test
    public void aShortListIsOneMessageWithTheEndMarker() {
        // 10 screens: still two messages, but the end marker is only on the last.
        final List<WithingsMessage> messages = ScanWatchScreens.messages(USER, false, false);
        assertEquals(2, messages.size());
        assertEquals(Arrays.asList(0x84, 0x82, 0x7f, 0x80, 0x8c, 0x89, 0xa1, 0x96, 0x97, 0x160), ScanWatchScreens.screens(false, false));
    }
}
