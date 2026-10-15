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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.phonemaps

import nodomain.freeyourgadget.gadgetbridge.R
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net.InAppNetworkImpl
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net.RemoteMap
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/** Manages the phone-side offline base maps (PMTiles) used by the map viewer: import and delete. */
class PhoneMapsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltimateTheme { PhoneMapsScreen(onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun PhoneMapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    var maps by remember { mutableStateOf(PhoneMapStore.installed(context)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var catalog by remember { mutableStateOf<List<RemoteMap>?>(null) }
    var loadingCatalog by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            PhoneMapStore.importMap(context, uri)
            maps = PhoneMapStore.installed(context)
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ult_menu_phone_maps)) },
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
                stringResource(R.string.ult_pmaps_explain) +
                    if (InAppNetworkImpl.available) stringResource(R.string.ult_pmaps_explain_catalog) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )
            Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.ult_pmaps_import))
            }
            if (InAppNetworkImpl.available) {
                OutlinedButton(
                    enabled = !loadingCatalog,
                    onClick = {
                        loadingCatalog = true; catalogError = null
                        scope.launch(Dispatchers.IO) {
                            runCatching { InAppNetworkImpl.fetchMapCatalog() }
                                .onSuccess { catalog = it }
                                .onFailure { catalogError = it.message ?: context.getString(R.string.ult_network_error) }
                            loadingCatalog = false
                        }
                    },
                ) { Text(if (loadingCatalog) stringResource(R.string.ult_pmaps_loading) else stringResource(R.string.ult_pmaps_download)) }
                catalogError?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val remote = catalog
                if (remote != null) {
                    item(key = "catalog-header") {
                        Text(stringResource(R.string.ult_catalog), style = MaterialTheme.typography.titleSmall, color = palette.onSurfaceVariant)
                    }
                    items(remote, key = { "remote-" + it.id }) { r ->
                        val busy = downloadingId == r.id
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.name, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                                        Text(
                                            Formatter.formatShortFileSize(context, r.sizeBytes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        enabled = downloadingId == null,
                                        onClick = {
                                            downloadingId = r.id; progress = 0f; catalogError = null
                                            scope.launch(Dispatchers.IO) {
                                                runCatching {
                                                    InAppNetworkImpl.downloadMap(context, r.id) { done, total ->
                                                        progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                                                    }
                                                }.onFailure { catalogError = it.message ?: context.getString(R.string.ult_download_error) }
                                                maps = PhoneMapStore.installed(context)
                                                downloadingId = null
                                            }
                                        },
                                    ) { Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.ult_download), tint = palette.secondary) }
                                }
                                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    item(key = "installed-header") {
                        Text(stringResource(R.string.ult_pmaps_installed), style = MaterialTheme.typography.titleSmall, color = palette.onSurfaceVariant)
                    }
                }
                if (maps.isEmpty()) {
                    item(key = "none") {
                        Text(stringResource(R.string.ult_pmaps_none), style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
                    }
                }
                items(maps, key = { it.name }) { m ->
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
                            Icon(Icons.Filled.Map, contentDescription = null, tint = palette.secondary)
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                                Text(
                                    Formatter.formatShortFileSize(context, m.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                PhoneMapStore.delete(context, m.name)
                                maps = PhoneMapStore.installed(context)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ult_delete), tint = palette.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
