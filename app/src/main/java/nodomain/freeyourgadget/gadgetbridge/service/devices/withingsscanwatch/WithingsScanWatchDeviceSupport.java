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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsSteelHRDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.GetActivitySamples;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TypeVersion;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.VasistasType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.ExpectedResponse;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.WithingsUUIDs;

/**
 * Withings ScanWatch (2020). It speaks the same protocol as the Steel HR, so everything is
 * inherited; only the BLE UUID family differs.
 */
public class WithingsScanWatchDeviceSupport extends WithingsSteelHRDeviceSupport {
    @Override
    protected WithingsUUIDs getWithingsUUIDs() {
        return WithingsUUIDs.SCANWATCH;
    }

    /** Samples are keyed by time, so re-reading a few hours is harmless and survives lost frames. */
    @Override
    protected long syncStartSeconds(final long lastSyncMillis) {
        return (lastSyncMillis - 6L * 60 * 60 * 1000) / 1000;
    }

    @Override
    protected boolean advanceSyncMarkerWithoutData() {
        return false;
    }

    /**
     * The ScanWatch only returns stored data when each kind is requested explicitly, the way the
     * official app does (kinds 6, 5, 9 and 8 for activity, 3 and 4 for movement).
     */
    @Override
    protected void addSampleRequests(final long fromSeconds) {
        for (final int kind : new int[]{6, 5, 9, 8}) {
            final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_ACTIVITY_SAMPLES, ExpectedResponse.EOT);
            message.addDataStructure(new GetActivitySamples(fromSeconds, (short) 0));
            message.addDataStructure(new VasistasType(kind));
            addSimpleConversationToQueue(message, getActivitySampleHandler());
        }
        WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_MOVEMENT_SAMPLES, ExpectedResponse.EOT);
        message.addDataStructure(new GetActivitySamples(fromSeconds, (short) 0));
        message.addDataStructure(new TypeVersion((byte) 3));
        addSimpleConversationToQueue(message, getActivitySampleHandler());
        message = new WithingsMessage(WithingsMessageType.GET_MOVEMENT_SAMPLES, ExpectedResponse.EOT);
        message.addDataStructure(new GetActivitySamples(fromSeconds, (short) 0));
        message.addDataStructure(new VasistasType(4));
        addSimpleConversationToQueue(message, getActivitySampleHandler());
        message = new WithingsMessage(WithingsMessageType.GET_HEARTRATE_SAMPLES, ExpectedResponse.EOT);
        message.addDataStructure(new GetActivitySamples(fromSeconds, (short) 0));
        message.addDataStructure(new TypeVersion());
        addSimpleConversationToQueue(message, getActivitySampleHandler());
    }
}
