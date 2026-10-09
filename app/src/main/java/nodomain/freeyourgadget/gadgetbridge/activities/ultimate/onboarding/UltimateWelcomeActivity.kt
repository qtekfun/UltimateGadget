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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils

/**
 * Ultimate first-run welcome screen (Compose). Brand intro + privacy pitch, with a single
 * "Empezar" action that marks the "first_run" preference as consumed — exactly like the legacy
 * [nodomain.freeyourgadget.gadgetbridge.activities.welcome.WelcomeActivity] get-started step —
 * and then continues to the permissions screen (if any are missing) or back to the launcher.
 */
class UltimateWelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                WelcomeScreen(onStart = { finishFirstRun() })
            }
        }
    }

    /**
     * Same side effect as the legacy WelcomeFragmentGetStarted: persist first_run=false, then
     * hand off to the permissions screen when something is still ungranted, otherwise just finish.
     */
    private fun finishFirstRun() {
        GBApplication.getPrefs().preferences.edit()
            .putBoolean("first_run", false)
            .apply()
        if (!PermissionsUtils.checkAllPermissions(this)) {
            startActivity(UltimatePermissionsActivity.intent(this, showDoNotAskButton = false))
        }
        finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, UltimateWelcomeActivity::class.java)
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    val palette = LocalUltimatePalette.current

    Scaffold(containerColor = palette.background) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = palette.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Watch,
                        contentDescription = null,
                        tint = palette.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            Text(
                "UltimateGadget",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                "Tu reloj, tus datos. Sincroniza, visualiza y controla tu dispositivo sin nubes " +
                    "ni cuentas de terceros.",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            FeatureRow(
                icon = Icons.Filled.Lock,
                title = "Privado por diseño",
                summary = "Tus datos de salud y actividad se quedan en tu teléfono.",
            )
            FeatureRow(
                icon = Icons.Filled.CloudOff,
                title = "Sin servicios en la nube",
                summary = "Funciona en local; nada se envía a servidores de terceros.",
            )
            FeatureRow(
                icon = Icons.Filled.Watch,
                title = "Hecho para tu reloj",
                summary = "Golf, mapas y sincronización pensados para Huawei y más.",
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = palette.onPrimary,
                ),
            ) {
                Text("Empezar", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, summary: String) {
    val palette = LocalUltimatePalette.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = palette.secondary, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
            }
        }
    }
}
