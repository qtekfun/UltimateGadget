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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/** Explains the readiness score: each factor, its weight and the method behind it. */
class ReadinessDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                val data = rememberReadiness()
                ReadinessDetailScreen(
                    data = data,
                    onBack = { finish() },
                    onEditManual = { startActivity(Intent(this, ManualEntryActivity::class.java)) },
                )
            }
        }
    }

    @Composable
    private fun rememberReadiness(): ReadinessData {
        val device = (application as GBApplication).deviceManager.devices
            .firstOrNull { it.isInitialized } ?: (application as GBApplication).deviceManager.devices.firstOrNull()
        return ReadinessRepository(application).load(device as GBDevice?)
    }

    companion object {
        fun intent(context: Context) = Intent(context, ReadinessDetailActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadinessDetailScreen(data: ReadinessData, onBack: () -> Unit, onEditManual: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Readiness", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background, titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val score = data.score
            Row(verticalAlignment = Alignment.Bottom) {
                Text(score?.toString() ?: "—", style = MetricValueStyle, color = scoreColor(score, scheme))
                Spacer(Modifier.height(0.dp))
                Text(
                    "  " + (score?.let { ReadinessEngine.label(it) } ?: "Sin datos suficientes"),
                    style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text("Confianza ${data.confidence}%", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

            data.factors.forEach { f ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(factorName(f.id), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                            Text("${f.score}", style = MaterialTheme.typography.titleMedium, color = scoreColor(f.score, scheme))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(f.detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        Text("Peso ${(f.weight * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEditManual),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Registrar sueño/entreno a mano", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    Text(
                        "Para días sin reloj: introduce horas de sueño y el entreno del día.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Modelo transparente (no médico): HRV y FC en reposo frente a su línea base de 7 días, " +
                    "sueño frente a un objetivo, y carga de entreno con la ratio agudo:crónico (ACWR, Gabbett). " +
                    "Se combinan con pesos documentados. Ver docs/readiness.md.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
    }
}

private fun factorName(id: ReadinessFactorId) = when (id) {
    ReadinessFactorId.HRV -> "Variabilidad cardiaca (HRV)"
    ReadinessFactorId.RHR -> "Frecuencia cardiaca en reposo"
    ReadinessFactorId.SLEEP -> "Sueño"
    ReadinessFactorId.LOAD -> "Carga de entreno"
}

private fun scoreColor(score: Int?, scheme: androidx.compose.material3.ColorScheme) = when {
    score == null -> scheme.onSurfaceVariant
    score >= 75 -> scheme.secondary
    score >= 35 -> scheme.tertiary
    else -> scheme.error
}
