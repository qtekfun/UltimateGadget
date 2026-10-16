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

/** Move the hands out of the way (to 10:10) when the screen turns on. */
public class TrackerMoveHands extends ByteSetting {
    public TrackerMoveHands() {
        super(WithingsStructureType.TRACKER_MOVE_HANDS, new byte[1]);
    }

    public TrackerMoveHands(final boolean enabled) {
        super(WithingsStructureType.TRACKER_MOVE_HANDS, new byte[]{(byte) (enabled ? 1 : 0)});
    }

    public boolean isEnabled() {
        return get(0) != 0;
    }
}
