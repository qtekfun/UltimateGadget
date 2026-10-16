package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Checks the feature-tag messages against frames the official app sent to a real ScanWatch. */
public class ScanWatchHealthFeaturesTest {

    private static byte[] hex(final String s) {
        final byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    @Test
    public void nothingActiveSendsOnlyTheBaselineTags() {
        // Captured: 0987 with a zero user id, tags 0035 and 0058, and the end marker (0x28 bytes).
        final byte[] frame = ScanWatchHealthFeatures.featureTagsMessage(false, false, false, false, false).getRawData();
        assertArrayEquals(hex("0109870028"
                + "0145000400000000"
                + "099C000A00350000000000000000"
                + "099C000A00580000000000000000"
                + "01000000"), frame);
    }

    @Test
    public void tagsFollowTheSwitches() {
        assertEquals(Arrays.asList((short) 0x35, (short) 0x58), tags(false, false, false, false, false));
        assertEquals(Arrays.asList((short) 0x04, (short) 0x35, (short) 0x58), tags(true, false, false, false, false));
        assertEquals(Arrays.asList((short) 0x0F, (short) 0x35, (short) 0x58), tags(false, true, false, false, false));
        assertEquals(Arrays.asList((short) 0x05, (short) 0x0F, (short) 0x35, (short) 0x58), tags(false, true, true, false, false));
        // Irregular-rhythm detection needs the ECG feature.
        assertEquals(Arrays.asList((short) 0x35, (short) 0x58), tags(false, false, false, true, true));
        assertEquals(Arrays.asList((short) 0x04, (short) 0x0E, (short) 0x11, (short) 0x35, (short) 0x58), tags(true, false, false, true, false));
    }

    @Test
    public void sleepModeNeedsSpo2() {
        assertFalse(tags(false, false, true, false, false).contains((short) 0x05));
        assertTrue(tags(false, true, true, false, false).contains((short) 0x05));
    }

    @Test
    public void localNotificationsKeepTheOfficialOrder() {
        final byte[] frame = ScanWatchHealthFeatures.localNotificationsMessage(true, false, false, false).getRawData();
        // 0990, then five 9-byte entries (ids 1, 4, 5, 2, 3) and the end marker.
        assertArrayEquals(hex("0109900031"
                + "09A8000500000001" + "01"
                + "09A8000500000004" + "00"
                + "09A8000500000005" + "00"
                + "09A8000500000002" + "00"
                + "09A8000500000003" + "00"
                + "01000000"), frame);
    }

    private static List<Short> tags(final boolean ecg, final boolean spo2, final boolean sleep, final boolean day, final boolean night) {
        return ScanWatchHealthFeatures.tags(ecg, spo2, sleep, day, night);
    }
}
