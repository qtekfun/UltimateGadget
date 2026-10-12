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
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.install.FwAppInstallerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui.DeviceOptionUi
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui.UltimateDeviceScreen
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.ui.HuaweiMapManagementActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes

class UltimateDeviceActivity : AppCompatActivity() {

    private lateinit var address: String

    // AGPS file the user picks is handed to Gadgetbridge's existing install pipeline.
    private var agpsDevice: GBDevice? = null
    private val agpsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val dev = agpsDevice
        if (uri != null && dev != null) {
            startActivity(
                Intent(this, FwAppInstallerActivity::class.java).apply {
                    setDataAndType(uri, null)
                    putExtra(GBDevice.EXTRA_DEVICE, dev)
                },
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
        val sConn = "Connection"
        val connecting = !device.isConnected && !device.isInitialized && device.state != GBDevice.State.NOT_CONNECTED
        if (device.isConnected || device.isInitialized) {
            options += DeviceOptionUi("disconnect", "Disconnect", "Drop the connection", section = sConn)
            options += DeviceOptionUi("sync", "Sync activity", "Download new data", section = sConn)
        } else if (connecting) {
            options += DeviceOptionUi("disconnect", "Cancel connection", "Stop the connection attempt", section = sConn)
        } else {
            options += DeviceOptionUi("connect", "Connect", "Connect to the device", section = sConn)
        }

        // --- Watch: settings and the watch's own features ---
        val sWatch = "Watch"
        options += DeviceOptionUi("settings", "Device settings", "Notificaciones, pantallas, gestos…", section = sWatch)
        if (coordinator != null) {
            if (runCatching { coordinator.getAlarmSlotCount(device) }.getOrDefault(0) > 0) {
                options += DeviceOptionUi("alarms", "Alarmas", "Watch alarms", section = sWatch)
            }
            if (runCatching { coordinator.getReminderSlotCount(device) }.getOrDefault(0) > 0) {
                options += DeviceOptionUi("reminders", "Recordatorios", "Date and time reminders", section = sWatch)
            }
            if (runCatching { coordinator.getWorldClocksSlotCount() }.getOrDefault(0) > 0) {
                options += DeviceOptionUi("worldclocks", "World clocks", "Otras zonas horarias", section = sWatch)
            }
            // Phone-side countdown timer (the watch has no timer protocol); always available.
            options += DeviceOptionUi("timers", "Timers", "Countdown on the phone", section = sWatch)
            if (cap { coordinator.supportsWatchfaceManagement(device) }) {
                options += DeviceOptionUi("watchfaces", "Watchfaces", "View, activate and delete watchfaces", section = sWatch)
            }
            if (cap { coordinator.supportsMusicInfo(device) }) {
                options += DeviceOptionUi("music", "Music", "View, upload and delete watch music", section = sWatch)
            }
            if (cap { coordinator.supportsInstalledAppManagement(device) }) {
                options += DeviceOptionUi("watchapps", "Apps", "View, delete and install watch apps", section = sWatch)
            }
        }

        // --- Maps and navigation ---
        val sNav = "Maps and navigation"
        if (coordinator is HuaweiCoordinator) {
            options += DeviceOptionUi("maps", "Mapas offline", "Install and delete watch maps", section = sNav)
            options += DeviceOptionUi("routes", "Rutas", "Planificar y enviar rutas", section = sNav)
            options += DeviceOptionUi("golf", "Golf courses", "Send and delete course maps on the watch", section = sNav)
            options += DeviceOptionUi("agnss", "Actualizar GPS (A-GNSS)", "Satellite data for a faster fix", section = sNav)
        }
        if (supportsAgps(device)) {
            options += DeviceOptionUi("agps", "Actualizar GPS (A-GNSS)", "Install satellite data for a faster fix", section = sNav)
        }

        // --- Device: actions on the pairing ---
        val sDev = "Device"
        options += DeviceOptionUi("find", "Find device", "Ring the device", section = sDev)
        // Power off: only if the device supports it and is initialized (you must be connected
        // to send the command). The gating is generic: it covers the Colmi/Yawell rings and any
        // other device whose coordinator declares supportsPowerOff.
        if (coordinator != null && device.isInitialized && cap { coordinator.supportsPowerOff(device) }) {
            options += DeviceOptionUi("poweroff", "Power off", "Power off the device", section = sDev)
        }
        options += DeviceOptionUi("remove", "Remove device", "Unlink from UltimateGadget", destructive = true, section = sDev)
        return options
    }

    private fun onOption(opt: DeviceOptionUi, device: GBDevice) {
        when (opt.id) {
            "connect" -> GBApplication.deviceService(device).connect()
            "disconnect" -> GBApplication.deviceService(device).disconnect()
            "sync" -> {
                GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
                toast("Syncing…")
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
                toast("Choose the downloaded A-GNSS file")
                agpsPicker.launch(arrayOf("*/*"))
            }
            "find" -> {
                // Gadgetbridge already implements "find my watch" for Huawei (onFindDevice →
                // HuaweiDataSyncFindDevice.sendStartFindDevice). Ring the watch and show a dialog to
                // stop it; dismissing (button, back, outside) stops the ringing.
                GBApplication.deviceService(device).onFindDevice(true)
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Find device")
                    .setMessage("The watch is ringing/vibrating. Tap Stop when you find it.")
                    .setPositiveButton("Stop", null)
                    .setOnDismissListener { GBApplication.deviceService(device).onFindDevice(false) }
                    .show()
            }
            "poweroff" -> {
                // Gadgetbridge already implements power off (onPowerOff → device command).
                // We confirm first because afterwards it must be turned on by hand to reconnect.
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Power off device")
                    .setMessage("Power off the device? You'll have to turn it on manually to connect again.")
                    .setPositiveButton("Power off") { _, _ ->
                        GBApplication.deviceService(device).onPowerOff()
                        toast("Apagando…")
                    }
                    .setNegativeButton("Cancel", null)
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
