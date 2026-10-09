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
        val options = mutableListOf<DeviceOptionUi>()
        if (device.isConnected || device.isInitialized) {
            options += DeviceOptionUi("disconnect", "Desconectar", "Cortar la conexión")
            options += DeviceOptionUi("sync", "Sincronizar actividad", "Descargar datos nuevos")
        } else {
            options += DeviceOptionUi("connect", "Conectar", "Conectar con el dispositivo")
        }
        options += DeviceOptionUi("settings", "Ajustes del dispositivo", "Notificaciones, alarmas, pantallas…")

        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()
        if (coordinator is HuaweiCoordinator) {
            options += DeviceOptionUi("maps", "Mapas offline", "Instalar y borrar mapas del reloj")
            options += DeviceOptionUi("routes", "Rutas", "Enviar una ruta GPX")
        }
        if (coordinator != null && runCatching { coordinator.supportsWatchfaceManagement(device) }.getOrDefault(false)) {
            options += DeviceOptionUi("watchfaces", "Esferas", "Ver, activar y borrar esferas del reloj")
        }
        if (supportsAgps(device)) {
            options += DeviceOptionUi("agps", "Actualizar GPS (A-GNSS)", "Instalar datos de satélites para fijar antes")
        }
        options += DeviceOptionUi("find", "Buscar dispositivo", "Hacer sonar el dispositivo")
        options += DeviceOptionUi("remove", "Quitar dispositivo", "Desvincular de UltimateGadget", destructive = true)
        return options
    }

    private fun onOption(opt: DeviceOptionUi, device: GBDevice) {
        when (opt.id) {
            "connect" -> GBApplication.deviceService(device).connect()
            "disconnect" -> GBApplication.deviceService(device).disconnect()
            "sync" -> {
                GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
                toast("Sincronizando…")
            }
            "settings" -> startActivity(
                Intent(this, DeviceSettingsActivity::class.java).apply {
                    putExtra(GBDevice.EXTRA_DEVICE, device)
                    putExtra(DeviceSettingsActivity.MENU_ENTRY_POINT, DeviceSettingsActivity.MENU_ENTRY_POINTS.DEVICE_SETTINGS)
                },
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
            "watchfaces" -> startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.watchfaces.UltimateWatchfacesActivity.intent(this, device),
            )
            "agps" -> {
                agpsDevice = device
                toast("Elige el fichero A-GNSS descargado")
                agpsPicker.launch(arrayOf("*/*"))
            }
            "find" -> toast("Buscar dispositivo — próximamente")
            "remove" -> startActivity(
                Intent(this, nodomain.freeyourgadget.gadgetbridge.activities.DeviceDeleteActivity::class.java).apply {
                    putExtra(GBDevice.EXTRA_DEVICE, device)
                },
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
