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

import java.nio.ByteBuffer;

/**
 * Which kind of stored data a sample request asks for (TLV 0x0515, a 32-bit value). The ScanWatch
 * answers an activity request without it with an empty transfer; the official app asks once per
 * kind (values seen in a capture: 4, 5, 6, 8 and 9).
 */
public class VasistasType extends WithingsStructure {

    private final int kind;

    public VasistasType(final int kind) {
        this.kind = kind;
    }

    @Override
    public short getLength() {
        return 8;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.putInt(kind);
    }

    @Override
    public short getType() {
        return 1301;
    }
}
