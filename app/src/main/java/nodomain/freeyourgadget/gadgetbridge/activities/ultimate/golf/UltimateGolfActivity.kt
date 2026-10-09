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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind

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

        setContent {
            UltimateTheme {
                var round by remember { mutableStateOf<GolfRound?>(null) }
                var loading by remember { mutableStateOf(true) }

                androidx.compose.runtime.LaunchedEffect(summaryId) {
                    val r = withContext(Dispatchers.IO) { loadRound(summaryId) }
                    round = r; loading = false
                }

                GolfScreen(round = round, loading = loading, onBack = { finish() })
            }
        }
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

        fun intent(context: Context, summaryId: Long): Intent =
            Intent(context, UltimateGolfActivity::class.java).putExtra(EXTRA_SUMMARY_ID, summaryId)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GolfScreen(round: GolfRound?, loading: Boolean, onBack: () -> Unit) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
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
                Text("No se pudo cargar la ronda", color = scheme.onSurfaceVariant)
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
            GolfCard {
                Text("Tiempo de la ronda", style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    round.stats.firstOrNull { it.label == "Duración" }?.value
                        ?: formatDurationFallback(round.durationSeconds),
                    style = MetricValueStyle, color = scheme.onSurface,
                )
            }

            // Real stats grid (everything but the duration, already shown as hero).
            val gridStats = round.stats.filterNot { it.label == "Duración" }
            if (gridStats.isNotEmpty()) {
                GolfCard {
                    Text("Datos de la ronda", style = MaterialTheme.typography.labelLarge,
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
                    Text("Scorecard por hoyo", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No hay datos por hoyo guardados para esta ronda. El reloj los envía en los " +
                            "bloques de sección del entreno, pero esta ronda se sincronizó antes de que " +
                            "se guardaran. Vuelve a descargarla (baja el puntero de última " +
                            "sincronización y re-sincroniza) para rellenar el scorecard; las rondas " +
                            "nuevas se guardan automáticamente.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                    round.rawHuaweiType?.let { t ->
                        Spacer(Modifier.height(8.dp))
                        Text("Tipo Huawei del entreno: $t", style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
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
        Text("Scorecard por hoyo", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(10.dp))

        // Header.
        ScoreRow(
            cells = listOf("Hoyo", "Par", "Golpes", "+/-", "Putts"),
            color = scheme.onSurfaceVariant,
            weight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = scheme.outlineVariant)

        card.holes.forEach { hole ->
            ScoreRow(
                cells = listOf(
                    hole.number.toString(),
                    hole.par.orDash(),
                    hole.score.orDash(),
                    hole.toPar.toParDash(),
                    hole.putts.orDash(),
                ),
                color = scheme.onSurface,
            )
        }

        HorizontalDivider(color = scheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        ScoreRow(
            cells = listOf(
                "Total",
                card.totalPar.orDash(),
                card.totalScore.orDash(),
                card.totalToPar.toParDash(),
                card.totalPutts.orDash(),
            ),
            color = scheme.primary,
            weight = FontWeight.Bold,
        )

        // Secondary totals as chips.
        val extras = buildList {
            card.fairwayTracked?.let { tracked ->
                add("Fairways" to "${card.fairwayHit ?: 0}/$tracked")
            }
            card.totalPenalty?.let { add("Penalizaciones" to it.toString()) }
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
        Text("Swing", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            card.avgHeadSpeed?.let {
                GolfChip(String.format(java.util.Locale.getDefault(), "%.0f", it), "Vel. cabeza media")
            }
            card.avgSwingTempo?.let {
                GolfChip(String.format(java.util.Locale.getDefault(), "%.1f", it), "Tempo medio")
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
