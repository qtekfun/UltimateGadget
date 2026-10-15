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
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
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
 * Scope of the generic renderer (see [DeviceSettingsParser]): switches, single-choice lists, text
 * / numeric inputs and sliders are rendered natively. Everything it does not model — complex custom
 * pickers, action-only preferences, DSL-model devices, and the programmatic Battery / Developer /
 * Auth / Experimental screens — is handed off to the legacy [DeviceSettingsActivity] so nothing is
 * lost or broken.
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

        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()

        // Devices using the settings DSL (getDeviceSettings != null: rings, earbuds, scales, …) are
        // converted to the same native node tree as the XML-based ones; only a device that exposes
        // neither model falls back to the legacy screen.
        val modelSpec = runCatching { coordinator?.getDeviceSettings(device) }.getOrNull()
        val settings: DeviceSpecificSettings? =
            if (modelSpec == null) runCatching { coordinator?.getDeviceSpecificSettings(device) }.getOrNull() else null

        if (modelSpec == null && settings == null) {
            openClassic(device)
            finish()
            return
        }

        val nodes: List<PrefNode> = if (modelSpec != null) {
            val dsl = DslSettingsConverter.convert(this, device, modelSpec)
            dslActions = dsl.actions
            appendCommonNodes(device, dsl.nodes.toMutableList())
        } else {
            buildNodes(device, settings!!)
        }

        setContent {
            UltimateTheme {
                UltimateDeviceSettingsScreen(
                    rootTitle = (device.aliasOrName ?: "").ifBlank { getString(R.string.ult_device_settings) },
                    rootNodes = nodes,
                    store = DeviceSettingsStore(device),
                    hasClassicFallback = false,
                    onBack = { finish() },
                    onDelegate = { openClassic(device) },
                    onAction = { key -> onAction(key, device) },
                )
            }
        }
    }

    private fun buildNodes(device: GBDevice, settings: DeviceSpecificSettings): List<PrefNode> {
        val nodes = DeviceSettingsParser.buildTopLevel(this, settings).toMutableList()
        return appendCommonNodes(device, nodes)
    }

    /** Adds the generic connection and battery-polling sub-screens the legacy builder appends. */
    private fun appendCommonNodes(device: GBDevice, nodes: MutableList<PrefNode>): List<PrefNode> {
        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()

        // The generic connection sub-screen (reconnect / priority / …) is added by the legacy
        // builder, not by the coordinator's own settings, so append it here from the public API.
        val connKey = DeviceSpecificSettingsScreen.CONNECTION.key
        val alreadyHasConnection = nodes.any { it is PrefNode.SubScreen && it.key == connKey }
        if (!alreadyHasConnection) {
            val conn = runCatching { coordinator?.getSupportedDeviceSpecificConnectionSettings() }.getOrNull()
            if (conn != null && conn.isNotEmpty()) {
                val children = conn.flatMap { DeviceSettingsParser.parseResource(resources, it) }
                if (children.isNotEmpty()) {
                    nodes.add(
                        PrefNode.SubScreen(
                            key = connKey,
                            title = getString(DeviceSpecificSettingsScreen.CONNECTION.title),
                            summary = null,
                            iconRes = 0,
                            children = children,
                        ),
                    )
                }
            }
        }

        // Battery polling is added programmatically by the legacy fragment (not via the coordinator's
        // XML), so reproduce it natively here from the known keys/defaults, as a sub-screen. This is
        // what the old "classic settings" row used to cover.
        if (runCatching { coordinator?.addBatteryPollingSettings() }.getOrDefault(false) == true) {
            nodes.add(
                PrefNode.SubScreen(
                    key = "pref_battery_polling_header",
                    title = getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_battery_polling_configuration),
                    summary = getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_battery_polling_summary),
                    iconRes = 0,
                    children = listOf(
                        PrefNode.Switch(
                            key = nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BATTERY_POLLING_ENABLE,
                            title = getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_battery_polling_enable),
                            summary = null, iconRes = 0, default = true, dependency = null,
                        ),
                        PrefNode.Text(
                            key = nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BATTERY_POLLING_INTERVAL,
                            title = getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_battery_polling_interval),
                            summary = null, iconRes = 0, default = "15", numeric = true, dependency = null,
                        ),
                        // Native battery history chart (replaces the classic BatteryInfoActivity).
                        PrefNode.Action(
                            key = ACTION_BATTERY_HISTORY,
                            title = getString(R.string.ult_battery_history),
                            summary = "See the battery level over time",
                            iconRes = 0,
                        ),
                    ),
                ),
            )
        }
        return nodes
    }

    /**
     * Runs a click-action preference ([PrefNode.Action]) natively: opens the dedicated editor
     * activity it maps to (the same one the legacy settings screen launched), or, for command-style
     * actions, fires the device's {@code onSendConfiguration} so nothing has to drop to the classic
     * preference screen.
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
                open(nodomain.freeyourgadget.gadgetbridge.activities.ConfigureContacts::class.java)
            "pref_perform_heart_rate_settings" ->
                open(nodomain.freeyourgadget.gadgetbridge.activities.heartratezones.HeartRateSettingsActivity::class.java)
            "pref_upload_notifications_app_icon" ->
                open(nodomain.freeyourgadget.gadgetbridge.activities.NotificationsAppIconUploadActivity::class.java)
            "calendar_blacklist" ->
                open(nodomain.freeyourgadget.gadgetbridge.activities.CalendarSelectionActivity::class.java)
            "pref_huawei_stress_perform_calibrate" ->
                open(nodomain.freeyourgadget.gadgetbridge.devices.huawei.ui.HuaweiStressCalibrationActivity::class.java)
            // Command-style actions (send canned messages, debug request, …): fire the same signal
            // the legacy screen's change-handler would, so the device support handles it.
            else -> runCatching { GBApplication.deviceService(device).onSendConfiguration(key) }
        }
    }

    private fun openClassic(device: GBDevice) {
        startActivity(
            Intent(this, DeviceSettingsActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
                putExtra(
                    DeviceSettingsActivity.MENU_ENTRY_POINT,
                    DeviceSettingsActivity.MENU_ENTRY_POINTS.DEVICE_SETTINGS,
                )
            },
        )
    }

    companion object {
        /** Synthetic action key for the native battery-history screen (not a persisted preference). */
        private const val ACTION_BATTERY_HISTORY = "ug_battery_history"

        @JvmStatic
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateDeviceSettingsActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
            }
    }
}
