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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import nodomain.freeyourgadget.gadgetbridge.activities.musicmanager.MusicManagerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.SectionLabelStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceMusic
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceMusicPlaylist

/**
 * Ultimate music manager: shows the tracks already stored on the connected watch, its storage/format
 * status, lets the user upload a track from a local file (document picker), delete tracks the
 * protocol allows removing, and — on watches that report playlist support — manage playlists
 * (create / rename / delete and add/remove tracks). 100% local — no online store, no Huawei Health.
 *
 * This reuses Gadgetbridge's existing music plumbing instead of re-implementing protocol:
 *   - the track list, playlists and storage info arrive through the same LocalBroadcastManager
 *     broadcasts the legacy [MusicManagerActivity] listens to
 *     ([MusicManagerActivity.ACTION_MUSIC_DATA] / [MusicManagerActivity.ACTION_MUSIC_UPDATE]);
 *     we request them with onMusicListReq();
 *   - the sync carries limits in the type-1 chunk (maxMusicCount, maxPlaylistCount, deleteSupported,
 *     uploadMimeTypes); the type-2 chunks carry musicList (ArrayList<GBDeviceMusic>) and
 *     musicPlaylist (ArrayList<GBDeviceMusicPlaylist>); type 10 ends the sync;
 *   - playlist/track operations go through onMusicOperation(operation, playlistId, name, ids), the
 *     exact same codes the legacy screen uses, so we add no new protocol:
 *       0 = create playlist   -> onMusicOperation(0, -1, name, null)
 *       1 = delete playlist   -> onMusicOperation(1, playlistId, null, null)
 *       2 = rename playlist   -> onMusicOperation(2, playlistId, newName, null)
 *       3 = add tracks        -> onMusicOperation(3, playlistId, null, ids)
 *       4 = remove tracks     -> onMusicOperation(4, playlistId, null, ids)
 *                                (playlistId 0 removes the tracks from the device entirely);
 *   - each operation is acknowledged through ACTION_MUSIC_UPDATE with the "operation",
 *     "playlistIndex", "playlistName" and "musicIds" extras, which we mirror into local state;
 *   - upload -> hand the picked audio Uri to [FwAppInstallerActivity] (ACTION_VIEW), exactly as the
 *     legacy screen does, so the Huawei install handler validates the format/metadata and shows
 *     progress before it reaches the watch.
 *
 * Playlist management is only offered when the watch reports maxPlaylistCount > 0 (same gate the
 * legacy screen uses to show the playlist UI). The screen is only offered for watches whose
 * coordinator reports supportsMusicInfo(device).
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

/** Returns a copy of this list with the playlist matching [id] replaced by [transform], for Compose. */
private fun List<GBDeviceMusicPlaylist>.mapPlaylist(
    id: Int,
    transform: (GBDeviceMusicPlaylist) -> GBDeviceMusicPlaylist,
): List<GBDeviceMusicPlaylist> = map { if (it.id == id) transform(it) else it }

private fun GBDeviceMusicPlaylist.withIds(ids: ArrayList<Int>): GBDeviceMusicPlaylist =
    GBDeviceMusicPlaylist(id, name, ids)

