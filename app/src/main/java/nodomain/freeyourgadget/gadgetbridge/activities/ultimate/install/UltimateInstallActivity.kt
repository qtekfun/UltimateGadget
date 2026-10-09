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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.install.InstallActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.devices.GpxRouteInstallHandler
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminGpxRouteInstallHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.model.ItemWithDetails
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.LoggerFactory

/**
 * Pantalla de progreso de instalación/transferencia en estética Ultimate.
 *
 * Sustituye, para los flujos lanzados desde la UI nueva, a
 * [nodomain.freeyourgadget.gadgetbridge.activities.install.FwAppInstallerActivity] (firmware,
 * apps, watchfaces, música) y a
 * [nodomain.freeyourgadget.gadgetbridge.activities.install.GpxRouteInstallerActivity] (rutas GPX).
 *
 * No reinventa la instalación: reutiliza exactamente el mismo protocolo que las pantallas viejas.
 *  - Valida con el install handler del coordinator: `coordinator.findInstallHandler(uri, options, ctx)`.
 *  - Instala con `GBApplication.deviceService(device).onInstallApp(uri, options)`.
 *  - Escucha el progreso por los broadcasts de GB:
 *    ACTION_SET_PROGRESS_BAR / ACTION_SET_PROGRESS_TEXT / ACTION_SET_INFO_TEXT /
 *    ACTION_DISPLAY_MESSAGE / ACTION_SET_FINISHED y GBDevice.ACTION_DEVICE_CHANGED.
 *
 * Para rutas GPX muestra, además, el campo de nombre de la pista y (si el handler lo soporta,
 * p.ej. Garmin) los interruptores de navegación giro a giro; el bundle con
 * [GpxRouteInstallHandler.EXTRA_TRACK_NAME] / navegación se construye aquí al pulsar instalar.
 */
class UltimateInstallActivity : AppCompatActivity(), InstallActivity {

    private val ui = InstallUiState()

    private lateinit var device: GBDevice
    private lateinit var currentUri: Uri
    private var options: Bundle = Bundle.EMPTY
    private var installHandler: InstallHandler? = null

    private var mayConnect = false
    private var finished = false
    private var installRequested = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                GBDevice.ACTION_DEVICE_CHANGED -> {
                    val changed = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
                    if (changed == null || changed != device) return
                    if (finished) return
                    device = changed
                    refreshBusyState(device)
                    if (!device.isInitialized) {
                        setInstallEnabled(false)
                        if (mayConnect) {
                            GB.toast(this@UltimateInstallActivity, getString(R.string.connecting), Toast.LENGTH_SHORT, GB.INFO)
                            connect()
                        } else {
                            setInfoText(getString(R.string.fwappinstaller_connection_state, device.getStateString(context)))
                        }
                    } else {
                        validateInstallation()
                    }
                }

                GB.ACTION_SET_PROGRESS_BAR -> {
                    if (intent.hasExtra(GB.PROGRESS_BAR_INDETERMINATE)) {
                        setProgressIndeterminate(intent.getBooleanExtra(GB.PROGRESS_BAR_INDETERMINATE, false))
                    }
                    if (intent.hasExtra(GB.PROGRESS_BAR_PROGRESS)) {
                        setProgressIndeterminate(false)
                        ui.progressVisible = true
                        ui.progress = intent.getIntExtra(GB.PROGRESS_BAR_PROGRESS, 0)
                    }
                }

                GB.ACTION_SET_PROGRESS_TEXT ->
                    intent.getStringExtra(GB.DISPLAY_MESSAGE_MESSAGE)?.let { ui.progressText = it }

                GB.ACTION_SET_INFO_TEXT ->
                    intent.getStringExtra(GB.DISPLAY_MESSAGE_MESSAGE)?.let { setInfoText(it) }

                GB.ACTION_DISPLAY_MESSAGE -> {
                    val message = intent.getStringExtra(GB.DISPLAY_MESSAGE_MESSAGE)
                    val severity = intent.getIntExtra(GB.DISPLAY_MESSAGE_SEVERITY, GB.INFO)
                    if (message != null) ui.messages.add(message to severity)
                }

