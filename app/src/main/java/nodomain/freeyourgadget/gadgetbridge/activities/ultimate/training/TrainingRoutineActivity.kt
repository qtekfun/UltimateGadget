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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/** Creates or edits a routine: exercises, planned sets and rest time. */
class TrainingRoutineActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ROUTINE_ID)
        setContent { UltimateTheme { RoutineEditor(id, onClose = { finish() }) } }
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "routine_id"
        fun intent(context: Context, routineId: String? = null): Intent =
            Intent(context, TrainingRoutineActivity::class.java).apply { routineId?.let { putExtra(EXTRA_ROUTINE_ID, it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditor(routineId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val store = remember { TrainingStore.get(context) }
    val catalog = remember { TrainingCatalog(store) }
    val units = remember { TrainingUnits.load(context) }
    val existing = remember { routineId?.let { store.routine(it) } }
    val defaultName = stringResource(R.string.ult_train_new_routine)
    var routine by remember { mutableStateOf(existing ?: Routine(name = "")) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun mapEx(i: Int, f: (RoutineExercise) -> RoutineExercise) {
        routine = routine.copy(exercises = routine.exercises.mapIndexed { idx, e -> if (idx == i) f(e) else e })
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) stringResource(R.string.ult_train_new_routine) else existing.name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back)) } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ult_train_delete)) }
                    Button(onClick = {
                        store.saveRoutine(routine.copy(name = routine.name.ifBlank { defaultName }))
                        onClose()
                    }) { Text(stringResource(R.string.ult_train_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface, titleContentColor = palette.onSurface, navigationIconContentColor = palette.onSurface, actionIconContentColor = palette.onSurface),
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(routine.name, { routine = routine.copy(name = it) }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ult_train_routine_name)) })
            }
            item {
                OutlinedTextField(routine.notes, { routine = routine.copy(notes = it) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ult_train_notes)) })
            }
            itemsIndexed(routine.exercises) { i, re ->
                val ex = catalog.find(re.exerciseId)
                val measure = ex?.measure ?: Measure.REPS_WEIGHT
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        var menu by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex?.displayName() ?: re.exerciseId, style = MaterialTheme.typography.titleMedium, color = palette.primary, modifier = Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = palette.onSurfaceVariant) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.ult_train_remove_exercise)) }, onClick = {
                                        menu = false
                                        routine = routine.copy(exercises = routine.exercises.filterIndexed { idx, _ -> idx != i })
                                    })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ult_train_rest_seconds), color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            NumberCell(re.restSeconds.toString(), { t -> mapEx(i) { it.copy(restSeconds = t.toIntOrNull() ?: 0) } }, Modifier.weight(0.6f), decimal = false)
                        }
                        SetTableHeader(measure, units, showPrevious = false, showDone = false)
                        re.sets.forEachIndexed { si, set ->
                            fun setMap(f: (PlannedSet) -> PlannedSet) = mapEx(i) { e -> e.copy(sets = e.sets.mapIndexed { x, s -> if (x == si) f(s) else s }) }
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                KindBadge(set.kind, re.sets.take(si + 1).count { it.kind != SetKind.WARMUP }) { k -> setMap { it.copy(kind = k) } }
                                if (measure.hasWeight) NumberCell(units.weightText(set.weightKg), { t -> setMap { it.copy(weightKg = units.parseWeight(t)) } }, Modifier.weight(1f))
                                if (measure.hasReps) NumberCell(set.reps?.toString() ?: "", { t -> setMap { it.copy(reps = t.toIntOrNull()) } }, Modifier.weight(1f), decimal = false)
                                if (measure.hasDistance) NumberCell(units.distanceText(set.distanceM), { t -> setMap { it.copy(distanceM = units.parseDistance(t)) } }, Modifier.weight(1f))
                                if (measure.hasTime) NumberCell(set.durationS?.toString() ?: "", { t -> setMap { it.copy(durationS = t.toIntOrNull()) } }, Modifier.weight(1f), decimal = false)
                            }
                        }
                        Row {
                            TextButton(onClick = { mapEx(i) { e -> e.copy(sets = e.sets + (e.sets.lastOrNull() ?: PlannedSet())) } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ult_train_add_set)) }
                            if (re.sets.size > 1) IconButton(onClick = { mapEx(i) { e -> e.copy(sets = e.sets.dropLast(1)) } }) { Icon(Icons.Filled.Delete, contentDescription = null, tint = palette.onSurfaceVariant) }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ult_train_add_exercise), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (picking) {
        ExercisePicker(catalog, store, onPick = { e ->
            picking = false
            routine = routine.copy(exercises = routine.exercises + RoutineExercise(e.id, 90, sets = listOf(PlannedSet())))
        }, onDismiss = { picking = false })
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.ult_train_delete) + "?", stringResource(R.string.ult_train_delete), onConfirm = {
            store.deleteRoutine(routine.id); onClose()
        }, onDismiss = { confirmDelete = false })
    }
}
