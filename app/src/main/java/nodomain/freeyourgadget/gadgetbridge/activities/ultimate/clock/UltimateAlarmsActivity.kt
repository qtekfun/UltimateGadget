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
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.Alarm
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.Alarm as AlarmModel
import nodomain.freeyourgadget.gadgetbridge.util.AlarmUtils

/**
 * Ultimate-styled alarm manager. Replaces the legacy ConfigureAlarms/AlarmDetails screens; it
 * reuses Gadgetbridge's Alarm entity, DBHelper persistence, AlarmUtils and the device service, only
 * the presentation is new.
 */
class UltimateAlarmsActivity : AppCompatActivity() {
    private lateinit var device: GBDevice

    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateAlarmsActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extra = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (extra == null) {
            finish()
            return
        }
        device = extra
        setContent { UltimateTheme { AlarmsScreen(device) { finish() } } }
    }
}

private fun loadAlarms(device: GBDevice): List<Alarm> = DBHelper.getAlarmsWithDefaults(device)

private fun pushAlarms(device: GBDevice) {
    if (device.isInitialized) {
        GBApplication.deviceService(device).onSetAlarms(ArrayList(loadAlarms(device)))
    }
}

@Composable
private fun AlarmsScreen(device: GBDevice, onBack: () -> Unit) {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(loadAlarms(device)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }

    fun refresh() { alarms = loadAlarms(device) }

    val editingAlarm = editing
    if (editingAlarm != null) {
        AlarmEditor(
            device = device,
            alarm = editingAlarm,
            onCancel = { editing = null },
            onSave = {
                DBHelper.store(editingAlarm)
                refresh()
                pushAlarms(device)
                editing = null
            },
        )
        return
    }

    val used = alarms.filter { !it.unused }
        .sortedWith(compareBy({ it.hour }, { it.minute }))

    ClockScaffold(
        title = "Alarmas",
        onBack = onBack,
        floatingActionButton = {
            val palette = LocalUltimatePalette.current
            FloatingActionButton(
                onClick = {
                    val free = alarms.firstOrNull { it.unused }
                    if (free != null) editing = free
                    else Toast.makeText(context, "No quedan huecos de alarma libres", Toast.LENGTH_SHORT).show()
                },
                containerColor = palette.primary,
                contentColor = palette.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Añadir alarma") }
        },
    ) {
        if (used.isEmpty()) {
            EmptyState("Sin alarmas", "Pulsa + para crear tu primera alarma.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(used, key = { it.position }) { alarm ->
                    AlarmCard(
                        context = context,
                        alarm = alarm,
                        onToggle = { enabled ->
                            alarm.enabled = enabled
                            DBHelper.store(alarm)
                            refresh()
                            pushAlarms(device)
                        },
                        onClick = { editing = alarm },
                        onDelete = {
                            alarm.unused = true
                            alarm.enabled = false
                            alarm.repetition = AlarmModel.ALARM_ONCE.toInt()
                            DBHelper.store(alarm)
                            refresh()
                            pushAlarms(device)
                        },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    context: Context,
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        formatTime(context, alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (alarm.enabled) palette.onSurface else palette.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    val title = alarm.title
                    if (!title.isNullOrBlank()) {
                        Text(title, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
                    }
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEKDAY_BITS.forEachIndexed { i, bit ->
                        val on = (alarm.repetition and bit) != 0
                        DayDot(WEEKDAY_LABELS[i], on)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = palette.error)
                }
            }
        }
    }
}

@Composable
private fun DayDot(label: String, on: Boolean) {
    val palette = LocalUltimatePalette.current
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (on) Modifier.background(palette.primaryContainer)
                else Modifier.border(1.dp, palette.outlineVariant, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) palette.onPrimaryContainer else palette.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    device: GBDevice,
    alarm: Alarm,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val coordinator = device.deviceCoordinator

    val timeState = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    val days = remember {
        androidx.compose.runtime.mutableStateListOf(
            *WEEKDAY_BITS.map { (alarm.repetition and it) != 0 }.toTypedArray(),
        )
    }
    var snooze by remember { mutableStateOf(alarm.snooze) }
    var smartWakeup by remember { mutableStateOf(alarm.smartWakeup) }
    var title by remember { mutableStateOf(alarm.title ?: "") }

    val supportsSnooze = coordinator.supportsAlarmSnoozing(device)
    val supportsSmart = coordinator.supportsSmartWakeup(device, alarm.position)
    val forcedSmart = coordinator.forcedSmartWakeup(device, alarm.position)
    val supportsTitle = coordinator.supportsAlarmTitle(device)
    val titleLimit = coordinator.getAlarmTitleLimit(device)

    ClockScaffold(
        title = if (alarm.unused) "Nueva alarma" else "Editar alarma",
        onBack = onCancel,
        actions = {
            TextButton(onClick = {
                alarm.hour = timeState.hour
                alarm.minute = timeState.minute
                alarm.repetition = AlarmUtils.createRepetitionMask(
                    days[0], days[1], days[2], days[3], days[4], days[5], days[6],
                )
                if (alarm.unused) {
                    alarm.unused = false
                    alarm.enabled = true
                }
                alarm.snooze = supportsSnooze && snooze
                alarm.smartWakeup = supportsSmart && (smartWakeup || forcedSmart)
                if (supportsTitle) alarm.title = title
                onSave()
            }) { Text("Guardar", color = palette.primary, fontWeight = FontWeight.Bold) }
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }

            EditorSection("Repetir") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEEKDAY_LABELS.forEachIndexed { i, label ->
                        val on = days[i]
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .then(
                                    if (on) Modifier.background(palette.primaryContainer)
                                    else Modifier.border(1.dp, palette.outlineVariant, CircleShape),
                                )
                                .clickable { days[i] = !days[i] },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (on) palette.onPrimaryContainer else palette.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (supportsSmart) {
                ToggleRow(
                    title = "Despertador inteligente",
                    subtitle = if (forcedSmart) "Obligatorio en este hueco" else "Despierta en fase de sueño ligero",
                    checked = smartWakeup || forcedSmart,
                    enabled = !forcedSmart,
                    onChange = { smartWakeup = it },
                )
            }
            if (supportsSnooze) {
                ToggleRow(
                    title = "Posponer",
                    subtitle = "Permitir aplazar la alarma",
                    checked = snooze,
                    onChange = { snooze = it },
                )
            }
            if (supportsTitle) {
                EditorSection("Etiqueta") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (titleLimit <= 0 || it.length <= titleLimit) title = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Nombre de la alarma") },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    val palette = LocalUltimatePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
internal fun EmptyState(title: String, subtitle: String) {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Alarm, contentDescription = null, tint = palette.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = palette.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
    }
}
