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

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsSteelHRDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.AbstractResponseHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.FeatureTagsUserId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.GlanceStatus;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.LuminosityLevel;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ShortcutAction;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TrackerMoveHands;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TrackerWearPos;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.GetActivitySamples;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TypeVersion;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.VasistasType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.ExpectedResponse;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.WithingsUUIDs;

/**
 * Withings ScanWatch (2020). It speaks the same protocol as the Steel HR, so everything is
 * inherited; only the BLE UUID family differs.
 */
public class WithingsScanWatchDeviceSupport extends WithingsSteelHRDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(WithingsScanWatchDeviceSupport.class);

    public static final String PREF_WEAR_LOCATION = "withings_scanwatch_wear_location";
    public static final String PREF_QUICKLOOK = "withings_scanwatch_quicklook";
    public static final String PREF_AUTO_BRIGHTNESS = "withings_scanwatch_auto_brightness";
    public static final String PREF_BRIGHTNESS_LEVEL = "withings_scanwatch_brightness_level";
    public static final String PREF_MOVE_HANDS = "withings_scanwatch_move_hands";
    public static final String PREF_SHORTCUT_ACTION = "withings_scanwatch_shortcut_action";
    public static final String PREF_ECG = "withings_scanwatch_ecg";
    public static final String PREF_SPO2 = "withings_scanwatch_spo2";
    public static final String PREF_SPO2_SLEEP = "withings_scanwatch_spo2_sleep";
    public static final String PREF_AFIB_DAY = "withings_scanwatch_afib_day";
    public static final String PREF_AFIB_NIGHT = "withings_scanwatch_afib_night";

    /**
     * Makes the watch forget its pairing, as the official app's "dissociate" does, so it can be paired
     * with another phone. The commands are sent at once, in order and with no answer expected, and
     * the link is closed a moment later.
     */
    @Override
    public void onFactoryReset() {
        for (final WithingsMessage message : ScanWatchDissociation.messages()) {
            sendToDevice(message);
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::disconnect, 2000);
    }

    private final StoredMeasureCollector measureCollector = new StoredMeasureCollector();
    private int deleteRequestId;

    public static final String PREF_DELETE_AFTER_SAVE = "withings_scanwatch_delete_after_save";

    private SharedPreferences prefs() {
        return GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
    }

    @Override
    public void finishSync() {
        super.finishSync();
        saveMeasurements();
    }

    /**
     * Saves what the watch sent for its stored measurements. Only when the optional setting is on,
     * and only for measurements that were read back from the database, are they deleted from the
     * watch (the official app always does; here a lost database must not mean a lost ECG).
     */
    private void saveMeasurements() {
        final List<StoredMeasurement> received = measureCollector.finish();
        if (received.isEmpty()) {
            return;
        }
        final List<StoredMeasurement> verified;
        try (DBHandler db = GBApplication.acquireDB()) {
            verified = ScanWatchMeasurementStore.save(db, getDevice(), received);
        } catch (final Exception e) {
            LOG.warn("Could not save the stored measurements", e);
            return;
        }
        LOG.info("Saved {} of {} stored measurement(s) from the watch", verified.size(), received.size());
        if (!prefs().getBoolean(PREF_DELETE_AFTER_SAVE, false)) {
            return;
        }
        for (final StoredMeasurement m : verified) {
            if (m.deleteKey == null) {
                continue;
            }
            final WithingsMessage delete = new WithingsMessage(WithingsMessageType.DELETE_STORED_MEASURE_SIGNAL, ExpectedResponse.SIMPLE);
            delete.addDataStructure(new FeatureTagsUserId(deleteRequestId++));
            delete.addDataStructure(StoredSignalMeta.fromRaw(m.deleteKey));
            addSimpleConversationToQueue(delete);
        }
        sendQueue();
    }

    /** Reads one structure out of a response and hands it to {@code consumer} (null-safe). */
    private <T extends WithingsStructure> AbstractResponseHandler reader(final Class<T> type, final Consumer<T> consumer) {
        return new AbstractResponseHandler(this) {
            @Override
            public void handleResponse(final Message response) {
                final T structure = response.getStructureByType(type);
                if (structure != null) {
                    consumer.accept(structure);
                }
            }
        };
    }

    /** Reads the settings the watch holds, so the app shows the real state instead of defaults. */
    @Override
    protected void addExtraSyncCommands() {
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_TRACKER_WEAR_POS),
                reader(TrackerWearPos.class, w -> prefs().edit().putString(PREF_WEAR_LOCATION, w.isLeftWrist() ? "left" : "right").apply()));
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GLANCE_GET),
                reader(GlanceStatus.class, g -> prefs().edit().putBoolean(PREF_QUICKLOOK, g.isEnabled()).apply()));
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_LUMINOSITY_LEVEL),
                reader(LuminosityLevel.class, l -> prefs().edit()
                        .putBoolean(PREF_AUTO_BRIGHTNESS, l.isAuto())
                        .putInt(PREF_BRIGHTNESS_LEVEL, l.getLevel()).apply()));
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_TRACKER_MOVE_HANDS),
                reader(TrackerMoveHands.class, m -> prefs().edit().putBoolean(PREF_MOVE_HANDS, m.isEnabled()).apply()));
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_SHORTCUT),
                reader(ShortcutAction.class, a -> prefs().edit().putString(PREF_SHORTCUT_ACTION, String.valueOf(a.getAction())).apply()));
    }

    @Override
    protected boolean handleExtraConfiguration(final String config) {
        final SharedPreferences p = prefs();
        final WithingsMessage message;
        switch (config) {
            case PREF_WEAR_LOCATION:
                message = new WithingsMessage(WithingsMessageType.SET_TRACKER_WEAR_POS, new TrackerWearPos(
                        "right".equals(p.getString(PREF_WEAR_LOCATION, "left")) ? TrackerWearPos.POS_RIGHT_WRIST : TrackerWearPos.POS_LEFT_WRIST));
                break;
            case PREF_QUICKLOOK:
                message = new WithingsMessage(WithingsMessageType.GLANCE_SET, new GlanceStatus(p.getBoolean(PREF_QUICKLOOK, false)));
                break;
            case PREF_AUTO_BRIGHTNESS:
            case PREF_BRIGHTNESS_LEVEL: {
                final boolean auto = p.getBoolean(PREF_AUTO_BRIGHTNESS, true);
                final int level = Math.max(0, Math.min(100, p.getInt(PREF_BRIGHTNESS_LEVEL, 100)));
                message = new WithingsMessage(WithingsMessageType.SET_LUMINOSITY_LEVEL, new LuminosityLevel(
                        auto ? LuminosityLevel.MODE_AUTO : LuminosityLevel.MODE_MANUAL, (byte) level));
                break;
            }
            case PREF_MOVE_HANDS:
                message = new WithingsMessage(WithingsMessageType.SET_TRACKER_MOVE_HANDS, new TrackerMoveHands(p.getBoolean(PREF_MOVE_HANDS, false)));
                break;
            case PREF_SHORTCUT_ACTION: {
                final byte action;
                try {
                    action = Byte.parseByte(p.getString(PREF_SHORTCUT_ACTION, "0"));
                } catch (final NumberFormatException e) {
                    LOG.warn("Invalid shortcut action", e);
                    return true;
                }
                message = new WithingsMessage(WithingsMessageType.SET_SHORTCUT, new ShortcutAction(action));
                break;
            }
            case PREF_ECG:
            case PREF_SPO2:
            case PREF_SPO2_SLEEP:
            case PREF_AFIB_DAY:
            case PREF_AFIB_NIGHT:
                sendHealthFeatures(p);
                return true;
            default:
                return false;
        }
        conversationSend(message);
        return true;
    }

    /** Sends the whole health configuration: the tag list, its two commits and the alerts. */
    private void sendHealthFeatures(final SharedPreferences p) {
        final boolean ecg = p.getBoolean(PREF_ECG, false);
        final boolean spo2 = p.getBoolean(PREF_SPO2, false);
        final boolean spo2Sleep = p.getBoolean(PREF_SPO2_SLEEP, false);
        final boolean afibDay = p.getBoolean(PREF_AFIB_DAY, false);
        final boolean afibNight = p.getBoolean(PREF_AFIB_NIGHT, false);
        addSimpleConversationToQueue(ScanWatchHealthFeatures.featureTagsMessage(ecg, spo2, spo2Sleep, afibDay, afibNight));
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.COMMIT_FEATURE_TAGS));
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.COMMIT_NOTIFICATION_CONFIG));
        addSimpleConversationToQueue(ScanWatchHealthFeatures.localNotificationsMessage(ecg && afibDay, ecg && afibNight, false, false));
        // ECG and SpO2 show up on the watch only when they are in the screen list.
        addScreenListCommands();
        sendQueue();
    }

    /** The ScanWatch screens, not the Steel HR ones (see {@link ScanWatchScreens}). */
    @Override
    protected void addScreenListCommands() {
        final SharedPreferences p = prefs();
        // The official app writes the messages one after the other, each starting a fresh write; the
        // watch reboots if one write mixes the end of a message with the start of the next. They go
        // straight out in order (a queued one would be lost when a sync clears the queue).
        for (final WithingsMessage message : ScanWatchScreens.messages(
                ScanWatchScreens.USER_ID, p.getBoolean(PREF_ECG, false), p.getBoolean(PREF_SPO2, false))) {
            sendToDevice(message);
        }
    }

    private void conversationSend(final WithingsMessage message) {
        addSimpleConversationToQueue(message);
        sendQueue();
    }

    @Override
    protected WithingsUUIDs getWithingsUUIDs() {
        return WithingsUUIDs.SCANWATCH;
    }

    /** Samples are keyed by time, so re-reading a few hours is harmless and survives lost frames. */
    @Override
    protected long syncStartSeconds(final long lastSyncMillis) {
        return (lastSyncMillis - 6L * 60 * 60 * 1000) / 1000;
    }

    /**
     * The ScanWatch has no direct "read the heart rate now" command (the Steel HR one is refused
     * with an error). It measures on its own and keeps the readings, so a heart-rate test just
     * fetches what is stored.
     */
    @Override
    public void onHeartRateTest() {
        doSync(true);
    }

    @Override
    protected boolean advanceSyncMarkerWithoutData() {
        return false;
    }

    /**
     * The ScanWatch only returns stored data when each kind is requested explicitly, the way the
     * official app does (kinds 6, 5, 9 and 8 for activity, 3 and 4 for movement).
     */
    @Override
    protected void addSampleRequests(final long fromSeconds) {
        for (final WithingsMessage request : ScanWatchRequests.sync(fromSeconds)) {
            // The data requests feed the activity handler; the stored-signal ones are only fetched for now.
            if (request.getType() == WithingsMessageType.GET_STORED_MEASURE_SIGNAL) {
                addSimpleConversationToQueue(request, new AbstractResponseHandler(this) {
                    @Override
                    public void handleResponse(final Message response) {
                        measureCollector.accept(response.getDataStructures());
                    }
                });
            } else {
                addSimpleConversationToQueue(request, getActivitySampleHandler());
            }
        }
    }
}
