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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.install.FwAppInstallerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.musicmanager.MusicManagerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.SectionLabelStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceMusic

/**
 * Ultimate music manager: shows the tracks already stored on the connected watch, its storage/format
 * status, lets the user upload a track from a local file (document picker) and delete tracks the
 * protocol allows removing. 100% local — no online store, no Huawei Health.
 *
 * This reuses Gadgetbridge's existing music plumbing instead of re-implementing protocol:
 *   - the track list, playlists and storage info arrive through the same LocalBroadcastManager
 *     broadcasts the legacy [MusicManagerActivity] listens to
 *     ([MusicManagerActivity.ACTION_MUSIC_DATA] / [MusicManagerActivity.ACTION_MUSIC_UPDATE]);
 *     we request them with onMusicListReq();
 *   - delete -> onMusicOperation(4, playlistId, null, ids) (operation 4 = delete music);
 *   - upload -> hand the picked audio Uri to [FwAppInstallerActivity] (ACTION_VIEW), exactly as the
 *     legacy screen does, so the Huawei install handler validates the format/metadata and shows
 *     progress before it reaches the watch.
 *
 * The screen is only offered for watches whose coordinator reports supportsMusicInfo(device)
 * (HuaweiCoordinator maps this to the device's "supports music" capability).
 */
class UltimateMusicActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }
        setContent { UltimateTheme { MusicScreen(device = device, onBack = { finish() }) } }
    }

    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateMusicActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicScreen(device: GBDevice, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var loading by remember { mutableStateOf(true) }
    var deviceInfo by remember { mutableStateOf<String?>(null) }
    var maxMusicCount by remember { mutableIntStateOf(0) }
    var deleteSupported by remember { mutableStateOf(true) }
    var uploadMimeTypes by remember { mutableStateOf(arrayOf("audio/*")) }
    var tracks by remember { mutableStateOf<List<GBDeviceMusic>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<GBDeviceMusic?>(null) }

    fun requestList() {
        loading = true
        GBApplication.deviceService(device).onMusicListReq()
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            @Suppress("UNCHECKED_CAST")
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    MusicManagerActivity.ACTION_MUSIC_DATA -> {
                        when (intent.getIntExtra("type", -1)) {
                            // Sync start: storage info + limits; reset the current list.
                            1 -> {
                                val info = intent.getStringExtra("deviceInfo")
                                deviceInfo = if (info.isNullOrEmpty()) null else info
                                maxMusicCount = intent.getIntExtra("maxMusicCount", 0)
                                deleteSupported = intent.getBooleanExtra("deleteSupported", true)
                                intent.getStringArrayExtra("uploadMimeTypes")?.takeIf { it.isNotEmpty() }?.let {
                                    uploadMimeTypes = it
                                }
                                tracks = emptyList()
                            }
                            // Music list chunk.
                            2 -> {
                                val list = intent.getSerializableExtra("musicList") as? ArrayList<GBDeviceMusic>
                                if (!list.isNullOrEmpty()) tracks = tracks + list
                            }
                            // Sync done.
                            10 -> loading = false
                        }
                    }

                    MusicManagerActivity.ACTION_MUSIC_UPDATE -> {
                        val success = intent.getBooleanExtra("success", false)
                        if (success && intent.getIntExtra("operation", -1) == 4) {
                            val ids = intent.getSerializableExtra("musicIds") as? ArrayList<Int>
                            if (!ids.isNullOrEmpty()) {
                                tracks = tracks.filterNot { ids.contains(it.id) }
                            }
                        }
                        loading = false
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(MusicManagerActivity.ACTION_MUSIC_DATA)
            addAction(MusicManagerActivity.ACTION_MUSIC_UPDATE)
        }
        val lbm = LocalBroadcastManager.getInstance(context)
        lbm.registerReceiver(receiver, filter)

        // Re-request whenever we come (back) to the foreground, e.g. after returning from the
        // installer that uploaded a track, mirroring the legacy activity's onStart().
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) requestList()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        requestList()
        onDispose {
            lbm.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Route through the installer so the Huawei handler validates format/metadata and shows
            // progress, exactly like the legacy music manager. The list refreshes on ON_START return.
            val startIntent = Intent(context, FwAppInstallerActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
                action = Intent.ACTION_VIEW
                setDataAndType(uri, null)
            }
            context.startActivity(startIntent)
            Toast.makeText(context, "Subiendo música…", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Música") },
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
                "Música guardada en el reloj. Sube pistas desde un fichero de audio del móvil o bórralas. " +
                    "Todo en local: sin tienda online ni Huawei Health.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )

            StorageCard(deviceInfo = deviceInfo, trackCount = tracks.size, maxMusicCount = maxMusicCount)

            Button(onClick = { importPicker.launch(uploadMimeTypes) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Añadir música")
            }

            Text("EN EL RELOJ", style = SectionLabelStyle, color = palette.onSurfaceVariant)

            when {
                loading && tracks.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.primary)
                    }
                }

                tracks.isEmpty() -> {
                    Text(
                        "No hay pistas. Conecta el reloj y pulsa actualizar, o añade música desde un fichero.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onSurfaceVariant,
                    )
                }

                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(tracks, key = { it.id }) { track ->
                            TrackCard(
                                track = track,
                                deleteSupported = deleteSupported,
                                onDelete = { pendingDelete = track },
                            )
                        }
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
            title = { Text("Borrar pista", color = palette.onSurface) },
            text = {
                Text(
                    "¿Borrar \"${toDelete.title}\" del reloj?",
                    color = palette.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // operation 4 = delete music, playlist 0 = remove from the device entirely.
                    GBApplication.deviceService(device)
                        .onMusicOperation(4, 0, null, arrayListOf(toDelete.id))
                    loading = true
                    pendingDelete = null
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
private fun StorageCard(deviceInfo: String?, trackCount: Int, maxMusicCount: Int) {
    val palette = LocalUltimatePalette.current
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
                Icons.Filled.Storage,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Almacenamiento",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.onSurface,
                )
                val capacity = if (maxMusicCount > 0) "$trackCount / $maxMusicCount pistas" else "$trackCount pistas"
                Text(
                    capacity,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
                if (!deviceInfo.isNullOrBlank()) {
                    Text(
                        deviceInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: GBDeviceMusic,
    deleteSupported: Boolean,
    onDelete: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val primaryLabel = (track.title ?: "").ifBlank { track.fileName ?: "" }
                Text(
                    primaryLabel.ifBlank { "(sin título)" },
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    if (!track.artist.isNullOrBlank()) append(track.artist)
                    if (!track.fileName.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(track.fileName)
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
            if (deleteSupported) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = palette.error)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}
