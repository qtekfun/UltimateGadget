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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.battery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.BatteryMetric
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Native (Compose) battery history for a device: the battery-level % over a selectable time range,
 * drawn on a Canvas from Gadgetbridge's own battery-level samples ([BatteryMetric.LEVEL]). Replaces
 * the classic [nodomain.freeyourgadget.gadgetbridge.activities.BatteryInfoActivity] chart in the new
 * UI. 100% offline; reads only the local DB.
 */
class UltimateBatteryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }
        setContent {
            UltimateTheme {
                var rangeDays by remember { mutableStateOf(7) }
                var samples by remember { mutableStateOf<List<BatteryMetric.Sample>?>(null) }

                androidx.compose.runtime.LaunchedEffect(rangeDays) {
                    samples = withContext(Dispatchers.IO) { loadLevels(device, rangeDays) }
                }

                BatteryScreen(
                    title = (device.aliasOrName ?: "").ifBlank { "Battery" },
                    rangeDays = rangeDays,
                    onRange = { rangeDays = it },
                    samples = samples,
                    onBack = { finish() },
                )
            }
        }
    }

    private fun loadLevels(device: GBDevice, days: Int): List<BatteryMetric.Sample> {
        val now = System.currentTimeMillis()
        val from = now - days * 24L * 3600L * 1000L
        return runCatching {
            GBApplication.acquireDB().use { db ->
                BatteryMetric.LEVEL.loadSamples(db, device, 0, from, now)
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateBatteryActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryScreen(
    title: String,
    rangeDays: Int,
    onRange: (Int) -> Unit,
    samples: List<BatteryMetric.Sample>?,
    onBack: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "24 h", 7 to "7 d", 30 to "30 d").forEach { (d, label) ->
                    FilterChip(selected = rangeDays == d, onClick = { onRange(d) }, label = { Text(label) })
                }
            }

            val list = samples
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.primary)
                }
                list.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("No battery data in this period", color = palette.onSurfaceVariant) }
                else -> {
                    val last = list.last().value.toInt()
                    val min = list.minOf { it.value }.toInt()
                    val max = list.maxOf { it.value }.toInt()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Actual", "$last%", Modifier.weight(1f))
                        StatCard("Minimum", "$min%", Modifier.weight(1f))
                        StatCard("Maximum", "$max%", Modifier.weight(1f))
                    }
                    BatteryChartCard(list)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalUltimatePalette.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(palette.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
    }
}

@Composable
private fun BatteryChartCard(samples: List<BatteryMetric.Sample>) {
    val palette = LocalUltimatePalette.current
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Battery level (%)", style = MaterialTheme.typography.labelLarge, color = palette.onSurfaceVariant)
        val line = palette.primary
        val grid = palette.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val w = size.width
            val h = size.height
            // Horizontal gridlines at 0/25/50/75/100 %.
            for (p in 0..4) {
                val y = h - h * (p / 4f)
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            if (samples.size < 2) return@Canvas
            val t0 = samples.first().timestampSeconds.toFloat()
            val t1 = samples.last().timestampSeconds.toFloat()
            val span = (t1 - t0).coerceAtLeast(1f)
            val path = Path()
            samples.forEachIndexed { i, s ->
                val x = w * ((s.timestampSeconds - t0) / span)
                val y = h - h * (s.value.coerceIn(0f, 100f) / 100f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = line, style = Stroke(width = 4f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt.format(java.util.Date(samples.first().timestampSeconds * 1000L)),
                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            Text(fmt.format(java.util.Date(samples.last().timestampSeconds * 1000L)),
                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        }
    }
}
