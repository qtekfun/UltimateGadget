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
import androidx.compose.ui.res.stringResource
import nodomain.freeyourgadget.gadgetbridge.R
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
                title = { Text(stringResource(R.string.ult_performance), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back))
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
    if (pai == null) { NoDataCard(stringResource(R.string.ult_pai), stringResource(R.string.ult_pai_no_data)) ; return }
    val color = when {
        pai.total >= 100 -> scheme.secondary
        pai.total >= 50 -> scheme.tertiary
        else -> scheme.primary
    }
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(stringResource(R.string.ult_pai_7days))
            Text(stringResource(R.string.ult_target_100), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = pai.total / 100f, color = color, track = scheme.surfaceContainerHighest) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${pai.total}", style = MetricValueStyle, color = color)
                    Text(stringResource(R.string.ult_pai), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                ZoneRow(stringResource(R.string.ult_moderate), "${pai.moderateMinutes} min", scheme.tertiary)
                Spacer(Modifier.height(6.dp))
                ZoneRow(stringResource(R.string.ult_vigorous), "${pai.vigorousMinutes} min", scheme.secondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        BarSparkline(
            values = pai.daily.map { it.toFloat() },
            color = color, trackColor = scheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(10.dp))
        Explain(stringResource(R.string.ult_pai_explain))
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
    if (load == null) { NoDataCard(stringResource(R.string.ult_training_load), stringResource(R.string.ult_training_load_no_data)) ; return }
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
            SectionLabel(stringResource(R.string.ult_training_load))
            Text(
                if (load.usedHeartRate) stringResource(R.string.ult_trimp) else stringResource(R.string.ult_estimated_no_hr),
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(stringResource(R.string.ult_acute_7d), "${load.acute}", scheme.onSurface)
            Metric(stringResource(R.string.ult_chronic_wk), "${load.chronic}", scheme.onSurface)
            Metric(stringResource(R.string.ult_acwr), acwr?.let { String.format(Locale.US, "%.2f", it) } ?: "—", acwrColor)
        }
        Spacer(Modifier.height(12.dp))
        BarSparkline(
            values = load.daily.map { it.toFloat() },
            color = scheme.primary, trackColor = scheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ult_workouts_28days, load.workoutsCounted),
            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Explain(stringResource(R.string.ult_load_explain))
    }
}

// ---- VO2max -----------------------------------------------------------------------------------

@Composable
private fun Vo2MaxCard(vo2: Vo2MaxData?) {
    val scheme = MaterialTheme.colorScheme
    if (vo2 == null) { NoDataCard(stringResource(R.string.ult_vo2max), stringResource(R.string.ult_vo2_no_data)) ; return }
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(stringResource(R.string.ult_vo2max))
            Text(stringResource(R.string.ult_vo2_estimated), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.US, "%.1f", vo2.value), style = MetricValueStyle, color = scheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text("ml/kg/min", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) // unit, not translated
        }
        Spacer(Modifier.height(6.dp))
        Text(vo2.detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Explain(
            stringResource(when (vo2.method) {
                Vo2Method.RUNNING_VDOT -> R.string.ult_vo2_explain_vdot
                Vo2Method.HR_RATIO -> R.string.ult_vo2_explain_hr
            }),
        )
    }
}

// ---- HRV --------------------------------------------------------------------------------------

@Composable
private fun HrvCard(hrv: HrvData?) {
    val scheme = MaterialTheme.colorScheme
    if (hrv == null) {
        NoDataCard(stringResource(R.string.ult_hrv_rmssd_title), stringResource(R.string.ult_hrv_no_data))
        return
    }
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(stringResource(R.string.ult_hrv_rmssd))
            hrv.status?.let { Text(hrvStatusLabel(it), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hrv.lastNight?.toString() ?: "—", style = MetricValueStyle, color = scheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ult_ms_last_night), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        hrv.weekly?.let {
            Text(stringResource(R.string.ult_weekly_avg_ms, it), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (hrv.trend.size >= 2) {
            Spacer(Modifier.height(10.dp))
            BarSparkline(
                values = hrv.trend.map { it.toFloat() },
                color = scheme.secondary, trackColor = scheme.surfaceContainerHighest,
            )
        }
        Spacer(Modifier.height(10.dp))
        Explain(stringResource(R.string.ult_hrv_explain))
    }
}

// ---- Shared pieces ----------------------------------------------------------------------------

@Composable
private fun MethodNote(d: PerformanceData) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        SectionLabel(stringResource(R.string.ult_how_calculated))
        Spacer(Modifier.height(8.dp))
        val intro = stringResource(R.string.ult_method_intro)
        val hrmaxStr = if (d.age > 0) stringResource(R.string.ult_hrmax_set, d.hrMax, d.age)
        else stringResource(R.string.ult_hrmax_noage, d.hrMax)
        val restingStr = d.restingHr?.let { stringResource(R.string.ult_resting_ref, it) }
            ?: stringResource(R.string.ult_resting_none)
        Text(
            intro + hrmaxStr + restingStr,
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
        Text(stringResource(R.string.ult_quality_nodata), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
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
        Text(stringResource(R.string.ult_perf_no_device), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ult_perf_connect_sync), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun hrvStatusLabel(status: String): String = when (status) {
    "BALANCED" -> stringResource(R.string.ult_hrv_balanced)
    "UNBALANCED" -> stringResource(R.string.ult_hrv_unbalanced)
    "LOW" -> stringResource(R.string.ult_hrv_low)
    "POOR" -> stringResource(R.string.ult_hrv_poor)
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}
