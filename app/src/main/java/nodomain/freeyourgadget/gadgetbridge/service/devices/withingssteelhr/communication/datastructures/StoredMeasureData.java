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

/** One value of a stored measurement (structure 0x0117): a signed value, its kind and a power-of-ten exponent. */
public class StoredMeasureData extends WithingsStructure {
    /** The watch's classification of an ECG; the detail is in the extension (see {@link StoredMeasureDataExtend}). */
    public static final int TYPE_ECG_RESULT = 0x0082;
    public static final int TYPE_AVERAGE_HEART_RATE = 0x000B;
    public static final int TYPE_SPO2 = 54;

    private int value;
    private int type;
    private int exponent;

    public int getValue() {
        return value;
    }

    public int getMeasurementType() {
        return type;
    }

    public int getExponent() {
        return exponent;
    }

    @Override
    public short getLength() {
        return HEADER_SIZE + 8;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.putInt(value);
        buffer.putShort((short) type);
        buffer.putShort((short) exponent);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < 8) {
            return;
        }
        value = buffer.getInt();
        type = buffer.getShort() & 0xffff;
        exponent = buffer.getShort();
    }

    @Override
    public short getType() {
        return WithingsStructureType.STORED_MEASURE_DATA;
    }
}
