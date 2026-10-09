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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.watchfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.AbstractAppManagerFragment
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.SectionLabelStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceApp
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiWatchfaceManager
import java.util.UUID

/**
 * Ultimate "showcase" for watchfaces (esferas): lists the watchfaces already installed on the
 * connected watch, lets the user activate or delete each one, and imports a watchface from a local
 * file (document picker). There is NO online gallery or download: the mainline flavour has no
 * INTERNET permission, so the only sources are the watch itself and a file the user picks.
 *
 * This reuses Gadgetbridge's existing app-management plumbing instead of re-implementing protocol:
 *   - the list arrives through the [AbstractAppManagerFragment.ACTION_REFRESH_APPLIST] broadcast
 *     (extra [AbstractAppManagerFragment.EXTRA_APP_LIST]); we request it with onAppInfoReq();
 *   - activate -> onAppStart(uuid, true), delete -> onAppDelete(uuid),
 *     import -> onInstallApp(uri, Bundle.EMPTY) (the same call FwAppInstallerActivity ends up making).
 */
class UltimateWatchfacesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }
        setContent { UltimateTheme { WatchfacesScreen(device = device, onBack = { finish() }) } }
    }

    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateWatchfacesActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchfacesScreen(device: GBDevice, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current

    var watchfaces by remember { mutableStateOf<List<GBDeviceApp>>(emptyList()) }
    // The device's active watchface is not carried by GBDeviceApp across the broadcast, so we track
    // the one activated from this screen to highlight it. Best effort, session-local.
    var activeUuid by remember { mutableStateOf<UUID?>(null) }
    var pendingDelete by remember { mutableStateOf<GBDeviceApp?>(null) }

    fun requestList() = GBApplication.deviceService(device).onAppInfoReq()

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != AbstractAppManagerFragment.ACTION_REFRESH_APPLIST) return
                val parcelables: Array<out Parcelable>? =
                    intent.getParcelableArrayExtra(AbstractAppManagerFragment.EXTRA_APP_LIST)
                if (parcelables == null) {
                    // A refresh with no payload just asks us to re-request from the device.
                    requestList()
                    return
                }
                val list = parcelables
                    .filterIsInstance<GBDeviceApp>()
                    .filter {
                        it.type == GBDeviceApp.Type.WATCHFACE ||
                            it.type == GBDeviceApp.Type.WATCHFACE_SYSTEM
                    }
                watchfaces = list
                // The watch reports the active watchface via the creator sentinel (set in
                // HuaweiWatchfaceManager.handleWatchfaceList). Trust it when present; otherwise keep
                // whatever we optimistically set on activation.
                list.firstOrNull { it.creator == HuaweiWatchfaceManager.CURRENT_MARKER }?.let {
                    activeUuid = it.uuid
                }
            }
        }
        val lbm = LocalBroadcastManager.getInstance(context)
        lbm.registerReceiver(receiver, IntentFilter(AbstractAppManagerFragment.ACTION_REFRESH_APPLIST))
        requestList()
        onDispose { lbm.unregisterReceiver(receiver) }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            GBApplication.deviceService(device).onInstallApp(uri, Bundle.EMPTY)
            Toast.makeText(context, "Instalando esfera…", Toast.LENGTH_SHORT).show()
            requestList()
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Esferas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { requestList() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                    actionIconContentColor = palette.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Esferas instaladas en el reloj. Actívalas o bórralas, o importa una desde un fichero " +
                    "(.watchface/.bin). No hay galería online: solo el reloj y tus ficheros.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )
            Button(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Importar esfera")
            }

            Text(
                "INSTALADAS",
                style = SectionLabelStyle,
                color = palette.onSurfaceVariant,
            )

            if (watchfaces.isEmpty()) {
                Text(
                    "No hay esferas. Conecta el reloj y pulsa actualizar, o importa una.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(watchfaces, key = { it.uuid.toString() }) { wf ->
                        WatchfaceCard(
                            app = wf,
                            isActive = wf.uuid == activeUuid,
                            onActivate = {
                                GBApplication.deviceService(device).onAppStart(wf.uuid, true)
                                activeUuid = wf.uuid
                                Toast.makeText(context, "Esfera activada", Toast.LENGTH_SHORT).show()
                                requestList()
                            },
                            onDelete = { pendingDelete = wf },
                        )
                    }
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            containerColor = palette.surfaceContainer,
            onDismissRequest = { pendingDelete = null },
            title = { Text("Borrar esfera", color = palette.onSurface) },
            text = {
                Text(
                    "¿Borrar \"${toDelete.name}\" del reloj?",
                    color = palette.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    GBApplication.deviceService(device).onAppDelete(toDelete.uuid)
                    if (toDelete.uuid == activeUuid) activeUuid = null
                    pendingDelete = null
                    requestList()
                }) { Text("Borrar", color = palette.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar", color = palette.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun WatchfaceCard(
    app: GBDeviceApp,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val isSystem = app.type == GBDeviceApp.Type.WATCHFACE_SYSTEM
    val canActivate = app.isCanBeStarted && !isActive

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) palette.secondaryContainer else palette.surfaceContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Watch,
                    contentDescription = null,
                    tint = if (isActive) palette.secondary else palette.primary,
                    modifier = Modifier.size(22.dp),
                )
                if (isActive) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Activa",
                        tint = palette.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    if (app.creator.isNotBlank() && app.creator != HuaweiWatchfaceManager.CURRENT_MARKER) append(app.creator)
                    if (app.version.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(app.version)
                    }
                    if (isActive) {
                        if (isNotEmpty()) append(" · ")
                        append("Activa")
                    } else if (isSystem) {
                        if (isNotEmpty()) append(" · ")
                        append("De fábrica")
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onActivate,
                    enabled = canActivate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isActive) "Activa" else "Activar")
                }
                // System/factory watchfaces cannot be removed from the device.
                if (!isSystem) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = palette.error)
                    }
                } else {
                    Box(Modifier.size(48.dp))
                }
            }
        }
    }
}
