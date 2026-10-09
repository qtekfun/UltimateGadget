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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.devsettings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import kotlin.math.roundToInt

private data class Level(val title: String, val nodes: List<PrefNode>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltimateDeviceSettingsScreen(
    rootTitle: String,
    rootNodes: List<PrefNode>,
    store: DeviceSettingsStore,
    hasClassicFallback: Boolean,
    onBack: () -> Unit,
    onDelegate: (PrefNode.Delegated?) -> Unit,
) {
    val palette = LocalUltimatePalette.current

    // Navigation stack for nested sub-screens. The first entry is the root.
    val stack = remember { mutableStateListOf(Level(rootTitle, rootNodes)) }
    val current = stack.last()

    // Shared, reactive snapshot of preference values, so dependencies and summaries recompose.
    // Seeded once from the store for every key in the tree, so controls only ever read/write it
    // (never write during composition).
    val states = remember(rootNodes) {
        mutableStateMapOf<String, Any?>().apply { seedStates(rootNodes, this, store) }
    }

    BackHandler(enabled = true) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack()
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(palette.background),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding(), bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (current.nodes.isEmpty()) {
                item { EmptyNotice() }
            }
            items(current.nodes) { node ->
                PrefRow(
                    node = node,
                    store = store,
                    states = states,
                    onOpenSubScreen = { title, children -> stack.add(Level(title, children)) },
                    onDelegate = onDelegate,
                )
            }
            if (stack.size == 1 && hasClassicFallback) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SimpleRow(
                        title = "Ajustes clásicos (avanzado)",
                        summary = "Batería, desarrollador, autenticación y otros ajustes aún no migrados",
                        iconRes = 0,
                        trailing = { Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = palette.onSurfaceVariant) },
                        onClick = { onDelegate(null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefRow(
    node: PrefNode,
    store: DeviceSettingsStore,
    states: SnapshotStateMap<String, Any?>,
    onOpenSubScreen: (String, List<PrefNode>) -> Unit,
    onDelegate: (PrefNode.Delegated?) -> Unit,
) {
    when (node) {
        is PrefNode.Category -> SectionHeader(node.title)

        is PrefNode.Switch -> {
            val enabled = depEnabled(node.dependency, states, store)
            val value = boolState(node.key, node.default, states, store)
            SimpleRow(
                title = node.title,
                summary = node.summary,
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) {
                    {
                        val nv = !value
                        states[node.key] = nv
                        store.putBoolean(node.key, nv)
                    }
                } else null,
                trailing = {
                    val palette = LocalUltimatePalette.current
                    Switch(
                        checked = value,
                        onCheckedChange = if (enabled) {
                            { nv -> states[node.key] = nv; store.putBoolean(node.key, nv) }
                        } else null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.onPrimary,
                            checkedTrackColor = palette.primary,
                        ),
                    )
                },
            )
        }

        is PrefNode.SingleChoice -> {
            val enabled = depEnabled(node.dependency, states, store)
            val value = stringState(node.key, node.default, states, store)
            var dialog by remember { mutableStateOf(false) }
            val idx = node.values.indexOf(value)
            val label = if (idx >= 0) node.entries[idx] else value
            SimpleRow(
                title = node.title,
                summary = if (!label.isNullOrEmpty()) label else node.summary,
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) ({ dialog = true }) else null,
            )
            if (dialog) {
                ChoiceDialog(
                    title = node.title,
                    entries = node.entries,
                    values = node.values,
                    selected = value,
                    onPick = { v ->
                        states[node.key] = v
                        store.putString(node.key, v)
                        dialog = false
                    },
                    onDismiss = { dialog = false },
                )
            }
        }

        is PrefNode.Text -> {
            val enabled = depEnabled(node.dependency, states, store)
            val value = stringState(node.key, node.default, states, store)
            var dialog by remember { mutableStateOf(false) }
            SimpleRow(
                title = node.title,
                summary = if (value.isNotEmpty()) value else node.summary,
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) ({ dialog = true }) else null,
            )
            if (dialog) {
                TextDialog(
                    title = node.title,
                    initial = value,
                    numeric = node.numeric,
                    onConfirm = { v ->
                        states[node.key] = v
                        store.putString(node.key, v)
                        dialog = false
                    },
                    onDismiss = { dialog = false },
                )
            }
        }

        is PrefNode.Slider -> {
            val enabled = depEnabled(node.dependency, states, store)
            val value = intState(node.key, node.default, states, store)
            SliderRow(
                title = node.title,
                summary = node.summary,
                iconRes = node.iconRes,
                value = value,
                min = node.min,
                max = node.max,
                enabled = enabled,
                onChange = { nv -> states[node.key] = nv; store.putInt(node.key, nv) },
            )
        }

        is PrefNode.SubScreen -> {
            val palette = LocalUltimatePalette.current
            SimpleRow(
                title = node.title,
                summary = node.summary,
                iconRes = node.iconRes,
                onClick = { onOpenSubScreen(node.title, node.children) },
                trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.onSurfaceVariant) },
            )
        }

        is PrefNode.Delegated -> {
            val palette = LocalUltimatePalette.current
            SimpleRow(
                title = node.title,
                summary = node.summary ?: "Abrir en los ajustes clásicos",
                iconRes = node.iconRes,
                onClick = { onDelegate(node) },
                trailing = { Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = palette.onSurfaceVariant) },
            )
        }
    }
}

