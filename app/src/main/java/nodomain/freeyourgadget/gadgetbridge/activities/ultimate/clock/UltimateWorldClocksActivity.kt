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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.clock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.WorldClock
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Ultimate-styled world clock manager. Replaces the legacy ConfigureWorldClocks/WorldClockDetails
 * screens; reuses the WorldClock entity, DBHelper persistence and the device service.
 */
class UltimateWorldClocksActivity : AppCompatActivity() {
    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateWorldClocksActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extra = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (extra == null) {
            finish()
            return
        }
        setContent { UltimateTheme { WorldClocksScreen(extra) { finish() } } }
    }
}

private fun loadClocks(device: GBDevice): List<WorldClock> = DBHelper.getWorldClocks(device)

private fun pushClocks(device: GBDevice) {
    if (device.isInitialized) {
        GBApplication.deviceService(device).onSetWorldClocks(ArrayList(loadClocks(device)))
    }
}

private fun createClock(device: GBDevice): WorldClock? {
    return try {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val dbDevice = DBHelper.getDevice(device, session)
            val user = DBHelper.getUser(session)
            val tz = TimeZone.getDefault().id
            val city = tz.split("/").last()
            WorldClock().apply {
                enabled = true
                timeZoneId = tz
                label = city
                code = city.take(3).uppercase(Locale.getDefault())
                deviceId = dbDevice.id!!
                userId = user.id!!
                worldClockId = UUID.randomUUID().toString()
            }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun WorldClocksScreen(device: GBDevice, onBack: () -> Unit) {
    val context = LocalContext.current
    val coordinator = device.deviceCoordinator
    var clocks by remember { mutableStateOf(loadClocks(device)) }
    var editing by remember { mutableStateOf<WorldClock?>(null) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }

    fun refresh() { clocks = loadClocks(device) }

    val editingClock = editing
    if (editingClock != null) {
        WorldClockEditor(
            device = device,
            clock = editingClock,
            onCancel = { editing = null },
            onSave = {
                DBHelper.store(editingClock)
                refresh()
                pushClocks(device)
                editing = null
            },
        )
        return
    }

    ClockScaffold(
        title = "Relojes mundiales",
        onBack = onBack,
        floatingActionButton = {
            val palette = LocalUltimatePalette.current
            FloatingActionButton(
                onClick = {
                    val slots = coordinator.worldClocksSlotCount
                    if (clocks.size >= slots) {
                        Toast.makeText(context, "No quedan huecos de reloj libres ($slots)", Toast.LENGTH_SHORT).show()
                    } else {
                        val c = createClock(device)
                        if (c != null) editing = c
                        else Toast.makeText(context, "Error al crear el reloj", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = palette.primary,
                contentColor = palette.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Añadir reloj") }
        },
    ) {
        if (clocks.isEmpty()) {
            WorldClockEmpty()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(clocks, key = { it.worldClockId }) { clock ->
                    WorldClockCard(
                        clock = clock,
                        tick = tick,
                        onClick = { editing = clock },
                        onDelete = {
                            DBHelper.delete(clock)
                            refresh()
                            pushClocks(device)
                        },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun WorldClockCard(
    clock: WorldClock,
    tick: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val timeStr = remember(clock.timeZoneId, tick) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        fmt.timeZone = TimeZone.getTimeZone(clock.timeZoneId)
        fmt.format(Date(tick))
    }
    val enabled = clock.enabled ?: true
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    clock.label ?: clock.timeZoneId,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) palette.onSurface else palette.onSurfaceVariant,
                )
                Text(clock.timeZoneId, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }
            Text(
                timeStr,
                style = MaterialTheme.typography.headlineMedium,
                color = if (enabled) palette.primary else palette.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = palette.error)
            }
        }
    }
}

@Composable
private fun WorldClockEditor(
    device: GBDevice,
    clock: WorldClock,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val coordinator = device.deviceCoordinator
    val labelLimit = coordinator.worldClocksLabelLength
    val supportsDisabled = coordinator.supportsDisabledWorldClocks(device)

    var timeZoneId by remember { mutableStateOf(clock.timeZoneId) }
    var label by remember { mutableStateOf(clock.label ?: "") }
    var code by remember { mutableStateOf(clock.code ?: "") }
    var enabled by remember { mutableStateOf(clock.enabled ?: true) }
    var showTzPicker by remember { mutableStateOf(false) }

    ClockScaffold(
        title = "Reloj mundial",
        onBack = onCancel,
        actions = {
            TextButton(onClick = {
                clock.timeZoneId = timeZoneId
                clock.label = label
                clock.code = code
                clock.enabled = enabled
                onSave()
            }) { Text("Guardar", color = palette.primary, fontWeight = FontWeight.Bold) }
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PickerRow("Zona horaria", timeZoneId) { showTzPicker = true }
            OutlinedTextField(
                value = label,
                onValueChange = { if (labelLimit <= 0 || it.length <= labelLimit) label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Etiqueta") },
                singleLine = true,
            )
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 3) code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Código (3 letras)") },
                singleLine = true,
            )
            if (supportsDisabled) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Activado", style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                        Text("Mostrar este reloj en el dispositivo", style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        }
    }

    if (showTzPicker) {
        TimezonePickerDialog(
            current = timeZoneId,
            onDismiss = { showTzPicker = false },
            onPick = { picked ->
                val oldCity = timeZoneId.split("/").last()
                val newCity = picked.split("/").last()
                // If label/code were still the default (derived from the old city), follow the new city.
                if (label.isBlank() || label == oldCity.take(labelLimit.coerceAtLeast(1))) {
                    label = newCity.take(if (labelLimit > 0) labelLimit else newCity.length)
                }
                if (code.isBlank() || code.equals(oldCity.take(3), ignoreCase = true)) {
                    code = newCity.take(3).uppercase(Locale.getDefault())
                }
                timeZoneId = picked
                showTzPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimezonePickerDialog(
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val allIds = remember { TimeZone.getAvailableIDs().sorted() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) allIds else allIds.filter { it.contains(query, ignoreCase = true) }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Zona horaria") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(filtered, key = { it }) { id ->
                        Text(
                            id,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (id == current) palette.primary else palette.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun WorldClockEmpty() {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Public, contentDescription = null, tint = palette.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Sin relojes mundiales", style = MaterialTheme.typography.titleLarge, color = palette.onSurface)
        Spacer(Modifier.height(6.dp))
        Text("Pulsa + para añadir una zona horaria.", style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
    }
}
