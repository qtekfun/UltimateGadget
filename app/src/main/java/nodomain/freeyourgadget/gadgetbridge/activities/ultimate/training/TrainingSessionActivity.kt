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
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import java.text.DateFormat
import java.util.Date

/**
 * The workout screen. With no extra it shows the session in progress (every change is stored at once,
 * so it survives the app being killed); with [EXTRA_SESSION_ID] of a finished session it shows its summary.
 */
class TrainingSessionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getStringExtra(EXTRA_SESSION_ID)
        setContent { UltimateTheme { SessionRoot(id, onClose = { finish() }) } }
    }

    override fun onResume() { super.onResume(); TrainingRestAlarm.screenVisible = true }
    override fun onPause() { TrainingRestAlarm.screenVisible = false; super.onPause() }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        fun intent(context: Context, sessionId: String? = null): Intent =
            Intent(context, TrainingSessionActivity::class.java).apply { sessionId?.let { putExtra(EXTRA_SESSION_ID, it) } }
    }
}

@Composable
internal fun SessionRoot(sessionId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TrainingStore.get(context) }
    val loaded = remember { if (sessionId != null) store.session(sessionId) else store.activeSession() }
    if (loaded == null) { LaunchedEffect(Unit) { onClose() }; return }
    if (loaded.status == SessionStatus.DONE) FinishedSessionScreen(loaded, store, onClose)
    else ActiveSessionScreen(loaded, store, onClose)
}

// ---------------------------------------------------------------------------------------------
// Session in progress
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveSessionScreen(initial: Session, store: TrainingStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val units = remember { TrainingUnits.load(context) }
    val catalog = remember { TrainingCatalog(store) }
    val history = remember { store.finishedSessions(60) }

    var session by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<Session?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    /** Applies a change and writes it to disk straight away. */
    fun update(f: (Session) -> Session) {
        val before = session.restEndsAtMs
        val next = f(session)
        session = next
        store.saveSession(next)
        if (next.restEndsAtMs != before) {
            if (next.restEndsAtMs > 0) TrainingRestAlarm.schedule(context, next.restEndsAtMs) else TrainingRestAlarm.cancel(context)
        }
    }

    // One tick a second for the elapsed time and the rest countdown.
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    val restLeft = TrainingSessions.restRemainingSeconds(session, now)

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.title, maxLines = 1)
                        Text(TrainingMath.formatDuration(((now - session.startMs) / 1000).toInt().coerceAtLeast(0)),
                            style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back)) } },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    Button(onClick = { confirmFinish = true }) { Text(stringResource(R.string.ult_train_finish)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.ult_train_discard)) }, onClick = { menu = false; confirmDiscard = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface, titleContentColor = palette.onSurface, navigationIconContentColor = palette.onSurface, actionIconContentColor = palette.onSurface),
            )
        },
        bottomBar = {
            if (restLeft > 0) {
                Row(
                    Modifier.fillMaxWidth().background(palette.primaryContainer).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.ult_train_rest_label, TrainingMath.formatDuration(restLeft)), color = palette.onPrimaryContainer, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { update { it.copy(restEndsAtMs = it.restEndsAtMs + 15_000) } }) { Text(stringResource(R.string.ult_train_rest_add)) }
                    TextButton(onClick = { update { TrainingSessions.clearRest(it) } }) { Text(stringResource(R.string.ult_train_rest_skip)) }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (session.exercises.isEmpty()) {
                item { Text(stringResource(R.string.ult_train_empty_session), color = palette.onSurfaceVariant) }
            }
            itemsIndexed(session.exercises) { exIndex, ex ->
                ExerciseCard(
                    ex = ex, units = units,
                    last = remember(ex.exerciseId) { TrainingMath.lastTime(ex.exerciseId, history, session.startMs) },
                    onSet = { setIndex, f -> update { TrainingSessions.updateSet(it, exIndex, setIndex, f) } },
                    onToggle = { setIndex -> update { TrainingSessions.toggleDone(it, exIndex, setIndex, System.currentTimeMillis()) } },
                    onAddSet = { update { TrainingSessions.addSet(it, exIndex) } },
                    onRemoveSet = { setIndex -> update { TrainingSessions.removeSet(it, exIndex, setIndex) } },
                    onRemove = { update { TrainingSessions.removeExercise(it, exIndex) } },
                    onRest = { sec -> update { TrainingSessions.setRest(it, exIndex, sec) } },
                )
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
        ExercisePicker(catalog, store, onPick = { e -> picking = false; update { TrainingSessions.addExercise(it, e, 90, history) } }, onDismiss = { picking = false })
    }
    if (confirmFinish) {
        ConfirmDialog(stringResource(R.string.ult_train_finish_confirm), stringResource(R.string.ult_train_finish), onConfirm = {
            confirmFinish = false
            val done = TrainingSessions.finish(session, System.currentTimeMillis())
            store.saveSession(done)
            TrainingRestAlarm.cancel(context)
            session = done
            summary = done
        }, onDismiss = { confirmFinish = false })
    }
    if (confirmDiscard) {
        ConfirmDialog(stringResource(R.string.ult_train_discard_confirm), stringResource(R.string.ult_train_discard), onConfirm = {
            store.deleteSession(session.id); TrainingRestAlarm.cancel(context); onClose()
        }, onDismiss = { confirmDiscard = false })
    }
    summary?.let { done ->
        SummaryDialog(done, units, TrainingMath.personalRecords(done, history), onClose = onClose)
    }
}

