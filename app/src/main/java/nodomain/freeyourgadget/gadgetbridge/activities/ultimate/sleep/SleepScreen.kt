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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.sleep

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.SectionLabel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    state: SleepUiState,
    onBack: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ult_sleep), fontWeight = FontWeight.Bold) },
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
            DaySelector(state, onPreviousDay, onNextDay)
            when {
                state.loading -> LoadingCard()
                state.data?.hasData != true -> EmptyCard()
                else -> {
                    val d = state.data
                    ScoreCard(d)
                    HypnogramCard(d)
                    SummaryCard(d)
                    if (d.trueSleep) TrueSleepCard(d)
                    if (d.spo2Avg > 0) Spo2Card(d)
                    if (d.naps.isNotEmpty()) NapsCard(d)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DaySelector(state: SleepUiState, onPrev: () -> Unit, onNext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.ult_prev_day), tint = scheme.onSurface)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel(if (state.isToday) stringResource(R.string.ult_today) else stringResource(R.string.ult_night_of))
                Spacer(Modifier.height(2.dp))
                Text(state.dayLabel, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onNext, enabled = !state.isToday) {
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.ult_next_day),
                    tint = if (state.isToday) scheme.onSurfaceVariant.copy(alpha = 0.3f) else scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ScoreCard(d: SleepDayData) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        d.score >= 80 -> scheme.secondary
        d.score >= 50 -> scheme.tertiary
        else -> scheme.error
    }
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(stringResource(R.string.ult_sleep_score))
            Text(
                if (d.scoreNative) stringResource(R.string.ult_from_device) else stringResource(R.string.ult_sleep_estimated),
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${d.score}", style = MetricValueStyle, color = color)
            Spacer(Modifier.width(8.dp))
            Text(scoreLabel(d.score), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hoursMinutes(d.totalMin), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ult_slept_range, d.sleepStartLabel, d.sleepEndLabel), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun HypnogramCard(d: SleepDayData) {
    val scheme = MaterialTheme.colorScheme
    val colors = phaseColors(scheme)
    DashboardCard {
        SectionLabel(stringResource(R.string.ult_sleep_stages))
        Spacer(Modifier.height(10.dp))
        if (d.segments.isEmpty()) {
            Text(stringResource(R.string.ult_no_stage_detail), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        } else {
            val totalMin = ((d.sessionEndMillis - d.sessionStartMillis) / 60000L).toInt().coerceAtLeast(1)
            Hypnogram(d.segments, totalMin, colors, scheme.surfaceContainerHighest)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(d.sleepStartLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text(d.sleepEndLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        PhaseLegend(d, colors)
    }
}

/** Classic hypnogram: four stacked lanes (Awake, REM, Light, Deep) across the night timeline. */
@Composable
private fun Hypnogram(segments: List<SleepSegment>, totalMin: Int, colors: Map<SleepPhase, Color>, track: Color) {
    val lanes = listOf(SleepPhase.AWAKE, SleepPhase.REM, SleepPhase.LIGHT, SleepPhase.DEEP)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val laneH = size.height / lanes.size
        val barH = laneH * 0.62f
        // Faint lane guides.
        lanes.forEachIndexed { i, _ ->
            val cy = laneH * i + (laneH - barH) / 2f
            drawRoundRect(
                color = track.copy(alpha = 0.25f),
                topLeft = Offset(0f, cy),
                size = Size(size.width, barH),
                cornerRadius = CornerRadius(barH / 2, barH / 2),
            )
        }
        segments.forEach { seg ->
            val laneIdx = lanes.indexOf(seg.phase).coerceAtLeast(0)
            val x = size.width * (seg.startOffsetMin.toFloat() / totalMin)
            val w = (size.width * (seg.durationMin.toFloat() / totalMin)).coerceAtLeast(2f)
            val cy = laneH * laneIdx + (laneH - barH) / 2f
            drawRoundRect(
                color = colors[seg.phase] ?: track,
                topLeft = Offset(x.coerceIn(0f, size.width), cy),
                size = Size(w.coerceAtMost(size.width - x), barH),
                cornerRadius = CornerRadius(barH / 2.5f, barH / 2.5f),
            )
        }
    }
}

@Composable
private fun PhaseLegend(d: SleepDayData, colors: Map<SleepPhase, Color>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendRow(colors[SleepPhase.DEEP]!!, stringResource(R.string.ult_deep), d.deepMin, d.totalMin)
        LegendRow(colors[SleepPhase.REM]!!, stringResource(R.string.ult_rem), d.remMin, d.totalMin)
        LegendRow(colors[SleepPhase.LIGHT]!!, stringResource(R.string.ult_light), d.lightMin, d.totalMin)
        LegendRow(colors[SleepPhase.AWAKE]!!, stringResource(R.string.ult_awake), d.awakeMin, d.totalMin)
    }
}

@Composable
private fun LegendRow(color: Color, label: String, minutes: Int, totalMin: Int) {
    val scheme = MaterialTheme.colorScheme
    val pct = if (totalMin > 0) (minutes * 100 / totalMin) else 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Text("${hoursMinutes(minutes)} · $pct%", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryCard(d: SleepDayData) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        SectionLabel(stringResource(R.string.ult_summary))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.ult_total), hoursMinutes(d.totalMin), scheme.onSurface)
            Stat(stringResource(R.string.ult_deep), hoursMinutes(d.deepMin), scheme.onSurface)
            Stat(stringResource(R.string.ult_rem), hoursMinutes(d.remMin), scheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.ult_light), hoursMinutes(d.lightMin), scheme.onSurface)
            Stat(stringResource(R.string.ult_awake), hoursMinutes(d.awakeMin), scheme.onSurface)
            Stat(stringResource(R.string.ult_awakenings), "${d.awakenings}", scheme.onSurface)
        }
    }
}

