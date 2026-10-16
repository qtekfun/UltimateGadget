package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.UUID;

/** The UUID families, checked against the services the two watches really expose. */
public class WithingsUUIDsTest {

    @Test
    public void steelHrKeepsItsOriginalUuids() {
        assertEquals(WithingsUUID.WITHINGS_SERVICE_UUID, WithingsUUIDs.STEEL_HR.WITHINGS_SERVICE_UUID);
        assertEquals(WithingsUUID.WITHINGS_WRITE_CHARACTERISTIC_UUID, WithingsUUIDs.STEEL_HR.WITHINGS_WRITE_CHARACTERISTIC_UUID);
        assertEquals(WithingsUUID.WITHINGS_ANCS_SERVICE_UUID, WithingsUUIDs.STEEL_HR.WITHINGS_ANCS_SERVICE_UUID);
        assertEquals(WithingsUUID.NOTIFICATION_SOURCE_CHARACTERISTIC_UUID, WithingsUUIDs.STEEL_HR.NOTIFICATION_SOURCE_CHARACTERISTIC_UUID);
        assertEquals(WithingsUUID.CONTROL_POINT_CHARACTERISTIC_UUID, WithingsUUIDs.STEEL_HR.CONTROL_POINT_CHARACTERISTIC_UUID);
        assertEquals(WithingsUUID.DATA_SOURCE_CHARACTERISTIC_UUID, WithingsUUIDs.STEEL_HR.DATA_SOURCE_CHARACTERISTIC_UUID);
        assertNull(WithingsUUIDs.STEEL_HR.WITHINGS_NOTIFY_CHARACTERISTIC_UUID);
    }

    @Test
    public void scanWatchUsesItsOwnFamilyForTheMainServiceOnly() {
        // As discovered on a ScanWatch: the main service ends in 005d, the notification service in 0037.
        assertEquals(UUID.fromString("00000020-5749-5448-005d-000000000000"), WithingsUUIDs.SCANWATCH.WITHINGS_SERVICE_UUID);
        assertEquals(UUID.fromString("00000024-5749-5448-005d-000000000000"), WithingsUUIDs.SCANWATCH.WITHINGS_WRITE_CHARACTERISTIC_UUID);
        assertEquals(UUID.fromString("00000023-5749-5448-005d-000000000000"), WithingsUUIDs.SCANWATCH.WITHINGS_NOTIFY_CHARACTERISTIC_UUID);
        assertEquals(UUID.fromString("10000057-5749-5448-0037-000000000000"), WithingsUUIDs.SCANWATCH.WITHINGS_ANCS_SERVICE_UUID);
        assertNotNull(WithingsUUIDs.SCANWATCH.CCC_DESCRIPTOR_UUID);
    }
}
