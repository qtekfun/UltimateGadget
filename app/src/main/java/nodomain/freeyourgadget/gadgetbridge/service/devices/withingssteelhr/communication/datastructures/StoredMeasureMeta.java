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

/**
 * Header of a measurement stored in the watch (structure 0x0116): which kind it is (0x0103 is an
 * ECG) and when it was made. The time is the last unsigned 32-bit value, in seconds.
 */
public class StoredMeasureMeta extends RawStructure {
    public static final int TYPE_ECG = 0x0103;

    public StoredMeasureMeta() {
        super(WithingsStructureType.STORED_MEASURE_META);
    }

    public int getMeasurementType() {
        return u16(4);
    }

    /** Seconds since the epoch. */
    public long getTimestamp() {
        return raw.length >= 4 ? u32(raw.length - 4) : 0;
    }
}
