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
package nodomain.freeyourgadget.gadgetbridge.devices.casio.gbd800;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.gbd200.CasioGBD200DeviceCoordinator;

/**
 * Casio G-SHOCK GBD-800 (G-SQUAD, module 3464). Reverse-engineered via a BT HCI snoop of the
 * CASIO WATCHES app (see ~/re/casio/gbd800-protocol.md): the watch advertises as "CASIO GBD-800"
 * and speaks the exact same Casio 2C2D GATT protocol (chars 26eb002c/002d request+all-features,
 * 26eb0023/0024 data-request+convoy) as the GBD-200, including step/activity convoy transfer,
 * set-time (feature 0x09), config (0x1d/0x1e), alarms (0x29) and the user-profile write (0x2d).
 * So it reuses the GBD-200 support stack unchanged; only the name match and label differ.
 */
public class CasioGBD800DeviceCoordinator extends CasioGBD200DeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^CASIO GBD-800.*");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_casiogbd800;
    }
}
