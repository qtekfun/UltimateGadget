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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Ultimate-styled countdown timers.
 *
 * Gadgetbridge has no generic "timer"/"countdown" model, EventHandler command or DeviceCoordinator
 * capability (only alarms, reminders and world clocks can be pushed to a watch), so there is no
 * watch protocol to drive here. Rather than invent one, this screen is an honest *phone-local*
 * countdown: timers are created, listed, started/paused and deleted on the phone, persisted in
 * SharedPreferences, and when one reaches zero (while this screen is open) the phone vibrates and
 * posts a notification. Nothing is sent to the watch.
 */
class UltimateTimersActivity : AppCompatActivity() {
    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateTimersActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        setContent { UltimateTheme { TimersScreen { finish() } } }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Model + persistence (phone-local, SharedPreferences backed)
// ---------------------------------------------------------------------------------------------

private const val PREFS_NAME = "ultimate_phone_timers"
private const val PREFS_KEY = "timers"

/**
 * A phone-local countdown timer.
 *
 * - IDLE:    [endAtMs] == null and [pausedMs] == null, remaining == [durationMs]
 * - RUNNING: [endAtMs] != null, remaining == endAtMs - now
 * - PAUSED:  [pausedMs] != null, remaining == pausedMs
 */
private data class PhoneTimer(
    val id: String,
    val label: String,
    val durationMs: Long,
    val endAtMs: Long? = null,
    val pausedMs: Long? = null,
) {
    val isRunning: Boolean get() = endAtMs != null
    val isPaused: Boolean get() = pausedMs != null

    fun remainingMs(now: Long): Long = when {
        endAtMs != null -> (endAtMs - now).coerceAtLeast(0L)
        pausedMs != null -> pausedMs
        else -> durationMs
    }
}

private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun loadTimers(context: Context): List<PhoneTimer> {
    return try {
        val raw = prefs(context).getString(PREFS_KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    PhoneTimer(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        label = o.optString("label", ""),
                        durationMs = o.optLong("durationMs", 0L),
                        endAtMs = if (o.has("endAtMs") && !o.isNull("endAtMs")) o.getLong("endAtMs") else null,
                        pausedMs = if (o.has("pausedMs") && !o.isNull("pausedMs")) o.getLong("pausedMs") else null,
                    ),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveTimers(context: Context, timers: List<PhoneTimer>) {
    try {
        val arr = JSONArray()
        timers.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("label", t.label)
            o.put("durationMs", t.durationMs)
            if (t.endAtMs != null) o.put("endAtMs", t.endAtMs)
            if (t.pausedMs != null) o.put("pausedMs", t.pausedMs)
            arr.put(o)
        }
        prefs(context).edit().putString(PREFS_KEY, arr.toString()).apply()
    } catch (e: Exception) {
        // Best-effort persistence; losing a phone timer is non-critical.
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSec = (ms + 999) / 1000 // round up so "00:00" only shows at the very end
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}

private fun vibrate(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        val pattern = longArrayOf(0, 400, 200, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    } catch (e: Exception) {
        // ignore
    }
}

private fun notifyFinished(context: Context, timer: PhoneTimer) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val label = timer.label.ifBlank { "Temporizador" }
        val builder = androidx.core.app.NotificationCompat.Builder(
            context, GB.NOTIFICATION_CHANNEL_HIGH_PRIORITY_ID,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Temporizador finalizado")
            .setContentText(label)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context)
            .notify(("ultimate_timer_" + timer.id).hashCode(), builder.build())
    } catch (e: Exception) {
        // ignore
    }
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

@Composable
private fun TimersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var timers by remember { mutableStateOf(loadTimers(context)) }
    var editing by remember { mutableStateOf<PhoneTimer?>(null) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Tracks timers already reported as finished so we vibrate/notify exactly once.
    val fired = remember { mutableSetOf<String>() }

    fun persist(list: List<PhoneTimer>) {
        timers = list
        saveTimers(context, list)
    }

    // 500ms ticker: refresh the clock and fire finished timers.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            tick = now
            var mutated = false
            val updated = timers.map { t ->
                if (t.isRunning && t.remainingMs(now) <= 0L) {
                    if (fired.add(t.id)) {
                        vibrate(context)
                        notifyFinished(context, t)
                    }
                    mutated = true
                    t.copy(endAtMs = null, pausedMs = null) // back to IDLE, ready to reuse
                } else {
                    t
                }
            }
            if (mutated) persist(updated)
            delay(500)
        }
    }

    val editingTimer = editing
    if (editingTimer != null) {
        TimerEditor(
            timer = editingTimer,
            onCancel = { editing = null },
            onSave = { label, durationMs ->
                val existing = timers.any { it.id == editingTimer.id }
                val updated = if (existing) {
                    timers.map {
                        if (it.id == editingTimer.id) {
                            it.copy(label = label, durationMs = durationMs, endAtMs = null, pausedMs = null)
                        } else {
                            it
                        }
                    }
                } else {
                    timers + editingTimer.copy(label = label, durationMs = durationMs)
                }
                fired.remove(editingTimer.id)
                persist(updated)
                editing = null
            },
        )
        return
    }

    ClockScaffold(
        title = "Temporizadores",
        onBack = onBack,
        floatingActionButton = {
            val palette = LocalUltimatePalette.current
            FloatingActionButton(
                onClick = {
                    editing = PhoneTimer(
                        id = UUID.randomUUID().toString(),
                        label = "",
                        durationMs = 5 * 60_000L,
                    )
                },
                containerColor = palette.primary,
                contentColor = palette.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Añadir temporizador") }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            LocalTimerNotice()
            if (timers.isEmpty()) {
                TimersEmpty()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(timers, key = { it.id }) { timer ->
                        TimerCard(
                            timer = timer,
                            tick = tick,
                            onStartPause = {
                                val now = System.currentTimeMillis()
                                val updated = timers.map { t ->
                                    if (t.id != timer.id) {
                                        t
                                    } else if (t.isRunning) {
                                        // pause
                                        t.copy(pausedMs = t.remainingMs(now), endAtMs = null)
                                    } else {
                                        // start (from idle or paused)
                                        val remaining = t.remainingMs(now).let { if (it <= 0L) t.durationMs else it }
                                        fired.remove(t.id)
                                        t.copy(endAtMs = now + remaining, pausedMs = null)
                                    }
                                }
                                persist(updated)
                            },
                            onReset = {
                                fired.remove(timer.id)
                                persist(timers.map { if (it.id == timer.id) it.copy(endAtMs = null, pausedMs = null) else it })
                            },
                            onEdit = { editing = timer },
                            onDelete = {
                                fired.remove(timer.id)
                                persist(timers.filterNot { it.id == timer.id })
                            },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LocalTimerNotice() {
    val palette = LocalUltimatePalette.current
    Text(
        "Temporizador local del teléfono. El reloj no admite temporizadores, así que la cuenta atrás corre en el móvil y avisa aquí.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun TimerCard(
    timer: PhoneTimer,
    tick: Long,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val remaining = timer.remainingMs(tick)
    val active = timer.isRunning
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        formatRemaining(remaining),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (active) palette.primary else palette.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    val label = timer.label.ifBlank { "Temporizador" }
                    val state = when {
                        timer.isRunning -> "En marcha"
                        timer.isPaused -> "En pausa"
                        else -> "Listo · ${formatRemaining(timer.durationMs)}"
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                    Text(state, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = palette.error)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(onClick = onStartPause) {
                    if (active) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pausar")
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Iniciar")
                    }
                }
                IconButton(onClick = onReset) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reiniciar", tint = palette.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TimerEditor(
    timer: PhoneTimer,
    onCancel: () -> Unit,
    onSave: (label: String, durationMs: Long) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val isNew = timer.durationMs == 5 * 60_000L && timer.label.isBlank() && !timer.isRunning && !timer.isPaused

    val initialTotal = timer.durationMs / 1000
    var hours by remember { mutableStateOf((initialTotal / 3600).toInt()) }
    var minutes by remember { mutableStateOf(((initialTotal % 3600) / 60).toInt()) }
    var seconds by remember { mutableStateOf((initialTotal % 60).toInt()) }
    var label by remember { mutableStateOf(timer.label) }

    ClockScaffold(
        title = if (isNew) "Nuevo temporizador" else "Editar temporizador",
        onBack = onCancel,
        actions = {
            TextButton(onClick = {
                val durationMs = ((hours * 3600 + minutes * 60 + seconds).coerceAtLeast(1)).toLong() * 1000L
                onSave(label.trim(), durationMs)
            }) { Text("Guardar", color = palette.primary, fontWeight = FontWeight.Bold) }
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("DURACIÓN", style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DurationStepper("Horas", hours, 0, 23, Modifier.weight(1f)) { hours = it }
                DurationStepper("Min", minutes, 0, 59, Modifier.weight(1f)) { minutes = it }
                DurationStepper("Seg", seconds, 0, 59, Modifier.weight(1f)) { seconds = it }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { if (it.length <= 40) label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Etiqueta") },
                placeholder = { Text("Nombre del temporizador") },
                singleLine = true,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DurationStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            IconButton(onClick = { onChange(if (value >= max) min else value + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "Más", tint = palette.primary)
            }
            Text(
                String.format(Locale.getDefault(), "%02d", value),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onSurface,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { onChange(if (value <= min) max else value - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Menos", tint = palette.primary)
            }
        }
    }
}

@Composable
private fun TimersEmpty() {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = palette.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Sin temporizadores", style = MaterialTheme.typography.titleLarge, color = palette.onSurface)
        Spacer(Modifier.height(6.dp))
        Text("Pulsa + para crear una cuenta atrás.", style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
    }
}
