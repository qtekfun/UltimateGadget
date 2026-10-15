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

import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import nodomain.freeyourgadget.gadgetbridge.R
import java.util.Locale

/** Shown inside a card when the device has no data for that metric yet. */
@Composable
private fun NoData(label: String) {
    val scheme = MaterialTheme.colorScheme
    SectionLabel(label)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.ult_no_data_yet), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
}

/** STEPS: big count, weekly bar sparkline, goal progress. */
@Composable
fun StepsCard(data: StepsData?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (data == null) { DashboardCard(onClick = onClick) { NoData(stringResource(R.string.ult_steps_today)) }; return }
    DashboardCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(stringResource(R.string.ult_steps_today))
            Text(
                stringResource(R.string.ult_pct_of_goal, pct(data.steps, data.goal)),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(group(data.steps), style = MetricValueStyle, color = scheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.ult_metric_steps), style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        BarSparkline(
            values = data.weekly.map { it.toFloat() },
            color = scheme.primary, trackColor = scheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ult_dist_kcal_goal, data.distanceKm, data.kcal, group(data.goal)),
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
    }
}

/** LAST WORKOUT: type/distance/duration, trace, chevron to the list. */
@Composable
fun WorkoutCard(data: WorkoutData?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard(onClick = onClick) {
        SectionLabel(stringResource(R.string.ult_last_workout))
        Spacer(Modifier.height(8.dp))
        if (data == null) {
            Text(stringResource(R.string.ult_no_workouts_yet), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            return@DashboardCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)),
            ) {
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(14.dp))) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        drawRect(Brush.linearGradient(listOf(scheme.secondaryContainer, scheme.surfaceContainerHigh)))
                    }
                }
                if (data.iconRes != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(data.iconRes), contentDescription = null,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(scheme.secondary),
                        modifier = Modifier.size(24.dp).align(Alignment.Center),
                    )
                } else {
                    Icon(
                        Icons.Filled.DirectionsRun, contentDescription = null,
                        tint = scheme.secondary, modifier = Modifier.size(24.dp).align(Alignment.Center),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(data.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Text(
                    if (data.line.isNotEmpty()) "${data.line} · ${data.whenLabel}"
                    else String.format(Locale.getDefault(), "%.1f km · %s · %s",
                        data.distanceKm, duration(data.durationSeconds), data.whenLabel),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(values = data.trace, color = scheme.secondary)
    }
}

/** HEART & SpO2: two metrics side by side with a trend line. */
@Composable
fun HeartCard(data: HeartData?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (data == null) { DashboardCard(onClick = onClick) { NoData(stringResource(R.string.ult_hr_spo2)) }; return }
    DashboardCard(onClick = onClick) {
        SectionLabel(stringResource(R.string.ult_hr_spo2))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${data.currentBpm}", style = MetricValueStyle, color = scheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ult_bpm), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(stringResource(R.string.ult_resting_bpm, data.restingBpm), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${data.spo2}%", style = MaterialTheme.typography.headlineSmall, color = scheme.secondary, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ult_spo2), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(values = data.recent.map { it.toFloat() }, color = scheme.error)
    }
}

/** SLEEP & STRESS: sleep duration + phase bar, stress level. */
@Composable
fun SleepStressCard(data: SleepStressData?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (data == null) { DashboardCard(onClick = onClick) { NoData(stringResource(R.string.ult_sleep_stress)) }; return }
    DashboardCard(onClick = onClick) {
        SectionLabel(stringResource(R.string.ult_sleep_stress))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hoursMinutes(data.sleepMinutes), style = MetricValueStyle, color = scheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ult_last_night), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        SegmentBar(
            segments = listOf(
                data.deepMinutes.toFloat() to scheme.primary,
                data.remMinutes.toFloat() to scheme.secondary,
                data.lightMinutes.toFloat() to scheme.primaryContainer,
                data.awakeMinutes.toFloat() to scheme.surfaceContainerHighest,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ult_sleep_phases, data.deepMinutes, data.remMinutes, data.lightMinutes),
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ult_stress), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Text(
                "${data.stressLevel} · ${stressLabel(data.stressLevel)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = stressColor(data.stressLevel, scheme.secondary, scheme.tertiary, scheme.error),
            )
        }
    }
}

/** READINESS: 0..100 recovery score, coloured by range, with its contributing factors. */
@Composable
fun ReadinessCard(
    data: nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessData?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val score = data?.score
    if (score == null) { DashboardCard(onClick = onClick) { NoData(stringResource(R.string.ult_readiness)) }; return }
    val color = when {
        score >= 75 -> scheme.secondary
        score >= 35 -> scheme.tertiary
        else -> scheme.error
    }
    DashboardCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(stringResource(R.string.ult_readiness))
            Text(stringResource(R.string.ult_confidence, data.confidence), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$score", style = MetricValueStyle, color = color)
            Spacer(Modifier.width(10.dp))
            Text(
                nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessEngine.label(score),
                style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            data.factors.forEach { f ->
                Text(
                    "${factorShort(f.id)} ${f.score}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun factorShort(id: nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessFactorId) = stringResource(when (id) {
    nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessFactorId.HRV -> R.string.ult_fs_hrv
    nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessFactorId.RHR -> R.string.ult_fs_rhr
    nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessFactorId.SLEEP -> R.string.ult_fs_sleep
    nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness.ReadinessFactorId.LOAD -> R.string.ult_fs_load
})

// --- small formatting helpers ---

private fun pct(v: Int, goal: Int) = if (goal <= 0) 0 else (v * 100 / goal)
private fun group(v: Int): String = String.format(Locale.getDefault(), "%,d", v)
private fun duration(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}
private fun hoursMinutes(min: Int) = String.format(Locale.getDefault(), "%dh %02dm", min / 60, min % 60)
@Composable
private fun stressLabel(level: Int) = stringResource(when {
    level < 30 -> R.string.ult_stress_relaxed; level < 60 -> R.string.ult_stress_normal; level < 80 -> R.string.ult_stress_medium; else -> R.string.ult_stress_high
})
private fun stressColor(level: Int, low: androidx.compose.ui.graphics.Color, mid: androidx.compose.ui.graphics.Color, high: androidx.compose.ui.graphics.Color) =
    when { level < 30 -> low; level < 80 -> mid; else -> high }