                GB.ACTION_SET_FINISHED -> {
                    finished = true
                    ui.progressVisible = false
                    setInstallEnabled(false)
                    setCloseEnabled(true)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentDevice = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (intentDevice == null) {
            GB.toast(this, "No device provided to UltimateInstallActivity", Toast.LENGTH_LONG, GB.ERROR)
            finish()
            return
        }
        device = intentDevice

        val intentUri = intent.data ?: intent.getParcelableCompat<Uri>(Intent.EXTRA_STREAM)
        if (intentUri == null) {
            GB.toast(this, "No URI provided to UltimateInstallActivity", Toast.LENGTH_LONG, GB.ERROR)
            finish()
            return
        }
        currentUri = intentUri
        options = intent.getParcelableCompat<Bundle>(DeviceService.EXTRA_OPTIONS) ?: Bundle.EMPTY

        val handler = device.deviceCoordinator.findInstallHandler(currentUri, options, this)
        if (handler == null) {
            LOG.error("Install handler not found for {}", currentUri)
            setInfoText(getString(R.string.installer_activity_unable_to_find_handler))
        }
        installHandler = handler

        (handler as? GpxRouteInstallHandler)?.let { gpx ->
            ui.isGpx = true
            ui.supportsTbt = handler is GarminGpxRouteInstallHandler
            ui.trackName = gpx.name ?: ""
        }

        mayConnect = true
        setInstallEnabled(false)

        val filter = IntentFilter().apply {
            addAction(GBDevice.ACTION_DEVICE_CHANGED)
            addAction(GB.ACTION_DISPLAY_MESSAGE)
            addAction(GB.ACTION_SET_PROGRESS_BAR)
            addAction(GB.ACTION_SET_PROGRESS_TEXT)
            addAction(GB.ACTION_SET_INFO_TEXT)
            addAction(GB.ACTION_SET_FINISHED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)

        setInfoText(getString(R.string.installer_activity_wait_while_determining_status))

        setContent {
            UltimateTheme {
                InstallScreen(
                    ui = ui,
                    onBack = { finish() },
                    onInstall = { onInstallClicked() },
                    onClose = { finish() },
                    onConfirmationToggle = { checked ->
                        ui.confirmationChecked = checked
                        setInstallEnabled(installRequested)
                    },
                )
            }
        }

        if (handler != null) {
            if (!device.isConnected) {
                if (mayConnect) connect()
            } else {
                GBApplication.deviceService(device)?.requestDeviceInfo()
            }
        }
    }

    private fun onInstallClicked() {
        val handler = installHandler ?: return
        val installOptions: Bundle = if (ui.isGpx) {
            val name = ui.trackName.trim()
            if (name.isEmpty()) {
                GB.toast(this, getString(R.string.track_name_required), Toast.LENGTH_SHORT, GB.WARN)
                return
            }
            Bundle().apply {
                putString(GpxRouteInstallHandler.EXTRA_TRACK_NAME, name)
                putBoolean(GpxRouteInstallHandler.EXTRA_NAVIGATION_ENABLED, ui.navEnabled)
                putBoolean(GpxRouteInstallHandler.EXTRA_STRAIGHT_NAVIGATION_ENABLED, ui.straightNavEnabled)
            }
        } else {
            options
        }

        setInstallEnabled(false)
        handler.onStartInstall(device)
        GBApplication.deviceService(device)?.onInstallApp(currentUri, installOptions)
    }

    private fun refreshBusyState(dev: GBDevice) {
        ui.progressVisible = dev.isConnecting || dev.isBusy
    }

    private fun setProgressIndeterminate(indeterminate: Boolean) {
        ui.progressVisible = true
        ui.progressIndeterminate = indeterminate
    }

    private fun connect() {
        mayConnect = false // only once per onCreate
        GBApplication.deviceService(device)?.connect()
    }

    private fun validateInstallation() {
        installHandler?.let {
            ui.confirmationText = null
            it.validateInstallation(this, device)
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onDestroy()
    }

    // --- InstallActivity ---

    override fun getInfoText(): CharSequence = ui.infoText

    override fun setInfoText(text: String?) {
        ui.infoText = text ?: ""
    }

    override fun setPreview(bitmap: Bitmap?) {
        ui.preview = bitmap
    }

    override fun setInstallEnabled(enable: Boolean) {
        installRequested = enable
        val confirmed = ui.confirmationText == null || ui.confirmationChecked
        val enabled = device.isConnected && enable && confirmed
        ui.installEnabled = enabled
        if (enabled) setCloseEnabled(false)
    }

    override fun setCloseEnabled(enable: Boolean) {
        ui.closeEnabled = enable
    }

    override fun setInstallConfirmation(text: String?) {
        ui.confirmationChecked = false
        ui.confirmationText = text
        setInstallEnabled(installRequested)
    }

    override fun clearInstallItems() {
        ui.itemName = null
        ui.itemDetails = null
    }

    override fun setInstallItem(item: ItemWithDetails) {
        ui.itemName = item.name
        ui.itemDetails = item.details
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UltimateInstallActivity::class.java)

        /**
         * Intent genérico para instalar/transferir un fichero (firmware, app, watchface, música,
         * ruta GPX…). Replica los extras que esperan las pantallas viejas:
         * [GBDevice.EXTRA_DEVICE], el [uri] como `data` y, opcionalmente,
         * [DeviceService.EXTRA_OPTIONS]. Añade el permiso de lectura del uri.
         */
        @JvmStatic
        @JvmOverloads
        fun forUri(context: Context, uri: Uri, device: GBDevice, options: Bundle? = null): Intent =
            Intent(context, UltimateInstallActivity::class.java).apply {
                setDataAndType(uri, null)
                putExtra(GBDevice.EXTRA_DEVICE, device)
                if (options != null) putExtra(DeviceService.EXTRA_OPTIONS, options)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}

/** State de Compose respaldado por snapshots; lo mutan el receiver y los callbacks de InstallActivity. */
private class InstallUiState {
    var infoText by mutableStateOf("")
    var itemName by mutableStateOf<String?>(null)
    var itemDetails by mutableStateOf<String?>(null)
    var preview by mutableStateOf<Bitmap?>(null)
    var progressVisible by mutableStateOf(false)
    var progressIndeterminate by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var progressText by mutableStateOf<String?>(null)
    val messages = mutableStateListOf<Pair<String, Int>>()
    var installEnabled by mutableStateOf(false)
    var closeEnabled by mutableStateOf(false)
    var confirmationText by mutableStateOf<String?>(null)
    var confirmationChecked by mutableStateOf(false)

    // GPX route specifics
    var isGpx by mutableStateOf(false)
    var supportsTbt by mutableStateOf(false)
    var trackName by mutableStateOf("")
    var navEnabled by mutableStateOf(false)
    var straightNavEnabled by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallScreen(
    ui: InstallUiState,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onClose: () -> Unit,
    onConfirmationToggle: (Boolean) -> Unit,
) {
    val palette = LocalUltimatePalette.current

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Transferencia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Elemento a instalar: nombre + tipo + preview
            if (ui.itemName != null || ui.preview != null) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ui.preview?.let {
                            androidx.compose.foundation.Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                            )
                        } ?: Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            ui.itemName?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                            }
                            ui.itemDetails?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Texto informativo / estado
            if (ui.infoText.isNotBlank()) {
                Text(ui.infoText, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
            }

            // Campos específicos de ruta GPX
            if (ui.isGpx && ui.installEnabled) {
                OutlinedTextField(
                    value = ui.trackName,
                    onValueChange = { ui.trackName = it },
                    label = { Text("Nombre de la ruta") },
                    singleLine = true,
                    isError = ui.trackName.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ui.supportsTbt) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Navegación giro a giro", Modifier.weight(1f), color = palette.onSurface)
                        Switch(checked = ui.navEnabled, onCheckedChange = { ui.navEnabled = it })
                    }
                    if (ui.navEnabled) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Navegación en línea recta", Modifier.weight(1f), color = palette.onSurface)
                            Switch(checked = ui.straightNavEnabled, onCheckedChange = { ui.straightNavEnabled = it })
                        }
                    }
                }
            }

            // Confirmación opcional (algunos firmware la exigen)
            ui.confirmationText?.let { text ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ui.confirmationChecked, onCheckedChange = onConfirmationToggle)
                    Text(text, Modifier.weight(1f), color = palette.onSurface)
                }
            }

            // Progreso
            if (ui.progressVisible) {
                if (ui.progressIndeterminate) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { ui.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ui.progressText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }

            // Mensajes/registro
            ui.messages.forEach { (msg, severity) ->
                val color = when (severity) {
                    GB.ERROR -> palette.error
                    GB.WARN -> palette.tertiary
                    else -> palette.onSurfaceVariant
                }
                Text(msg, style = MaterialTheme.typography.bodySmall, color = color)
            }

            // Botones
            if (ui.installEnabled) {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Instalar")
                }
            }
            if (ui.closeEnabled) {
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Cerrar")
                }
            }
        }
    }
}
