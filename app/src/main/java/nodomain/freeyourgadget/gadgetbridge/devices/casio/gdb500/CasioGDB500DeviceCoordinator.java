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
package nodomain.freeyourgadget.gadgetbridge.devices.casio.gdb500;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.gbd200.CasioGBD200DeviceCoordinator;

/**
 * Casio G-SHOCK GD-B500 (module 3552). Reverse-engineered via a BT HCI snoop of the CASIO WATCHES
 * app (see ~/re/casio/gdb500-protocol.md): it ADVERTISES as "CASIO GD-B500" (the GAP name used for
 * discovery) but internally reports model name "CASIO GBD-200" (feature 0x23) and speaks the exact
 * same Casio 2C2D protocol as the GBD-200 — set-time, config, alarms, user-profile and step/activity
 * convoy all identical. It exposes two extra characteristics (26eb002e/002f) that are not used for
 * the base feature set (they returned ATT errors on access during pairing), so the GBD-200 support
 * stack is reused unchanged; only the (GAP) name match and label differ.
 */
public class CasioGDB500DeviceCoordinator extends CasioGBD200DeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^CASIO GD-B500.*");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_casiogdb500;
    }
}