@Composable
private fun ExerciseCard(
    ex: SessionExercise,
    units: TrainingUnits,
    last: List<SessionSet>,
    onSet: (Int, (SessionSet) -> SessionSet) -> Unit,
    onToggle: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onRemove: () -> Unit,
    onRest: (Int) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    var menu by remember { mutableStateOf(false) }
    var editRest by remember { mutableStateOf(false) }
    val kgLabel = stringResource(units.weightLabelRes)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ex.displayName(), style = MaterialTheme.typography.titleMedium, color = palette.primary, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = palette.onSurfaceVariant) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.ult_train_rest_seconds) + ": " + ex.restSeconds) }, onClick = { menu = false; editRest = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.ult_train_remove_exercise)) }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            SetTableHeader(ex.measure, units, showPrevious = true, showDone = true)
            ex.sets.forEachIndexed { i, set ->
                val number = ex.sets.take(i + 1).count { it.kind != SetKind.WARMUP }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(set.kind, number) { k -> onSet(i) { it.copy(kind = k) } }
                    val prev = last.getOrNull(i)
                    Text(
                        prev?.let { TrainingMath.describe(it, ex.measure) { kg -> units.weightWithUnit(kg, kgLabel) } } ?: "–",
                        style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1.1f), maxLines = 1,
                    )
                    if (ex.measure.hasWeight) NumberCell(units.weightText(set.weightKg), { t -> onSet(i) { it.copy(weightKg = units.parseWeight(t)) } }, Modifier.weight(1f))
                    if (ex.measure.hasReps) NumberCell(set.reps?.toString() ?: "", { t -> onSet(i) { it.copy(reps = t.toIntOrNull()) } }, Modifier.weight(1f), decimal = false)
                    if (ex.measure.hasDistance) NumberCell(units.distanceText(set.distanceM), { t -> onSet(i) { it.copy(distanceM = units.parseDistance(t)) } }, Modifier.weight(1f))
                    if (ex.measure.hasTime) NumberCell(set.durationS?.toString() ?: "", { t -> onSet(i) { it.copy(durationS = t.toIntOrNull()) } }, Modifier.weight(1f), decimal = false)
                    IconButton(
                        onClick = { onToggle(i) },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = if (set.done) palette.primary else palette.surfaceHigh),
                    ) { Icon(Icons.Filled.Check, contentDescription = null, tint = if (set.done) palette.onPrimary else palette.onSurfaceVariant) }
                }
            }
            Row {
                TextButton(onClick = onAddSet, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ult_train_add_set)) }
                if (ex.sets.size > 1) IconButton(onClick = { onRemoveSet(ex.sets.lastIndex) }) { Icon(Icons.Filled.Delete, contentDescription = null, tint = palette.onSurfaceVariant) }
            }
        }
    }
    if (editRest) RestDialog(ex.restSeconds, onDismiss = { editRest = false }, onSave = { editRest = false; onRest(it) })
}

