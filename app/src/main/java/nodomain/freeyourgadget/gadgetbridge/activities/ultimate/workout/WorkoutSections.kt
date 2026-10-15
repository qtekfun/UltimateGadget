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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qtekfun.mapcore.LatLon
import com.qtekfun.mapcore.MapTheme
import com.qtekfun.mapcore.UltimateMapEngine
import com.qtekfun.mapcore.UltimateMapView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.SectionLabel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.SegmentBar
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.Sparkline
import java.io.File

/** Colors of zones 1..5, the same ones Gadgetbridge's own workout charts use. */
@Composable
fun zoneColors(): List<Color> = listOf(
    colorResource(R.color.hr_zone_warm_up_color),
    colorResource(R.color.hr_zone_easy_color),
    colorResource(R.color.hr_zone_aerobic_color),
    colorResource(R.color.hr_zone_threshold_color),
    colorResource(R.color.hr_zone_maximum_color),
)

/** The headline numbers, big, two or three per row. */
@Composable
fun WorkoutHeroCard(hero: List<WorkoutMetric>, secondary: List<WorkoutMetric>) {
    if (hero.isEmpty() && secondary.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        val perRow = if (hero.size == 3) 3 else 2
        hero.chunked(perRow).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                row.forEach { m ->
                    Column(Modifier.weight(1f)) {
                        Text(m.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (secondary.isNotEmpty()) {
            if (hero.isNotEmpty()) Spacer(Modifier.height(14.dp))
            MetricChips(secondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricChips(metrics: List<WorkoutMetric>) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEach { m ->
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(m.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Text(m.label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** A row of small "label value" pairs, used above each curve. */
@Composable
private fun StatsRow(stats: List<WorkoutMetric>) {
    if (stats.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        stats.forEach { m ->
            Column {
                Text(m.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Text(m.label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A workout curve over time, with the y range labelled on the left and the elapsed time below.
 * [bands] are (lower bound, color) pairs drawn as soft horizontal stripes behind the line: the
 * heart-rate zones.
 */
@Composable
fun WorkoutCurve(series: WorkoutSeries, color: Color, bands: List<Pair<Float, Color>> = emptyList()) {
    val scheme = MaterialTheme.colorScheme
    val pts = series.points
    if (pts.size < 2) return
    val rawMin = pts.minOf { it.v }
    val rawMax = pts.maxOf { it.v }
    val pad = ((rawMax - rawMin) * 0.08f).coerceAtLeast(1f)
    val minV = rawMin - pad
    val maxV = rawMax + pad
    val t0 = pts.first().t
    val t1 = pts.last().t.coerceAtLeast(t0 + 1f)

    Column {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                fun x(t: Float) = (t - t0) / (t1 - t0) * size.width
                fun y(v: Float): Float {
                    val f = (v - minV) / (maxV - minV)
                    return if (series.inverted) f * size.height else (1f - f) * size.height
                }
                bands.forEachIndexed { i, (lower, c) ->
                    val upper = bands.getOrNull(i + 1)?.first ?: Float.MAX_VALUE
                    val lo = lower.coerceIn(minV, maxV)
                    val hi = upper.coerceIn(minV, maxV)
                    if (hi > lo) {
                        val top = minOf(y(lo), y(hi))
                        drawRect(c.copy(alpha = 0.13f), Offset(0f, top), Size(size.width, kotlin.math.abs(y(hi) - y(lo))))
                    }
                }
                val line = Path().apply {
                    moveTo(x(pts[0].t), y(pts[0].v))
                    for (i in 1 until pts.size) lineTo(x(pts[i].t), y(pts[i].v))
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(pts.last().t), size.height)
                    lineTo(x(pts.first().t), size.height)
                    close()
                }
                if (!series.inverted) {
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f))))
                }
                drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val topLabel = if (series.inverted) rawMin else rawMax
            val bottomLabel = if (series.inverted) rawMax else rawMin
            Text(series.format(topLabel), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart))
            Text(series.format(bottomLabel), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(t0, (t0 + t1) / 2, t1).forEachIndexed { i, t ->
                Text(
                    WorkoutMath.clock(t.toLong()),
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                    textAlign = when (i) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Pace, speed, elevation, cadence or power: its averages and its curve. */
@Composable
fun CurveCard(series: WorkoutSeries, color: Color) {
    DashboardCard {
        SectionLabel(if (series.unit.isNotBlank()) "${series.title} (${series.unit})" else series.title)
        Spacer(Modifier.height(10.dp))
        StatsRow(series.stats)
        Spacer(Modifier.height(12.dp))
        WorkoutCurve(series, color)
    }
}

@Composable
fun HeartRateCard(heart: HeartSection) {
    val scheme = MaterialTheme.colorScheme
    val colors = zoneColors()
    val hrColor = colorResource(R.color.hr_zone_maximum_color)
    DashboardCard {
        SectionLabel("Heart rate (bpm)")
        Spacer(Modifier.height(10.dp))
        StatsRow(heart.stats)

        heart.curve?.let { curve ->
            Spacer(Modifier.height(12.dp))
            val bands = heart.zoneLowerBounds.mapIndexed { i, b -> b.toFloat() to colors[i] }
            WorkoutCurve(curve, hrColor, bands)
        }

        if (heart.zones.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.ult_time_in_zone), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Spacer(Modifier.height(8.dp))
            SegmentBar(heart.zones.map { it.seconds.toFloat() to colors[it.index - 1] })
            Spacer(Modifier.height(10.dp))
            val total = heart.zones.sumOf { it.seconds }.takeIf { it > 0 } ?: 1.0
            heart.zones.reversed().forEach { z ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[z.index - 1]))
                    Spacer(Modifier.width(8.dp))
                    Text("Z${z.index} · ${z.label}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(WorkoutMath.clock(z.seconds.toLong()), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Text("${Math.round(100 * z.seconds / total)}%", style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 44.dp))
                }
            }
        }

        if (heart.recovery.size >= 2) {
            Spacer(Modifier.height(16.dp))
            val first = heart.recovery.first().toInt()
            val last = heart.recovery.last().toInt()
            Text(stringResource(R.string.ult_recovery), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Text(
                if (first > last) "Dropped ${first - last} bpm after the workout ($first → $last)"
                else "$first → $last bpm after the workout",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Sparkline(heart.recovery, hrColor)
        }
    }
}

@Composable
fun EffortCard(effort: List<WorkoutMetric>) {
    if (effort.isEmpty()) return
    DashboardCard {
        SectionLabel("Effort")
        Spacer(Modifier.height(10.dp))
        MetricChips(effort)
    }
}

/** Splits, laps or swim lengths as the watch sent them. */
@Composable
fun TableCard(table: WorkoutTable) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        SectionLabel(table.title)
        Spacer(Modifier.height(8.dp))
        val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            @Composable
            fun TableRow(cells: List<String>, header: Boolean) {
                Row(Modifier.padding(vertical = 4.dp)) {
                    for (c in 0 until columns) {
                        Text(
                            cells.getOrNull(c).orEmpty(),
                            style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (header) scheme.onSurfaceVariant else scheme.onSurface,
                            modifier = Modifier.width(if (c == 0) 44.dp else 92.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (table.header.isNotEmpty()) TableRow(table.header, header = true)
            table.rows.forEach { TableRow(it, header = false) }
        }
    }
}

/** Every remaining summary value, folded away by default. */
@Composable
fun MoreDataCard(more: List<WorkoutMetric>) {
    if (more.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    DashboardCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("More data", Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 8.dp)) {
                more.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(m.label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(m.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    }
                }
            }
        }
    }
}

/**
 * The route as a small map. It does not take gestures (it sits inside a scrolling page): a tap
 * opens the full-screen map instead.
 */
@Composable
fun RouteMapCard(track: List<LatLon>, tilesDir: File, hasBaseMap: Boolean, onOpen: () -> Unit, onImportMap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Route", Modifier.weight(1f))
            if (hasBaseMap) Icon(Icons.Filled.OpenInFull, contentDescription = stringResource(R.string.ult_full_screen), tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).clickable { onOpen() })
        }
        Spacer(Modifier.height(10.dp))
        if (!hasBaseMap) {
            Text(stringResource(R.string.ult_import_basemap_route), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            TextButton(onClick = onImportMap) { Text(stringResource(R.string.ult_import_basemap)) }
            return@DashboardCard
        }
        var engine by remember { mutableStateOf<UltimateMapEngine?>(null) }
        LaunchedEffect(engine, track) {
            val e = engine ?: return@LaunchedEffect
            e.drawTrack(track); e.fitTo(track)
        }
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))) {
            UltimateMapView(modifier = Modifier.fillMaxSize(), tilesDir = tilesDir, theme = MapTheme.DARK, onReady = { engine = it })
            // Catches every touch so the page scrolls instead of the map panning.
            Box(Modifier.fillMaxSize().clickable { onOpen() })
        }
    }
}
