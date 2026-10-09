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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard

import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onToggleEditing: () -> Unit,
    onToggleCard: (DashboardCardId) -> Unit,
    onMoveCard: (Int, Int) -> Unit,
    onOpenDetail: (DashboardCardId) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "UltimateGadget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (!state.editing) {
                        IconButton(onClick = { }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                    IconButton(onClick = onToggleEditing) {
                        Icon(
                            if (state.editing) Icons.Filled.Check else Icons.Filled.Tune,
                            contentDescription = if (state.editing) "Done" else "Customize",
                            tint = if (state.editing) scheme.primary else scheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                ),
            )
        },
    ) { inner ->
        val data = state.data
        if (data == null) {
            Column(
                Modifier.fillMaxSize().padding(inner),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("Loading…", color = scheme.onSurfaceVariant) }
            return@Scaffold
        }

        if (state.editing) {
            CustomizeList(
                cards = state.cards,
                onToggle = onToggleCard,
                onMove = onMoveCard,
                modifier = Modifier.padding(inner),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = DashboardContentPadding,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (data.isSample) {
                    item { SampleBanner() }
                }
                items(state.cards.filter { it.enabled }, key = { it.id.name }) { cfg ->
                    when (cfg.id) {
                        DashboardCardId.READINESS -> ReadinessCard(data.readiness) { onOpenDetail(DashboardCardId.READINESS) }
                        DashboardCardId.STEPS -> StepsCard(data.steps) { onOpenDetail(DashboardCardId.STEPS) }
                        DashboardCardId.LAST_WORKOUT -> WorkoutCard(data.lastWorkout) { onOpenDetail(DashboardCardId.LAST_WORKOUT) }
                        DashboardCardId.HEART -> HeartCard(data.heart) { onOpenDetail(DashboardCardId.HEART) }
                        DashboardCardId.SLEEP_STRESS -> SleepStressCard(data.sleepStress) { onOpenDetail(DashboardCardId.SLEEP_STRESS) }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SampleBanner() {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        Text(
            "Showing sample data — device metrics will appear here once wired to the database.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomizeList(
    cards: List<CardConfig>,
    onToggle: (DashboardCardId) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = DashboardContentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionLabel("Customize cards")
            Spacer(Modifier.height(6.dp))
            Text(
                "Turn cards on or off and reorder them.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        items(cards, key = { it.id.name }) { cfg ->
            val index = cards.indexOf(cfg)
            DashboardCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cardTitle(cfg.id), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                        Text(cardSubtitle(cfg.id), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (index > 0) onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up",
                            tint = if (index > 0) scheme.onSurface else scheme.outlineVariant)
                    }
                    IconButton(onClick = { if (index < cards.lastIndex) onMove(index, index + 1) }, enabled = index < cards.lastIndex) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down",
                            tint = if (index < cards.lastIndex) scheme.onSurface else scheme.outlineVariant)
                    }
                    Switch(checked = cfg.enabled, onCheckedChange = { onToggle(cfg.id) })
                }
            }
        }
    }
}

private fun cardTitle(id: DashboardCardId) = when (id) {
    DashboardCardId.READINESS -> "Readiness"
    DashboardCardId.STEPS -> "Steps & activity"
    DashboardCardId.LAST_WORKOUT -> "Last workout"
    DashboardCardId.HEART -> "Heart rate & SpO₂"
    DashboardCardId.SLEEP_STRESS -> "Sleep & stress"
}

private fun cardSubtitle(id: DashboardCardId) = when (id) {
    DashboardCardId.READINESS -> "Recuperación estimada de hoy"
    DashboardCardId.STEPS -> "Daily steps, distance and goal"
    DashboardCardId.LAST_WORKOUT -> "Your most recent session"
    DashboardCardId.HEART -> "Current, resting and oxygen"
    DashboardCardId.SLEEP_STRESS -> "Last night and stress level"
}

// --- previews ---

private fun sampleState() = DashboardUiState(
    loading = false,
    data = SampleHealthRepository().load(),
    cards = DashboardCardId.entries.map { CardConfig(it, it.defaultEnabled) },
)

@Preview(name = "Dashboard", heightDp = 1000)
@Composable
private fun DashboardPreview() {
    UltimateTheme {
        DashboardScreen(sampleState(), {}, {}, { _, _ -> }, {})
    }
}

@Preview(name = "Customize")
@Composable
private fun CustomizePreview() {
    UltimateTheme {
        DashboardScreen(sampleState().copy(editing = true), {}, {}, { _, _ -> }, {})
    }
}

@Preview(name = "Steps card")
@Composable
private fun StepsPreview() {
    UltimateTheme { StepsCard(SampleHealthRepository().load().steps) {} }
}

@Preview(name = "Workout card")
@Composable
private fun WorkoutPreview() {
    UltimateTheme { WorkoutCard(SampleHealthRepository().load().lastWorkout) {} }
}

@Preview(name = "Heart card")
@Composable
private fun HeartPreview() {
    UltimateTheme { HeartCard(SampleHealthRepository().load().heart) {} }
}

@Preview(name = "Sleep card")
@Composable
private fun SleepPreview() {
    UltimateTheme { SleepStressCard(SampleHealthRepository().load().sleepStress) {} }
}
