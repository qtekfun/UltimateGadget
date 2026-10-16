package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The wire bytes of the ScanWatch settings (checked against what the watch itself answers). */
public class ScanWatchSettingStructuresTest {

    private static byte[] hex(final String s) {
        final byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    @Test
    public void quickLookCarriesOneByteForOnAndOff() {
        assertArrayEquals(hex("097A000101"), new GlanceStatus(true).getRawData());
        assertArrayEquals(hex("097A000100"), new GlanceStatus(false).getRawData());
    }

    @Test
    public void moveHandsCarriesOneByte() {
        assertArrayEquals(hex("09BB000101"), new TrackerMoveHands(true).getRawData());
        assertArrayEquals(hex("09BB000100"), new TrackerMoveHands(false).getRawData());
    }

    @Test
    public void wearPositionAndShortcutCarryOneByte() {
        assertArrayEquals(hex("012F000103"), new TrackerWearPos(TrackerWearPos.POS_RIGHT_WRIST).getRawData());
        assertArrayEquals(hex("012F000102"), new TrackerWearPos(TrackerWearPos.POS_LEFT_WRIST).getRawData());
        assertArrayEquals(hex("09A100010B"), new ShortcutAction((byte) 11).getRawData());
    }

    @Test
    public void brightnessCarriesModeAndLevel() {
        // The watch answers its own state as 0937 0002 00 64: automatic, level 100.
        assertArrayEquals(hex("0937000200" + "64"), new LuminosityLevel(LuminosityLevel.MODE_AUTO, (byte) 100).getRawData());
        assertArrayEquals(hex("0937000201" + "28"), new LuminosityLevel(LuminosityLevel.MODE_MANUAL, (byte) 40).getRawData());
    }

    @Test
    public void answersFromTheWatchAreParsed() {
        final List<WithingsStructure> parsed = new DataStructureFactory().createStructuresFromRawData(hex("09370002006409" + "7A000101"));
        assertEquals(2, parsed.size());
        final LuminosityLevel level = (LuminosityLevel) parsed.get(0);
        assertTrue(level.isAuto());
        assertEquals(100, level.getLevel());
        assertTrue(((GlanceStatus) parsed.get(1)).isEnabled());
        final List<WithingsStructure> off = new DataStructureFactory().createStructuresFromRawData(hex("09BB000100"));
        assertFalse(((TrackerMoveHands) off.get(0)).isEnabled());
    }
}