private fun GBDeviceMusicPlaylist.trackIds(): List<Int> = musicIds ?: emptyList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicScreen(device: GBDevice, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var loading by remember { mutableStateOf(true) }
    var deviceInfo by remember { mutableStateOf<String?>(null) }
    var maxMusicCount by remember { mutableIntStateOf(0) }
    var maxPlaylistCount by remember { mutableIntStateOf(0) }
    var deleteSupported by remember { mutableStateOf(true) }
    var uploadMimeTypes by remember { mutableStateOf(arrayOf("audio/*")) }
    var tracks by remember { mutableStateOf<List<GBDeviceMusic>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<GBDeviceMusicPlaylist>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    var pendingDelete by remember { mutableStateOf<GBDeviceMusic?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<GBDeviceMusicPlaylist?>(null) }
    var deletePlaylistTarget by remember { mutableStateOf<GBDeviceMusicPlaylist?>(null) }
    var editTarget by remember { mutableStateOf<GBDeviceMusicPlaylist?>(null) }

    fun service() = GBApplication.deviceService(device)

    fun requestList() {
        loading = true
        service().onMusicListReq()
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            @Suppress("UNCHECKED_CAST")
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    MusicManagerActivity.ACTION_MUSIC_DATA -> {
                        when (intent.getIntExtra("type", -1)) {
                            // Sync start: storage info + limits; reset the current lists.
                            1 -> {
                                val info = intent.getStringExtra("deviceInfo")
                                deviceInfo = if (info.isNullOrEmpty()) null else info
                                maxMusicCount = intent.getIntExtra("maxMusicCount", 0)
                                maxPlaylistCount = intent.getIntExtra("maxPlaylistCount", 0)
                                deleteSupported = intent.getBooleanExtra("deleteSupported", true)
                                intent.getStringArrayExtra("uploadMimeTypes")?.takeIf { it.isNotEmpty() }?.let {
                                    uploadMimeTypes = it
                                }
                                tracks = emptyList()
                                playlists = emptyList()
                            }
                            // Music list / playlist chunk.
                            2 -> {
                                (intent.getSerializableExtra("musicList") as? ArrayList<GBDeviceMusic>)
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { tracks = tracks + it }
                                (intent.getSerializableExtra("musicPlaylist") as? ArrayList<GBDeviceMusicPlaylist>)
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { playlists = playlists + it }
                            }
                            // Sync done.
                            10 -> loading = false
                        }
                    }

                    MusicManagerActivity.ACTION_MUSIC_UPDATE -> {
                        val success = intent.getBooleanExtra("success", false)
                        if (success && intent.hasExtra("operation")) {
                            val operation = intent.getIntExtra("operation", -1)
                            val playlistIndex = intent.getIntExtra("playlistIndex", -1)
                            val playlistName = intent.getStringExtra("playlistName")
                            val ids = intent.getSerializableExtra("musicIds") as? ArrayList<Int>
                            when (operation) {
                                // Playlist created.
                                0 -> if (playlistIndex != -1 && playlistName != null) {
                                    playlists = playlists + GBDeviceMusicPlaylist(playlistIndex, playlistName, ArrayList())
                                }
                                // Playlist deleted.
                                1 -> if (playlistIndex != -1) {
                                    playlists = playlists.filterNot { it.id == playlistIndex }
                                }
                                // Playlist renamed.
                                2 -> if (playlistIndex != -1 && playlistName != null) {
                                    playlists = playlists.mapPlaylist(playlistIndex) {
                                        GBDeviceMusicPlaylist(it.id, playlistName, it.musicIds)
                                    }
                                }
                                // Tracks added to playlist.
                                3 -> if (playlistIndex != -1 && !ids.isNullOrEmpty()) {
                                    playlists = playlists.mapPlaylist(playlistIndex) { pl ->
                                        val merged = ArrayList(pl.trackIds())
                                        ids.forEach { if (!merged.contains(it)) merged.add(it) }
                                        pl.withIds(merged)
                                    }
                                }
                                // Tracks removed: from a playlist (id != 0) or from the device (id == 0).
                                4 -> if (!ids.isNullOrEmpty()) {
                                    if (playlistIndex == 0) {
                                        tracks = tracks.filterNot { ids.contains(it.id) }
                                        // also drop them from every playlist they belonged to
                                        playlists = playlists.map { pl ->
                                            if (pl.trackIds().any { ids.contains(it) }) {
                                                pl.withIds(ArrayList(pl.trackIds().filterNot { ids.contains(it) }))
                                            } else {
                                                pl
                                            }
                                        }
                                    } else if (playlistIndex != -1) {
                                        playlists = playlists.mapPlaylist(playlistIndex) { pl ->
                                            pl.withIds(ArrayList(pl.trackIds().filterNot { ids.contains(it) }))
                                        }
                                    }
                                }
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
            // Route through the Ultimate install screen so the Huawei handler validates format/metadata
            // and shows progress. The list refreshes on ON_START return.
            context.startActivity(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.install.UltimateInstallActivity
                    .forUri(context, uri, device),
            )
        }
    }

    val playlistsSupported = maxPlaylistCount > 0

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
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (playlistsSupported) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = palette.surface,
                    contentColor = palette.primary,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        selectedContentColor = palette.primary,
                        unselectedContentColor = palette.onSurfaceVariant,
                        text = { Text("Pistas") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        selectedContentColor = palette.primary,
                        unselectedContentColor = palette.onSurfaceVariant,
                        text = { Text("Listas") },
                    )
                }
            }

            if (selectedTab == 0 || !playlistsSupported) {
                TracksTab(
                    loading = loading,
                    deviceInfo = deviceInfo,
                    maxMusicCount = maxMusicCount,
                    deleteSupported = deleteSupported,
                    tracks = tracks,
                    onAdd = { importPicker.launch(uploadMimeTypes) },
                    onDeleteTrack = { pendingDelete = it },
                )
            } else {
                PlaylistsTab(
                    loading = loading,
                    playlists = playlists,
                    maxPlaylistCount = maxPlaylistCount,
                    onCreate = { showCreatePlaylist = true },
                    onRename = { renameTarget = it },
                    onDelete = { deletePlaylistTarget = it },
                    onEdit = { editTarget = it },
                )
            }
        }
    }

    // --- Dialogs ---------------------------------------------------------------------------------

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            containerColor = palette.surfaceContainer,
            onDismissRequest = { pendingDelete = null },
            title = { Text("Borrar pista", color = palette.onSurface) },
            text = {
                Text("¿Borrar \"${toDelete.title}\" del reloj?", color = palette.onSurfaceVariant)
            },
            confirmButton = {
                TextButton(onClick = {
                    // operation 4 = delete music, playlist 0 = remove from the device entirely.
                    service().onMusicOperation(4, 0, null, arrayListOf(toDelete.id))
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

    if (showCreatePlaylist) {
        PlaylistNameDialog(
            title = "Nueva lista",
            initialName = "",
            confirmLabel = "Crear",
            onDismiss = { showCreatePlaylist = false },
            onConfirm = { name ->
                // operation 0 = create playlist, playlistId -1 = new.
                service().onMusicOperation(0, -1, name, null)
                loading = true
                showCreatePlaylist = false
            },
        )
    }

    val toRename = renameTarget
    if (toRename != null) {
        PlaylistNameDialog(
            title = "Renombrar lista",
            initialName = toRename.name ?: "",
            confirmLabel = "Guardar",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                // operation 2 = rename playlist.
                service().onMusicOperation(2, toRename.id, name, null)
                loading = true
                renameTarget = null
            },
        )
    }

    val toDeletePlaylist = deletePlaylistTarget
    if (toDeletePlaylist != null) {
        AlertDialog(
            containerColor = palette.surfaceContainer,
            onDismissRequest = { deletePlaylistTarget = null },
            title = { Text("Borrar lista", color = palette.onSurface) },
            text = {
                Text(
                    "¿Borrar la lista \"${toDeletePlaylist.name}\"? Las pistas seguirán en el reloj.",
                    color = palette.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // operation 1 = delete playlist.
                    service().onMusicOperation(1, toDeletePlaylist.id, null, null)
                    loading = true
                    deletePlaylistTarget = null
                }) { Text("Borrar", color = palette.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletePlaylistTarget = null }) {
                    Text("Cancelar", color = palette.onSurfaceVariant)
                }
            },
        )
    }

    val toEdit = editTarget
    if (toEdit != null) {
        PlaylistContentDialog(
            playlist = toEdit,
            allTracks = tracks,
            onDismiss = { editTarget = null },
            onConfirm = { added, removed ->
                if (added.isNotEmpty()) {
                    // operation 3 = add tracks to playlist.
                    service().onMusicOperation(3, toEdit.id, null, ArrayList(added))
                }
                if (removed.isNotEmpty()) {
                    // operation 4 on a real playlist id = remove tracks from that playlist.
                    service().onMusicOperation(4, toEdit.id, null, ArrayList(removed))
                }
                if (added.isNotEmpty() || removed.isNotEmpty()) loading = true
                editTarget = null
            },
        )
    }
}

