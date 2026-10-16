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
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.FeatureTag;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.FeatureTagsUserId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.LocalNotification;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;

/**
 * The ScanWatch health features (ECG, SpO2, irregular-rhythm detection) are switched on by sending
 * the complete list of active feature tags; the alerts the watch raises by itself are switched with
 * a second message. Both are built here, with no Android dependency, so the bytes can be tested.
 */
public final class ScanWatchHealthFeatures {
    private ScanWatchHealthFeatures() {
    }

    /** The tags that must be present for the given switches (the list is the whole state). */
    public static List<Short> tags(final boolean ecg, final boolean spo2, final boolean spo2Sleep,
                                   final boolean afibDay, final boolean afibNight) {
        final List<Short> tags = new ArrayList<>();
        if (ecg) {
            tags.add(FeatureTag.ECG_TERMS);
        }
        if (spo2 && spo2Sleep) {
            tags.add(FeatureTag.SPO2_SLEEP);
        }
        if (ecg && (afibDay || afibNight)) {
            tags.add(FeatureTag.AFIB);
        }
        if (spo2) {
            tags.add(FeatureTag.SPO2_MEASUREMENT);
        }
        if (ecg && (afibDay || afibNight)) {
            tags.add(FeatureTag.AFIB_PART_TWO);
        }
        tags.add(FeatureTag.BASELINE_A);
        tags.add(FeatureTag.BASELINE_B);
        return tags;
    }

    public static WithingsMessage featureTagsMessage(final boolean ecg, final boolean spo2, final boolean spo2Sleep,
                                                     final boolean afibDay, final boolean afibNight) {
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.SET_FEATURE_TAGS);
        message.addDataStructure(new FeatureTagsUserId(0));
        for (final short tag : tags(ecg, spo2, spo2Sleep, afibDay, afibNight)) {
            message.addDataStructure(new FeatureTag(tag));
        }
        message.addDataStructure(new EndOfTransmission());
        return message;
    }

    /** The order is the one the official app uses. */
    public static WithingsMessage localNotificationsMessage(final boolean afibDay, final boolean afibNight,
                                                            final boolean activityReminder, final boolean hrAlerts) {
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.SET_LOCAL_NOTIFICATIONS);
        message.addDataStructure(new LocalNotification(LocalNotification.AFIB_DAY, afibDay));
        message.addDataStructure(new LocalNotification(LocalNotification.ACTIVITY_REMINDER, activityReminder));
        message.addDataStructure(new LocalNotification(LocalNotification.AFIB_NIGHT, afibNight));
        message.addDataStructure(new LocalNotification(LocalNotification.HIGH_HR, hrAlerts));
        message.addDataStructure(new LocalNotification(LocalNotification.LOW_HR, hrAlerts));
        message.addDataStructure(new EndOfTransmission());
        return message;
    }
}
