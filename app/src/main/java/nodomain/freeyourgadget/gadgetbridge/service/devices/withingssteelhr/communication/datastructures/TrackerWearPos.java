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

/** Which wrist the watch is worn on. */
public class TrackerWearPos extends ByteSetting {
    public TrackerWearPos() {
        super(WithingsStructureType.TRACKER_WEAR_POS, new byte[1]);
    }

    public static final byte POS_LEFT_WRIST = 2;
    public static final byte POS_RIGHT_WRIST = 3;

    public TrackerWearPos(final byte position) {
        super(WithingsStructureType.TRACKER_WEAR_POS, new byte[]{position});
    }

    public byte getPosition() {
        return get(0);
    }

    public boolean isLeftWrist() {
        return get(0) != POS_RIGHT_WRIST;
    }
}
