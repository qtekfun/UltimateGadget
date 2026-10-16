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

import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.ExpectedResponse;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;

/**
 * What the official app writes to a ScanWatch when the product is dissociated, as captured on a real
 * one: two empty commands, 0x0123 and then 0x0110, with no answer (the watch resets itself and waits
 * to be paired again).
 */
public final class ScanWatchDissociation {
    public static final short DISSOCIATE = 0x0123;
    public static final short FORGET = 0x0110;

    private ScanWatchDissociation() {
    }

    public static List<WithingsMessage> messages() {
        return Arrays.asList(
                new WithingsMessage(DISSOCIATE, ExpectedResponse.NONE),
                new WithingsMessage(FORGET, ExpectedResponse.NONE));
    }
}
