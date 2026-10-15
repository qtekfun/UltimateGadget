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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.appicons

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.NotificationUtils
import nodomain.freeyourgadget.gadgetbridge.util.Prefs

/**
 * Native picker for which notification app icons are uploaded to the watch. Replaces the classic
 * `NotificationsAppIconUploadActivity`: same selection preference
 * ([DeviceSettingsPreferenceConst.PREF_UPLOAD_NOTIFICATIONS_APP_ICON]) and the same
 * `onSendConfiguration` event to trigger the upload.
 */
class UltimateAppIconUploadActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }
        setContent { UltimateTheme { AppIconScreen(device = device, onBack = { finish() }) } }
    }

    companion object {
        @JvmStatic
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateAppIconUploadActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}

private data class AppEntry(val pkg: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppIconScreen(device: GBDevice, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    val selected: SnapshotStateList<String> = remember {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val saved = prefs.getStringSet(DeviceSettingsPreferenceConst.PREF_UPLOAD_NOTIFICATIONS_APP_ICON, null).orEmpty()
        mutableStateListOf<String>().also { it.addAll(saved) }
    }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val app = GBApplication.getContext()
            NotificationUtils.getAllApplications(app)
                .mapNotNull { pkg ->
                    val label = NotificationUtils.getApplicationLabel(app, pkg)
                    if (label.isNullOrEmpty()) null else AppEntry(pkg, label)
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_app_icon_upload), fontWeight = FontWeight.Bold) },
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
        bottomBar = {
            Button(
                onClick = {
                    if (selected.isEmpty()) {
                        Toast.makeText(ctx, ctx.getString(R.string.notifications_app_icon_nothing_selected), Toast.LENGTH_LONG).show()
                    } else confirm = true
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(stringResource(R.string.notifications_app_icon_upload_to_device)) }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Text(
                stringResource(R.string.notifications_app_icon_info),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.ult_notif_search_app)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val shown = list.filter {
                    query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shown, key = { it.pkg }) { entry ->
                        val on = entry.pkg in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceContainer)
                                .clickable { if (on) selected.remove(entry.pkg) else selected.add(entry.pkg) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = remember(entry.pkg) {
                                runCatching { ctx.packageManager.getApplicationIcon(entry.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                            }
                            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                                Text(entry.pkg, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                            }
                            Checkbox(checked = on, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.notifications_app_icon_upload_to_device)) },
            text = { Text(stringResource(R.string.notifications_app_icon_uploading_confirm_description, selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
                    val editor = prefs.edit()
                    Prefs.putStringSet(editor, DeviceSettingsPreferenceConst.PREF_UPLOAD_NOTIFICATIONS_APP_ICON, HashSet(selected))
                    editor.apply()
                    GBApplication.deviceService(device)
                        .onSendConfiguration(DeviceSettingsPreferenceConst.PREF_UPLOAD_NOTIFICATIONS_APP_ICON)
                    Toast.makeText(ctx, ctx.getString(R.string.notifications_app_icon_uploading), Toast.LENGTH_LONG).show()
                    confirm = false
                }) { Text(stringResource(R.string.ult_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.ult_cancel)) } },
        )
    }
}
