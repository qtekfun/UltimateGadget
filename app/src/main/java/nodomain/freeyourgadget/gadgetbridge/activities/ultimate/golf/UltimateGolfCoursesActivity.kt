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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiConstants
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.p2p.HuaweiP2PGolfService
import java.io.File

/**
 * Golf courses stored on a Huawei watch, in the UltimateGadget look. Lists the courses on the
 * watch, lets you import a course map .bin (downloaded with the map companion) and send it, and
 * delete a course — all over the golf P2P channel, no Huawei Health needed.
 */
class UltimateGolfCoursesActivity : AppCompatActivity() {

    data class CourseItem(val courseId: Int, val version: Int)

    private var device: GBDevice? = null
    private var items by mutableStateOf<List<CourseItem>>(emptyList())
    private var loaded by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var selectMode by mutableStateOf(false)
    private var selected by mutableStateOf<Set<Int>>(emptySet())
    // When set, the next course-list refresh shows this as a confirmation with the resulting count.
    private var pendingAction by mutableStateOf("")

    private val listReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ids = intent.getIntArrayExtra(HuaweiP2PGolfService.EXTRA_GOLF_COURSE_IDS)
            val versions = intent.getIntArrayExtra(HuaweiP2PGolfService.EXTRA_GOLF_COURSE_VERSIONS)
            val list = mutableListOf<CourseItem>()
            if (ids != null) {
                for (i in ids.indices) list.add(CourseItem(ids[i], versions?.getOrNull(i) ?: 0))
            }
            items = list.sortedBy { it.courseId }
            loaded = true
            if (pendingAction.isNotBlank()) {
                status = "$pendingAction ✓ · el reloj tiene ${list.size} campo(s)"
                Toast.makeText(
                    this@UltimateGolfCoursesActivity,
                    "$pendingAction ✓ · el reloj tiene ${list.size} campo(s)",
                    Toast.LENGTH_LONG,
                ).show()
                pendingAction = ""
            }
        }
    }

    // Cache of courseId -> friendly name, learned from the file names of courses sent from here.
    private var names by mutableStateOf<Map<Int, String>>(emptyMap())

    private fun namesPrefs() = getSharedPreferences("huawei_golf_names", Context.MODE_PRIVATE)

    private fun loadNames() {
        names = namesPrefs().all.mapNotNull { (k, v) ->
            val id = k.toIntOrNull() ?: return@mapNotNull null
            val n = v as? String ?: return@mapNotNull null
            id to n
        }.toMap()
    }

    private fun saveName(courseId: Int, name: String) {
        if (name.isBlank()) return
        namesPrefs().edit().putString(courseId.toString(), name).apply()
        names = names + (courseId to name)
    }

    /** Course label: cached friendly name if known, else the numeric id. */
    private fun labelFor(courseId: Int): String = names[courseId] ?: "Campo $courseId"

    private val pickBin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importAndSend(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE)
        loadNames()
        setContent {
            UltimateTheme {
                DisposableEffect(Unit) {
                    val lbm = LocalBroadcastManager.getInstance(this@UltimateGolfCoursesActivity)
                    lbm.registerReceiver(listReceiver, IntentFilter(HuaweiP2PGolfService.ACTION_GOLF_COURSE_LIST))
                    requestList()
                    onDispose { lbm.unregisterReceiver(listReceiver) }
                }
                GolfScreen()
            }
        }
    }

    private fun requestList() {
        val d = device
        if (d == null || !d.isInitialized) {
            loaded = true
            return
        }
        GBApplication.deviceService(d).onSendConfiguration(HuaweiConstants.PREF_HUAWEI_GOLF_LIST)
    }

    private fun delete(item: CourseItem) {
        val d = device ?: return
        GBApplication.deviceService(d).onSendConfiguration(
            HuaweiConstants.PREF_HUAWEI_GOLF_DELETE_PREFIX + item.courseId,
        )
        status = "Borrando ${labelFor(item.courseId)}…"
        pendingAction = "Borrado 1 campo"
        items = items.filterNot { it.courseId == item.courseId }
        // Re-query shortly so the list reflects the watch's authoritative state.
        d.let { requestListDelayed() }
    }

    private fun requestListDelayed(delayMs: Long = 2500) {
        android.os.Handler(mainLooper).postDelayed({ requestList() }, delayMs)
    }

    private fun toggle(courseId: Int) {
        selected = if (courseId in selected) selected - courseId else selected + courseId
    }

    /** Bulk-delete the selected courses in a single request (type 15 with N course ids). */
    private fun deleteSelected() {
        val d = device ?: return
        if (selected.isEmpty()) return
        val ids = selected.toList()
        GBApplication.deviceService(d).onSendConfiguration(
            HuaweiConstants.PREF_HUAWEI_GOLF_DELETE_PREFIX + ids.joinToString(","),
        )
        status = "Borrando ${ids.size} campo(s)…"
        pendingAction = "Borrados ${ids.size} campo(s)"
        items = items.filterNot { it.courseId in selected }
        selected = emptySet()
        selectMode = false
        requestListDelayed()
    }

    /** Copy the picked .bin to a local file and send it, parsing courseId + version from the name. */
    private fun importAndSend(uri: Uri) {
        val d = device
        if (d == null || !d.isInitialized) {
            Toast.makeText(this, "Reloj no conectado", Toast.LENGTH_SHORT).show()
            return
        }
        val name = queryDisplayName(uri) ?: "course.bin"
        val courseId = Regex("\\((\\d+)\\)").findAll(name).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        if (courseId == null) {
            Toast.makeText(this, "El fichero debe incluir el id del campo, p. ej. '(22261)'", Toast.LENGTH_LONG).show()
            return
        }
        val version = Regex("v(\\d{6,})").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        // Friendly name = file name without the "(id) vXXXX.bin" tail, so the list can show it.
        val friendly = name.substringBefore(" (").substringBeforeLast('.').trim()
        if (friendly.isNotBlank()) saveName(courseId, friendly)
        try {
            val dest = File(cacheDir, "golf_import_$courseId.bin")
            contentResolver.openInputStream(uri)!!.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            GBApplication.deviceService(d).onSendConfiguration(
                HuaweiConstants.PREF_HUAWEI_GOLF_SEND_PREFIX + dest.absolutePath + "|" + courseId + "|" + version,
            )
            status = "Enviando ${labelFor(courseId)} al reloj…"
            pendingAction = "Enviado ${labelFor(courseId)}"
            Toast.makeText(this, "Enviando ${labelFor(courseId)}…", Toast.LENGTH_SHORT).show()
            // The service broadcasts the refreshed list when the upload actually completes; this is
            // only a long fallback in case the upload never reports completion.
            requestListDelayed(20000)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer el fichero: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return uri.lastPathSegment
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GolfScreen() {
        val palette = LocalUltimatePalette.current
        var pendingDelete by remember { mutableStateOf<CourseItem?>(null) }
        var pendingBulk by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = palette.background,
            topBar = {
                TopAppBar(
                    title = { Text(if (selectMode) "${selected.size} seleccionados" else "Campos de golf", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { if (selectMode) { selectMode = false; selected = emptySet() } else finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (items.isNotEmpty()) {
                            if (selectMode) {
                                TextButton(onClick = { selected = items.map { it.courseId }.toSet() }) { Text("Todos") }
                                TextButton(enabled = selected.isNotEmpty(), onClick = { pendingBulk = true }) {
                                    Text("Borrar (${selected.size})", color = if (selected.isNotEmpty()) palette.error else palette.onSurfaceVariant)
                                }
                            } else {
                                TextButton(onClick = { selectMode = true }) { Text("Seleccionar") }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.background,
                        titleContentColor = palette.onSurface,
                        navigationIconContentColor = palette.onSurface,
                        actionIconContentColor = palette.onSurface,
                    ),
                )
            },
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize().padding(16.dp)) {
                if (!selectMode) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.primary, RoundedCornerShape(16.dp))
                            .clickable(enabled = device?.isInitialized == true) { pickBin.launch(arrayOf("*/*")) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Importar y enviar mapa de campo", color = palette.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Elige un .bin descargado con la app de mapas (su nombre lleva el id del campo). El reloj lo mostrará en su app de golf.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = palette.secondary)
                }

                Text(
                    "EN EL RELOJ",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (!loaded) "Cargando…"
                            else if (device?.isInitialized != true) "Reloj no conectado"
                            else "No hay campos en el reloj",
                            color = palette.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items, key = { it.courseId }) { item ->
                            val isSel = item.courseId in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (isSel) palette.secondaryContainer else palette.surfaceContainer, RoundedCornerShape(16.dp))
                                    .clickable(enabled = selectMode) { toggle(item.courseId) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (selectMode) {
                                    Checkbox(checked = isSel, onCheckedChange = { toggle(item.courseId) })
                                }
                                Column(Modifier.weight(1f).padding(start = if (selectMode) 8.dp else 0.dp)) {
                                    Text(labelFor(item.courseId), style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                                    Text("id ${item.courseId} · v${item.version}", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                                }
                                if (!selectMode) {
                                    IconButton(onClick = { pendingDelete = item }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = palette.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        pendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = palette.surfaceContainer,
                title = { Text("Borrar campo") },
                text = { Text("¿Quitar ${labelFor(item.courseId)} del reloj?") },
                confirmButton = {
                    TextButton(onClick = { delete(item); pendingDelete = null }) {
                        Text("Borrar", color = palette.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text(getString(android.R.string.cancel)) }
                },
            )
        }

        if (pendingBulk) {
            AlertDialog(
                onDismissRequest = { pendingBulk = false },
                containerColor = palette.surfaceContainer,
                title = { Text("Borrar campos") },
                text = { Text("¿Quitar ${selected.size} campo(s) del reloj?") },
                confirmButton = {
                    TextButton(onClick = { deleteSelected(); pendingBulk = false }) {
                        Text("Borrar", color = palette.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingBulk = false }) { Text(getString(android.R.string.cancel)) }
                },
            )
        }
    }

    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateGolfCoursesActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}
