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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.performance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.BarSparkline
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.SectionLabel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(state: PerformanceUiState, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Performance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                state.loading -> LoadingCard()
                state.data == null -> EmptyCard()
                else -> {
                    val d = state.data
                    PaiCard(d.pai)
                    TrainingLoadCard(d.load)
                    Vo2MaxCard(d.vo2max)
                    HrvCard(d.hrv)
                    MethodNote(d)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- PAI --------------------------------------------------------------------------------------

@Composable
private fun PaiCard(pai: PaiData?) {
    val scheme = MaterialTheme.colorScheme
    if (pai == null) { NoDataCard("PAI", "Not enough heart-rate data in the last 7 days.") ; return }
    val color = when {
        pai.total >= 100 -> scheme.secondary
        pai.total >= 50 -> scheme.tertiary
        else -> scheme.primary
    }
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("PAI · 7 days")
            Text("objetivo 100", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = pai.total / 100f, color = color, track = scheme.surfaceContainerHighest) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${pai.total}", style = MetricValueStyle, color = color)
                    Text("PAI", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                ZoneRow("Moderado", "${pai.moderateMinutes} min", scheme.tertiary)
                Spacer(Modifier.height(6.dp))
                ZoneRow("Vigoroso", "${pai.vigorousMinutes} min", scheme.secondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        BarSparkline(
            values = pai.daily.map { it.toFloat() },
            color = color, trackColor = scheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(10.dp))
        Explain(
            "HUNT-style model: accrues points for minutes with elevated HR (moderate zone 59–76% " +
                "of HRmax, vigorous ≥76%) over the last 7 days. Keeping ~100 PAI/week is the target.",
        )
    }
}

@Composable
private fun ZoneRow(label: String, value: String, dot: Color) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

// ---- Training load ----------------------------------------------------------------------------

@Composable
private fun TrainingLoadCard(load: TrainingLoadData?) {
    val scheme = MaterialTheme.colorScheme
    if (load == null) { NoDataCard("Training load", "No workouts recorded in the last 28 days.") ; return }
    val acwr = load.acwr
    val acwrColor = when {
        acwr == null -> scheme.onSurface
        acwr in 0.8..1.3 -> scheme.secondary
        acwr < 0.8 -> scheme.tertiary
        acwr <= 1.5 -> scheme.tertiary
        else -> scheme.error
    }
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("Training load")
            Text(
                if (load.usedHeartRate) "TRIMP" else "estimated (no HR)",
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Aguda · 7d", "${load.acute}", scheme.onSurface)
            Metric("Chronic · wk", "${load.chronic}", scheme.onSurface)
            Metric("ACWR", acwr?.let { String.format(Locale.US, "%.2f", it) } ?: "—", acwrColor)
        }
        Spacer(Modifier.height(12.dp))
        BarSparkline(
            values = load.daily.map { it.toFloat() },
            color = scheme.primary, trackColor = scheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${load.workoutsCounted} workouts · last 28 days",
            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Explain(
            "Banister TRIMP per workout (duration × HR reserve, exponentially weighted). The " +
                "acute load (7 days) against the chronic load (28-day weekly average) gives the ACWR: " +
                "0.8–1.3 is the recommended zone; above 1.5 the injury risk rises.",
        )
    }
}

// ---- VO2max -----------------------------------------------------------------------------------

@Composable
private fun Vo2MaxCard(vo2: Vo2MaxData?) {
    val scheme = MaterialTheme.colorScheme
    if (vo2 == null) { NoDataCard("VO2max", "Needs resting HR or a recent run with distance.") ; return }
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("VO2max")
            Text("estimado", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.US, "%.1f", vo2.value), style = MetricValueStyle, color = scheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text("ml/kg/min", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(vo2.detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Explain(
            when (vo2.method) {
                Vo2Method.RUNNING_VDOT ->
                    "Estimated with Jack Daniels' VDOT from a recent run (speed and duration)."
                Vo2Method.HR_RATIO ->
                    "Estimated with the Uth–Sørensen HR ratio: 15.3 × HRmax / HRrest. It is an approximation, " +
                        "not a lab measurement."
            },
        )
    }
}

// ---- HRV --------------------------------------------------------------------------------------

@Composable
private fun HrvCard(hrv: HrvData?) {
    val scheme = MaterialTheme.colorScheme
    if (hrv == null) {
        NoDataCard("HRV (rMSSD)", "The device has not synced heart-rate variability data.")
        return
    }
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("HRV · rMSSD")
            hrv.status?.let { Text(hrvStatusLabel(it), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hrv.lastNight?.toString() ?: "—", style = MetricValueStyle, color = scheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text("ms anoche", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        hrv.weekly?.let {
            Text("Media semanal $it ms", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (hrv.trend.size >= 2) {
            Spacer(Modifier.height(10.dp))
            BarSparkline(
                values = hrv.trend.map { it.toFloat() },
                color = scheme.secondary, trackColor = scheme.surfaceContainerHighest,
            )
        }
        Spacer(Modifier.height(10.dp))
        Explain("Values read straight from the device; no estimation of our own is computed.")
    }
}

// ---- Shared pieces ----------------------------------------------------------------------------

@Composable
private fun MethodNote(d: PerformanceData) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        SectionLabel("How it is calculated")
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("Everything is computed on the phone from the Gadgetbridge database, offline. ")
                if (d.age > 0) append("Estimated HRmax ${d.hrMax} bpm (220 − ${d.age}). ")
                else append("Estimated HRmax ${d.hrMax} bpm (age not set; adjust it in the profile). ")
                d.restingHr?.let { append("Reference resting HR $it bpm.") }
                    ?: append("No resting HR recorded: some metrics use an approximation.")
            },
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, valueColor: Color) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun Explain(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NoDataCard(title: String, reason: String) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        SectionLabel(title)
        Spacer(Modifier.height(8.dp))
        Text("Sin datos", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(reason, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingCard() {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyCard() {
    DashboardCard {
        Text("No device with data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("Connect and sync the watch to see your performance.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A circular progress ring with centred content. Progress clamps at 1 for the arc; label can exceed it. */
@Composable
private fun ProgressRing(
    progress: Float,
    color: Color,
    track: Color,
    content: @Composable () -> Unit,
) {
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color, startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

private fun hrvStatusLabel(status: String): String = when (status) {
    "BALANCED" -> "Equilibrado"
    "UNBALANCED" -> "Desequilibrado"
    "LOW" -> "Bajo"
    "POOR" -> "Pobre"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}
