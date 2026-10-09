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
import java.util.Locale

/** STEPS: big count, weekly bar sparkline, goal progress. */
@Composable
fun StepsCard(data: StepsData, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("Steps today")
            Text(
                "${pct(data.steps, data.goal)}% of goal",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(group(data.steps), style = MetricValueStyle, color = scheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(
                "steps", style = MaterialTheme.typography.bodyMedium,
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
            String.format(Locale.getDefault(), "%.1f km · %d kcal · goal %s",
                data.distanceKm, data.kcal, group(data.goal)),
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
    }
}

/** LAST WORKOUT: type/distance/duration, trace, chevron to the list. */
@Composable
fun WorkoutCard(data: WorkoutData?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard(onClick = onClick) {
        SectionLabel("Last workout")
        Spacer(Modifier.height(8.dp))
        if (data == null) {
            Text("No workouts yet", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
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
                Icon(
                    Icons.Filled.DirectionsRun, contentDescription = null,
                    tint = scheme.secondary, modifier = Modifier.size(24.dp).align(Alignment.Center),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(data.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Text(
                    String.format(Locale.getDefault(), "%.1f km · %s · %s",
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
fun HeartCard(data: HeartData, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard(onClick = onClick) {
        SectionLabel("Heart rate & SpO₂")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${data.currentBpm}", style = MetricValueStyle, color = scheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text("bpm", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("Resting ${data.restingBpm} bpm", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${data.spo2}%", style = MaterialTheme.typography.headlineSmall, color = scheme.secondary, fontWeight = FontWeight.Bold)
                Text("SpO₂", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(values = data.recent.map { it.toFloat() }, color = scheme.error)
    }
}

/** SLEEP & STRESS: sleep duration + phase bar, stress level. */
@Composable
fun SleepStressCard(data: SleepStressData, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard(onClick = onClick) {
        SectionLabel("Sleep & stress")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hoursMinutes(data.sleepMinutes), style = MetricValueStyle, color = scheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text("last night", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
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
            String.format(Locale.getDefault(), "Deep %dm · REM %dm · Light %dm",
                data.deepMinutes, data.remMinutes, data.lightMinutes),
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Stress", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Text(
                "${data.stressLevel} · ${stressLabel(data.stressLevel)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = stressColor(data.stressLevel, scheme.secondary, scheme.tertiary, scheme.error),
            )
        }
    }
}

// --- small formatting helpers ---

private fun pct(v: Int, goal: Int) = if (goal <= 0) 0 else (v * 100 / goal)
private fun group(v: Int): String = String.format(Locale.getDefault(), "%,d", v)
private fun duration(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}
private fun hoursMinutes(min: Int) = String.format(Locale.getDefault(), "%dh %02dm", min / 60, min % 60)
private fun stressLabel(level: Int) = when {
    level < 30 -> "Relaxed"; level < 60 -> "Normal"; level < 80 -> "Medium"; else -> "High"
}
private fun stressColor(level: Int, low: androidx.compose.ui.graphics.Color, mid: androidx.compose.ui.graphics.Color, high: androidx.compose.ui.graphics.Color) =
    when { level < 30 -> low; level < 80 -> mid; else -> high }
