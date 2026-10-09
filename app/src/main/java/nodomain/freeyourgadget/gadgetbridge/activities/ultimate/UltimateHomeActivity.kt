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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils
import nodomain.freeyourgadget.gadgetbridge.activities.welcome.WelcomeActivity
import nodomain.freeyourgadget.gadgetbridge.activities.PermissionsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ControlCenterv2
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardScreen
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardViewModel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.detail.UltimateHealthDetailActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessDetailActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCardId
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui.DeviceCardUi
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui.UltimateHomeScreen
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/**
 * Host for the new UltimateGadget UI. Bottom nav with two sections:
 *  - "Inicio": placeholder for the health dashboard (built separately). See [HomeDashboardSlot].
 *  - "Dispositivos": the device list (this file).
 *
 * To plug the dashboard in, replace the body of [HomeDashboardSlot] with the dashboard composable;
 * the theme (UltimateTheme / LocalUltimatePalette) and this host are already set up for it.
 */
class UltimateHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (runStartupGate()) return

        setContent {
            UltimateTheme {
                val palette = LocalUltimatePalette.current
                var tab by remember { mutableStateOf(0) } // start on the health dashboard
                var devices by remember { mutableStateOf(loadDevices(this)) }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            devices = loadDevices(this@UltimateHomeActivity)
                        }
                    }
                    val lbm = LocalBroadcastManager.getInstance(this@UltimateHomeActivity)
                    lbm.registerReceiver(receiver, IntentFilter(GBDevice.ACTION_DEVICE_CHANGED))
                    onDispose { lbm.unregisterReceiver(receiver) }
                }

                Scaffold(
                    containerColor = palette.background,
                    bottomBar = {
                        NavigationBar(containerColor = palette.surface) {
                            val itemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.onPrimary,
                                selectedTextColor = palette.primary,
                                indicatorColor = palette.primary,
                                unselectedIconColor = palette.onSurfaceVariant,
                                unselectedTextColor = palette.onSurfaceVariant,
                            )
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                                label = { Text("Inicio") },
                                colors = itemColors,
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(Icons.Filled.Watch, contentDescription = null) },
                                label = { Text("Dispositivos") },
                                colors = itemColors,
                            )
                        }
                    },
                ) { inner ->
                    Box(Modifier.padding(inner)) {
                        when (tab) {
                            0 -> HomeDashboardSlot()
                            else -> UltimateHomeScreen(
                                devices = devices,
                                onOpenDevice = { openDevice(it) },
                                onAddDevice = { startActivity(Intent(this@UltimateHomeActivity, UltimateAddDeviceActivity::class.java)) },
                                onOverflow = { startActivity(Intent(this@UltimateHomeActivity, ControlCenterv2::class.java)) },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * First-run / permission gating, mirroring ControlCenterv2 so the new launcher behaves
     * correctly. Returns true if it redirected to the welcome flow (and finished this activity).
     */
    private fun runStartupGate(): Boolean {
        val prefs = GBApplication.getPrefs()
        if (prefs.getBoolean("first_run", true)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return true
        }
        if (prefs.getBoolean("permission_pestering", true) && !PermissionsUtils.checkAllPermissions(this)) {
            startActivity(
                Intent(this, PermissionsActivity::class.java)
                    .putExtra(PermissionsActivity.ARG_SHOW_DO_NOT_ASK_BUTTON, true),
            )
        }
        GBApplication.deviceService().requestDeviceInfo()
        return false
    }

    private fun openDevice(d: DeviceCardUi) {
        val device = findDevice(d.address) ?: return
        val intent = Intent(this, UltimateDeviceActivity::class.java)
        intent.putExtra(GBDevice.EXTRA_DEVICE, device)
        startActivity(intent)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun findDevice(address: String): GBDevice? =
        (application as GBApplication).deviceManager.devices.firstOrNull { it.address == address }

    companion object {
        fun loadDevices(context: Context): List<DeviceCardUi> {
            val app = context.applicationContext as GBApplication
            return app.deviceManager.devices.map { it.toCardUi(context) }
        }
    }
}

/**
 * TODO(dashboard): the health dashboard (built in a separate worktree) replaces this body.
 * Keep the signature trivial so wiring is a one-line swap.
 */
@Composable
private fun HomeDashboardSlot() {
    val context = LocalContext.current
    val vm: DashboardViewModel = viewModel()
    val state = vm.state.collectAsStateWithLifecycle().value
    DashboardScreen(
        state = state,
        onToggleEditing = { vm.setEditing(!state.editing) },
        onToggleCard = vm::toggleCard,
        onMoveCard = vm::moveCard,
        onOpenDetail = { id ->
            if (id == DashboardCardId.READINESS) {
                context.startActivity(ReadinessDetailActivity.intent(context))
            } else {
                context.startActivity(UltimateHealthDetailActivity.intent(context, id))
            }
        },
        onRefresh = { vm.sync() },
    )
}

/** Maps a GBDevice to its display model using the real coordinator metadata. */
fun GBDevice.toCardUi(context: Context): DeviceCardUi {
    val coordinator = runCatching { deviceCoordinator }.getOrNull()
    val iconRes = coordinator?.defaultIconResource ?: 0
    val typeName = runCatching {
        coordinator?.deviceNameResource?.takeIf { it != 0 }?.let { context.getString(it) }
    }.getOrNull() ?: (coordinator?.manufacturer ?: "")
    val battery = runCatching { getBatteryLevel(0) }.getOrDefault(-1)
    return DeviceCardUi(
        address = address,
        name = aliasOrName ?: name ?: address,
        typeName = typeName,
        stateLabel = getStateString(context),
        connected = isConnected,
        connecting = !isConnected && state != GBDevice.State.NOT_CONNECTED,
        busy = isBusy,
        batteryLevel = if (battery in 0..100) battery else -1,
        model = runCatching { model }.getOrNull(),
        iconRes = iconRes,
        accentSeed = address.hashCode(),
    )
}
