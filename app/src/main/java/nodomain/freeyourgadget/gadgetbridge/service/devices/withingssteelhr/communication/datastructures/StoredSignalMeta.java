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
 * Describes a stored signal (an ECG strip, a PPG trace...): its kind, sampling frequency, sample
 * format and resolution. In a request only {@link #type} is set and the rest is zero.
 */
public class StoredSignalMeta extends WithingsStructure {
    private int type;
    private int samplingFreq;
    private int format;
    private int size;
    private int resolution;
    private int channel;
    private byte[] rawPayload;

    public StoredSignalMeta() {
    }

    public StoredSignalMeta(final int type) {
        this.type = type;
    }

    /** The exact bytes the watch sent; echoed back to delete the measurement it belongs to. */
    public byte[] getRawPayload() {
        return rawPayload;
    }

    public static StoredSignalMeta fromRaw(final byte[] payload) {
        final StoredSignalMeta meta = new StoredSignalMeta();
        meta.fillFromRawData(payload);
        return meta;
    }

    public int getSignalType() {
        return type;
    }

    public int getSamplingFreq() {
        return samplingFreq;
    }

    public int getFormat() {
        return format;
    }

    public int getSampleSize() {
        return size;
    }

    public int getResolution() {
        return resolution;
    }

    public int getChannel() {
        return channel;
    }

    @Override
    public short getLength() {
        return HEADER_SIZE + 8;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        if (rawPayload != null) {
            buffer.put(rawPayload);
            return;
        }
        buffer.putShort((short) type);
        buffer.putShort((short) samplingFreq);
        buffer.put((byte) format);
        buffer.put((byte) size);
        buffer.put((byte) resolution);
        buffer.put((byte) channel);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < 8) {
            return;
        }
        rawPayload = new byte[8];
        buffer.duplicate().get(rawPayload);
        type = buffer.getShort() & 0xffff;
        samplingFreq = buffer.getShort() & 0xffff;
        format = buffer.get() & 0xff;
        size = buffer.get() & 0xff;
        resolution = buffer.get() & 0xff;
        channel = buffer.get() & 0xff;
    }

    @Override
    public short getType() {
        return WithingsStructureType.STORED_SIGNAL_META;
    }
}
