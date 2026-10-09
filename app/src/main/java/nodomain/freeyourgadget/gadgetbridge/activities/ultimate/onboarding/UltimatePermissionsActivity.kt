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

import android.app.Activity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils

/**
 * Ultimate permissions screen (Compose). Presentation only: all the actual permission logic —
 * the required-permissions list, granted checks, request flow and the special-permission dialogs —
 * is reused verbatim from [PermissionsUtils], mirroring the legacy
 * [nodomain.freeyourgadget.gadgetbridge.activities.welcome.WelcomeFragmentPermissions].
 *
 * The [ARG_SHOW_DO_NOT_ASK_BUTTON] extra matches the legacy
 * [nodomain.freeyourgadget.gadgetbridge.activities.PermissionsActivity] contract so the startup
 * gate can pass it through unchanged.
 */
class UltimatePermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val showDoNotAsk = intent.getBooleanExtra(ARG_SHOW_DO_NOT_ASK_BUTTON, false)
        setContent {
            UltimateTheme {
                PermissionsScreen(showDoNotAsk = showDoNotAsk, onBack = { finish() })
            }
        }
    }

    companion object {
        /** Same key as the legacy PermissionsActivity, so the gate's extra carries over. */
        const val ARG_SHOW_DO_NOT_ASK_BUTTON = "show_do_not_ask"

        fun intent(context: Context, showDoNotAskButton: Boolean = false): Intent =
            Intent(context, UltimatePermissionsActivity::class.java)
                .putExtra(ARG_SHOW_DO_NOT_ASK_BUTTON, showDoNotAskButton)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsScreen(showDoNotAsk: Boolean, onBack: () -> Unit) {
    val palette = LocalUltimatePalette.current
    val activity = LocalContext.current as Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bumped on every ON_RESUME to re-read grant state, exactly like the fragment's onResume().
    var refreshTick by remember { mutableStateOf(0) }
    var showDoNotAskDialog by remember { mutableStateOf(false) }

    // Pending sequential requests (special permissions must be requested one at a time, resuming
    // after each returns) — same queue model as WelcomeFragmentPermissions.requestAllPermissions().
    val requesting = remember { mutableStateListOf<String>() }

    fun requestAll() {
        if (requesting.isEmpty()) return
        val it = requesting.iterator()
        while (it.hasNext()) {
            val current = it.next()
            if (PermissionsUtils.specialPermissions.contains(current)) {
                it.remove()
                if (!PermissionsUtils.checkPermission(activity, current)) {
                    PermissionsUtils.requestPermission(activity, current)
                    return
                }
            }
        }
        val combined = requesting.toTypedArray()
        requesting.clear()
        ActivityCompat.requestPermissions(activity, combined, 0)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
                if (PermissionsUtils.checkAllPermissions(activity) && showDoNotAsk) {
                    // Just got everything while pestering — disappear, like the fragment did.
                    activity.finish()
                }
                if (requesting.isNotEmpty()) requestAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Recompute whenever refreshTick changes. Ungranted first, then alphabetical (as the fragment).
    val permissions = remember(refreshTick) {
        PermissionsUtils.getRequiredPermissionsList(activity).sortedWith { p1, p2 ->
            val g1 = PermissionsUtils.checkPermission(activity, p1.permission())
            val g2 = PermissionsUtils.checkPermission(activity, p2.permission())
            when {
                g1 && !g2 -> 1
                !g1 && g2 -> -1
                else -> p1.title().compareTo(p2.title(), ignoreCase = true)
            }
        }
    }
    val allGranted = remember(refreshTick) { PermissionsUtils.checkAllPermissions(activity) }

    if (showDoNotAskDialog) {
        AlertDialog(
            onDismissRequest = { showDoNotAskDialog = false },
            containerColor = palette.surfaceContainer,
            title = { Text("No volver a preguntar", color = palette.onSurface) },
            text = {
                Text(
                    "UltimateGadget puede no funcionar correctamente sin estos permisos. " +
                        "¿Seguro que no quieres que te los vuelva a pedir?",
                    color = palette.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    GBApplication.getPrefs().preferences.edit()
                        .putBoolean("permission_pestering", false)
                        .apply()
                    showDoNotAskDialog = false
                    activity.finish()
                }) { Text("Aceptar", color = palette.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDoNotAskDialog = false }) {
                    Text("Cancelar", color = palette.onSurfaceVariant)
                }
            },
        )
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Permisos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "UltimateGadget necesita estos permisos para comunicarse con tu reloj y mostrar " +
                    "notificaciones, llamadas, calendario y ubicación. Todo se procesa en local.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )

            Button(
                onClick = {
                    requesting.clear()
                    requesting.addAll(
                        PermissionsUtils.getRequiredPermissionsList(activity).map { it.permission() },
                    )
                    requestAll()
                },
                enabled = !allGranted,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = palette.onPrimary,
                ),
            ) { Text("Conceder todos") }

            if (showDoNotAsk) {
                OutlinedButton(
                    onClick = { showDoNotAskDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("No volver a preguntar", color = palette.onSurfaceVariant) }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(permissions, key = { it.permission() }) { perm ->
                    val granted = PermissionsUtils.checkPermission(activity, perm.permission())
                    PermissionCard(
                        title = perm.title(),
                        summary = perm.summary(),
                        granted = granted,
                        onGrant = { PermissionsUtils.requestPermission(activity, perm.permission()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    summary: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }
            if (granted) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Concedido",
                    tint = palette.secondary,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryContainer,
                        contentColor = palette.onPrimaryContainer,
                    ),
                ) { Text("Conceder") }
            }
        }
    }
}
