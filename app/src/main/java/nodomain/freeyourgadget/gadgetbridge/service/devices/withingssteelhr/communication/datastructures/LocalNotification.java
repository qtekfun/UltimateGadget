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

/** Switches one of the alerts the watch raises by itself (irregular rhythm, high/low heart rate...). */
public class LocalNotification extends WithingsStructure {
    public static final byte AFIB_DAY = 1;
    public static final byte HIGH_HR = 2;
    public static final byte LOW_HR = 3;
    public static final byte ACTIVITY_REMINDER = 4;
    public static final byte AFIB_NIGHT = 5;

    private final byte id;
    private final boolean enabled;

    public LocalNotification(final byte id, final boolean enabled) {
        this.id = id;
        this.enabled = enabled;
    }

    @Override
    public short getLength() {
        return HEADER_SIZE + 5;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.putShort((short) 0);
        buffer.put((byte) 0);
        buffer.put(id);
        buffer.put((byte) (enabled ? 1 : 0));
    }

    @Override
    public short getType() {
        return WithingsStructureType.LOCAL_NOTIFICATION;
    }
}
