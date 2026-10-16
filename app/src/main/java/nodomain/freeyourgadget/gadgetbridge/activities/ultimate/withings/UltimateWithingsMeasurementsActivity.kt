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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.withings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanWatchMeasurement
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.ScanWatchMeasurementStore
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.ScanWatchSignalCodec
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.StoredMeasurement

/** The measurements (ECG...) a Withings ScanWatch made, as saved by the sync, with the ECG strip. */
class UltimateWithingsMeasurementsActivity : ComponentActivity() {

    private var device: GBDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE)
        setContent { UltimateTheme { MeasurementsScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MeasurementsScreen() {
        val palette = LocalUltimatePalette.current
        var items by remember { mutableStateOf<List<WithingsScanWatchMeasurement>>(emptyList()) }
        var loaded by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<WithingsScanWatchMeasurement?>(null) }
        LaunchedEffect(Unit) {
            items = withContext(Dispatchers.IO) {
                runCatching {
                    val d = device ?: return@runCatching emptyList()
                    GBApplication.acquireDB().use { db -> ScanWatchMeasurementStore.load(db, d) }
                }.getOrDefault(emptyList())
            }
            loaded = true
        }
        Scaffold(
            containerColor = palette.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.ult_withings_measurements), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { if (selected != null) selected = null else finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back))
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
            Box(Modifier.padding(inner).fillMaxSize().padding(16.dp)) {
                val current = selected
                when {
                    current != null -> Detail(current)
                    loaded && items.isEmpty() -> Text(
                        stringResource(R.string.ult_withings_no_measurements),
                        color = palette.onSurfaceVariant,
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items, key = { "${it.timestamp}-${it.type}" }) { m ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(palette.surfaceContainer, RoundedCornerShape(16.dp))
                                    .clickable { selected = m }
                                    .padding(16.dp),
                            ) {
                                Text(title(m), fontWeight = FontWeight.Bold, color = palette.onSurface)
                                Text(whenText(m), style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                                Text(resultText(m), style = MaterialTheme.typography.bodyMedium, color = palette.secondary)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Detail(m: WithingsScanWatchMeasurement) {
        val palette = LocalUltimatePalette.current
        val stroke = palette.primary
        val grid = palette.onSurfaceVariant
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title(m), style = MaterialTheme.typography.titleLarge, color = palette.onSurface, fontWeight = FontWeight.Bold)
            Text(whenText(m), color = palette.onSurfaceVariant)
            Text(resultText(m), color = palette.secondary)
            if (m.heartRate > 0) {
                Text(stringResource(R.string.ult_withings_hr_fmt, m.heartRate), color = palette.onSurface)
            }
            val signal = m.signal
            if (signal != null && signal.isNotEmpty()) {
                val rate = if (m.sampleRate > 0) m.sampleRate else 300
                val samples = remember(m.timestamp) { display(ScanWatchSignalCodec.decode(signal), rate) }
                Text(
                    stringResource(R.string.ult_withings_signal_fmt, samples.size / rate, rate),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
                val seconds = (samples.size + rate - 1) / rate
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceContainer, RoundedCornerShape(16.dp))
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Canvas(Modifier.width((seconds * 120).dp).height(220.dp).padding(8.dp)) {
                        for (s in 0..seconds) {
                            val x = size.width * s / seconds
                            drawLine(grid.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        }
                        if (samples.isNotEmpty()) {
                            val lo = samples.min()
                            val hi = samples.max()
                            val span = (hi - lo).coerceAtLeast(1f)
                            val path = Path()
                            samples.forEachIndexed { i, v ->
                                val x = size.width * i / (samples.size - 1).coerceAtLeast(1)
                                val y = size.height * (1f - (v - lo) / span)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, stroke, style = Stroke(width = 3f))
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.ult_withings_not_diagnosis),
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurfaceVariant,
            )
        }
    }

    /** For display only: removes the slow drift of the baseline with a one-second moving average. */
    private fun display(raw: IntArray, rate: Int): FloatArray {
        val out = FloatArray(raw.size)
        val half = (rate / 2).coerceAtLeast(1)
        var sum = 0L
        var lo = 0
        var hi = 0 // window [lo, hi)
        for (i in raw.indices) {
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half + 1).coerceAtMost(raw.size)
            while (hi < to) { sum += raw[hi]; hi++ }
            while (lo < from) { sum -= raw[lo]; lo++ }
            out[i] = raw[i] - sum.toFloat() / (hi - lo)
        }
        return out
    }

    @Composable
    private fun title(m: WithingsScanWatchMeasurement): String =
        if (m.type == 0x0103) stringResource(R.string.ult_withings_ecg_title)
        else stringResource(R.string.ult_withings_other_measurement, "0x" + Integer.toHexString(m.type))

    @Composable
    private fun whenText(m: WithingsScanWatchMeasurement): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(m.timestamp * 1000))

    @Composable
    private fun resultText(m: WithingsScanWatchMeasurement): String = when {
        m.type != 0x0103 -> m.readings.orEmpty()
        m.result == StoredMeasurement.RESULT_NORMAL_SINUS -> stringResource(R.string.ult_withings_ecg_normal)
        m.result < 0 -> stringResource(R.string.ult_withings_ecg_no_result)
        else -> stringResource(R.string.ult_withings_ecg_unknown, "0x" + java.lang.Long.toHexString(m.result))
    }

    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateWithingsMeasurementsActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}
