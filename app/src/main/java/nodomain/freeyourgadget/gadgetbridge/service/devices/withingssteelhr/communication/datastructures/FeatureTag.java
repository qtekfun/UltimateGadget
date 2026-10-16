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
 * One health feature switched on in the watch: a 16-bit tag id and the period it is valid for
 * (both 0 = always). Which tags are present is the whole configuration; the watch drops the rest.
 */
public class FeatureTag extends WithingsStructure {
    /** The user accepted the ECG terms: the ECG app becomes available. */
    public static final short ECG_TERMS = 0x0004;
    /** SpO2 is also monitored during sleep. */
    public static final short SPO2_SLEEP = 0x0005;
    /** Atrial fibrillation detection (part one). */
    public static final short AFIB = 0x000E;
    /** On-demand SpO2 measurement. */
    public static final short SPO2_MEASUREMENT = 0x000F;
    /** Atrial fibrillation detection (part two, always sent with {@link #AFIB}). */
    public static final short AFIB_PART_TWO = 0x0011;
    /** Present in every list the official app sends. */
    public static final short BASELINE_A = 0x0035;
    public static final short BASELINE_B = 0x0058;

    private final short tag;
    private final int start;
    private final int end;

    public FeatureTag(final short tag) {
        this(tag, 0, 0);
    }

    public FeatureTag(final short tag, final int start, final int end) {
        this.tag = tag;
        this.start = start;
        this.end = end;
    }

    @Override
    public short getLength() {
        return HEADER_SIZE + 10;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.putShort(tag);
        buffer.putInt(start);
        buffer.putInt(end);
    }

    @Override
    public short getType() {
        return WithingsStructureType.FEATURE_TAG;
    }
}
