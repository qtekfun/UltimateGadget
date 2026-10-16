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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.install.FwAppInstallerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui.DeviceOptionUi
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui.UltimateDeviceScreen
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.ui.HuaweiMapManagementActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes

class UltimateDeviceActivity : AppCompatActivity() {

    private lateinit var address: String

    // AGPS file the user picks is handed to Gadgetbridge's existing install pipeline.
    private var agpsDevice: GBDevice? = null
    private val agpsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val dev = agpsDevice
        if (uri != null && dev != null) {
            startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.install.UltimateInstallActivity
                    .forUri(this, uri, dev),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extra = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (extra == null) {
            finish()
            return
        }
        address = extra.address

        setContent {
            UltimateTheme {
                // The GBDevice instance mutates in place, so reassigning the same reference does
                // not trigger recomposition. Bump a counter on each ACTION_DEVICE_CHANGED and
                // recompute the UI model from the live device, so connecting/connected states
                // show as they happen.
                var refresh by remember { mutableIntStateOf(0) }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            refresh++
                        }
                    }
                    val lbm = LocalBroadcastManager.getInstance(this@UltimateDeviceActivity)
                    lbm.registerReceiver(receiver, IntentFilter(GBDevice.ACTION_DEVICE_CHANGED))
                    onDispose { lbm.unregisterReceiver(receiver) }
                }