@Composable
private fun RestDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ult_train_rest_seconds)) },
        text = { NumberCell(text, { text = it }, Modifier.fillMaxWidth(), decimal = false) },
        confirmButton = { TextButton(onClick = { onSave(text.toIntOrNull() ?: current) }) { Text(stringResource(R.string.ult_train_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ult_train_cancel)) } },
    )
}

// ---------------------------------------------------------------------------------------------
// Summary and finished sessions
// ---------------------------------------------------------------------------------------------

@Composable
private fun SummaryStats(s: Session, units: TrainingUnits) {
    val palette = LocalUltimatePalette.current
    val unit = stringResource(units.weightLabelRes)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(
            stringResource(R.string.ult_train_duration) to TrainingMath.formatDuration((s.durationMs / 1000).toInt()),
            stringResource(R.string.ult_train_volume) to units.weightWithUnit(TrainingMath.volumeKg(s), unit),
            stringResource(R.string.ult_train_sets) to TrainingMath.doneSets(s).toString(),
        ).forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                Text(label, style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SummaryDialog(s: Session, units: TrainingUnits, records: List<RecordHit>, onClose: () -> Unit) {
    val unit = stringResource(units.weightLabelRes)
    val lines = records.map { r ->
        val name = s.exercises.firstOrNull { it.exerciseId == r.exerciseId }?.displayName() ?: r.exerciseId
        name + ": " + when (r.kind) {
            RecordKind.WEIGHT -> stringResource(R.string.ult_train_pr_weight, units.weightWithUnit(r.value, unit))
            RecordKind.ONE_REP_MAX -> stringResource(R.string.ult_train_pr_1rm, units.weightWithUnit(r.value, unit))
            RecordKind.SET_VOLUME -> stringResource(R.string.ult_train_pr_volume, units.weightWithUnit(r.value, unit))
            RecordKind.REPS -> stringResource(R.string.ult_train_pr_reps, r.value.toInt().toString())
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStats(s, units)
                if (lines.isNotEmpty()) {
                    Text(stringResource(R.string.ult_train_pr), style = MaterialTheme.typography.titleSmall)
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(android.R.string.ok)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishedSessionScreen(s: Session, store: TrainingStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val units = remember { TrainingUnits.load(context) }
    val unit = stringResource(units.weightLabelRes)
    var confirmDelete by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.ult_train_routine_saved)
    val history = remember { store.finishedSessions() }
    val records = remember { TrainingMath.personalRecords(s, history) }
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(s.title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back)) } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ult_train_delete_session)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface, titleContentColor = palette.onSurface, navigationIconContentColor = palette.onSurface, actionIconContentColor = palette.onSurface),
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.startMs)), color = palette.onSurfaceVariant)
            }
            item { SummaryStats(s, units) }
            item {
                OutlinedButton(onClick = {
                    store.saveRoutine(TrainingSessions.toRoutine(s, s.title))
                    Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ult_train_save_as_routine)) }
            }
            itemsIndexed(s.exercises) { _, ex ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ex.displayName(), style = MaterialTheme.typography.titleMedium, color = palette.primary)
                        ex.sets.filter { it.done }.forEachIndexed { i, set ->
                            val number = ex.sets.filter { it.done }.take(i + 1).count { it.kind != SetKind.WARMUP }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(kindBadge(set.kind, number), color = palette.onSurfaceVariant, modifier = Modifier.width(24.dp))
                                Text(TrainingMath.describe(set, ex.measure) { kg -> units.weightWithUnit(kg, unit) }, color = palette.onSurface)
                            }
                        }
                        records.filter { it.exerciseId == ex.exerciseId }.takeIf { it.isNotEmpty() }?.let {
                            Text("★ " + stringResource(R.string.ult_train_pr), color = palette.tertiary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.ult_train_delete_session_confirm), stringResource(R.string.ult_train_delete), onConfirm = {
            store.deleteSession(s.id); onClose()
        }, onDismiss = { confirmDelete = false })
    }
}
