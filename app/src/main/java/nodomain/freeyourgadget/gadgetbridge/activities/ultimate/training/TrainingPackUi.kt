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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net.InAppNetworkImpl
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette

private const val PREFS = "ultimate_training_packs"
private const val KEY_WIFI_ONLY = "wifi_only"

/** Wi-Fi only is the default for the pack download. */
fun packWifiOnly(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WIFI_ONLY, true)

private fun setPackWifiOnly(context: Context, v: Boolean) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WIFI_ONLY, v).apply()

/** Installs the pack zip behind [uri]; blocking, call off the main thread. Returns the manifest. */
fun installPackFromUri(context: Context, uri: Uri): PackManifest =
    context.contentResolver.openInputStream(uri)?.use { TrainingPacks.install(context, it) }
        ?: throw PackException("Cannot open the file")

/** Shown on the Exercises list while no pack is installed: how to get the pictures and the full list. */
@Composable
fun PackBanner(onImport: () -> Unit, onDownload: (() -> Unit)?) {
    val palette = LocalUltimatePalette.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ult_train_pack_banner_title), style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
            Text(stringResource(R.string.ult_train_pack_banner_text), style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDownload != null) Button(onClick = onDownload) { Text(stringResource(R.string.ult_train_pack_download)) }
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.ult_train_pack_import)) }
            }
        }
    }
}

/** About / attribution of the installed pack(s), taken from their manifests. */
@Composable
fun PackAboutDialog(packs: List<TrainingPacks.Installed>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ult_train_pack_about)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (packs.isEmpty()) Text(stringResource(R.string.ult_train_pack_none))
                packs.forEach { p ->
                    val m = p.manifest
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${m.id} ${m.version}", style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.ult_train_pack_counts, m.exercises, m.withMedia), style = MaterialTheme.typography.bodySmall)
                        if (m.licenses.isNotEmpty()) Text(stringResource(R.string.ult_train_pack_licenses, m.licenses.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                        if (m.attribution.isNotBlank()) Text(m.attribution, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

/** Play flavor: downloads the pack through the in-app network contract, then installs it. */
@Composable
fun PackDownloadDialog(onDismiss: () -> Unit, onInstalled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var wifiOnly by remember { mutableStateOf(packWifiOnly(context)) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val networkError = stringResource(R.string.ult_network_error)

    fun start() {
        busy = true; error = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = InAppNetworkImpl.downloadFile(context, TrainingPackSource.URL, wifiOnly) { done, total ->
                        progress = if (total > 0) "${done * 100 / total}%" else "${done / 1024} KB"
                    }
                    try { file.inputStream().use { TrainingPacks.install(context, it) } } finally { file.delete() }
                }
            }
            busy = false
            result.onSuccess { onInstalled() }.onFailure { error = it.message?.takeIf { m -> m.isNotBlank() } ?: networkError }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ult_train_pack_download)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ult_train_pack_download_text))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it; setPackWifiOnly(context, it) }, enabled = !busy)
                    Text(stringResource(R.string.ult_download_wifi_only))
                }
                if (busy) Text(stringResource(R.string.ult_train_pack_downloading, progress))
                error?.let { Text(it, color = LocalUltimatePalette.current.error) }
            }
        },
        confirmButton = { TextButton(onClick = { start() }, enabled = !busy) { Text(stringResource(R.string.ult_train_pack_download_action)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } },
    )
}
