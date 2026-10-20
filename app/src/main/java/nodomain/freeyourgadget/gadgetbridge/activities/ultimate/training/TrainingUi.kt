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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette

private fun parseNum(s: String) = s.replace(',', '.').toDoubleOrNull()

/** Compact numeric input used by the set rows. Keeps what is being typed (e.g. "8.") while the parent holds the parsed value. */
@Composable
fun NumberCell(
    text: String,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    hint: String = "",
) {
    val palette = LocalUltimatePalette.current
    var local by remember { mutableStateOf(text) }
    // Follow outside changes (e.g. a set copied from the previous one) but never fight the typing.
    LaunchedEffect(text) { if (parseNum(local) != parseNum(text)) local = text }
    BasicTextField(
        value = local,
        onValueChange = { v ->
            if (v.length <= 8 && v.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }) { local = v; onText(v) }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.onSurface, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        cursorBrush = SolidColor(palette.primary),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().background(palette.surfaceHigh, RoundedCornerShape(8.dp)).padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (local.isEmpty()) Text(hint, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        },
        modifier = modifier,
    )
}

@Composable
fun kindLabel(kind: SetKind): String = stringResource(when (kind) {
    SetKind.NORMAL -> R.string.ult_train_kind_normal
    SetKind.WARMUP -> R.string.ult_train_kind_warmup
    SetKind.DROP -> R.string.ult_train_kind_drop
    SetKind.FAILURE -> R.string.ult_train_kind_failure
})

fun kindBadge(kind: SetKind, number: Int): String = when (kind) {
    SetKind.NORMAL -> number.toString()
    SetKind.WARMUP -> "W"
    SetKind.DROP -> "D"
    SetKind.FAILURE -> "F"
}

/** The round number/letter at the start of a set row; tapping it changes the kind of set. */
@Composable
fun KindBadge(kind: SetKind, number: Int, onKind: (SetKind) -> Unit) {
    val palette = LocalUltimatePalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (kind == SetKind.NORMAL) palette.surfaceHigh else palette.primaryContainer).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(kindBadge(kind, number), color = if (kind == SetKind.NORMAL) palette.onSurface else palette.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SetKind.values().forEach { k ->
                DropdownMenuItem(text = { Text(kindLabel(k)) }, onClick = { open = false; onKind(k) })
            }
        }
    }
}

@Composable
fun measureLabel(m: Measure): String = stringResource(when (m) {
    Measure.REPS_WEIGHT -> R.string.ult_train_measure_reps_weight
    Measure.REPS -> R.string.ult_train_measure_reps
    Measure.TIME -> R.string.ult_train_measure_time
    Measure.DISTANCE -> R.string.ult_train_measure_distance
    Measure.DISTANCE_TIME -> R.string.ult_train_measure_distance_time
})

/** Header row for the columns of a set table. */
@Composable
fun SetTableHeader(measure: Measure, units: TrainingUnits, showPrevious: Boolean, showDone: Boolean) {
    val palette = LocalUltimatePalette.current
    val style = MaterialTheme.typography.labelMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ult_train_set), style = style, color = palette.onSurfaceVariant, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        if (showPrevious) Text(stringResource(R.string.ult_train_previous), style = style, color = palette.onSurfaceVariant, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center)
        if (measure.hasWeight) Text(stringResource(R.string.ult_train_weight, stringResource(units.weightLabelRes)), style = style, color = palette.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (measure.hasReps) Text(stringResource(R.string.ult_train_reps), style = style, color = palette.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (measure.hasDistance) Text(stringResource(R.string.ult_train_distance, stringResource(units.distanceLabelRes)), style = style, color = palette.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (measure.hasTime) Text(stringResource(R.string.ult_train_time), style = style, color = palette.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (showDone) Box(Modifier.width(40.dp))
    }
}

/** Full-screen picker over the catalog, with search and creation of a custom exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePicker(catalog: TrainingCatalog, store: TrainingStore, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalUltimatePalette.current
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    val results = remember(query, version) { catalog.search(query) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = palette.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.ult_train_add_exercise)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ult_back)) } },
                    actions = { IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ult_train_create_exercise)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface, titleContentColor = palette.onSurface, navigationIconContentColor = palette.onSurface, actionIconContentColor = palette.onSurface),
                )
            },
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.ult_train_search_exercise)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                if (results.isEmpty()) {
                    Text(stringResource(R.string.ult_train_no_exercises), color = palette.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { e -> ExerciseRow(e) { onPick(e) } }
                }
            }
        }
    }
    if (creating) {
        CreateExerciseDialog(
            onDismiss = { creating = false },
            onCreate = { e -> store.saveExercise(e); catalog.invalidate(); version++; creating = false; onPick(e) },
        )
    }
}

@Composable
fun ExerciseRow(e: Exercise, onClick: () -> Unit) {
    val palette = LocalUltimatePalette.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(e.displayName(), style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
        val sub = listOf(e.muscle, e.equipment).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
    }
}

@Composable
fun CreateExerciseDialog(onDismiss: () -> Unit, onCreate: (Exercise) -> Unit) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf(Measure.REPS_WEIGHT) }
    var menu by remember { mutableStateOf(false) }
    val lang = java.util.Locale.getDefault().language
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ult_train_create_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.ult_train_exercise_name)) })
                OutlinedTextField(muscle, { muscle = it }, singleLine = true, label = { Text(stringResource(R.string.ult_train_muscle)) })
                OutlinedTextField(equipment, { equipment = it }, singleLine = true, label = { Text(stringResource(R.string.ult_train_equipment)) })
                Box {
                    TextButton(onClick = { menu = true }) { Text(stringResource(R.string.ult_train_measure) + ": " + measureLabel(measure)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Measure.values().forEach { m -> DropdownMenuItem(text = { Text(measureLabel(m)) }, onClick = { measure = m; menu = false }) }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onCreate(Exercise(newId(), mapOf(lang to name.trim()), measure, muscle.trim().lowercase(), equipment.trim().lowercase(), custom = true))
            }) { Text(stringResource(R.string.ult_train_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ult_train_cancel)) } },
    )
}

@Composable
fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ult_train_cancel)) } },
    )
}
