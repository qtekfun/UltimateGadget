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
import nodomain.freeyourgadget.gadgetbridge.GBApplication
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
        /** Keys the brand's customizer hid for this device (unsupported features). */
        val hidden: Set<String> = emptySet(),
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
        val resolved = resolveBuiltIns(context, device, customized.nodes)
        return Built(resolved, launchHandlers(resolved) + customized.clicks + built.dslActions, customized.hidden)
    }

    /** Click handlers for XML rows that carry an `<intent>` (open an activity or view a link). */
    private fun launchHandlers(nodes: List<PrefNode>): Map<String, (Context, GBDevice?) -> Boolean> {
        val out = LinkedHashMap<String, (Context, GBDevice?) -> Boolean>()
        fun walk(list: List<PrefNode>) {
            for (n in list) when (n) {
                is PrefNode.SubScreen -> walk(n.children)
                is PrefNode.Conditional -> walk(n.children)
                is PrefNode.Action -> {
                    val spec = n.launch
                    val key = n.key
                    if (spec != null && key != null) out[key] = { ctx, device -> launch(ctx, device, spec) }
                }
                else -> {}
            }
        }
        walk(nodes)
        return out
    }

    private fun launch(ctx: Context, device: GBDevice?, spec: LaunchSpec): Boolean {
        val intent = if (spec.targetClass != null) {
            android.content.Intent().setClassName(ctx.packageName, spec.targetClass)
        } else {
            android.content.Intent(spec.action ?: android.content.Intent.ACTION_VIEW, spec.data?.let(android.net.Uri::parse))
        }
        if (spec.targetClass != null) {
            spec.action?.let { intent.action = it }
            spec.data?.let { intent.data = android.net.Uri.parse(it) }
        }
        device?.let { intent.putExtra(GBDevice.EXTRA_DEVICE, it) }
        if (ctx !is android.app.Activity) intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
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
                            summary = context.getString(R.string.ult_battery_history_sub),
                            iconRes = 0,
                        ),
                    ),
                ),
            )
        }
        appendGenericScreens(context, device, coordinator, nodes)
        return nodes
    }

    /**
     * The generic sub-screens the classic fragment added to every device on top of the brand's own
     * settings: authentication (Huawei account id, auth keys), activity info, developer options and
     * the experimental ones. Merged into a screen of the same key when the brand already has one.
     */
    private fun appendGenericScreens(
        context: Context,
        device: GBDevice,
        coordinator: nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator?,
        nodes: MutableList<PrefNode>,
    ) {
        coordinator ?: return
        fun add(screen: DeviceSpecificSettingsScreen, xmls: List<Int>) {
            val children = xmls.flatMap { DeviceSettingsParser.parseResource(context.resources, it) }
            if (children.isEmpty()) return
            val at = nodes.indexOfFirst { it is PrefNode.SubScreen && it.key == screen.key }
            if (at >= 0) {
                val existing = nodes[at] as PrefNode.SubScreen
                val known = existing.children.mapNotNull { nodeKey(it) }.toSet()
                nodes[at] = existing.copy(children = existing.children + children.filter { nodeKey(it) == null || nodeKey(it) !in known })
            } else {
                nodes.add(PrefNode.SubScreen(screen.key, context.getString(screen.title), null, 0, children))
            }
        }

        val auth = runCatching { coordinator.supportedDeviceSpecificAuthenticationSettings }.getOrNull()
        if (auth != null && auth.isNotEmpty()) add(DeviceSpecificSettingsScreen.AUTHENTICATION, auth.toList())

        if (runCatching { coordinator.supportsCharts(device) }.getOrDefault(false)) {
            add(DeviceSpecificSettingsScreen.ACTIVITY_INFO, listOf(R.xml.devicesettings_chartstabs))
        }
        if (runCatching { coordinator.supportsActivityTracking(device) }.getOrDefault(false)) {
            add(DeviceSpecificSettingsScreen.ACTIVITY_INFO, listOf(R.xml.devicesettings_device_card_activity_card_preferences))
        }

        val developer = mutableListOf<Int>()
        if (coordinator.connectionType != nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator.ConnectionType.USB) {
            developer += R.xml.devicesettings_device_support_can_reconnect
        }
        developer += R.xml.devicesettings_header_intent_api
        developer += R.xml.devicesettings_settings_third_party_apps
        if (runCatching { coordinator.getAlarmSlotCount(device) }.getOrDefault(0) > 0) {
            developer += R.xml.devicesettings_alarms_third_party_apps
        }
        if (coordinator.connectionType.usesBluetoothLE()) {
            developer += R.xml.devicesettings_ble_api
            developer += R.xml.devicesettings_gatt_synchronous_writes
            developer += R.xml.devicesettings_connection_force_legacy_gatt
        }
        if (nodomain.freeyourgadget.gadgetbridge.BuildConfig.DEBUG) {
            val debug = runCatching { coordinator.getSupportedDebugSettings(device) }.getOrNull()
            if (debug != null && debug.isNotEmpty()) {
                developer += R.xml.devicesettings_header_debug
                developer += debug.toList()
            }
        }
        add(DeviceSpecificSettingsScreen.DEVELOPER, developer)

        if (GBApplication.getPrefs().experimentalSettings()) {
            val exp = runCatching { coordinator.getSupportedDeviceSpecificExperimentalSettings(device) }.getOrNull()
            if (exp != null && exp.isNotEmpty()) {
                add(DeviceSpecificSettingsScreen.EXPERIMENTAL, listOf(R.xml.devicesettings_experimental_warning) + exp.toList())
            }
        }
    }

    private fun nodeKey(n: PrefNode): String? = when (n) {
        is PrefNode.Switch -> n.key
        is PrefNode.SingleChoice -> n.key
        is PrefNode.Text -> n.key
        is PrefNode.Slider -> n.key
        is PrefNode.TimePicker -> n.key
        is PrefNode.MultiChoice -> n.key
        is PrefNode.Sortable -> n.key
        is PrefNode.DatePicker -> n.key
        is PrefNode.Action -> n.key
        is PrefNode.SubScreen -> n.key
        is PrefNode.Info -> n.key
        else -> null
    }
}
