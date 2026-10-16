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
package nodomain.freeyourgadget.gadgetbridge.devices;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * A coordinator whose watch can be told to forget its pairing when it is removed from the app, so it
 * can be paired with another phone without resetting it by hand. The device support's
 * {@code onFactoryReset()} does the work.
 */
public interface ResettableOnRemove {
    boolean supportsResetOnRemove(GBDevice device);
}
