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
 * Extension of a stored value (structure 0x0149): the kind it refers to and an extra 32-bit code. For
 * an ECG result, {@code 0x01020401} is a normal sinus rhythm.
 */
public class StoredMeasureDataExtend extends RawStructure {
    public StoredMeasureDataExtend() {
        super(WithingsStructureType.STORED_MEASURE_DATA_EXTEND);
    }

    public int getMeasurementType() {
        return (int) u32(0);
    }

    public long getExtraData() {
        return u32(4);
    }
}
