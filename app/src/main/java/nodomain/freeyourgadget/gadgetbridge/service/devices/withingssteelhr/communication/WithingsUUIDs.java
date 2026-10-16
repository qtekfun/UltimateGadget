/*  Copyright (C) 2026 UltimateGadget contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication;

import java.util.UUID;

/**
 * The BLE UUIDs of a Withings device family. The Withings protocol is the same on every model;
 * only the last-but-one group of the UUIDs differs ("0037" on the Steel HR, "005d" on the
 * ScanWatch). The ANCS-like notification service the watch reads from the phone keeps "0037" on
 * the ScanWatch, as observed in a capture of the official app with a ScanWatch.
 */
public final class WithingsUUIDs {

    public final UUID WITHINGS_SERVICE_UUID;
    public final UUID WITHINGS_WRITE_CHARACTERISTIC_UUID;
    public final UUID WITHINGS_APP_CHARACTERISTIC_UUID;
    public final UUID WITHINGS_APP_CHARACTERISTIC2_UUID;
    public final UUID CCC_DESCRIPTOR_UUID = WithingsUUID.CCC_DESCRIPTOR_UUID;
    public final UUID WITHINGS_ANCS_SERVICE_UUID;
    public final UUID NOTIFICATION_SOURCE_CHARACTERISTIC_UUID;
    public final UUID CONTROL_POINT_CHARACTERISTIC_UUID;
    public final UUID DATA_SOURCE_CHARACTERISTIC_UUID;

    private WithingsUUIDs(final String protocolSuffix, final String ancsSuffix) {
        WITHINGS_SERVICE_UUID = uuid("00000020", protocolSuffix);
        WITHINGS_WRITE_CHARACTERISTIC_UUID = uuid("00000024", protocolSuffix);
        WITHINGS_APP_CHARACTERISTIC_UUID = uuid("10000059", protocolSuffix);
        WITHINGS_APP_CHARACTERISTIC2_UUID = uuid("10000028", protocolSuffix);
        WITHINGS_ANCS_SERVICE_UUID = uuid("10000057", ancsSuffix);
        NOTIFICATION_SOURCE_CHARACTERISTIC_UUID = uuid("10000059", ancsSuffix);
        CONTROL_POINT_CHARACTERISTIC_UUID = uuid("10000058", ancsSuffix);
        DATA_SOURCE_CHARACTERISTIC_UUID = uuid("1000005a", ancsSuffix);
    }

    private static UUID uuid(final String first, final String suffix) {
        return UUID.fromString(first + "-5749-5448-" + suffix + "-000000000000");
    }

    public static final WithingsUUIDs STEEL_HR = new WithingsUUIDs("0037", "0037");
    public static final WithingsUUIDs SCANWATCH = new WithingsUUIDs("005d", "0037");
}
