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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.Reminder
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.Reminder as ReminderModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Ultimate-styled reminder manager. Replaces the legacy ConfigureReminders/ReminderDetails screens;
 * reuses the Reminder entity, DBHelper persistence and the device service.
 */
class UltimateRemindersActivity : AppCompatActivity() {
    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateRemindersActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extra = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (extra == null) {
            finish()
            return
        }
        setContent { UltimateTheme { RemindersScreen(extra) { finish() } } }
    }
}

private val REPEAT_LABELS = arrayOf("Una vez", "Cada día", "Cada semana", "Cada mes", "Cada año")

private fun loadReminders(device: GBDevice): List<Reminder> = DBHelper.getReminders(device)

private fun pushReminders(device: GBDevice) {
    if (device.isInitialized) {
        GBApplication.deviceService(device).onSetReminders(ArrayList(loadReminders(device)))
    }
}

private fun createReminder(device: GBDevice): Reminder? {
    return try {
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val dbDevice = DBHelper.getDevice(device, session)
            val user = DBHelper.getUser(session)
            Reminder().apply {
                repetition = ReminderModel.ONCE
                if (device.deviceCoordinator.remindersHaveTime) {
                    date = Calendar.getInstance().time
                } else {
                    val noonUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    noonUtc.set(noonUtc.get(Calendar.YEAR), noonUtc.get(Calendar.MONTH), noonUtc.get(Calendar.DAY_OF_MONTH), 12, 0)
                    date = noonUtc.time
                }
                message = ""
                deviceId = dbDevice.id!!
                userId = user.id!!
                reminderId = UUID.randomUUID().toString()
            }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun RemindersScreen(device: GBDevice, onBack: () -> Unit) {
    val context = LocalContext.current
    val coordinator = device.deviceCoordinator
    var reminders by remember { mutableStateOf(loadReminders(device)) }
    var editing by remember { mutableStateOf<Reminder?>(null) }

    fun refresh() { reminders = loadReminders(device) }

    val editingReminder = editing
    if (editingReminder != null) {
        ReminderEditor(
            device = device,
            reminder = editingReminder,
            onCancel = { editing = null },
            onSave = {
                DBHelper.store(editingReminder)
                refresh()
                pushReminders(device)
                editing = null
            },
        )
        return
    }

    ClockScaffold(
        title = "Recordatorios",
        onBack = onBack,
        floatingActionButton = {
            val palette = LocalUltimatePalette.current
            FloatingActionButton(
                onClick = {
                    val slots = coordinator.getReminderSlotCount(device) -
                        GBApplication.getDevicePrefs(device).reservedReminderCalendarSlots
                    if (reminders.size >= slots) {
                        Toast.makeText(context, "No quedan huecos de recordatorio libres ($slots)", Toast.LENGTH_SHORT).show()
                    } else {
                        val r = createReminder(device)
                        if (r != null) editing = r
                        else Toast.makeText(context, "Error al crear el recordatorio", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = palette.primary,
                contentColor = palette.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Añadir recordatorio") }
        },
    ) {
        if (reminders.isEmpty()) {
            ReminderEmpty()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reminders, key = { it.reminderId }) { reminder ->
                    ReminderCard(
                        context = context,
                        reminder = reminder,
                        hasTime = coordinator.remindersHaveTime,
                        onClick = { editing = reminder },
                        onDelete = {
                            DBHelper.delete(reminder)
                            refresh()
                            pushReminders(device)
                        },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    context: Context,
    reminder: Reminder,
    hasTime: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val dateFmt = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (reminder.message ?: "").ifBlank { "(sin texto)" },
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                val date = reminder.date
                val timeStr = if (hasTime && date != null) {
                    "  ·  " + formatTime(context, date.hours, date.minutes)
                } else ""
                Text(
                    (if (date != null) dateFmt.format(date) else "") + timeStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
                if (reminder.repetition != ReminderModel.ONCE) {
                    Text(
                        REPEAT_LABELS[reminder.repetition.coerceIn(0, REPEAT_LABELS.size - 1)],
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = palette.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditor(
    device: GBDevice,
    reminder: Reminder,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val coordinator = device.deviceCoordinator
    val hasTime = coordinator.remindersHaveTime
    val maxLen = coordinator.maximumReminderMessageLength

    val initial = remember { reminder.date ?: Date() }
    var message by remember { mutableStateOf(reminder.message ?: "") }
    var repetition by remember { mutableStateOf(reminder.repetition) }
    var year by remember { mutableStateOf(initial.year + 1900) }
    var month by remember { mutableStateOf(initial.month) }
    var day by remember { mutableStateOf(initial.date) }
    var hour by remember { mutableStateOf(initial.hours) }
    var minute by remember { mutableStateOf(initial.minutes) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
    fun currentDate(): Date = GregorianCalendar(year, month, day, hour, minute).time

    ClockScaffold(
        title = "Recordatorio",
        onBack = onCancel,
        actions = {
            TextButton(onClick = {
                reminder.message = message
                reminder.repetition = repetition
                if (hasTime) {
                    reminder.date = GregorianCalendar(year, month, day, hour, minute).time
                } else {
                    val noonUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    noonUtc.set(year, month, day, 12, 0)
                    reminder.date = noonUtc.time
                }
                onSave()
            }) { Text("Guardar", color = palette.primary, fontWeight = FontWeight.Bold) }
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { if (maxLen <= 0 || it.length <= maxLen) message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Texto del recordatorio") },
            )
            PickerRow("Fecha", dateFmt.format(currentDate())) { showDatePicker = true }
            if (hasTime) {
                PickerRow("Hora", formatTime(context, hour, minute)) { showTimePicker = true }
            }
            PickerRow("Repetir", REPEAT_LABELS[repetition.coerceIn(0, REPEAT_LABELS.size - 1)]) { showRepeat = true }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = GregorianCalendar(year, month, day).timeInMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        c.timeInMillis = it
                        year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH); day = c.get(Calendar.DAY_OF_MONTH)
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = dpState) }
    }

    if (showTimePicker) {
        val tpState = rememberTimePickerState(hour, minute, DateFormat.is24HourFormat(context))
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { hour = tpState.hour; minute = tpState.minute; showTimePicker = false }) {
                    Text("Aceptar")
                }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") } },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = tpState) } },
        )
    }

    if (showRepeat) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRepeat = false },
            confirmButton = {},
            title = { Text("Repetir") },
            text = {
                Column {
                    REPEAT_LABELS.forEachIndexed { i, label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (i == repetition) palette.primary else palette.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { repetition = i; showRepeat = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ReminderEmpty() {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = palette.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Sin recordatorios", style = MaterialTheme.typography.titleLarge, color = palette.onSurface)
        Spacer(Modifier.height(6.dp))
        Text("Pulsa + para crear un recordatorio.", style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
    }
}
