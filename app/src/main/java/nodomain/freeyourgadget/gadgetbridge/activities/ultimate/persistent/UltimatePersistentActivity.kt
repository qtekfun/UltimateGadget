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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.persistent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/**
 * Settings screen for the optional persistent (ongoing) steps notification. A single master switch
 * backed by [UltimatePersistentNotification]; turning it on starts the foreground service (and
 * requests POST_NOTIFICATIONS on Android 13+ if needed), turning it off stops it.
 *
 * Open it from anywhere with [intent]. To expose it from the Home overflow menu, see the snippet in
 * the task report.
 */
class UltimatePersistentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltimateTheme { PersistentRoot(onBack = { finish() }) } }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, UltimatePersistentActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersistentRoot(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current

    var enabled by remember { mutableStateOf(UltimatePersistentNotification.isEnabled(context)) }

    // On Android 13+ the ongoing notification needs POST_NOTIFICATIONS; enable after it is granted.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            UltimatePersistentNotification.setEnabled(context, true)
            enabled = true
        }
    }

    fun onToggle(wantOn: Boolean) {
        if (wantOn) {
            if (needsNotificationPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            UltimatePersistentNotification.setEnabled(context, true)
            enabled = true
        } else {
            UltimatePersistentNotification.setEnabled(context, false)
            enabled = false
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Notificación fija") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Muestra en la barra de notificaciones los pasos de hoy y el estado del reloj. " +
                    "Al ser una notificación en primer plano, también ayuda a que el sistema " +
                    "(ColorOS, OPPO, etc.) no cierre la app en segundo plano.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Mostrar pasos fijos",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Notificación persistente con los pasos del día",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.onPrimary,
                            checkedTrackColor = palette.primary,
                            uncheckedThumbColor = palette.onSurfaceVariant,
                            uncheckedTrackColor = palette.surfaceHigh,
                        ),
                    )
                }
            }

            Text(
                "La notificación se actualiza cada 15 minutos y también cuando llegan datos nuevos " +
                    "del reloj. Tócala para abrir UltimateGadget.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurfaceVariant,
            )
        }
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
