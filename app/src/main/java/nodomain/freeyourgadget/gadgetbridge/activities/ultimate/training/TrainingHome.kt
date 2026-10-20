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

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import java.text.DateFormat
import java.util.Date

private enum class Pane { ROUTINES, HISTORY, EXERCISES }

/** The Workouts tab of the home screen: start a workout, routines, history and the exercise list. */
@Composable
fun TrainingHome() {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val store = remember { TrainingStore.get(context) }
    val catalog = remember { TrainingCatalog(store) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var pane by remember { mutableStateOf(Pane.ROUTINES) }
    var menu by remember { mutableStateOf(false) }
    var units by remember { mutableStateOf(TrainingUnits.load(context)) }
    var pendingImport by remember { mutableStateOf<TrainingJson.Document?>(null) }
    var info by remember { mutableStateOf<Exercise?>(null) }

    // Come back from the other screens with fresh data.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { catalog.invalidate(); version++ } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val routines = remember(version) { store.routines() }
    val history = remember(version) { store.finishedSessions() }
    val active = remember(version) { store.activeSession() }
    val exercises = remember(version) { catalog.search("") }

    val exportDone = stringResource(R.string.ult_train_export_done)
    val importError = stringResource(R.string.ult_train_import_error)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(TrainingJson.export(store.exportDocument()).toByteArray()) }
            }
            Toast.makeText(context, exportDone, Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val doc = withContext(Dispatchers.IO) {
                runCatching { TrainingJson.import(context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }) }.getOrNull()
            }
            if (doc == null) Toast.makeText(context, importError, Toast.LENGTH_LONG).show() else pendingImport = doc
        }
    }

    fun start(routine: Routine?) {
        if (active != null) { context.startActivity(TrainingSessionActivity.intent(context)); return }
        val title = routine?.name ?: context.getString(R.string.ult_train_default_title)
        val s = TrainingSessions.start(title, routine, catalog::find, history.take(60), System.currentTimeMillis())
        store.saveSession(s)
        context.startActivity(TrainingSessionActivity.intent(context))
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ult_train_title), style = MaterialTheme.typography.headlineSmall, color = palette.onSurface, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = palette.onSurface) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.ult_train_export)) }, onClick = { menu = false; exportLauncher.launch("ultimategadget-workouts.json") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.ult_train_import)) }, onClick = { menu = false; importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ult_train_units) + ": " + stringResource(if (units.imperial) R.string.ult_train_units_imperial else R.string.ult_train_units_metric)) },
                        onClick = { menu = false; TrainingUnits.save(context, !units.imperial); units = TrainingUnits.load(context) },
                    )
                }
            }
        }
        if (active != null) {
            Card(Modifier.fillMaxWidth().padding(16.dp).clickable { start(null) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.primaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ult_train_resume), style = MaterialTheme.typography.titleMedium, color = palette.onPrimaryContainer)
                        Text(active.title, color = palette.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { start(null) }) { Text(stringResource(R.string.ult_train_resume_action)) }
                }
            }
        } else {
            Button(onClick = { start(null) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.ult_train_start_empty), modifier = Modifier.padding(start = 8.dp))
            }
        }
        TabRow(selectedTabIndex = pane.ordinal, containerColor = palette.background, contentColor = palette.primary) {
            Tab(selected = pane == Pane.ROUTINES, onClick = { pane = Pane.ROUTINES }, text = { Text(stringResource(R.string.ult_train_routines)) })
            Tab(selected = pane == Pane.HISTORY, onClick = { pane = Pane.HISTORY }, text = { Text(stringResource(R.string.ult_train_history)) })
            Tab(selected = pane == Pane.EXERCISES, onClick = { pane = Pane.EXERCISES }, text = { Text(stringResource(R.string.ult_train_exercises)) })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (pane) {
                Pane.ROUTINES -> {
                    item {
                        OutlinedButton(onClick = { context.startActivity(TrainingRoutineActivity.intent(context)) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(stringResource(R.string.ult_train_new_routine), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (routines.isEmpty()) item { Text(stringResource(R.string.ult_train_no_routines), color = palette.onSurfaceVariant) }
                    items(routines, key = { it.id }) { r ->
                        Card(Modifier.fillMaxWidth().clickable { context.startActivity(TrainingRoutineActivity.intent(context, r.id)) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                                    Text(stringResource(R.string.ult_train_routine_sub, r.exercises.size), style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                                }
                                TextButton(onClick = { start(r) }) { Text(stringResource(R.string.ult_train_start)) }
                            }
                        }
                    }
                }
                Pane.HISTORY -> {
                    if (history.isEmpty()) item { Text(stringResource(R.string.ult_train_no_history), color = palette.onSurfaceVariant) }
                    items(history, key = { it.id }) { s ->
                        Card(Modifier.fillMaxWidth().clickable { context.startActivity(TrainingSessionActivity.intent(context, s.id)) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(s.title, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.startMs)), style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                                Text(
                                    TrainingMath.formatDuration((s.durationMs / 1000).toInt()) + " · " +
                                        units.weightWithUnit(TrainingMath.volumeKg(s), stringResource(units.weightLabelRes)) + " · " +
                                        stringResource(R.string.ult_train_planned_sets, TrainingMath.doneSets(s)),
                                    style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Pane.EXERCISES -> {
                    items(exercises, key = { it.id }) { e ->
                        ExerciseRow(e) { info = e }
                    }
                }
            }
        }
    }

    info?.let { e ->
        val best = remember(e.id, version) {
            history.flatMap { s -> s.exercises.filter { it.exerciseId == e.id }.flatMap { it.sets.filter { st -> TrainingMath.counts(st) } } }
                .maxByOrNull { TrainingMath.estimatedOneRepMaxKg(it) }
        }
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(e.displayName()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(listOf(e.muscle, e.equipment, measureLabelPlain(e.measure)).filter { it.isNotBlank() }.joinToString(" · "))
                    Text(
                        best?.let { stringResource(R.string.ult_train_best, TrainingMath.describe(it, e.measure) { kg -> units.weightWithUnit(kg, context.getString(units.weightLabelRes)) }) }
                            ?: stringResource(R.string.ult_train_no_history_ex),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = if (e.custom) {
                { TextButton(onClick = { store.deleteExercise(e.id); catalog.invalidate(); version++; info = null }) { Text(stringResource(R.string.ult_train_delete)) } }
            } else null,
        )
    }

    pendingImport?.let { doc ->
        val count = doc.exercises.size + doc.routines.size + doc.sessions.size
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.ult_train_import_title)) },
            text = { Text("$count") },
            confirmButton = {
                TextButton(onClick = {
                    val r = store.importDocument(doc, replace = true); pendingImport = null; catalog.invalidate(); version++
                    Toast.makeText(context, context.getString(R.string.ult_train_import_done, r.added, r.skipped), Toast.LENGTH_LONG).show()
                }) { Text(stringResource(R.string.ult_train_import_replace)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val r = store.importDocument(doc, replace = false); pendingImport = null; catalog.invalidate(); version++
                    Toast.makeText(context, context.getString(R.string.ult_train_import_done, r.added, r.skipped), Toast.LENGTH_LONG).show()
                }) { Text(stringResource(R.string.ult_train_import_merge)) }
            },
        )
    }
}

@Composable
private fun measureLabelPlain(m: Measure): String = measureLabel(m)
