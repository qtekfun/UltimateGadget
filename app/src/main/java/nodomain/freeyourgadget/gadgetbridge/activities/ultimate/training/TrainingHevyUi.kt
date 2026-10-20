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

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import java.text.DateFormat
import java.util.Date

/**
 * Wires "Import from Hevy" into the Workouts tab: returns the function that opens the file picker;
 * the preview dialog and the progress dialog are shown by this composable while it is in the
 * composition. [onDone] runs after an import so the lists can refresh.
 */
@Composable
fun rememberHevyImport(store: TrainingStore, catalog: TrainingCatalog, onDone: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Pair<HevyParsed, HevyPlan>?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val errorText = stringResource(R.string.ult_train_hevy_error)
    val emptyText = stringResource(R.string.ult_train_hevy_empty)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                    val parsed = HevyCsv.parse(text)
                    parsed to HevyImport.plan(parsed, catalog.all())
                }.getOrNull()
            }
            when {
                result == null -> Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                result.first.workouts.isEmpty() -> Toast.makeText(context, emptyText, Toast.LENGTH_LONG).show()
                else -> preview = result
            }
        }
    }

    preview?.let { (parsed, plan) ->
        HevyPreviewDialog(
            parsed = parsed, plan = plan,
            onDismiss = { preview = null },
            onConfirm = { create, weightUnit, distanceUnit ->
                preview = null
                progress = 0f
                scope.launch {
                    val applied = withContext(Dispatchers.IO) {
                        val built = HevyImport.build(parsed, plan, create, weightUnit, distanceUnit)
                        HevyImport.apply(store, built) { done, total -> progress = if (total == 0) 1f else done.toFloat() / total }
                    }
                    progress = null
                    catalog.invalidate()
                    onDone()
                    Toast.makeText(
                        context,
                        context.getString(R.string.ult_train_hevy_done, applied.sessionsAdded, applied.sessionsSkipped, applied.exercisesCreated),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }
    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.ult_train_hevy_importing)) },
            text = { LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }
    return { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*", "*/*")) }
}

/** What will be imported, with a switch per unmatched exercise (create as custom, or skip). */
@Composable
fun HevyPreviewDialog(
    parsed: HevyParsed,
    plan: HevyPlan,
    onDismiss: () -> Unit,
    onConfirm: (create: Set<String>, WeightUnit, DistanceUnit) -> Unit,
) {
    val create = remember(plan) { mutableStateMapOf<String, Boolean>().also { m -> plan.unmatched.forEach { m[it.title] = true } } }
    var weightUnit by remember(parsed) { mutableStateOf(if (parsed.weightUnit == WeightUnit.UNKNOWN) WeightUnit.KG else parsed.weightUnit) }
    var distanceUnit by remember(parsed) { mutableStateOf(if (parsed.distanceUnit == DistanceUnit.UNKNOWN) DistanceUnit.KM else parsed.distanceUnit) }
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ult_train_hevy_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ult_train_hevy_summary, plan.workouts, date.format(Date(plan.firstMs)), date.format(Date(plan.lastMs)), plan.totalSets))
                Text(stringResource(R.string.ult_train_hevy_matched, plan.matched.size))
                if (parsed.weightUnit == WeightUnit.UNKNOWN) {
                    Text(stringResource(R.string.ult_train_hevy_weight_unit), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(weightUnit == WeightUnit.KG, { weightUnit = WeightUnit.KG }, { Text(stringResource(R.string.ult_train_kg)) })
                        FilterChip(weightUnit == WeightUnit.LB, { weightUnit = WeightUnit.LB }, { Text(stringResource(R.string.ult_train_lb)) })
                    }
                }
                if (parsed.distanceUnit == DistanceUnit.UNKNOWN && parsed.workouts.any { w -> w.exercises.any { e -> e.sets.any { (it.distance ?: 0.0) > 0 } } }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(distanceUnit == DistanceUnit.KM, { distanceUnit = DistanceUnit.KM }, { Text(stringResource(R.string.ult_train_km)) })
                        FilterChip(distanceUnit == DistanceUnit.MILES, { distanceUnit = DistanceUnit.MILES }, { Text(stringResource(R.string.ult_train_mi)) })
                    }
                }
                if (plan.unmatched.isNotEmpty()) {
                    Text(stringResource(R.string.ult_train_hevy_unmatched, plan.unmatched.size), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(plan.unmatched, key = { it.title }) { u ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(u.title, Modifier.weight(1f))
                                Switch(checked = create[u.title] == true, onCheckedChange = { create[u.title] = it })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(create.filterValues { it }.keys.toSet(), weightUnit, distanceUnit) }) {
                Text(stringResource(R.string.ult_train_hevy_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ult_train_cancel)) } },
    )
}
