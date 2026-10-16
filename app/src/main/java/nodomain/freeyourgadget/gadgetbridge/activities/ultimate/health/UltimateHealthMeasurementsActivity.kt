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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health

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
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/**
 * The ECGs and SpO2 readings of every watch that makes them, in one list (or of one watch when it is
 * opened from its page). Brand specifics live in the [HealthMeasurementSource]s, not here.
 */
class UltimateHealthMeasurementsActivity : ComponentActivity() {

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
        var items by remember { mutableStateOf<List<HealthMeasurement>>(emptyList()) }
        var loaded by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<HealthMeasurement?>(null) }
        LaunchedEffect(Unit) {
            items = withContext(Dispatchers.IO) {
                val d = device
                if (d != null) HealthMeasurements.load(d) else HealthMeasurements.loadAll(this@UltimateHealthMeasurementsActivity)
            }
            loaded = true
        }
        Scaffold(
            containerColor = palette.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.ult_health_measurements), fontWeight = FontWeight.Bold) },
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
                    loaded && items.isEmpty() -> Text(stringResource(R.string.ult_health_no_measurements), color = palette.onSurfaceVariant)
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items, key = { "${it.deviceAddress}-${it.timestampSec}-${it::class.simpleName}" }) { m ->
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
    private fun Detail(m: HealthMeasurement) {
        val palette = LocalUltimatePalette.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title(m), style = MaterialTheme.typography.titleLarge, color = palette.onSurface, fontWeight = FontWeight.Bold)
            Text(whenText(m), color = palette.onSurfaceVariant)
            Text(sourceText(m), style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            Text(resultText(m), color = palette.secondary)
            if (m is EcgRecord) {
                m.averageHeartRate?.let { Text(stringResource(R.string.ult_health_hr_fmt, it), color = palette.onSurface) }
                if (m.samples.isNotEmpty() && m.sampleRate > 0) Strip(m)
            }
            Text(
                stringResource(R.string.ult_health_not_diagnosis),
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun Strip(m: EcgRecord) {
        val palette = LocalUltimatePalette.current
        val stroke = palette.primary
        val grid = palette.onSurfaceVariant
        val rate = m.sampleRate
        val samples = remember(m.timestampSec, m.deviceAddress) { baselineRemoved(m.samples, rate) }
        Text(
            stringResource(R.string.ult_health_signal_fmt, samples.size / rate, rate),
            style = MaterialTheme.typography.bodySmall,
            color = palette.onSurfaceVariant,
        )
        val seconds = ((samples.size + rate - 1) / rate).coerceAtLeast(1)
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

    @Composable
    private fun title(m: HealthMeasurement): String = when (m) {
        is EcgRecord -> stringResource(R.string.ult_health_ecg_title)
        is Spo2Record -> stringResource(R.string.ult_health_spo2_title)
    }

    @Composable
    private fun whenText(m: HealthMeasurement): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(m.timestampSec * 1000))

    @Composable
    private fun sourceText(m: HealthMeasurement): String = stringResource(
        R.string.ult_health_source_fmt,
        when (m) { is EcgRecord -> m.source; is Spo2Record -> m.source },
        m.deviceName,
    )

    @Composable
    private fun resultText(m: HealthMeasurement): String = when (m) {
        is EcgRecord -> ecgLabel(m)
        is Spo2Record -> spo2Label(m)
    }

    companion object {
        /** The app-wide wording for what a watch concluded; also used on the home tile. */
        @Composable
        fun ecgLabel(m: EcgRecord): String = when (m.classification) {
            EcgClassification.NORMAL -> stringResource(R.string.ult_health_ecg_normal)
            EcgClassification.ATRIAL_FIBRILLATION -> stringResource(R.string.ult_health_ecg_afib)
            EcgClassification.OTHER -> stringResource(R.string.ult_health_ecg_other)
            EcgClassification.INCONCLUSIVE -> stringResource(R.string.ult_health_ecg_inconclusive)
            EcgClassification.UNKNOWN ->
                if (m.rawCode == null) stringResource(R.string.ult_health_no_result)
                else stringResource(R.string.ult_health_ecg_unknown, "0x" + java.lang.Long.toHexString(m.rawCode))
        }

        @Composable
        fun spo2Label(m: Spo2Record): String {
            val tenths = m.tenthsOfPercent
            return when {
                !m.conclusive || tenths == null -> stringResource(R.string.ult_health_spo2_inconclusive)
                else -> stringResource(R.string.ult_health_spo2_fmt, tenths / 10, tenths % 10)
            }
        }

        /** For display only: removes the slow drift of the baseline with a one-second moving average. */
        fun baselineRemoved(raw: FloatArray, rate: Int): FloatArray {
            val out = FloatArray(raw.size)
            val half = (rate / 2).coerceAtLeast(1)
            var sum = 0.0
            var lo = 0
            var hi = 0 // window [lo, hi)
            for (i in raw.indices) {
                val from = (i - half).coerceAtLeast(0)
                val to = (i + half + 1).coerceAtMost(raw.size)
                while (hi < to) { sum += raw[hi]; hi++ }
                while (lo < from) { sum -= raw[lo]; lo++ }
                out[i] = raw[i] - (sum / (hi - lo)).toFloat()
            }
            return out
        }

        /** [device] limits the list to one watch; without it every watch with measurements is listed. */
        fun intent(context: Context, device: GBDevice? = null): Intent =
            Intent(context, UltimateHealthMeasurementsActivity::class.java).apply {
                if (device != null) putExtra(GBDevice.EXTRA_DEVICE, device)
            }
    }
}
