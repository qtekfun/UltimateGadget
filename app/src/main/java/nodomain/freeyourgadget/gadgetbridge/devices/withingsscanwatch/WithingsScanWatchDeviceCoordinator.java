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
package nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr.WithingsSteelHRDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.WithingsScanWatchDeviceSupport;

/** Withings ScanWatch (2020): the Steel HR protocol on a different BLE UUID family. */
public class WithingsScanWatchDeviceCoordinator extends WithingsSteelHRDeviceCoordinator {
    // "ScanWatch C6": the model name plus the last bytes of the address. The Light and the 2 use
    // other UUID families and are not handled here.
    private static final Pattern NAME = Pattern.compile("(?i)^ScanWatch(?! ?(2|Light|Nova)).*");

    @Override
    protected Pattern getSupportedDeviceName() {
        return NAME;
    }

    @Override
    public boolean supports(final GBDeviceCandidate candidate) {
        final String name = candidate.getName();
        return name != null && NAME.matcher(name).matches();
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(final GBDevice device) {
        return new int[]{R.xml.devicesettings_withingsscanwatch};
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return WithingsScanWatchDeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_withings_scanwatch;
    }
}