// ---- reactive value helpers -------------------------------------------------

/** Pre-populates [states] from the store for every persisted key in the (nested) node tree. */
private fun seedStates(nodes: List<PrefNode>, states: SnapshotStateMap<String, Any?>, store: DeviceSettingsStore) {
    for (node in nodes) {
        when (node) {
            is PrefNode.Switch -> states[node.key] = store.getBoolean(node.key, node.default)
            is PrefNode.SingleChoice -> states[node.key] = store.getString(node.key, node.default)
            is PrefNode.Text -> states[node.key] = store.getString(node.key, node.default)
            is PrefNode.Slider -> states[node.key] = store.getInt(node.key, node.default)
            is PrefNode.SubScreen -> seedStates(node.children, states, store)
            else -> {}
        }
    }
}

private fun boolState(key: String, def: Boolean, states: SnapshotStateMap<String, Any?>, store: DeviceSettingsStore): Boolean =
    states[key] as? Boolean ?: store.getBoolean(key, def)

private fun stringState(key: String, def: String, states: SnapshotStateMap<String, Any?>, store: DeviceSettingsStore): String =
    states[key] as? String ?: store.getString(key, def)

private fun intState(key: String, def: Int, states: SnapshotStateMap<String, Any?>, store: DeviceSettingsStore): Int =
    states[key] as? Int ?: store.getInt(key, def)

private fun depEnabled(dependency: String?, states: SnapshotStateMap<String, Any?>, store: DeviceSettingsStore): Boolean {
    if (dependency == null) return true
    return states[dependency] as? Boolean ?: store.getBoolean(dependency, false)
}

// ---- building-block composables --------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    val palette = LocalUltimatePalette.current
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = palette.primary,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun SimpleRow(
    title: String,
    summary: String?,
    iconRes: Int,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalUltimatePalette.current
    var mod = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(palette.surfaceContainer)
    if (onClick != null && enabled) mod = mod.clickable(onClick = onClick)
    Row(
        mod.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (iconRes != 0) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) palette.onSurfaceVariant else palette.outlineVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) palette.onSurface else palette.onSurfaceVariant,
            )
            if (!summary.isNullOrEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun SliderRow(
    title: String,
    summary: String?,
    iconRes: Int,
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    val safeMax = if (max > min) max else min + 1
    var live by remember(value) { mutableStateOf(value.coerceIn(min, safeMax).toFloat()) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (iconRes != 0) {
                Icon(painterResource(iconRes), null, tint = palette.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.onSurface, modifier = Modifier.weight(1f))
            Text(live.roundToInt().toString(), style = MaterialTheme.typography.titleMedium, color = palette.primary)
        }
        if (!summary.isNullOrEmpty()) {
            Text(summary, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        }
        Slider(
            value = live,
            onValueChange = { if (enabled) live = it },
            onValueChangeFinished = { if (enabled) onChange(live.roundToInt()) },
            valueRange = min.toFloat()..safeMax.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    entries: List<String>,
    values: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceHigh,
        title = { Text(title, color = palette.onSurface) },
        text = {
            Column {
                entries.forEachIndexed { i, label ->
                    val v = values[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(v) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(selected = v == selected, onClick = { onPick(v) })
                        Text(label, color = palette.onSurface)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    numeric: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceHigh,
        title = { Text(title, color = palette.onSurface) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun EmptyNotice() {
    val palette = LocalUltimatePalette.current
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "No hay ajustes que mostrar para este dispositivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant,
        )
    }
}
