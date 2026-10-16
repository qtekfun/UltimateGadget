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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.devsettings

import nodomain.freeyourgadget.gadgetbridge.activities.app_specific_notifications.AppSpecificNotificationSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.audiorecordings.AudioRecordingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.files.FtpFileBrowserActivity
import nodomain.freeyourgadget.gadgetbridge.activities.internet.InternetFirewallActivity
import nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointPairingActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.clock.UltimateWorldClocksActivity
import nodomain.freeyourgadget.gadgetbridge.activities.widgets.WidgetScreensListActivity
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.WorkoutTemplateListActivity

/**
 * Click-action rows that open a dedicated editor activity (as opposed to command-style rows that
 * just fire {@code onSendConfiguration(key)}). Kept as data so a test can assert the routing.
 */
object ActionRouting {
    /** Preference key -> editor activity, launched with the device as an extra. */
    val editors: Map<String, Class<*>> = mapOf(
        "pref_world_clocks" to UltimateWorldClocksActivity::class.java,
        "pref_workout_templates" to WorkoutTemplateListActivity::class.java,
        "pref_widgets" to WidgetScreensListActivity::class.java,
        "pref_key_loyalty_cards" to LoyaltyCardsSettingsActivity::class.java,
        "pref_key_internet_firewall" to InternetFirewallActivity::class.java,
        "pref_multipoint" to MultipointPairingActivity::class.java,
        "pref_key_audio_recordings" to AudioRecordingsActivity::class.java,
        "ftp_server_browse" to FtpFileBrowserActivity::class.java,
        "pref_per_app_notification_settings" to AppSpecificNotificationSettingsActivity::class.java,
    )

    /** Canned-message "send to watch" rows: key -> (slot-key prefix, spec type). */
    const val CANNED_DISMISS_CALL = "canned_messages_dismisscall_send"
    const val CANNED_GENERIC = "canned_messages_generic_send"

    /** Keys [UltimateDeviceSettingsActivity] opens a native Compose screen for. */
    val nativeScreens: Set<String> = setOf(
        DeviceSettingsTree.ACTION_BATTERY_HISTORY,
        "pref_huawei_offline_maps_manage",
        "pref_music_management",
        "pref_contacts",
        "pref_perform_heart_rate_settings",
        "pref_upload_notifications_app_icon",
        "calendar_blacklist",
        "pref_huawei_stress_perform_calibrate",
    )
}
