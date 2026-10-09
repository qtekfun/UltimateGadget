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

import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.SectionLabelStyle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Small uppercase section header, e.g. "STEPS TODAY". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SectionLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The base dashboard card: rounded, surfaceContainer, tappable. */
@Composable
fun DashboardCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/**
 * A smooth filled sparkline. [values] are plotted left..right, auto-scaled to their own min/max,
 * with a soft gradient fill under the line. Used for weekly steps, HR trend, workout trace.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidthDp: Float = 2.5f,
    fill: Boolean = true,
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            if (values.size < 2) return@Canvas
            val minV = values.min()
            val maxV = values.max()
            val range = (maxV - minV).takeIf { it > 0f } ?: 1f
            val stepX = size.width / (values.size - 1)
            val pad = size.height * 0.12f
            val usableH = size.height - pad * 2
            fun x(i: Int) = stepX * i
            fun y(v: Float) = pad + usableH * (1f - (v - minV) / range)

            val line = Path().apply {
                moveTo(x(0), y(values[0]))
                for (i in 1 until values.size) lineTo(x(i), y(values[i]))
            }
            if (fill) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(values.size - 1), size.height)
                    lineTo(x(0), size.height)
                    close()
                }
                drawPath(
                    area,
                    brush = Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
                        startY = 0f, endY = size.height,
                    ),
                )
            }
            drawPath(
                line, color = color,
                style = Stroke(width = strokeWidthDp * density, cap = StrokeCap.Round),
            )
        }
    }
}

/** Vertical bars sparkline (for weekly steps where bars read better than a line). */
@Composable
fun BarSparkline(
    values: List<Float>,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxV = values.max().takeIf { it > 0f } ?: 1f
        val gap = size.width * 0.03f
        val barW = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * (v / maxV)
            val left = i * (barW + gap)
            val top = size.height - h
            drawRoundRect(
                color = if (i == values.lastIndex) color else trackColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3, barW / 3),
            )
        }
    }
}

/** A thin segmented bar used for sleep phases. [segments] = list of (value, color). */
@Composable
fun SegmentBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val total = segments.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
        var x = 0f
        val r = size.height / 2
        segments.forEach { (v, c) ->
            val w = size.width * (v / total)
            drawRoundRect(
                color = c,
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size((w - 2f).coerceAtLeast(0f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            x += w
        }
    }
}

val DashboardContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
