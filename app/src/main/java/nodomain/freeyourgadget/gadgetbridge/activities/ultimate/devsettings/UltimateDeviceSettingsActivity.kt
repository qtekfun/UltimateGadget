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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettings
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/**
 * Compose re-skin of the per-device "device settings" screen, in the UltimateGadget visual
 * language. It is a presentation layer only: it reuses Gadgetbridge's coordinator-provided
 * [DeviceSpecificSettings] (the same preference-XML resources and keys), writes to the same
 * per-device SharedPreferences and fires the same {@code onSendConfiguration(key)} event, so the
 * watch receives changes exactly as with the legacy screen (see [DeviceSettingsStore]).
 *
 * Scope: both coordinator models are rendered natively — the preference-XML model through
 * [DeviceSettingsParser] and the settings DSL (rings, earbuds, scales, …) through
 * [DslSettingsConverter]. Switches, single/multi-choice and sortable lists, text and numeric
 * inputs, sliders, time and date pickers, file/folder pickers, info and action rows are all
 * native; the editors that used to be separate classic screens (heart-rate zones, contacts,
 * calendars, notification app icons, stress calibration, battery history) open their Compose
 * counterparts. The classic preference screen is no longer used.
 */
class UltimateDeviceSettingsActivity : AppCompatActivity() {

    /** Click handlers of DSL action rows, by preference key (empty for XML-based devices). */
    private var dslActions: Map<String, (android.content.Context, GBDevice?) -> Boolean> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }

        val built = DeviceSettingsTree.build(this, device)
        dslActions = built.dslActions
        val nodes = built.nodes

        setContent {
            UltimateTheme {
                UltimateDeviceSettingsScreen(
                    rootTitle = (device.aliasOrName ?: "").ifBlank { getString(R.string.ult_device_settings) },
                    rootNodes = nodes,
                    store = DeviceSettingsStore(device),
                    hasClassicFallback = false,
                    onBack = { finish() },
                    onDelegate = {},
                    onAction = { key -> onAction(key, device) },
                    initialScreenKey = intent.getStringExtra(EXTRA_OPEN_SCREEN),
                )
            }
        }
    }

    /**
     * Runs a click-action preference ([PrefNode.Action]) natively: opens the dedicated editor
     * activity it maps to, or, for command-style actions, fires the device's
     * {@code onSendConfiguration} so the device support handles it.
     */
    private fun onAction(key: String?, device: GBDevice) {
        if (key == null) return
        // DSL action rows carry their own click handler (the settings model's onClick).
        dslActions[key]?.let { handler ->
            runCatching { handler(this, device) }
            return
        }
        fun open(cls: Class<*>) = startActivity(
            Intent(this, cls).putExtra(GBDevice.EXTRA_DEVICE, device),
        )
        when (key) {
            ACTION_BATTERY_HISTORY ->
                startActivity(nodomain.freeyourgadget.gadgetbridge.activities.ultimate.battery.UltimateBatteryActivity.intent(this, device))
            "pref_huawei_offline_maps_manage" ->
                open(nodomain.freeyourgadget.gadgetbridge.devices.huawei.ui.HuaweiMapManagementActivity::class.java)
            "pref_music_management" ->
                startActivity(nodomain.freeyourgadget.gadgetbridge.activities.ultimate.music.UltimateMusicActivity.intent(this, device))
            "pref_contacts" ->
                startActivity(nodomain.freeyourgadget.gadgetbridge.activities.ultimate.contacts.UltimateContactsActivity.intent(this, device))
            "pref_perform_heart_rate_settings" ->
                startActivity(nodomain.freeyourgadget.gadgetbridge.activities.ultimate.heartrate.UltimateHeartRateZonesActivity.intent(this, device))
            "pref_upload_notifications_app_icon" ->
                startActivity(nodomain.freeyourgadget.gadgetbridge.activities.ultimate.appicons.UltimateAppIconUploadActivity.intent(this, device))
            "calendar_blacklist" ->
                startActivity(nodomain.freeyourgadget.gadgetbridge.activities.ultimate.calendar.UltimateCalendarSelectionActivity.intent(this, device))
            "pref_huawei_stress_perform_calibrate" ->
                startActivity(nodomain.freeyourgadget.gadgetbridge.activities.ultimate.stress.UltimateStressCalibrationActivity.intent(this, device))
            // Command-style actions (send canned messages, debug request, …): fire the same signal
            // the legacy screen's change-handler would, so the device support handles it.
            else -> runCatching { GBApplication.deviceService(device).onSendConfiguration(key) }
        }
    }

    companion object {
        /** Synthetic action key for the native battery-history screen (not a persisted preference). */
        private const val ACTION_BATTERY_HISTORY = DeviceSettingsTree.ACTION_BATTERY_HISTORY

        /** Optional: key of a sub-screen to open straight away (e.g. the authentication screen). */
        const val EXTRA_OPEN_SCREEN = "ug_open_screen"

        @JvmStatic
        @JvmOverloads
        fun intent(context: Context, device: GBDevice, openScreenKey: String? = null): Intent =
            Intent(context, UltimateDeviceSettingsActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
                openScreenKey?.let { putExtra(EXTRA_OPEN_SCREEN, it) }
            }
    }
}
