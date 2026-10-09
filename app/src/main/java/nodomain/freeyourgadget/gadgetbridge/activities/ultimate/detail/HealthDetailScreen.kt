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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.BarSparkline
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCardId
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardData
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.SectionLabel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.Sparkline
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDetailScreen(
    card: DashboardCardId,
    data: DashboardData?,
    workouts: List<WorkoutRow>,
    loading: Boolean,
    onBack: () -> Unit,
    onOpenWorkout: (Long) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(titleFor(card), fontWeight = FontWeight.Bold) },
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
        if (loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = scheme.primary)
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (card) {
                DashboardCardId.STEPS -> StepsDetail(data)
                DashboardCardId.HEART -> HeartDetail(data)
                DashboardCardId.SLEEP_STRESS -> SleepDetail(data)
                DashboardCardId.LAST_WORKOUT -> WorkoutsDetail(workouts, onOpenWorkout)
                DashboardCardId.READINESS -> Unit // opens its own ReadinessDetailActivity
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepsDetail(data: DashboardData?) {
    val s = data?.steps
    val scheme = MaterialTheme.colorScheme
    if (s == null) { EmptyCard("Sin datos de pasos") ; return }
    DashboardCard {
        SectionLabel("Hoy")
        Spacer(Modifier.height(6.dp))
        Text("%,d".format(s.steps), style = MetricValueStyle, color = scheme.onSurface)
        Spacer(Modifier.height(10.dp))
        BarSparkline(s.weekly.map { it.toFloat() }, scheme.primary, scheme.surfaceContainerHighest)
        Spacer(Modifier.height(8.dp))
        Text(
            String.format(Locale.getDefault(), "%.1f km · %d kcal · meta %,d",
                s.distanceKm, s.kcal, s.goal),
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
    }
    DashboardCard {
        SectionLabel("Últimos 7 días")
        Spacer(Modifier.height(8.dp))
        s.weekly.reversed().forEachIndexed { idx, v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (idx == 0) "Hoy" else "-$idx d", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text("%,d pasos".format(v), color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeartDetail(data: DashboardData?) {
    val h = data?.heart
    val scheme = MaterialTheme.colorScheme
    if (h == null) { EmptyCard("Sin datos de pulso") ; return }
    DashboardCard {
        SectionLabel("Frecuencia cardiaca")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${h.currentBpm}", style = MetricValueStyle, color = scheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text("bpm", color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (h.recent.size >= 2) {
            Spacer(Modifier.height(10.dp))
            Sparkline(h.recent.map { it.toFloat() }, scheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Text("Reposo ${h.restingBpm} bpm", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (h.spo2 > 0) Text("SpO₂ ${h.spo2}%", color = scheme.secondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SleepDetail(data: DashboardData?) {
    val sl = data?.sleepStress
    val scheme = MaterialTheme.colorScheme
    if (sl == null) { EmptyCard("Sin datos de sueño") ; return }
    DashboardCard {
        SectionLabel("Sueño de anoche")
        Spacer(Modifier.height(6.dp))
        Text(String.format(Locale.getDefault(), "%dh %02dm", sl.sleepMinutes / 60, sl.sleepMinutes % 60),
            style = MetricValueStyle, color = scheme.onSurface)
        if (sl.stressLevel > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Estrés: ${sl.stressLevel}/100", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One activity type present in the workout list, for the filter chips. */
private data class WorkoutType(val code: Int, val label: String, val iconRes: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutsDetail(workouts: List<WorkoutRow>, onOpenWorkout: (Long) -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    if (workouts.isEmpty()) { EmptyCard("Sin entrenos todavía") ; return }

    // Distinct activity types present in the data, in order of first appearance.
    val types = remember(workouts) {
        workouts.distinctBy { it.activityKindCode }
            .map { WorkoutType(it.activityKindCode, it.typeLabel, it.iconRes) }
    }
    // null = "Todos"; otherwise the selected ActivityKind code.
    var selected by remember(workouts) { mutableStateOf<Int?>(null) }

    // Show the type filter only when there is more than one type to choose from.
    if (types.size > 1) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { selected = null },
                label = { Text("Todos") },
            )
            types.forEach { t ->
                FilterChip(
                    selected = selected == t.code,
                    onClick = { selected = if (selected == t.code) null else t.code },
                    label = { Text(t.label.ifEmpty { "—" }) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(t.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }

    val shown = if (selected == null) workouts else workouts.filter { it.activityKindCode == selected }

    shown.forEach { w ->
        Box(Modifier.clickable { onOpenWorkout(w.id) }) {
            DashboardCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Type icon in a tinted circle.
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(w.iconRes),
                            contentDescription = w.typeLabel.ifEmpty { null },
                            colorFilter = ColorFilter.tint(scheme.primary),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        // Localized activity type name as the primary line.
                        Text(
                            w.typeLabel.ifEmpty { w.title },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                        // Custom device name, only when it differs from the type.
                        if (w.title.isNotBlank() && w.title != w.typeLabel) {
                            Text(
                                w.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val dur = w.durationSeconds
                        val durLabel = String.format(
                            Locale.getDefault(), "%d:%02d:%02d",
                            dur / 3600, (dur % 3600) / 60, dur % 60,
                        )
                        Text(
                            "${w.whenLabel} · $durLabel · ver mapa ›",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        Text(text, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
    }
}

private fun titleFor(card: DashboardCardId) = when (card) {
    DashboardCardId.STEPS -> "Pasos y actividad"
    DashboardCardId.HEART -> "Pulso y SpO₂"
    DashboardCardId.SLEEP_STRESS -> "Sueño y estrés"
    DashboardCardId.LAST_WORKOUT -> "Entrenos"
    DashboardCardId.READINESS -> "Readiness"
}
