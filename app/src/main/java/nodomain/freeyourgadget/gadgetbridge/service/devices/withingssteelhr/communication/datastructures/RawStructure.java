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

/** A structure kept as the bytes the watch sent, with typed accessors added by subclasses. */
public abstract class RawStructure extends WithingsStructure {
    private final short type;
    protected byte[] raw = new byte[0];

    protected RawStructure(final short type) {
        this.type = type;
    }

    public byte[] getRawPayload() {
        return raw;
    }

    protected long u32(final int offset) {
        return offset + 4 <= raw.length ? ByteBuffer.wrap(raw, offset, 4).getInt() & 0xffffffffL : 0;
    }

    protected int u16(final int offset) {
        return offset + 2 <= raw.length ? ByteBuffer.wrap(raw, offset, 2).getShort() & 0xffff : 0;
    }

    @Override
    public short getLength() {
        return (short) (HEADER_SIZE + raw.length);
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put(raw);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer buffer) {
        raw = new byte[buffer.remaining()];
        buffer.get(raw);
    }

    @Override
    public short getType() {
        return type;
    }
}
