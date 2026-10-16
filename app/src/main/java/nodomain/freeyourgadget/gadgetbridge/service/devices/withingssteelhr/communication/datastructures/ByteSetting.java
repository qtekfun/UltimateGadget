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

/** A watch setting that travels as a TLV holding a few raw bytes (a flag, a mode, a level...). */
public abstract class ByteSetting extends WithingsStructure {
    private final short type;
    protected byte[] value;

    protected ByteSetting(final short type, final byte[] value) {
        this.type = type;
        this.value = value;
    }

    public byte get(final int index) {
        return index < value.length ? value[index] : 0;
    }

    @Override
    public short getLength() {
        return (short) (HEADER_SIZE + value.length);
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put(value);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer rawDataBuffer) {
        final byte[] read = new byte[Math.min(rawDataBuffer.remaining(), value.length)];
        rawDataBuffer.get(read);
        System.arraycopy(read, 0, value, 0, read.length);
    }

    @Override
    public short getType() {
        return type;
    }
}