                val d = remember(refresh) { resolve() ?: extra }
                UltimateDeviceScreen(
                    device = remember(refresh) { d.toCardUi(this) },
                    options = remember(refresh) { buildOptions(d) },
                    onBack = { finish() },
                    onOption = { onOption(it, d) },
                )
            }
        }
    }

    private fun resolve(): GBDevice? =
        (application as GBApplication).deviceManager.devices.firstOrNull { it.address == address }

    private fun buildOptions(device: GBDevice): List<DeviceOptionUi> {
        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()
        fun cap(block: () -> Boolean) = runCatching { block() }.getOrDefault(false)

        val options = mutableListOf<DeviceOptionUi>()

        // --- Connection: status and sync ---
        val sConn = getString(R.string.ult_section_connection)
        val connecting = !device.isConnected && !device.isInitialized && device.state != GBDevice.State.NOT_CONNECTED
        if (device.isConnected || device.isInitialized) {
            options += DeviceOptionUi("disconnect", getString(R.string.ult_disconnect), getString(R.string.ult_disconnect_sub), section = sConn)
            options += DeviceOptionUi("sync", getString(R.string.ult_sync), getString(R.string.ult_sync_sub), section = sConn)
        } else if (connecting) {
            options += DeviceOptionUi("disconnect", getString(R.string.ult_cancel_connection), getString(R.string.ult_cancel_connection_sub), section = sConn)
        } else {
            options += DeviceOptionUi("connect", getString(R.string.ult_connect), getString(R.string.ult_connect_sub), section = sConn)
        }

        // --- Watch: settings and the watch's own features ---
        val sWatch = getString(R.string.ult_section_watch)
        options += DeviceOptionUi("settings", getString(R.string.ult_device_settings), getString(R.string.ult_device_settings_sub), section = sWatch)
        if (coordinator != null) {
            if (runCatching { coordinator.getAlarmSlotCount(device) }.getOrDefault(0) > 0) {
                options += DeviceOptionUi("alarms", getString(R.string.ult_alarms), getString(R.string.ult_alarms_sub), section = sWatch)
            }
            if (runCatching { coordinator.getReminderSlotCount(device) }.getOrDefault(0) > 0) {
                options += DeviceOptionUi("reminders", getString(R.string.ult_reminders), getString(R.string.ult_reminders_sub), section = sWatch)
            }
            if (runCatching { coordinator.getWorldClocksSlotCount() }.getOrDefault(0) > 0) {
                options += DeviceOptionUi("worldclocks", getString(R.string.ult_world_clocks), getString(R.string.ult_world_clocks_sub), section = sWatch)
            }
            // Phone-side countdown timer (the watch has no timer protocol); always available.
            options += DeviceOptionUi("timers", getString(R.string.ult_timers), getString(R.string.ult_timers_sub), section = sWatch)
            if (cap { coordinator.supportsWatchfaceManagement(device) }) {
                options += DeviceOptionUi("watchfaces", getString(R.string.ult_watchfaces), getString(R.string.ult_watchfaces_sub), section = sWatch)
            }
            if (cap { coordinator.supportsMusicInfo(device) }) {
                options += DeviceOptionUi("music", getString(R.string.ult_music), getString(R.string.ult_music_sub), section = sWatch)
            }
            if (cap { coordinator.supportsInstalledAppManagement(device) }) {
                options += DeviceOptionUi("watchapps", getString(R.string.ult_apps), getString(R.string.ult_apps_sub), section = sWatch)
            }
        }

        if (coordinator != null && device.isInitialized && cap { coordinator.supportsHeartRateMeasurement(device) }) {
            options += DeviceOptionUi("measure_hr", getString(R.string.ult_measure_hr), getString(R.string.ult_measure_hr_sub), section = sWatch)
        }

        if (nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.HealthMeasurements.supports(device)) {
            options += DeviceOptionUi("health_measurements", getString(R.string.ult_health_measurements), getString(R.string.ult_health_measurements_sub), section = sWatch)
        }

        // --- Maps and navigation ---
        val sNav = getString(R.string.ult_section_nav)
        if (coordinator is HuaweiCoordinator) {
            options += DeviceOptionUi("maps", getString(R.string.ult_maps_offline), getString(R.string.ult_maps_offline_sub), section = sNav)
            options += DeviceOptionUi("routes", getString(R.string.ult_routes), getString(R.string.ult_routes_sub), section = sNav)
            options += DeviceOptionUi("golf", getString(R.string.ult_golf_courses), getString(R.string.ult_golf_courses_sub), section = sNav)
            options += DeviceOptionUi("agnss", getString(R.string.ult_agnss), getString(R.string.ult_agnss_sub), section = sNav)
        }
        if (supportsAgps(device)) {
            options += DeviceOptionUi("agps", getString(R.string.ult_agnss), getString(R.string.ult_agps_sub), section = sNav)
        }

        // --- Device: actions on the pairing ---
        val sDev = getString(R.string.ult_section_device)
        options += DeviceOptionUi("find", getString(R.string.ult_find_device), getString(R.string.ult_find_device_sub), section = sDev)
        // Power off: only if the device supports it and is initialized (you must be connected
        // to send the command). The gating is generic: it covers the Colmi/Yawell rings and any
        // other device whose coordinator declares supportsPowerOff.
        if (coordinator != null && device.isInitialized && cap { coordinator.supportsPowerOff(device) }) {
            options += DeviceOptionUi("poweroff", getString(R.string.ult_power_off), getString(R.string.ult_power_off_sub), section = sDev)
        }
        options += DeviceOptionUi("remove", getString(R.string.ult_remove_device), getString(R.string.ult_remove_device_sub), destructive = true, section = sDev)
        return options
    }

    private fun onOption(opt: DeviceOptionUi, device: GBDevice) {
        when (opt.id) {
            "connect" -> GBApplication.deviceService(device).connect()
            "disconnect" -> GBApplication.deviceService(device).disconnect()
            "sync" -> {
                GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
                toast(getString(R.string.ult_syncing_toast))
            }
            "health_measurements" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health.UltimateHealthMeasurementsActivity.intent(this, device),
            )
            "measure_hr" -> {
                GBApplication.deviceService(device).onHeartRateTest()
                toast(getString(R.string.ult_measure_hr_toast))
            }
            "settings" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.devsettings.UltimateDeviceSettingsActivity.intent(this, device),
            )
            "maps" -> startActivity(
                Intent(this, HuaweiMapManagementActivity::class.java).apply { putExtra(GBDevice.EXTRA_DEVICE, device) },
            )
            "routes" -> startActivity(
                Intent(this, HuaweiMapManagementActivity::class.java).apply {
                    putExtra(GBDevice.EXTRA_DEVICE, device)
                    putExtra("ug_tab", 1) // open on the Routes tab
                },
            )
            "golf" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf.UltimateGolfCoursesActivity.intent(this, device),
            )
            "agnss" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.agnss.UltimateAgnssActivity.intent(this, device),
            )
            "watchfaces" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.watchfaces.UltimateWatchfacesActivity.intent(this, device),
            )
            "alarms" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.clock.UltimateAlarmsActivity.intent(this, device),
            )
            "reminders" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.clock.UltimateRemindersActivity.intent(this, device),
            )
            "worldclocks" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.clock.UltimateWorldClocksActivity.intent(this, device),
            )
            "music" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.music.UltimateMusicActivity.intent(this, device),
            )
            "watchapps" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.apps.UltimateWatchAppsActivity.intent(this, device),
            )
            "timers" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.clock.UltimateTimersActivity.intent(this, device),
            )
            "agps" -> {
                agpsDevice = device
                toast(getString(R.string.ult_choose_agnss_file))
                agpsPicker.launch(arrayOf("*/*"))
            }
            "find" -> {
                // Gadgetbridge already implements "find my watch" for Huawei (onFindDevice →
                // HuaweiDataSyncFindDevice.sendStartFindDevice). Ring the watch and show a dialog to
                // stop it; dismissing (button, back, outside) stops the ringing.
                GBApplication.deviceService(device).onFindDevice(true)
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.ult_find_device)
                    .setMessage(R.string.ult_find_dialog_msg)
                    .setPositiveButton(R.string.ult_stop, null)
                    .setOnDismissListener { GBApplication.deviceService(device).onFindDevice(false) }
                    .show()
            }
            "poweroff" -> {
                // Gadgetbridge already implements power off (onPowerOff → device command).
                // We confirm first because afterwards it must be turned on by hand to reconnect.
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.ult_power_off_dialog_title)
                    .setMessage(R.string.ult_power_off_dialog_msg)
                    .setPositiveButton(R.string.ult_power_off) { _, _ ->
                        GBApplication.deviceService(device).onPowerOff()
                        toast(getString(R.string.ult_powering_off_toast))
                    }
                    .setNegativeButton(R.string.ult_cancel, null)
                    .show()
            }
            "remove" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.install.UltimateRemoveDeviceActivity.intent(this, device),
            )
        }
    }

    /**
     * AGPS/ephemeris updates. Gadgetbridge already supports this for several brands (Huami/ZeppOS
     * Amazfit, Garmin, …) via an install handler for the downloaded AGPS pack. The capability
     * method is not on the common coordinator interface (no-arg on Huami/ZeppOS, device-arg on
     * Garmin), so it is looked up reflectively. Huawei is excluded: its format is proprietary and
     * cannot be produced from open data.
     */
    private fun supportsAgps(device: GBDevice): Boolean {
        val coordinator = runCatching { device.deviceCoordinator }.getOrNull() ?: return false
        if (coordinator is HuaweiCoordinator) return false
        runCatching {
            return coordinator.javaClass.getMethod("supportsAgpsUpdates").invoke(coordinator) as Boolean
        }
        runCatching {
            return coordinator.javaClass.getMethod("supportsAgpsUpdates", GBDevice::class.java)
                .invoke(coordinator, device) as Boolean
        }
        return false
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
