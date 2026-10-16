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
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettings
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs

/**
 * Builds the full native settings tree of a device: the coordinator's own settings (XML preference
 * model or settings DSL) plus the generic connection and battery-polling sub-screens. Kept out of
 * the activity so it can be exercised for every device type in unit tests.
 */
object DeviceSettingsTree {

    /** Synthetic action key for the native battery-history screen (not a persisted preference). */
    const val ACTION_BATTERY_HISTORY = "ug_battery_history"

    class Built(
        val nodes: List<PrefNode>,
        /** Click handlers of DSL action rows, by preference key (empty for XML-based devices). */
        val dslActions: Map<String, (Context, GBDevice?) -> Boolean>,
    )

    fun build(context: Context, device: GBDevice): Built {
        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()
        val modelSpec = runCatching { coordinator?.getDeviceSettings(device) }.getOrNull()
        val settings: DeviceSpecificSettings? =
            if (modelSpec == null) runCatching { coordinator?.getDeviceSpecificSettings(device) }.getOrNull() else null
        val built = when {
            modelSpec != null -> {
                val dsl = DslSettingsConverter.convert(context, device, modelSpec)
                Built(appendCommonNodes(context, device, dsl.nodes.toMutableList()), dsl.actions)
            }
            settings != null -> Built(buildNodes(context, device, settings), emptyMap())
            else -> Built(appendCommonNodes(context, device, mutableListOf()), emptyMap())
        }
        // Runtime-filled lists, hidden rows, ranges and summaries come from the brand's customizer,
        // and "connect when another device connects" lists the phone's paired devices.
        val customized = CustomizerBridge.customize(context, device, built.nodes)
        return Built(resolveBuiltIns(context, device, customized), built.dslActions)
    }

    private fun buildNodes(context: Context, device: GBDevice, settings: DeviceSpecificSettings): List<PrefNode> {
        val nodes = DeviceSettingsParser.buildTopLevel(context, settings).toMutableList()
        return appendCommonNodes(context, device, nodes)
    }

    /** Adds the generic connection and battery-polling sub-screens the legacy builder appends. */
    /** Options the classic fragment filled in itself, not through a brand customizer. */
    private fun resolveBuiltIns(context: Context, device: GBDevice, nodes: List<PrefNode>): List<PrefNode> = nodes.map { n ->
        when {
            n is PrefNode.SubScreen -> n.copy(children = resolveBuiltIns(context, device, n.children))
            n is PrefNode.Conditional -> n.copy(children = resolveBuiltIns(context, device, n.children))
            (n is PrefNode.SingleChoice && n.key == GBPrefs.DEVICE_CONNECT_BY_TRIGGER) ->
                connectByTrigger(context, device, n)
            else -> n
        }
    }

    /** "Connect to this device when another device connects": none, or one of the phone's paired devices. */
    private fun connectByTrigger(context: Context, device: GBDevice, base: PrefNode.SingleChoice): PrefNode.SingleChoice {
        val entries = mutableListOf(context.getString(R.string.none))
        val values = mutableListOf("none")
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bonded = manager?.adapter?.bondedDevices.orEmpty()
                .filter { it.address != device.address }
                .sortedBy { runCatching { it.name }.getOrNull()?.lowercase() ?: "\uffff" }
            for (b in bonded) {
                entries += (runCatching { b.name }.getOrNull() ?: context.getString(R.string.unknown)) + " (" + b.address + ")"
                values += b.address
            }
        } catch (_: SecurityException) {
            // No Bluetooth permission yet: only "none" is offered until it is granted.
        }
        return base.copy(entries = entries, values = values, default = "none")
    }

    internal fun appendCommonNodes(context: Context, device: GBDevice, nodes: MutableList<PrefNode>): List<PrefNode> {
        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()

        // The generic connection sub-screen (reconnect / priority / …) is added by the legacy
        // builder, not by the coordinator's own settings, so append it here from the public API.
        val connKey = DeviceSpecificSettingsScreen.CONNECTION.key
        val alreadyHasConnection = nodes.any { it is PrefNode.SubScreen && it.key == connKey }
        if (!alreadyHasConnection) {
            val conn = runCatching { coordinator?.getSupportedDeviceSpecificConnectionSettings() }.getOrNull()
            if (conn != null && conn.isNotEmpty()) {
                val children = conn.flatMap { DeviceSettingsParser.parseResource(context.resources, it) }
                if (children.isNotEmpty()) {
                    nodes.add(
                        PrefNode.SubScreen(
                            key = connKey,
                            title = context.getString(DeviceSpecificSettingsScreen.CONNECTION.title),
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
                    title = context.getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_battery_polling_configuration),
                    summary = context.getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_battery_polling_summary),
                    iconRes = 0,
                    children = listOf(
                        PrefNode.Switch(
                            key = nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BATTERY_POLLING_ENABLE,
                            title = context.getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_battery_polling_enable),
                            summary = null, iconRes = 0, default = true, dependency = null,
                        ),
                        PrefNode.Text(
                            key = nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BATTERY_POLLING_INTERVAL,
                            title = context.getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_battery_polling_interval),
                            summary = null, iconRes = 0, default = "15", numeric = true, dependency = null,
                        ),
                        // Native battery history chart (replaces the classic BatteryInfoActivity).
                        PrefNode.Action(
                            key = ACTION_BATTERY_HISTORY,
                            title = context.getString(R.string.ult_battery_history),
                            summary = "See the battery level over time",
                            iconRes = 0,
                        ),
                    ),
                ),
            )
        }
        return nodes
    }
}
