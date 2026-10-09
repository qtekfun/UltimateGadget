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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.SectionLabel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onSetPeriod: (ReportPeriod) -> Unit,
    onBack: () -> Unit,
    onOpenGoals: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val data = state.data
    val summary = when (state.period) {
        ReportPeriod.WEEK -> data?.weekly
        ReportPeriod.MONTH -> data?.monthly
    }
    val exportable = summary != null && summary.daysWithData > 0

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val s = summary
        if (uri != null && s != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { ReportPdfExporter.writeTo(it, s) }
                }
            }
        }
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Informes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (exportable) {
                        val s = summary
                        IconButton(onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    runCatching { ReportPdfExporter.export(context, s) }.getOrNull()
                                }
                                if (file != null) ReportPdfExporter.shareFile(context, file)
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartir informe en PDF")
                        }
                        IconButton(onClick = {
                            runCatching { saveLauncher.launch(ReportPdfExporter.suggestedName(s)) }
                        }) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = "Guardar informe en PDF")
                        }
                    }
                    IconButton(onClick = onOpenGoals) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = "Objetivos y medallas")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                    actionIconContentColor = scheme.onSurface,
                ),
            )
        },
    ) { inner ->
        if (state.loading) {
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
            item { PeriodSelector(state.period, onSetPeriod) }

            if (data == null || !data.hasDevice) {
                item { InfoCard("Sin dispositivo", "Conecta un reloj para ver tus informes.") }
                return@LazyColumn
            }
            if (summary == null || summary.daysWithData == 0) {
                item { InfoCard("Sin datos todavía", "Sincroniza el reloj para empezar a ver tus informes semanales y mensuales.") }
                return@LazyColumn
            }

            item { StatGrid(summary) }
            item { StepsChartCard(summary, scheme.primary, scheme.surfaceContainerHighest, scheme.onSurfaceVariant) }
            item { SleepChartCard(summary, scheme.secondary, scheme.surfaceContainerHighest, scheme.onSurfaceVariant) }
            if (summary.points.any { it.restingHr > 0 }) {
                item { RestingHrChartCard(summary, scheme.error, scheme.surfaceContainerHighest, scheme.onSurfaceVariant) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PeriodSelector(period: ReportPeriod, onSetPeriod: (ReportPeriod) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PeriodChip("Semana", period == ReportPeriod.WEEK, Modifier.weight(1f)) { onSetPeriod(ReportPeriod.WEEK) }
        PeriodChip("Mes", period == ReportPeriod.MONTH, Modifier.weight(1f)) { onSetPeriod(ReportPeriod.MONTH) }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(44.dp).clickable { onClick() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatGrid(summary: ReportSummary) {
    val scheme = MaterialTheme.colorScheme
    val distance = String.format(Locale.getDefault(), "%.1f km", summary.totalDistanceKm)
    val sleep = if (summary.avgSleepMinutes > 0)
        String.format(Locale.getDefault(), "%dh %02dm", summary.avgSleepMinutes / 60, summary.avgSleepMinutes % 60)
    else "—"
    val rhr = if (summary.avgRestingHr > 0) "${summary.avgRestingHr} bpm" else "—"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Pasos / día", "%,d".format(summary.avgSteps), Modifier.weight(1f))
            StatTile("Distancia", distance, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Sueño medio", sleep, Modifier.weight(1f))
            StatTile("FC reposo", rhr, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Entrenos", "${summary.workoutCount}", Modifier.weight(1f))
            StatTile("Días con meta", "${summary.goalDays}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = scheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel(label)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MetricValueStyle.copy(fontSize = 26.sp), color = scheme.onSurface)
        }
    }
}

@Composable
private fun StepsChartCard(summary: ReportSummary, barColor: Color, trackColor: Color, labelColor: Color) {
    val scheme = MaterialTheme.colorScheme
    val heading = if (summary.period == ReportPeriod.WEEK) "Pasos por día" else "Pasos (media diaria por semana)"
    DashboardCard {
        SectionLabel(heading)
        Spacer(Modifier.height(10.dp))
        LabeledBarChart(
            points = summary.points,
            value = { it.steps.toFloat() },
            highlight = { it.goalMet },
            barColor = barColor,
            highlightColor = scheme.secondary,
            trackColor = trackColor,
            labelColor = labelColor,
        )
        Spacer(Modifier.height(8.dp))
        Text("Total ${"%,d".format(summary.totalSteps)} pasos",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SleepChartCard(summary: ReportSummary, barColor: Color, trackColor: Color, labelColor: Color) {
    val scheme = MaterialTheme.colorScheme
    if (summary.points.none { it.sleepMinutes > 0 }) return
    DashboardCard {
        SectionLabel("Sueño (horas)")
        Spacer(Modifier.height(10.dp))
        LabeledBarChart(
            points = summary.points,
            value = { it.sleepMinutes / 60f },
            highlight = { false },
            barColor = barColor,
            highlightColor = barColor,
            trackColor = trackColor,
            labelColor = labelColor,
        )
    }
}

@Composable
private fun RestingHrChartCard(summary: ReportSummary, barColor: Color, trackColor: Color, labelColor: Color) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        SectionLabel("FC en reposo (bpm)")
        Spacer(Modifier.height(10.dp))
        LabeledBarChart(
            points = summary.points,
            value = { it.restingHr.toFloat() },
            highlight = { false },
            barColor = barColor,
            highlightColor = barColor,
            trackColor = trackColor,
            labelColor = labelColor,
            zeroBased = false,
        )
    }
}

/**
 * Vertical bars with an x-axis label per bucket, drawn with Compose Canvas (no chart library).
 * Bars that satisfy [highlight] are drawn in [highlightColor] (used for days that met the goal).
 * [zeroBased] = false scales to the min..max range instead of 0..max (better for resting HR).
 */
@Composable
fun LabeledBarChart(
    points: List<ReportPoint>,
    value: (ReportPoint) -> Float,
    highlight: (ReportPoint) -> Boolean,
    barColor: Color,
    highlightColor: Color,
    trackColor: Color,
    labelColor: Color,
    zeroBased: Boolean = true,
) {
    if (points.isEmpty()) return
    val values = points.map { value(it) }
    val maxV = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val minV = if (zeroBased) 0f else values.filter { it > 0f }.minOrNull() ?: 0f
    val base = if (zeroBased) 0f else (minV * 0.9f)
    val range = (maxV - base).takeIf { it > 0f } ?: 1f

    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = points.size
            val gap = size.width * (if (n > 12) 0.015f else 0.04f)
            val barW = (size.width - gap * (n - 1)) / n
            points.forEachIndexed { i, p ->
                val v = value(p)
                val norm = ((v - base) / range).coerceIn(0f, 1f)
                val h = if (v <= 0f) 0f else (size.height * norm).coerceAtLeast(2f)
                val left = i * (barW + gap)
                val top = size.height - h
                drawRoundRect(
                    color = if (v <= 0f) trackColor.copy(alpha = 0.4f)
                    else if (highlight(p)) highlightColor else barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barW, if (v <= 0f) 2f else h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3, barW / 3),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    p.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = labelColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}

// --- previews ---

private fun sampleSummary(period: ReportPeriod): ReportSummary {
    val labels = if (period == ReportPeriod.WEEK) listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    else listOf("S1", "S2", "S3", "S4", "S5")
    val steps = if (period == ReportPeriod.WEEK) listOf(9120, 7430, 11200, 6050, 12840, 8900, 8421)
    else listOf(9800, 10400, 7600, 11200, 9000)
    val points = labels.mapIndexed { i, l ->
        ReportPoint(l, steps[i % steps.size], steps[i % steps.size] / 1300.0,
            7 * 60 + (i * 7 % 40), 56 + i % 5, steps[i % steps.size] >= 10000)
    }
    return ReportSummary(period, points, steps.sumOf { it.toLong() },
        steps.average().toInt(), points.sumOf { it.distanceKm }, 7 * 60 + 12, 57,
        workoutCount = 4, daysWithData = points.size, goalDays = points.count { it.goalMet })
}

@Preview(name = "Reports week", heightDp = 1100)
@Composable
private fun ReportsWeekPreview() {
    UltimateTheme {
        ReportsScreen(
            ReportsUiState(loading = false, period = ReportPeriod.WEEK,
                data = ReportData(sampleSummary(ReportPeriod.WEEK), sampleSummary(ReportPeriod.MONTH),
                    GoalsData(10000, 8421, 3, 11, emptyList()), hasDevice = true)),
            {}, {}, {},
        )
    }
}

@Preview(name = "Reports month", heightDp = 1100)
@Composable
private fun ReportsMonthPreview() {
    UltimateTheme {
        ReportsScreen(
            ReportsUiState(loading = false, period = ReportPeriod.MONTH,
                data = ReportData(sampleSummary(ReportPeriod.WEEK), sampleSummary(ReportPeriod.MONTH),
                    GoalsData(10000, 8421, 3, 11, emptyList()), hasDevice = true)),
            {}, {}, {},
        )
    }
}
