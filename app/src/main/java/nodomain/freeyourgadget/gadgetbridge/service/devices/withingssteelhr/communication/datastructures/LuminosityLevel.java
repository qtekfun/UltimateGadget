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

/** Screen brightness: mode 0 = automatic, 1 = manual; level 0-100. */
public class LuminosityLevel extends ByteSetting {
    public LuminosityLevel() {
        super(WithingsStructureType.LUMINOSITY_LEVEL, new byte[2]);
    }

    public static final byte MODE_AUTO = 0;
    public static final byte MODE_MANUAL = 1;

    public LuminosityLevel(final byte mode, final byte level) {
        super(WithingsStructureType.LUMINOSITY_LEVEL, new byte[]{mode, level});
    }

    public boolean isAuto() {
        return get(0) == MODE_AUTO;
    }

    public int getLevel() {
        return get(1) & 0xff;
    }
}
