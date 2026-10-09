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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }

        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()

        // Devices using the newer DSL settings model (getDeviceSettings != null) are not handled by
        // this generic XML renderer yet — fall back to the legacy screen entirely.
        val modelSpec = runCatching { coordinator?.getDeviceSettings(device) }.getOrNull()
        val settings: DeviceSpecificSettings? =
            if (modelSpec == null) runCatching { coordinator?.getDeviceSpecificSettings(device) }.getOrNull() else null

        if (settings == null) {
            openClassic(device)
            finish()
            return
        }

        val nodes = buildNodes(device, settings)

        setContent {
            UltimateTheme {
                UltimateDeviceSettingsScreen(
                    rootTitle = (device.aliasOrName ?: "").ifBlank { "Ajustes del dispositivo" },
                    rootNodes = nodes,
                    store = DeviceSettingsStore(device),
                    hasClassicFallback = true,
                    onBack = { finish() },
                    onDelegate = { openClassic(device) },
                )
            }
        }
    }

    private fun buildNodes(device: GBDevice, settings: DeviceSpecificSettings): List<PrefNode> {
        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()
        val nodes = DeviceSettingsParser.buildTopLevel(this, settings).toMutableList()

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
        return nodes
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
        @JvmStatic
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateDeviceSettingsActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
            }
    }
}
