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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.SectionLabel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    loading: Boolean,
    goals: GoalsData?,
    onBack: () -> Unit,
    onSetStepGoal: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Objetivos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                ),
            )
        },
    ) { inner ->
        val g = goals
        if (loading || g == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = scheme.primary)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { StepGoalCard(g) { editing = true } }
            item { StreakCard(g) }
            item {
                SectionLabel("Medallas")
                Spacer(Modifier.height(8.dp))
                MedalGrid(g.medals)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (editing && goals != null) {
        StepGoalDialog(
            current = goals.stepGoal,
            onDismiss = { editing = false },
            onConfirm = { editing = false; onSetStepGoal(it) },
        )
    }
}

@Composable
private fun StepGoalCard(goals: GoalsData, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val progress = (goals.todaySteps.toFloat() / goals.stepGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Objetivo diario de pasos")
                Spacer(Modifier.height(6.dp))
                Text("%,d".format(goals.stepGoal), style = MetricValueStyle, color = scheme.onSurface)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar objetivo", tint = scheme.primary)
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = scheme.primary,
            trackColor = scheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(8.dp))
        Text("Hoy ${"%,d".format(goals.todaySteps)} de ${"%,d".format(goals.stepGoal)} pasos",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StreakCard(goals: GoalsData) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    SectionLabel("Racha actual")
                }
                Spacer(Modifier.height(6.dp))
                Text("${goals.currentStreak}", style = MetricValueStyle, color = scheme.onSurface)
                Text(if (goals.currentStreak == 1) "día" else "días", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) {
                SectionLabel("Mejor racha")
                Spacer(Modifier.height(6.dp))
                Text("${goals.bestStreak}", style = MetricValueStyle, color = scheme.onSurface)
                Text(if (goals.bestStreak == 1) "día" else "días", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MedalGrid(medals: List<Medal>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        medals.chunked(2).forEach { rowMedals ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowMedals.forEach { MedalTile(it, Modifier.weight(1f)) }
                if (rowMedals.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MedalTile(medal: Medal, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val achieved = medal.achieved
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (achieved) scheme.secondaryContainer else scheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                if (achieved) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (achieved) scheme.tertiary else scheme.outline,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                medal.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (achieved) scheme.onSecondaryContainer else scheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                medal.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (achieved) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepGoalDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Objetivo de pasos") },
        text = {
            Column {
                Text("Fija tu meta diaria de pasos.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Pasos") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull()?.coerceIn(1, 100000) ?: current) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// --- previews ---

private fun sampleGoals() = GoalsData(
    stepGoal = 10000, todaySteps = 8421, currentStreak = 4, bestStreak = 12,
    medals = listOf(
        Medal("first_goal", "Primer objetivo", "Alcanza tu meta en un día", true),
        Medal("streak_7", "Semana perfecta", "7 días seguidos", true),
        Medal("streak_30", "Mes de fuego", "30 días seguidos", false),
        Medal("steps_1m", "Millón de pasos", "1 000 000 acumulados", false),
    ),
)

@Preview(name = "Goals", heightDp = 1000)
@Composable
private fun GoalsPreview() {
    UltimateTheme { GoalsScreen(loading = false, goals = sampleGoals(), onBack = {}, onSetStepGoal = {}) }
}
