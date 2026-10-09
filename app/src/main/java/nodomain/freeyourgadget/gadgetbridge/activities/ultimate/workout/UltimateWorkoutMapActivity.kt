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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.mapcore.LatLon
import nodomain.freeyourgadget.gadgetbridge.mapcore.MapTheme
import nodomain.freeyourgadget.gadgetbridge.mapcore.UltimateMapEngine
import nodomain.freeyourgadget.gadgetbridge.mapcore.UltimateMapView
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Shows a single workout's recorded GPS track on the shared MapLibre+PMTiles map ([UltimateMapView]).
 * Opened from the workouts list / Last Workout card. The track points come from Gadgetbridge's own
 * activity-track provider; the map background is whatever offline region is installed in `filesDir/maps`.
 */
class UltimateWorkoutMapActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge so the map fills the screen AND the system-bar insets reach Compose; the
        // overlaid controls then pad themselves away from the status/navigation bars.
        enableEdgeToEdge()
        val summaryId = intent.getLongExtra(EXTRA_SUMMARY_ID, -1L)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        val summary: BaseActivitySummary? = runCatching {
            GBApplication.acquireDB().use { it.daoSession.baseActivitySummaryDao.load(summaryId) }
        }.getOrNull()

        val kind = summary?.activityKind?.let { k -> runCatching { ActivityKind.fromCode(k).getLabel(this) }.getOrNull() }
        val title = summary?.name?.takeIf { it.isNotBlank() } ?: kind ?: getString(R.string.ug_workout_map_title)
        val stats = summary?.let { buildStats(it, kind) } ?: ""
        val noRoute = getString(R.string.ug_workout_no_route)

        setContent {
            UltimateTheme {
                val palette = LocalUltimatePalette.current
                var engine by remember { mutableStateOf<UltimateMapEngine?>(null) }
                var points by remember { mutableStateOf<List<LatLon>?>(null) }

                LaunchedEffect(summary, device) {
                    points = if (summary != null && device != null) loadTrack(summary, device) else emptyList()
                }
                LaunchedEffect(engine, points) {
                    val e = engine; val p = points
                    if (e != null && p != null && p.isNotEmpty()) { e.drawTrack(p); e.fitTo(p) }
                }
                // Lift the native MapLibre logo/attribution above the gesture/navigation bar.
                val navBottomPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                LaunchedEffect(engine, navBottomPx) { engine?.setBottomChromeInset(navBottomPx) }

                Box(Modifier.fillMaxSize().background(palette.background)) {
                    UltimateMapView(
                        modifier = Modifier.fillMaxSize(),
                        tilesDir = File(filesDir, "maps"),
                        theme = MapTheme.DARK,
                        onReady = { engine = it },
                    )

                    Column(
                        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = palette.onSurface)
                            }
                            Text(title, style = MaterialTheme.typography.titleLarge, color = palette.onSurface, modifier = Modifier.weight(1f))
                            if (summaryId > 0) {
                                IconButton(onClick = {
                                    startActivity(
                                        nodomain.freeyourgadget.gadgetbridge.activities.ultimate.export.UltimateWorkoutExportActivity
                                            .intent(this@UltimateWorkoutMapActivity, summaryId, device),
                                    )
                                }) {
                                    Icon(Icons.Filled.Share, "Exportar", tint = palette.onSurface)
                                }
                            }
                        }
                    }

                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp)
                            .background(palette.surfaceContainer, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (points?.isEmpty() == true) noRoute else stats,
                            color = palette.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (!this@UltimateWorkoutMapActivity.hasBaseMap()) {
                        Box(
                            Modifier.align(Alignment.Center)
                                .background(palette.secondaryContainer, RoundedCornerShape(14.dp))
                                .clickable { startActivity(Intent().setClassName(this@UltimateWorkoutMapActivity, PHONE_MAPS)) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text("Importar mapa base", color = palette.onSecondaryContainer, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    private fun hasBaseMap(): Boolean =
        File(filesDir, "maps").listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") }?.isNotEmpty() == true

    private suspend fun loadTrack(summary: BaseActivitySummary, device: GBDevice): List<LatLon> =
        withContext(Dispatchers.IO) {
            runCatching {
                val provider = device.deviceCoordinator.getActivityTrackProvider(device, GBApplication.getContext())
                val track = provider.getActivityTrack(summary) ?: return@runCatching emptyList()
                track.allPoints.mapNotNull { it.location }.map { LatLon(it.latitude, it.longitude) }
            }.getOrDefault(emptyList())
        }

    private fun buildStats(s: BaseActivitySummary, kind: String?): String {
        val parts = mutableListOf<String>()
        kind?.let { parts += it }
        val start = s.startTime
        val end = s.endTime
        if (start != null && end != null) {
            val dur = ((end.time - start.time) / 1000).coerceAtLeast(0)
            parts += String.format(Locale.getDefault(), "%d:%02d:%02d", dur / 3600, (dur % 3600) / 60, dur % 60)
        }
        start?.let { parts += FMT.format(it) }
        return parts.joinToString(" · ")
    }

    companion object {
        // Phone base-map manager (lives in main; referenced by name so this builds without it here).
        private const val PHONE_MAPS = "nodomain.freeyourgadget.gadgetbridge.activities.ultimate.phonemaps.PhoneMapsActivity"
        const val EXTRA_SUMMARY_ID = "ug_summary_id"
        private val FMT = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())

        fun intent(context: Context, summaryId: Long, device: GBDevice): Intent =
            Intent(context, UltimateWorkoutMapActivity::class.java)
                .putExtra(EXTRA_SUMMARY_ID, summaryId)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}
