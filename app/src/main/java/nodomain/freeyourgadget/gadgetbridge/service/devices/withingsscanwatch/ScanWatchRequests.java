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

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.GetActivitySamples;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TypeVersion;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.VasistasType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.ExpectedResponse;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;

/**
 * The data requests of a ScanWatch sync, in the order and shape the official app uses. The watch
 * answers an activity request without the kind (or an unknown one) with an empty transfer.
 */
public final class ScanWatchRequests {
    /** Activity kinds asked with GET_ACTIVITY_SAMPLES: 6 and 5 hold steps/movement, 9 and 8 sleep and SpO2. */
    static final int[] ACTIVITY_KINDS = {6, 5, 9, 8};
    /** Signal kinds asked with GET_STORED_MEASURE_SIGNAL. */
    static final int[] SIGNAL_KINDS = {1, 4, 5};

    private ScanWatchRequests() {
    }

    public static WithingsMessage activity(final long fromSeconds, final int kind) {
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_ACTIVITY_SAMPLES, ExpectedResponse.EOT);
        message.addDataStructure(new GetActivitySamples(fromSeconds, (short) 0));
        message.addDataStructure(new VasistasType(kind));
        return message;
    }

    /** Per-minute movement, either by version (3) or by kind (4). */
    public static WithingsMessage movementByVersion(final long fromSeconds, final byte version) {
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_MOVEMENT_SAMPLES, ExpectedResponse.EOT);
        message.addDataStructure(new GetActivitySamples(fromSeconds, (short) 0));
        message.addDataStructure(new TypeVersion(version));
        return message;
    }

    public static WithingsMessage movementByKind(final long fromSeconds, final int kind) {
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_MOVEMENT_SAMPLES, ExpectedResponse.EOT);
        message.addDataStructure(new GetActivitySamples(fromSeconds, (short) 0));
        message.addDataStructure(new VasistasType(kind));
        return message;
    }

    public static WithingsMessage heartRate(final long fromSeconds) {
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_HEARTRATE_SAMPLES, ExpectedResponse.EOT);
        message.addDataStructure(new GetActivitySamples(fromSeconds, (short) 0));
        message.addDataStructure(new TypeVersion());
        return message;
    }

    public static WithingsMessage storedSignal(final int kind) {
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_STORED_MEASURE_SIGNAL, ExpectedResponse.EOT);
        message.addDataStructure(new StoredSignalMeta(kind));
        return message;
    }

    /** Every request of a sync since {@code fromSeconds}. */
    public static List<WithingsMessage> sync(final long fromSeconds) {
        final List<WithingsMessage> all = new ArrayList<>();
        for (final int kind : ACTIVITY_KINDS) {
            all.add(activity(fromSeconds, kind));
        }
        all.add(movementByVersion(fromSeconds, (byte) 3));
        all.add(movementByKind(fromSeconds, 4));
        all.add(heartRate(fromSeconds));
        for (final int kind : SIGNAL_KINDS) {
            all.add(storedSignal(kind));
        }
        return all;
    }
}
