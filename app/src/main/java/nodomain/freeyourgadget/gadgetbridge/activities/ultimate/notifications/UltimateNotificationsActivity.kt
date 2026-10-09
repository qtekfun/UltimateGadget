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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.notifications

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.SectionLabelStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.util.NotificationUtils
import java.text.DateFormatSymbols

/**
 * Ultimate notifications management: a phone-global (not per-device) screen to choose which apps
 * forward their notifications to the watch, plus a quiet-hours ("No molestar") rule.
 *
 * "Enviar al reloj" is backed by Gadgetbridge's existing notification blacklist
 * (an app in the blacklist does NOT notify). The quiet-hours rule is persisted by
 * [NotificationRulesStore] under its documented keys.
 */
class UltimateNotificationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltimateTheme { NotificationsRoot(onBack = { finish() }) } }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, UltimateNotificationsActivity::class.java)
    }
}

private enum class NotifPane { APPS, RULES }

@Composable
private fun NotificationsRoot(onBack: () -> Unit) {
    var pane by remember { mutableStateOf(NotifPane.APPS) }
    when (pane) {
        NotifPane.APPS -> AppsScreen(onBack = onBack, onOpenRules = { pane = NotifPane.RULES })
        NotifPane.RULES -> RulesScreen(onBack = { pane = NotifPane.APPS })
    }
}

// ---------------------------------------------------------------------------
// App list
// ---------------------------------------------------------------------------

private data class AppRow(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsScreen(onBack: () -> Unit, onOpenRules: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current

    var query by remember { mutableStateOf("") }
    // Toggling a switch mutates the global blacklist; bump this to recompute the derived states.
    var revision by remember { mutableStateOf(0) }

    val apps by produceState<List<AppRow>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            NotificationUtils.getAllApplications(context)
                .map { pkg ->
                    AppRow(pkg, NotificationUtils.getApplicationLabel(context, pkg) ?: pkg)
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenRules) {
                        Icon(Icons.Filled.Bedtime, contentDescription = "Reglas de horario")
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
            Text(
                "Elige qué apps envían sus notificaciones al reloj.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Buscar app…") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.primary)
                }
            } else {
                val filtered = remember(list, query) {
                    val q = query.trim().lowercase()
                    if (q.isEmpty()) list
                    else list.filter {
                        it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.packageName }) { row ->
                        AppListItem(row = row, revision = revision, onToggled = { revision++ })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(row: AppRow, revision: Int, onToggled: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current

    // "send to watch" == NOT blacklisted. Keyed on revision so it refreshes after toggles.
    val sendToWatch = remember(row.packageName, revision) {
        !GBApplication.appIsNotifBlacklisted(row.packageName)
    }

    val icon by produceState<BitmapPainter?>(initialValue = null, row.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                NotificationUtils.getAppIcon(context, row.packageName)
                    ?.toBitmap(96, 96)?.asImageBitmap()?.let { BitmapPainter(it) }
            }.getOrNull()
        }
    }

    fun toggle() {
        if (sendToWatch) {
            // currently sending -> stop: add to blacklist
            GBApplication.addAppToNotifBlacklist(row.packageName)
        } else {
            GBApplication.removeFromAppsNotifBlacklist(row.packageName)
        }
        onToggled()
    }

    Card(
        Modifier.fillMaxWidth().clickable { toggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val p = icon
                if (p != null) {
                    androidx.compose.foundation.Image(
                        painter = p,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                    maxLines = 1,
                )
                Text(
                    row.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(
                checked = sendToWatch,
                onCheckedChange = { toggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.onPrimary,
                    checkedTrackColor = palette.primary,
                    uncheckedThumbColor = palette.onSurfaceVariant,
                    uncheckedTrackColor = palette.surfaceHigh,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Quiet-hours rules
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RulesScreen(onBack: () -> Unit) {
    val palette = LocalUltimatePalette.current

    var rule by remember { mutableStateOf(NotificationRulesStore.load()) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    fun update(newRule: NotificationRulesStore.QuietHours) {
        rule = newRule
        NotificationRulesStore.save(newRule)
    }

    val weekdayNames = remember { DateFormatSymbols.getInstance().shortWeekdays } // [1..7] used

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("No molestar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
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
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Durante este horario no se enviarán notificaciones al reloj.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Activar horario",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.onSurface,
                            )
                            Text(
                                "No molestar en el rango elegido",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { update(rule.copy(enabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.onPrimary,
                                checkedTrackColor = palette.primary,
                                uncheckedThumbColor = palette.onSurfaceVariant,
                                uncheckedTrackColor = palette.surfaceHigh,
                            ),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField(
                            label = "Inicio",
                            value = NotificationRulesStore.formatMinutes(rule.startMinutes),
                            enabled = rule.enabled,
                            modifier = Modifier.weight(1f),
                            onClick = { pickStart = true },
                        )
                        TimeField(
                            label = "Fin",
                            value = NotificationRulesStore.formatMinutes(rule.endMinutes),
                            enabled = rule.enabled,
                            modifier = Modifier.weight(1f),
                            onClick = { pickEnd = true },
                        )
                    }
                }
            }

            Text(
                "DÍAS",
                style = SectionLabelStyle,
                color = palette.onSurfaceVariant,
            )
            // Calendar.DAY_OF_WEEK order: 1 = Sunday … 7 = Saturday
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (day in 1..7) {
                    val selected = day in rule.days
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val days = rule.days.toMutableSet()
                            if (selected) days.remove(day) else days.add(day)
                            update(rule.copy(days = days))
                        },
                        enabled = rule.enabled,
                        label = { Text(weekdayNames.getOrElse(day) { "" }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.primaryContainer,
                            selectedLabelColor = palette.onPrimaryContainer,
                            labelColor = palette.onSurfaceVariant,
                            containerColor = palette.surfaceContainer,
                        ),
                    )
                }
            }
        }
    }

    if (pickStart) {
        TimePickerDialog(
            initialMinutes = rule.startMinutes,
            onDismiss = { pickStart = false },
            onConfirm = { update(rule.copy(startMinutes = it)); pickStart = false },
        )
    }
    if (pickEnd) {
        TimePickerDialog(
            initialMinutes = rule.endMinutes,
            onDismiss = { pickEnd = false },
            onConfirm = { update(rule.copy(endMinutes = it)); pickEnd = false },
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceHigh),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                label.uppercase(),
                style = SectionLabelStyle,
                color = palette.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) palette.onSurface else palette.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceContainer,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("Aceptar", color = palette.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.onSurfaceVariant) }
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
    )
}
