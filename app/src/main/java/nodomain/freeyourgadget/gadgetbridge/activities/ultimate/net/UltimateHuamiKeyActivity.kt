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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net

import nodomain.freeyourgadget.gadgetbridge.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/**
 * Fetches the Bluetooth auth key of Amazfit/Zepp or Xiaomi (Mi Fitness) watches from the user's own
 * account, so they can be paired without the vendor app. Login happens on the vendor's own page in
 * a WebView ([UltimateAccountLoginActivity]), which handles passwords, OTP, captcha and two-factor;
 * this screen only receives the resulting session token and lists the devices with their keys.
 *
 * No email or password is ever entered or stored here. FLAG_SECURE keeps it out of screenshots.
 */
class UltimateHuamiKeyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { UltimateTheme { KeyScreen(onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val scope = rememberCoroutineScope()
    var region by remember { mutableStateOf(defaultRegionIndex()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var keys by remember { mutableStateOf<List<HuamiKey>?>(null) }
    var xBusy by remember { mutableStateOf(false) }
    var xError by remember { mutableStateOf<String?>(null) }
    var xKeys by remember { mutableStateOf<List<XiaomiKey>?>(null) }

    val zeppLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val userId = data.getStringExtra(UltimateAccountLoginActivity.EXTRA_USER_ID).orEmpty()
        val token = data.getStringExtra(UltimateAccountLoginActivity.EXTRA_TOKEN).orEmpty()
        if (userId.isBlank() || token.isBlank()) { error = context.getString(R.string.ult_huami_token_error); return@rememberLauncherForActivityResult }
        busy = true; error = null; keys = null
        val r = region
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { InAppNetworkImpl.fetchHuamiKeysWithToken(userId, token, r) } }
                .onSuccess { keys = it }
                .onFailure { error = it.message ?: context.getString(R.string.ult_network_error) }
            busy = false
        }
    }
    val xiaomiLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val userId = data.getStringExtra(UltimateAccountLoginActivity.EXTRA_USER_ID).orEmpty()
        val token = data.getStringExtra(UltimateAccountLoginActivity.EXTRA_TOKEN).orEmpty()
        if (userId.isBlank() || token.isBlank()) { xError = context.getString(R.string.ult_huami_token_error); return@rememberLauncherForActivityResult }
        xBusy = true; xError = null; xKeys = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { InAppNetworkImpl.fetchXiaomiKeysWithPassToken(userId, token) } }
                .onSuccess { xKeys = it }
                .onFailure { xError = it.message ?: context.getString(R.string.ult_network_error) }
            xBusy = false
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ult_huami_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
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
            Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Amazfit / Zepp", style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
            Text(
                stringResource(R.string.ult_huami_zepp_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InAppNetworkImpl.huamiRegions.forEachIndexed { i, label ->
                    FilterChip(selected = region == i, onClick = { region = i }, label = { Text(label) })
                }
            }
            Button(
                enabled = !busy,
                onClick = {
                    error = null
                    zeppLogin.launch(UltimateAccountLoginActivity.intent(context, UltimateAccountLoginActivity.PROVIDER_ZEPP))
                },
            ) { Text(if (busy) stringResource(R.string.ult_checking) else stringResource(R.string.ult_huami_signin_zepp)) }
            error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
            keys?.let { list -> KeyList(list.map { Triple(it.mac, it.authKey, "") }, palette, context) }

            // --- Xiaomi (Mi Fitness) ---
            Text("Xiaomi (Mi Fitness)", style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
            Text(
                stringResource(R.string.ult_huami_xiaomi_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )
            Button(
                enabled = !xBusy,
                onClick = {
                    xError = null
                    xiaomiLogin.launch(UltimateAccountLoginActivity.intent(context, UltimateAccountLoginActivity.PROVIDER_XIAOMI))
                },
            ) { Text(if (xBusy) stringResource(R.string.ult_checking) else stringResource(R.string.ult_huami_signin_xiaomi)) }
            xError?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
            xKeys?.let { list -> KeyList(list.map { Triple(it.mac, it.authKey, it.name) }, palette, context) }
        }
    }
}

/** One card per device: a title (name/mac), the key and a copy button. */
@Composable
private fun KeyList(
    items: List<Triple<String, String, String>>,
    palette: nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimatePalette,
    context: Context,
) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.ult_huami_no_devices), color = palette.onSurfaceVariant)
    }
    items.forEach { (mac, authKey, name) ->
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (name.isBlank()) mac else "$name · $mac",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                )
                Text(authKey, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("auth key", authKey))
                    Toast.makeText(context, context.getString(R.string.ult_key_copied), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.ult_copy_key)) }
            }
        }
    }
}

/** Default to the US shard Gadgetbridge's own pairing script uses, when the region list has it. */
private fun defaultRegionIndex(): Int {
    val idx = InAppNetworkImpl.huamiRegions.indexOfFirst { it.contains("alt", ignoreCase = true) }
    return if (idx >= 0) idx else 0
}
