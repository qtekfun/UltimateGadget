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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf

import nodomain.freeyourgadget.gadgetbridge.R
import androidx.compose.ui.res.stringResource
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout.EffortCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout.HeartRateCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout.WorkoutDetail
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout.WorkoutDetailRepository
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiWorkoutSectionsSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiWorkoutSummarySampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import com.qtekfun.mapcore.LatLon
import com.qtekfun.mapcore.MapTheme
import com.qtekfun.mapcore.UltimateMapEngine
import com.qtekfun.mapcore.UltimateMapView
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.io.File

/**
 * Golf round detail — a scorecard-style card for a golf workout, in the Ultimate aesthetic.
 *
 * It shows only the real data the workout summary carries (duration, walking distance, steps,
 * calories, heart-rate...). Detailed per-hole scoring (par / strokes / putts / per-hole distance)
 * is NOT available: Huawei produces it in the watch golf mini-app with its proprietary cloud, which
 * UltimateGadget does not use, so the screen states that plainly instead of inventing numbers.
 */
class UltimateGolfActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val summaryId = intent.getLongExtra(EXTRA_SUMMARY_ID, -1L)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)

        setContent {
            UltimateTheme {
                var round by remember { mutableStateOf<GolfRound?>(null) }
                var loading by remember { mutableStateOf(true) }
                // null = still loading / unknown; emptyList = no GPS track for this round.
                var track by remember { mutableStateOf<List<LatLon>?>(null) }

                androidx.compose.runtime.LaunchedEffect(summaryId) {
                    val r = withContext(Dispatchers.IO) { loadRound(summaryId) }
                    round = r; loading = false
                }
                androidx.compose.runtime.LaunchedEffect(summaryId, device) {
                    track = withContext(Dispatchers.IO) { loadTrack(summaryId, device) }
                }
                // Heart rate and effort, from the same per-sport workout detail other sports use.
                var detail by remember { mutableStateOf<WorkoutDetail?>(null) }
                androidx.compose.runtime.LaunchedEffect(summaryId, device) {
                    detail = withContext(Dispatchers.IO) {
                        WorkoutDetailRepository(this@UltimateGolfActivity).load(summaryId, device)
                    }
                }

                GolfScreen(
                    round = round, loading = loading, track = track, detail = detail,
                    hasBaseMap = hasBaseMap(), onBack = { finish() },
                )
            }
        }
    }

    private fun hasBaseMap(): Boolean =
        File(filesDir, "maps").listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") }?.isNotEmpty() == true

    /**
     * The round's recorded GPS track (the walked path on the course), from Gadgetbridge's own
     * activity-track provider — the same source the regular workout map uses. Empty when there is
     * no device, no track, or the round carried no GPS.
     */
    private fun loadTrack(summaryId: Long, device: GBDevice?): List<LatLon> {
        if (summaryId <= 0 || device == null) return emptyList()
        return runCatching {
            GBApplication.acquireDB().use { db ->
                val summary = db.daoSession.baseActivitySummaryDao.load(summaryId) ?: return emptyList()
                val provider = device.deviceCoordinator.getActivityTrackProvider(device, GBApplication.getContext())
                val trackData = provider.getActivityTrack(summary) ?: return emptyList()
                trackData.allPoints.mapNotNull { it.location }.map { LatLon(it.latitude, it.longitude) }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadRound(summaryId: Long): GolfRound? {
        if (summaryId <= 0) return null
        return runCatching {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val summary: BaseActivitySummary =
                    session.baseActivitySummaryDao.load(summaryId) ?: return@use null
                val round = GolfRound.fromSummary(summary)
                round.copy(scorecard = loadScorecard(session, summary))
            }
        }.getOrNull()
    }

    /**
     * Reads the per-hole golf rows persisted in HUAWEI_WORKOUT_SECTIONS_SAMPLE for this round.
     * BaseActivitySummary has no direct link to the Huawei workout, so it is matched the same way
     * the parser creates it: by device + start timestamp (seconds). Returns null when no per-hole
     * row carries any golf field (older rounds synced before golf parsing, or a re-fetch pending).
     */
    private fun loadScorecard(
        session: nodomain.freeyourgadget.gadgetbridge.entities.DaoSession,
        summary: BaseActivitySummary,
    ): GolfScorecard? {
        val startTs = ((summary.startTime?.time ?: return null) / 1000L).toInt()

        val summaryDao = session.huaweiWorkoutSummarySampleDao
        val workouts = summaryDao.queryBuilder()
            .where(
                HuaweiWorkoutSummarySampleDao.Properties.DeviceId.eq(summary.deviceId),
                HuaweiWorkoutSummarySampleDao.Properties.StartTimestamp.eq(startTs),
            )
            .list()
        if (workouts.isEmpty()) return null

        val sectionsDao = session.huaweiWorkoutSectionsSampleDao
        val holes = mutableListOf<GolfHole>()
        for (workout in workouts) {
            val rows = sectionsDao.queryBuilder()
                .where(HuaweiWorkoutSectionsSampleDao.Properties.WorkoutId.eq(workout.workoutId))
                .orderAsc(
                    HuaweiWorkoutSectionsSampleDao.Properties.DataIdx,
                    HuaweiWorkoutSectionsSampleDao.Properties.RowIdx,
                )
                .list()
            for (row in rows) {
                // Only keep rows that actually carry golf data; generic sections stay out.
                val hasGolf = row.golfHoleId != null || row.golfPar != null || row.golfScore != null ||
                    row.golfPutts != null || row.golfPenalty != null || row.golfFairwayHits != null ||
                    row.golfHeadSpeed != null || row.golfSwingTempo != null
                if (!hasGolf) continue
                holes += GolfHole(
                    number = holes.size + 1,
                    holeId = row.golfHoleId,
                    par = row.golfPar,
                    score = row.golfScore,
                    putts = row.golfPutts,
                    penalty = row.golfPenalty,
                    fairwayHits = row.golfFairwayHits,
                    headSpeed = row.golfHeadSpeed,
                    swingTempo = row.golfSwingTempo,
                )
            }
        }
        return GolfScorecard.fromHoles(holes)
    }

    companion object {
        const val EXTRA_SUMMARY_ID = "ug_summary_id"

        fun intent(context: Context, summaryId: Long, device: GBDevice? = null): Intent =
            Intent(context, UltimateGolfActivity::class.java)
                .putExtra(EXTRA_SUMMARY_ID, summaryId)
                .apply { device?.let { putExtra(GBDevice.EXTRA_DEVICE, it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GolfScreen(
    round: GolfRound?,
    loading: Boolean,
    track: List<LatLon>?,
    detail: WorkoutDetail?,
    hasBaseMap: Boolean,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Resolve the localized "Golf" label + icon the same way the list does.
    val ctx = GBApplication.getContext()
    val golfLabel = remember {
        (ctx?.let { runCatching { ActivityKind.GOLF.getLabel(it) }.getOrNull() }).orEmpty()
            .ifEmpty { "Golf" }
    }
    val golfIcon = remember { ActivityKind.GOLF.getIcon() }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(golfLabel, fontWeight = FontWeight.Bold) },
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
        if (loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = scheme.primary)
            }
            return@Scaffold
        }
        if (round == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ult_golf_load_error), color = scheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: golf icon + round title + when.
            GolfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(golfIcon),
                            contentDescription = golfLabel,
                            colorFilter = ColorFilter.tint(scheme.primary),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(round.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        if (round.whenLabel.isNotEmpty()) {
                            Text(round.whenLabel, style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Hero duration.
            val durationLabel = stringResource(R.string.ult_wd_duration)
            GolfCard {
                Text(stringResource(R.string.ult_golf_round_time), style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    round.stats.firstOrNull { it.label == durationLabel }?.value
                        ?: formatDurationFallback(round.durationSeconds),
                    style = MetricValueStyle, color = scheme.onSurface,
                )
            }

            // Real stats grid (everything but the duration, already shown as hero).
            val gridStats = round.stats.filterNot { it.label == durationLabel }
            if (gridStats.isNotEmpty()) {
                GolfCard {
                    Text(stringResource(R.string.ult_golf_round_data), style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridStats.forEach { stat ->
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surfaceContainerHighest)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(stat.value, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                                Text(stat.label, style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Per-hole scorecard, when the round actually has per-hole rows persisted.
            val scorecard = round.scorecard
            if (scorecard != null) {
                GolfScorecardCard(scorecard)
                GolfSwingCard(scorecard)
            } else {
                // Honest note: no per-hole data stored for this round.
                GolfCard {
                    Text(stringResource(R.string.ult_golf_scorecard_hole), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.ult_golf_no_hole_data),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                    round.rawHuaweiType?.let { t ->
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ult_golf_huawei_type, t.toString()), style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant)
                    }
                }
            }

            detail?.heart?.let { HeartRateCard(it) }
            detail?.let { EffortCard(it.effort) }

            // Route walked on the course, below the card — the map view the user liked.
            GolfRouteCard(track = track, hasBaseMap = hasBaseMap)

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The round's GPS track drawn on the shared offline course map ([UltimateMapView]). Shows a short
 * note instead when there is no base map installed or the round carried no GPS track, so the card
 * never renders an empty/confusing map.
 */
@Composable
private fun GolfRouteCard(track: List<LatLon>?, hasBaseMap: Boolean) {
    val scheme = MaterialTheme.colorScheme
    GolfCard {
        Text(stringResource(R.string.ult_golf_route_title), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(10.dp))

        when {
            !hasBaseMap -> Text(
                stringResource(R.string.ult_golf_import_basemap),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
            )
            track == null -> Box(
                Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = scheme.primary) }
            track.isEmpty() -> Text(
                stringResource(R.string.ult_golf_no_gps),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
            )
            else -> {
                var engine by remember { mutableStateOf<UltimateMapEngine?>(null) }
                androidx.compose.runtime.LaunchedEffect(engine, track) {
                    val e = engine ?: return@LaunchedEffect
                    e.drawTrack(track); e.fitTo(track)
                }
                Box(
                    Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(14.dp)),
                ) {
                    UltimateMapView(
                        modifier = Modifier.fillMaxSize(),
                        tilesDir = File(GBApplication.getContext()!!.filesDir, "maps"),
                        theme = MapTheme.DARK,
                        onReady = { engine = it },
                    )
                }
            }
        }
    }
}

/** Simple surface card matching the Ultimate dashboard look. */
@Composable
private fun GolfCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainer)
            .padding(16.dp),
        content = content,
    )
}

/** Per-hole scorecard table: Hoyo | Par | Golpes | +/- | Putts, with a totals row. */
@Composable
private fun GolfScorecardCard(card: GolfScorecard) {
    val scheme = MaterialTheme.colorScheme
    GolfCard {
        Text(stringResource(R.string.ult_golf_scorecard_n, card.holeCount), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(10.dp))

        // Header.
        ScoreRow(
            cells = listOf(stringResource(R.string.ult_golf_hole), stringResource(R.string.ult_golf_par), stringResource(R.string.ult_golf_strokes), "+/-", stringResource(R.string.ult_golf_putts)),
            color = scheme.onSurfaceVariant,
            weight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = scheme.outlineVariant)

        val animate = rememberAnimationsEnabled()
        val light = LocalUltimatePalette.current.isLight
        card.holes.forEachIndexed { index, hole ->
            HoleRow(hole, index, animate, light)
        }

        HorizontalDivider(color = scheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        // Total strokes count up from 0 when the card appears.
        val totalScore = card.totalScore
        val shownTotal = if (totalScore != null) {
            val counter = remember(totalScore, animate) { Animatable(if (animate) 0f else totalScore.toFloat()) }
            LaunchedEffect(totalScore, animate) {
                if (animate) {
                    delay(GolfScoreStyle.staggerDelayMs(card.holes.size).toLong())
                    counter.animateTo(totalScore.toFloat(), tween(700))
                }
            }
            counter.value.roundToInt().toString()
        } else "—"
        ScoreRow(
            cells = listOf(
                "Total",
                card.totalPar.orDash(),
                shownTotal,
                card.totalToPar.toParDash(),
                card.totalPutts.orDash(),
            ),
            color = scheme.primary,
            weight = FontWeight.Bold,
        )

        // Secondary totals as chips.
        val extras = buildList {
            card.fairwayTracked?.let { tracked ->
                add(stringResource(R.string.ult_golf_fairways) to "${card.fairwayHit ?: 0}/$tracked")
            }
            card.totalPenalty?.let { add(stringResource(R.string.ult_golf_penalties) to it.toString()) }
        }
        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                extras.forEach { (label, value) -> GolfChip(value, label) }
            }
        }
    }
}

/** Swing stats, shown only when the watch sent them. */
@Composable
private fun GolfSwingCard(card: GolfScorecard) {
    if (card.avgHeadSpeed == null && card.avgSwingTempo == null) return
    val scheme = MaterialTheme.colorScheme
    GolfCard {
        Text(stringResource(R.string.ult_golf_swing), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            card.avgHeadSpeed?.let {
                GolfChip(String.format(java.util.Locale.getDefault(), "%.0f", it), stringResource(R.string.ult_golf_avg_head_speed))
            }
            card.avgSwingTempo?.let {
                GolfChip(String.format(java.util.Locale.getDefault(), "%.1f", it), stringResource(R.string.ult_golf_avg_tempo))
            }
        }
    }
}

@Composable
private fun GolfChip(value: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ScoreRow(
    cells: List<String>,
    color: androidx.compose.ui.graphics.Color,
    weight: FontWeight = FontWeight.Normal,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                cell,
                modifier = Modifier.weight(if (index == 0) 1.2f else 1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
                color = color,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.Center,
            )
        }
    }
}

/** False when the system "remove animations" / animator scale 0 is set: then nothing animates. */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/**
 * One hole row: same columns as before, with the strokes in a box whose border colour follows the
 * difference against par (see [GolfScoreStyle]), par in green, "-" when not played; the row fades
 * and scales in staggered by hole.
 */
@Composable
private fun HoleRow(hole: GolfHole, index: Int, animate: Boolean, light: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            delay(GolfScoreStyle.staggerDelayMs(index).toLong())
            progress.animateTo(1f, tween(280))
        } else progress.snapTo(1f)
    }
    val category = GolfScoreStyle.category(hole.score, hole.par)
    val border = GolfScoreStyle.borderColor(category, light)
    val played = category != ScoreCategory.UNPLAYED
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .graphicsLayer {
                val p = progress.value
                alpha = p
                scaleX = 0.9f + 0.1f * p
                scaleY = 0.9f + 0.1f * p
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val body = MaterialTheme.typography.bodyMedium
        Text(hole.number.toString(), Modifier.weight(1.2f), style = body, color = scheme.onSurface)
        Text(
            hole.par.orDash(), Modifier.weight(1f), style = body, textAlign = TextAlign.Center,
            color = if (hole.par != null && hole.par > 0) GolfScoreStyle.parColor(light) else scheme.onSurface,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(6.dp)
            Text(
                if (played) hole.score.orDash() else "-",
                modifier = Modifier
                    .then(
                        if (border != null) Modifier.border(1.5.dp, border, shape)
                        else if (played) Modifier.border(1.dp, scheme.outlineVariant, shape)
                        else Modifier
                    )
                    .padding(horizontal = 9.dp, vertical = 2.dp),
                style = body,
                fontWeight = if (border != null) FontWeight.SemiBold else FontWeight.Normal,
                color = scheme.onSurface,
            )
        }
        Text(hole.toPar.toParDash(), Modifier.weight(1f), style = body,
            textAlign = TextAlign.Center, color = scheme.onSurface)
        Text(hole.putts.orDash(), Modifier.weight(1f), style = body,
            textAlign = TextAlign.Center, color = scheme.onSurface)
    }
}

private fun Int?.orDash(): String = this?.toString() ?: "—"

/** Formats a score-relative-to-par value: 0 -> "E" (even), positive with a leading "+". */
private fun Int?.toParDash(): String = when {
    this == null -> "—"
    this == 0 -> "E"
    this > 0 -> "+$this"
    else -> this.toString()
}

private fun formatDurationFallback(sec: Long): String =
    String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
