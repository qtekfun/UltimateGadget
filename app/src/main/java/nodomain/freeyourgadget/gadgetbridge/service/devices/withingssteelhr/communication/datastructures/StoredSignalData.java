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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures;

import java.util.Arrays;

/** A chunk of a stored signal (structure 0x0144): one length byte followed by that many bytes. */
public class StoredSignalData extends RawStructure {
    public StoredSignalData() {
        super(WithingsStructureType.STORED_SIGNAL_DATA);
    }

    public byte[] getSamples() {
        if (raw.length == 0) {
            return raw;
        }
        final int length = Math.min(raw[0] & 0xff, raw.length - 1);
        return Arrays.copyOfRange(raw, 1, 1 + length);
    }
}
