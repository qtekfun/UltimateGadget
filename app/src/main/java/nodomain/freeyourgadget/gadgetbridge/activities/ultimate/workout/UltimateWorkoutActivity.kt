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

import nodomain.freeyourgadget.gadgetbridge.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.export.UltimateWorkoutExportActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.io.File

/**
 * One workout, laid out for its sport ([WorkoutProfile]): a run leads with the route, pace and
 * elevation; a gym machine with heart rate, cadence and effort, and no map. Golf rounds have their
 * own scorecard screen.
 */
class UltimateWorkoutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val summaryId = intent.getLongExtra(EXTRA_SUMMARY_ID, -1L)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        val tilesDir = File(filesDir, "maps")

        setContent {
            UltimateTheme {
                var detail by remember { mutableStateOf<WorkoutDetail?>(null) }
                var loading by remember { mutableStateOf(true) }
                LaunchedEffect(summaryId) {
                    detail = withContext(Dispatchers.IO) { WorkoutDetailRepository(this@UltimateWorkoutActivity).load(summaryId, device) }
                    loading = false
                }
                WorkoutScreen(
                    detail = detail,
                    loading = loading,
                    tilesDir = tilesDir,
                    hasBaseMap = hasBaseMap(),
                    onBack = { finish() },
                    onExport = { startActivity(UltimateWorkoutExportActivity.intent(this, summaryId, device)) },
                    onOpenMap = { device?.let { startActivity(UltimateWorkoutMapActivity.intent(this, summaryId, it)) } },
                    onImportMap = { startActivity(Intent().setClassName(this, PHONE_MAPS)) },
                )
            }
        }
    }

    private fun hasBaseMap(): Boolean =
        File(filesDir, "maps").listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") }?.isNotEmpty() == true

    companion object {
        private const val PHONE_MAPS = "nodomain.freeyourgadget.gadgetbridge.activities.ultimate.phonemaps.PhoneMapsActivity"
        const val EXTRA_SUMMARY_ID = "ug_summary_id"

        fun intent(context: Context, summaryId: Long, device: GBDevice?): Intent =
            Intent(context, UltimateWorkoutActivity::class.java)
                .putExtra(EXTRA_SUMMARY_ID, summaryId)
                .apply { device?.let { putExtra(GBDevice.EXTRA_DEVICE, it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutScreen(
    detail: WorkoutDetail?,
    loading: Boolean,
    tilesDir: File,
    hasBaseMap: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onOpenMap: () -> Unit,
    onImportMap: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(detail?.typeLabel.orEmpty(), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back)) }
                },
                actions = {
                    if (detail != null) IconButton(onClick = onExport) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.ult_export)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                    actionIconContentColor = scheme.onSurface,
                ),
            )
        },
    ) { inner ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = scheme.primary)
            }
            detail == null -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ult_workout_load_error), color = scheme.onSurfaceVariant)
            }
            else -> Column(
                Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkoutHeader(detail)
                WorkoutHeroCard(detail.hero, detail.secondary)
                sectionsFor(detail.profile).forEach { section ->
                    when (section) {
                        Section.MAP -> if (detail.track.isNotEmpty()) {
                            RouteMapCard(detail.track, tilesDir, hasBaseMap, onOpenMap, onImportMap)
                        }
                        Section.HEART -> detail.heart?.let { HeartRateCard(it) }
                        Section.CURVES -> detail.curves.forEach { CurveCard(it, curveColor(it.title)) }
                        Section.TABLES -> detail.tables.forEach { TableCard(it) }
                        Section.EFFORT -> EffortCard(detail.effort)
                    }
                }
                MoreDataCard(detail.more)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private enum class Section { MAP, HEART, CURVES, TABLES, EFFORT }

/** What leads for each sport: the route for distance sports, the body for gym and generic ones. */
private fun sectionsFor(profile: WorkoutProfile): List<Section> = when (profile) {
    WorkoutProfile.OUTDOOR -> listOf(Section.MAP, Section.CURVES, Section.TABLES, Section.HEART, Section.EFFORT)
    WorkoutProfile.SWIM -> listOf(Section.MAP, Section.TABLES, Section.CURVES, Section.HEART, Section.EFFORT)
    WorkoutProfile.INDOOR -> listOf(Section.HEART, Section.CURVES, Section.TABLES, Section.EFFORT)
    WorkoutProfile.GENERIC, WorkoutProfile.GOLF ->
        listOf(Section.HEART, Section.MAP, Section.CURVES, Section.TABLES, Section.EFFORT)
}

@Composable
private fun curveColor(title: String): Color {
    val scheme = MaterialTheme.colorScheme
    return when (title) {
        "Pace", "Speed" -> scheme.primary
        "Elevation" -> scheme.tertiary
        "Cadence" -> scheme.secondary
        else -> scheme.error
    }
}

@Composable
private fun WorkoutHeader(detail: WorkoutDetail) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(detail.iconRes),
                    contentDescription = detail.typeLabel,
                    colorFilter = ColorFilter.tint(scheme.primary),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(detail.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                if (detail.whenLabel.isNotEmpty()) {
                    Text(detail.whenLabel, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