@Composable
private fun TracksTab(
    loading: Boolean,
    deviceInfo: String?,
    maxMusicCount: Int,
    deleteSupported: Boolean,
    tracks: List<GBDeviceMusic>,
    onAdd: () -> Unit,
    onDeleteTrack: (GBDeviceMusic) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Música guardada en el reloj. Sube pistas desde un fichero de audio del móvil o bórralas. " +
                "Todo en local: sin tienda online ni Huawei Health.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant,
        )

        StorageCard(deviceInfo = deviceInfo, trackCount = tracks.size, maxMusicCount = maxMusicCount)

        Button(onClick = onAdd) {
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
                            onDelete = { onDeleteTrack(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    loading: Boolean,
    playlists: List<GBDeviceMusicPlaylist>,
    maxPlaylistCount: Int,
    onCreate: () -> Unit,
    onRename: (GBDeviceMusicPlaylist) -> Unit,
    onDelete: (GBDeviceMusicPlaylist) -> Unit,
    onEdit: (GBDeviceMusicPlaylist) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Listas de reproducción guardadas en el reloj. Crea listas y añade o quita pistas de las " +
                "que ya has subido. Todo en local.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant,
        )

        val canCreate = playlists.size < maxPlaylistCount
        Button(onClick = onCreate, enabled = canCreate) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Crear lista")
        }

        val countLabel = "${playlists.size} / $maxPlaylistCount listas"
        Text(countLabel, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)

        Text("EN EL RELOJ", style = SectionLabelStyle, color = palette.onSurfaceVariant)

        when {
            loading && playlists.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.primary)
                }
            }

            playlists.isEmpty() -> {
                Text(
                    "No hay listas. Crea una con el botón de arriba.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onRename = { onRename(playlist) },
                            onDelete = { onDelete(playlist) },
                            onEdit = { onEdit(playlist) },
                        )
                    }
                }
            }
        }
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

