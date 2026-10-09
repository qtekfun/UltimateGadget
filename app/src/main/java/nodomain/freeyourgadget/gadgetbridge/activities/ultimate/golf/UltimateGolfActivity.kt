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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
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
        val summary: BaseActivitySummary = runCatching {
            GBApplication.acquireDB().use { it.daoSession.baseActivitySummaryDao.load(summaryId) }
        }.getOrNull() ?: return null
        return runCatching { GolfRound.fromSummary(summary) }.getOrNull()
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

            // Honest scorecard note.
            if (!round.hasScorecard) {
                GolfCard {
                    Text("Scorecard por hoyo", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No disponible. Los datos por hoyo (par, golpes, putts, distancias) los " +
                            "genera la mini-app de golf del reloj junto con la nube de Huawei, en un " +
                            "formato propietario que UltimateGadget no utiliza. Por eso solo se " +
                            "muestran los datos reales del resumen del entreno.",
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

private fun formatDurationFallback(sec: Long): String =
    String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
