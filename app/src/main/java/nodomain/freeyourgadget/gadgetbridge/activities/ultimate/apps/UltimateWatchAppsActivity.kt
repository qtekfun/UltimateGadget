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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.apps

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

/**
 * Ultimate watch-app manager: lists the third-party apps installed on the connected watch (NOT
 * watchfaces), deletes them, and installs one from a local file (document picker). There is NO
 * online store: the mainline flavour has no INTERNET permission, so the only sources are the watch
 * itself and a file the user picks.
 *
 * This reuses Gadgetbridge's existing app-management plumbing instead of re-implementing protocol:
 *   - the list arrives through the [AbstractAppManagerFragment.ACTION_REFRESH_APPLIST] broadcast
 *     (extra [AbstractAppManagerFragment.EXTRA_APP_LIST]); we request it with onAppInfoReq() and
 *     keep only the non-watchface entries;
 *   - delete -> onAppDelete(uuid),
 *     import -> onInstallApp(uri, Bundle.EMPTY) (the same call FwAppInstallerActivity ends up making).
 *
 * NOTE on launching: Huawei's HuaweiAppManager.startApp() only confirms the app exists; the watch
 * exposes no "start app" command, so remotely launching/opening an app is NOT supported. We
 * therefore do not offer a launch action and say so in the UI, rather than showing a button that
 * silently does nothing.
 */
class UltimateWatchAppsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }
        setContent { UltimateTheme { WatchAppsScreen(device = device, onBack = { finish() }) } }
    }

    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateWatchAppsActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchAppsScreen(device: GBDevice, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current

    var apps by remember { mutableStateOf<List<GBDeviceApp>>(emptyList()) }
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
                apps = parcelables
                    .filterIsInstance<GBDeviceApp>()
                    // Everything that is NOT a watchface: real apps (APP_GENERIC on Huawei),
                    // activity trackers and system apps. Watchfaces live in their own screen.
                    .filter {
                        it.type != GBDeviceApp.Type.WATCHFACE &&
                            it.type != GBDeviceApp.Type.WATCHFACE_SYSTEM
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
            context.startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.install.UltimateInstallActivity
                    .forUri(context, uri, device),
            )
            requestList()
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Apps del reloj") },
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
                "Apps instaladas en el reloj (no esferas). Bórralas o instala una desde un fichero. " +
                    "No hay tienda online: solo el reloj y tus ficheros. El reloj no permite abrir " +
                    "apps en remoto, así que ábrelas desde el propio reloj.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )
            Button(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Instalar app")
            }

            Text(
                "INSTALADAS",
                style = SectionLabelStyle,
                color = palette.onSurfaceVariant,
            )

            if (apps.isEmpty()) {
                Text(
                    "No hay apps. Conecta el reloj y pulsa actualizar, o instala una.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(apps, key = { it.uuid.toString() }) { app ->
                        AppCard(
                            app = app,
                            onDelete = { pendingDelete = app },
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
            title = { Text("Borrar app", color = palette.onSurface) },
            text = {
                Text(
                    "¿Borrar \"${toDelete.name}\" del reloj?",
                    color = palette.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    GBApplication.deviceService(device).onAppDelete(toDelete.uuid)
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
private fun AppCard(
    app: GBDeviceApp,
    onDelete: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    // System apps (APP_SYSTEM) are baked into the watch and cannot be removed.
    val isSystem = app.type == GBDeviceApp.Type.APP_SYSTEM

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.Apps,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    if (app.creator.isNotBlank()) append(app.creator)
                    if (app.version.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(app.version)
                    }
                    if (isSystem) {
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
            // Factory/system apps cannot be removed from the device.
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
