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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.install

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.BondingUtil
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.LoggerFactory

/**
 * Confirmación de borrado de dispositivo en estética Ultimate.
 *
 * Hace exactamente lo mismo que
 * [nodomain.freeyourgadget.gadgetbridge.activities.DeviceDeleteActivity], pero mostrando antes una
 * confirmación en lugar de borrar directamente: tras confirmar,
 *  - `coordinator.deleteDevice(device, deleteFiles)` (quita/olvida el dispositivo y, opcionalmente, sus datos),
 *  - `BondingUtil.Unpair(context, address)` (desempareja el Bluetooth),
 *  - elimina el acceso directo dinámico (Android R+),
 *  - emite `DeviceManager.ACTION_REFRESH_DEVICELIST` para refrescar la lista,
 *  - da feedback (toast de éxito o texto de error) y cierra.
 */
class UltimateRemoveDeviceActivity : AppCompatActivity() {

    private lateinit var device: GBDevice
    private var deleteFiles = true

    private var state by mutableStateOf<RemoveState>(RemoveState.Confirm)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dev = intent.getParcelableCompat<GBDevice>(EXTRA_DEVICE)
        if (dev == null) {
            finish()
            return
        }
        device = dev
        deleteFiles = intent.getBooleanExtra(EXTRA_DELETE_FILES, true)

        setContent {
            UltimateTheme {
                RemoveDeviceScreen(
                    name = device.aliasOrName,
                    state = state,
                    onConfirm = { startDelete() },
                    onCancel = { finish() },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun startDelete() {
        state = RemoveState.Deleting
        lifecycleScope.launch {
            val error = performDelete()
            LocalBroadcastManager.getInstance(this@UltimateRemoveDeviceActivity)
                .sendBroadcast(Intent(DeviceManager.ACTION_REFRESH_DEVICELIST))
            if (error == null) {
                GB.toast(
                    this@UltimateRemoveDeviceActivity,
                    getString(R.string.device_deleted_successfully),
                    Toast.LENGTH_SHORT,
                    GB.INFO,
                )
                finish()
            } else {
                state = RemoveState.Error(
                    getString(R.string.error_deleting_device, error.localizedMessage ?: "Unknown error"),
                )
            }
        }
    }

    private suspend fun performDelete(): Exception? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            device.deviceCoordinator.deleteDevice(device, deleteFiles)
            BondingUtil.Unpair(this@UltimateRemoveDeviceActivity, device.address)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val shortcutManager = applicationContext.getSystemService(SHORTCUT_SERVICE) as ShortcutManager
                shortcutManager.removeDynamicShortcuts(mutableListOf(device.address))
            }
        } catch (ex: Exception) {
            LOG.error("Error deleting device", ex)
            return@withContext ex
        }
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < 1000L) delay(1000L - elapsed) // avoid a too-fast blink
        null
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UltimateRemoveDeviceActivity::class.java)

        const val EXTRA_DEVICE = "device"
        const val EXTRA_DELETE_FILES = "delete_files"

        @JvmStatic
        @JvmOverloads
        fun intent(context: Context, device: GBDevice, deleteFiles: Boolean = true): Intent =
            Intent(context, UltimateRemoveDeviceActivity::class.java).apply {
                putExtra(EXTRA_DEVICE, device)
                putExtra(EXTRA_DELETE_FILES, deleteFiles)
            }
    }
}

private sealed interface RemoveState {
    data object Confirm : RemoveState
    data object Deleting : RemoveState
    data class Error(val message: String) : RemoveState
}

@Composable
private fun RemoveDeviceScreen(
    name: String,
    state: RemoveState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = palette.error,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    "Olvidar “$name”",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onSurface,
                )
                when (state) {
                    is RemoveState.Confirm -> {
                        Text(
                            "Se quitará el dispositivo de UltimateGadget, se desemparejará del Bluetooth y se " +
                                "borrarán sus datos sincronizados. Esta acción no se puede deshacer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                                Text("Cancelar")
                            }
                            Button(
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.error,
                                    contentColor = palette.onErrorContainer,
                                ),
                            ) {
                                Text("Olvidar")
                            }
                        }
                    }

                    is RemoveState.Deleting -> {
                        CircularProgressIndicator(color = palette.primary)
                        Text(
                            "Eliminando…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurfaceVariant,
                        )
                    }

                    is RemoveState.Error -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = palette.error)
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}