/** Huawei TrueSleep extras the watch recorded for the night (only the fields actually present). */
@Composable
private fun TrueSleepCard(d: SleepDayData) {
    val scheme = MaterialTheme.colorScheme
    val lEfficiency = stringResource(R.string.ult_efficiency)
    val lLatency = stringResource(R.string.ult_latency)
    val lPulse = stringResource(R.string.ult_pulse)
    val lAvgPulse = stringResource(R.string.ult_avg_pulse)
    val lBreathing = stringResource(R.string.ult_breathing)
    val lAvgHrv = stringResource(R.string.ult_avg_hrv)
    val lSnoring = stringResource(R.string.ult_snoring)
    val lApnea = stringResource(R.string.ult_apnea_rdi)
    val lTurnovers = stringResource(R.string.ult_turnovers)
    val items = buildList {
        d.sleepEfficiency?.let { add(lEfficiency to "$it%") }
        d.sleepLatencyMin?.let { add(lLatency to "$it min") }
        if (d.hrMin != null && d.hrMax != null) add(lPulse to "${d.hrMin}–${d.hrMax} bpm")
        else d.hrAvg?.let { add(lAvgPulse to "$it bpm") }
        d.breathAvg?.let { add(lBreathing to "$it/min") }
        d.hrvAvg?.let { add(lAvgHrv to "$it ms") }
        d.snoreCount?.let { add(lSnoring to "$it") }
        d.apneaIndex?.let { add(lApnea to "$it") }
        d.turnOverCount?.let { add(lTurnovers to "$it") }
    }
    if (items.isEmpty()) return
    DashboardCard {
        SectionLabel(stringResource(R.string.ult_truesleep))
        Spacer(Modifier.height(10.dp))
        items.chunked(3).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { (l, v) -> Stat(l, v, scheme.onSurface) }
                repeat(3 - row.size) { Stat("", "", scheme.onSurface) }
            }
        }
    }
}

@Composable
private fun Spo2Card(d: SleepDayData) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(stringResource(R.string.ult_spo2_night))
            Text(stringResource(R.string.ult_spo2_min_avg, d.spo2Min, d.spo2Avg), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${d.spo2Avg}", style = MetricValueStyle, color = scheme.secondary)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ult_pct_avg), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (d.spo2Samples.size >= 2) {
            Spacer(Modifier.height(10.dp))
            Spo2Bars(d.spo2Samples, scheme.secondary, scheme.error, scheme.surfaceContainerHighest)
        }
    }
}

/** SpO2 bar chart: each reading scaled in 90..100%, readings below 90% drawn in the alert color. */
@Composable
private fun Spo2Bars(values: List<Int>, normal: Color, low: Color, track: Color) {
    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
        if (values.isEmpty()) return@Canvas
        val floor = 90f
        val gap = size.width * 0.01f
        val barW = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val norm = ((v - floor) / (100f - floor)).coerceIn(0.05f, 1f)
            val h = size.height * norm
            val left = i * (barW + gap)
            drawRoundRect(
                color = if (v < floor.toInt()) low else normal,
                topLeft = Offset(left, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 3, barW / 3),
            )
        }
    }
}

@Composable
private fun NapsCard(d: SleepDayData) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        SectionLabel(stringResource(R.string.ult_naps))
        Spacer(Modifier.height(8.dp))
        d.naps.forEach { nap ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${nap.startLabel}–${nap.endLabel}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text(hoursMinutes(nap.durationMin), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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
        Text(stringResource(R.string.ult_no_sleep_data_day), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ult_sync_or_pick), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun phaseColors(scheme: androidx.compose.material3.ColorScheme): Map<SleepPhase, Color> = mapOf(
    SleepPhase.DEEP to scheme.primary,
    SleepPhase.REM to scheme.secondary,
    SleepPhase.LIGHT to scheme.primaryContainer,
    SleepPhase.AWAKE to scheme.tertiary,
)

private fun hoursMinutes(min: Int): String =
    if (min >= 60) String.format(Locale.getDefault(), "%dh %02dm", min / 60, min % 60)
    else String.format(Locale.getDefault(), "%dm", min)

@Composable
private fun scoreLabel(score: Int): String = stringResource(when {
    score >= 85 -> R.string.ult_quality_excellent
    score >= 70 -> R.string.ult_quality_good
    score >= 50 -> R.string.ult_quality_fair
    score > 0 -> R.string.ult_quality_poor
    else -> R.string.ult_quality_nodata
})
