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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.EndOfTransmission;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ScreenSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.ExpectedResponse;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;

/**
 * The apps shown on the ScanWatch's small display are the list of screens the phone sends. The ids
 * and the order are the ones the official app sent to a real ScanWatch; the Steel HR list is not
 * valid here (it leaves out screens such as SpO2). A screen is shown by its id on the device.
 */
public final class ScanWatchScreens {
    private static final int MAX_PER_MESSAGE = 8;
    /** The id the app gives the user everywhere else (it must not be 0). */
    public static final int USER_ID = 123456;

    public static final int DATE = 0x84;
    public static final int HEART_RATE = 0x82;
    public static final int STEPS = 0x7f;
    public static final int DISTANCE = 0x80;
    public static final int ELEVATION = 0x8c;
    public static final int ECG = 0x8a;
    public static final int SPO2 = 0x8b;
    public static final int WORKOUTS = 0x89;
    public static final int BREATHE = 0xa1;
    public static final int CLOCK = 0x96;
    public static final int SETTINGS = 0x97;
    public static final int SLEEP = 0x160;

    private ScanWatchScreens() {
    }

    /** The position of a screen in the watch (its "id on device"), or -1 when unknown. */
    public static byte idOnDevice(final int screen) {
        switch (screen) {
            case STEPS: return 0x01;
            case DISTANCE: return 0x02;
            case HEART_RATE: return 0x04;
            case DATE: return 0x06;
            case ECG: return 0x09;
            case SPO2: return 0x0a;
            case WORKOUTS: return 0x0b;
            case ELEVATION: return 0x0c;
            case CLOCK: return 0x10;
            case SETTINGS: return 0x11;
            case BREATHE: return 0x12;
            case SLEEP: return 0x16;
            default: return -1;
        }
    }

    /** The screens in the order of the list; ECG and SpO2 appear only when their feature is on. */
    public static List<Integer> screens(final boolean ecg, final boolean spo2) {
        final List<Integer> list = new ArrayList<>();
        list.add(DATE);
        list.add(HEART_RATE);
        list.add(STEPS);
        list.add(DISTANCE);
        list.add(ELEVATION);
        if (ecg) {
            list.add(ECG);
        }
        if (spo2) {
            list.add(SPO2);
        }
        list.add(WORKOUTS);
        list.add(BREATHE);
        list.add(CLOCK);
        list.add(SETTINGS);
        list.add(SLEEP);
        return list;
    }

    /**
     * The messages that set the list: entries in chunks of eight, the last one closed with the end
     * marker (the official app answers only that one). A zero user id makes the watch reboot.
     */
    public static List<WithingsMessage> messages(final int userId, final boolean ecg, final boolean spo2) {
        final List<Integer> screens = screens(ecg, spo2);
        final List<WithingsMessage> messages = new ArrayList<>();
        for (int from = 0; from < screens.size(); from += MAX_PER_MESSAGE) {
            final int to = Math.min(from + MAX_PER_MESSAGE, screens.size());
            final boolean last = to >= screens.size();
            final WithingsMessage message = last
                    ? new WithingsMessage(WithingsMessageType.SET_SCREEN_LIST)
                    : new WithingsMessage(WithingsMessageType.SET_SCREEN_LIST, ExpectedResponse.NONE);
            for (int i = from; i < to; i++) {
                final ScreenSettings entry = new ScreenSettings();
                entry.setId(screens.get(i));
                entry.setIdOnDevice(idOnDevice(screens.get(i)));
                entry.setUserId(userId);
                message.addDataStructure(entry);
            }
            if (last) {
                message.addDataStructure(new EndOfTransmission());
            }
            messages.add(message);
        }
        return messages;
    }
}
