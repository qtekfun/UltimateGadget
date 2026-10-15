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

import nodomain.freeyourgadget.gadgetbridge.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import kotlin.math.roundToInt

private data class Level(val title: String, val nodes: List<PrefNode>)

private fun findSubScreen(nodes: List<PrefNode>, key: String): PrefNode.SubScreen? {
    for (n in nodes) {
        if (n is PrefNode.SubScreen) {
            if (n.key == key) return n
            findSubScreen(n.children, key)?.let { return it }
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltimateDeviceSettingsScreen(
    rootTitle: String,
    rootNodes: List<PrefNode>,
    store: DeviceSettingsStore,
    hasClassicFallback: Boolean,
    onBack: () -> Unit,
    onDelegate: (PrefNode.Delegated?) -> Unit,
    onAction: (String?) -> Unit,
    initialScreenKey: String? = null,
) {
    val palette = LocalUltimatePalette.current

    // Navigation stack for nested sub-screens. The first entry is the root; when a deep link key
    // is given, the matching sub-screen is opened on top of it right away.
    val stack = remember {
        mutableStateListOf(Level(rootTitle, rootNodes)).also { s ->
            initialScreenKey?.let { k -> findSubScreen(rootNodes, k)?.let { s.add(Level(it.title, it.children)) } }
        }
    }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back))
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
                    onAction = onAction,
                )
            }
            if (stack.size == 1 && hasClassicFallback) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SimpleRow(
                        title = stringResource(R.string.ult_classic_settings),
                        summary = "Battery, developer, authentication and other settings not yet migrated",
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
    onAction: (String?) -> Unit,
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

        is PrefNode.TimePicker -> {
            val context = LocalContext.current
            val enabled = depEnabled(node.dependency, states, store)
            val value = stringState(node.key, node.default, states, store)
            SimpleRow(
                title = node.title,
                summary = if (value.isNotEmpty()) value else node.summary,
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) {
                    {
                        val parts = value.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
                        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        android.app.TimePickerDialog(
                            context,
                            { _, nh, nm ->
                                val nv = String.format(java.util.Locale.ROOT, "%02d:%02d", nh, nm)
                                states[node.key] = nv
                                store.putString(node.key, nv)
                            },
                            h, m, android.text.format.DateFormat.is24HourFormat(context),
                        ).show()
                    }
                } else null,
            )
        }

        is PrefNode.Action -> {
            val palette = LocalUltimatePalette.current
            SimpleRow(
                title = node.title,
                summary = node.summary,
                iconRes = node.iconRes,
                onClick = { onAction(node.key) },
                trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.onSurfaceVariant) },
            )
        }

        is PrefNode.MultiChoice -> {
            val enabled = depEnabled(node.dependency, states, store)
            @Suppress("UNCHECKED_CAST")
            val value = (states[node.key] as? Set<String>) ?: node.default
            var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val chosen = node.entries.filterIndexed { i, _ -> node.values.getOrNull(i) in value }.joinToString(", ")
            SimpleRow(
                title = node.title,
                summary = node.summary ?: chosen.ifEmpty { null },
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) { { open = true } } else null,
            )
            if (open) {
                var draft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(value) }
                AlertDialog(
                    onDismissRequest = { open = false },
                    title = { Text(node.title) },
                    text = {
                        Column {
                            node.entries.forEachIndexed { i, label ->
                                val v = node.values[i]
                                Row(
                                    Modifier.fillMaxWidth().clickable { draft = if (v in draft) draft - v else draft + v },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    androidx.compose.material3.Checkbox(checked = v in draft, onCheckedChange = null)
                                    Text(label, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            states[node.key] = draft
                            store.putStringSet(node.key, draft)
                            open = false
                        }) { Text(stringResource(R.string.ult_save)) }
                    },
                    dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ult_cancel)) } },
                )
            }
        }

        is PrefNode.Sortable -> {
            val enabled = depEnabled(node.dependency, states, store)
            val joined = stringState(node.key, node.default.joinToString(","), states, store)
            val current = joined.split(",").filter { it.isNotEmpty() }
            var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            fun labelOf(v: String) = node.entries.getOrNull(node.values.indexOf(v)) ?: v
            SimpleRow(
                title = node.title,
                summary = node.summary ?: current.joinToString(", ") { labelOf(it) }.ifEmpty { null },
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) { { open = true } } else null,
            )
            if (open) {
                val order = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<String>().also { it.addAll(current) } }
                AlertDialog(
                    onDismissRequest = { open = false },
                    title = { Text(node.title) },
                    text = {
                        Column {
                            order.forEachIndexed { i, v ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(labelOf(v), modifier = Modifier.weight(1f))
                                    androidx.compose.material3.IconButton(
                                        enabled = i > 0,
                                        onClick = { val x = order.removeAt(i); order.add(i - 1, x) },
                                    ) { Icon(Icons.Filled.ArrowUpward, contentDescription = null) }
                                    androidx.compose.material3.IconButton(
                                        enabled = i < order.lastIndex,
                                        onClick = { val x = order.removeAt(i); order.add(i + 1, x) },
                                    ) { Icon(Icons.Filled.ArrowDownward, contentDescription = null) }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val nv = order.joinToString(",")
                            states[node.key] = nv
                            store.putString(node.key, nv)
                            open = false
                        }) { Text(stringResource(R.string.ult_save)) }
                    },
                    dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ult_cancel)) } },
                )
            }
        }

        is PrefNode.DatePicker -> {
            val context = LocalContext.current
            val enabled = depEnabled(node.dependency, states, store)
            val value = stringState(node.key, node.default, states, store)
            SimpleRow(
                title = node.title,
                summary = if (value.isNotEmpty()) value else node.summary,
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) {
                    {
                        val cal = java.util.Calendar.getInstance()
                        runCatching {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).parse(value)?.let { cal.time = it }
                        }
                        val dialog = android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val nv = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d)
                                states[node.key] = nv
                                store.putString(node.key, nv)
                            },
                            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH),
                        )
                        if (node.minDate > 0L) dialog.datePicker.minDate = node.minDate
                        if (node.maxDate < Long.MAX_VALUE) dialog.datePicker.maxDate = node.maxDate
                        dialog.show()
                    }
                } else null,
            )
        }

        is PrefNode.FilePicker -> {
            val enabled = depEnabled(node.dependency, states, store)
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
            ) { uris -> if (uris.isNotEmpty()) node.onPicked(uris) }
            SimpleRow(
                title = node.title,
                summary = node.summary,
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) { { launcher.launch(node.mimeTypes.toTypedArray()) } } else null,
            )
        }

        is PrefNode.FolderPicker -> {
            val context = LocalContext.current
            val enabled = depEnabled(node.dependency, states, store)
            val value = stringState(node.key, "", states, store)
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    if (node.persistPermission) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
                    states[node.key] = uri.toString()
                    store.putString(node.key, uri.toString())
                    node.onPicked?.invoke(uri)
                }
            }
            SimpleRow(
                title = node.title,
                summary = if (value.isNotEmpty()) nodomain.freeyourgadget.gadgetbridge.util.UriUtils.resolveLocationSummary(context, value) else null,
                iconRes = node.iconRes,
                enabled = enabled,
                onClick = if (enabled) { { launcher.launch(null) } } else null,
            )
        }

        is PrefNode.Info -> SimpleRow(
            title = node.title,
            summary = node.summary,
            iconRes = node.iconRes,
            enabled = true,
            onClick = null,
        )

        is PrefNode.Delegated -> {
            val palette = LocalUltimatePalette.current
            SimpleRow(
                title = node.title,
                summary = node.summary ?: stringResource(R.string.ult_open_classic),
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
            is PrefNode.TimePicker -> states[node.key] = store.getString(node.key, node.default)
            is PrefNode.MultiChoice -> states[node.key] = store.getStringSet(node.key, node.default)
            is PrefNode.Sortable -> states[node.key] = store.getString(node.key, node.default.joinToString(","))
            is PrefNode.DatePicker -> states[node.key] = store.getString(node.key, node.default)
            is PrefNode.FolderPicker -> states[node.key] = store.getString(node.key, "")
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ult_cancel)) } },
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
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ult_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ult_cancel)) } },
    )
}

@Composable
private fun EmptyNotice() {
    val palette = LocalUltimatePalette.current
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "There are no settings to show for this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant,
        )
    }
}