@Composable
private fun PlaylistCard(
    playlist: GBDeviceMusicPlaylist,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (playlist.name ?: "").ifBlank { "(sin nombre)" },
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val count = playlist.trackIds().size
                Text(
                    if (count == 1) "1 pista" else "$count pistas",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Editar pistas", tint = palette.primary)
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Renombrar", tint = palette.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = palette.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        containerColor = palette.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(title, color = palette.onSurface) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nombre") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.onSurface,
                    unfocusedTextColor = palette.onSurface,
                    focusedBorderColor = palette.primary,
                    unfocusedBorderColor = palette.outline,
                    focusedLabelColor = palette.primary,
                    unfocusedLabelColor = palette.onSurfaceVariant,
                    cursorColor = palette.primary,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel, color = palette.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = palette.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun PlaylistContentDialog(
    playlist: GBDeviceMusicPlaylist,
    allTracks: List<GBDeviceMusic>,
    onDismiss: () -> Unit,
    onConfirm: (added: List<Int>, removed: List<Int>) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val original = remember(playlist) { playlist.trackIds().toSet() }
    var selected by remember(playlist) { mutableStateOf(original) }

    AlertDialog(
        containerColor = palette.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text("Editar \"${playlist.name}\"", color = palette.onSurface) },
        text = {
            if (allTracks.isEmpty()) {
                Text(
                    "No hay pistas subidas al reloj. Añade música desde la pestaña Pistas primero.",
                    color = palette.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(allTracks, key = { it.id }) { track ->
                        val checked = selected.contains(track.id)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selected = if (it) selected + track.id else selected - track.id
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = palette.primary,
                                    uncheckedColor = palette.outline,
                                    checkmarkColor = palette.onPrimary,
                                ),
                            )
                            Column(Modifier.weight(1f)) {
                                val label = (track.title ?: "").ifBlank { track.fileName ?: "" }
                                Text(
                                    label.ifBlank { "(sin título)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!track.artist.isNullOrBlank()) {
                                    Text(
                                        track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val added = (selected - original).toList()
                val removed = (original - selected).toList()
                onConfirm(added, removed)
            }) { Text("Guardar", color = palette.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = palette.onSurfaceVariant)
            }
        },
    )
}
