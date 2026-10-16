package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch.WithingsScanWatchDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;

/** The dissociation of the official app, as captured on a real ScanWatch. */
public class ScanWatchDissociationTest {

    @Test
    public void theTwoCommandsAreTheCapturedOnesInOrder() {
        final List<WithingsMessage> messages = ScanWatchDissociation.messages();
        assertEquals(2, messages.size());
        // 14:29:19 and 14:29:20 of the capture: 0x0123 then 0x0110, both empty.
        assertArrayEquals(new byte[]{0x01, 0x01, 0x23, 0x00, 0x00}, messages.get(0).getRawData());
        assertArrayEquals(new byte[]{0x01, 0x01, 0x10, 0x00, 0x00}, messages.get(1).getRawData());
    }

    @Test
    public void theWatchAnswersNeitherSoNothingIsWaitedFor() {
        for (final WithingsMessage message : ScanWatchDissociation.messages()) {
            assertFalse(message.needsResponse());
            assertFalse(message.needsEOT());
        }
    }

    @Test
    public void theScanWatchCoordinatorAsksToBeResetWhenRemoved() {
        assertTrue(new WithingsScanWatchDeviceCoordinator().supportsResetOnRemove(null));
    }
}
