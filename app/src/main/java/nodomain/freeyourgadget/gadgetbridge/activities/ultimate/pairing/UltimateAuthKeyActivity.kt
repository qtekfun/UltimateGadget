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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.pairing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AuthKeyActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper

/**
 * Native authentication-key entry shown while pairing devices that need one (replaces the classic
 * `AuthKeyActivity`). It keeps the same extras and result contract, so the pairing flow is
 * unchanged: the key (and an optional second credential) is validated by the coordinator, stored
 * in the device preferences and returned to the caller.
 */
class UltimateAuthKeyActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val candidate = intent.getParcelableExtra<nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate>(
            AuthKeyActivity.EXTRA_DEVICE_CANDIDATE,
        )
        val coordinator = candidate?.let { runCatching { DeviceHelper.getInstance().resolveDeviceType(it).deviceCoordinator }.getOrNull() }
        if (candidate == null || coordinator == null) {
            Toast.makeText(this, getString(R.string.ult_device_info_missing), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val secondaryPref: String? = coordinator.secondaryAuthKeyPref
        val secondaryHint: Int = coordinator.secondaryAuthKeyHint
        val helpUrl: String? = coordinator.authHelp
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(candidate.macAddress)
        val initialKey = prefs.getString(DeviceSettingsPreferenceConst.PREF_AUTH_KEY, "").orEmpty()
        val initialSecondary = secondaryPref?.let { prefs.getString(it, "").orEmpty() }.orEmpty()
        val title = getString(
            R.string.auth_key_activity_title_for,
            candidate.name ?: getString(R.string.devicetype_unknown),
        )

        setContent {
            UltimateTheme {
                val scheme = MaterialTheme.colorScheme
                var key by remember { mutableStateOf(initialKey) }
                var secondary by remember { mutableStateOf(initialSecondary) }
                var keyError by remember { mutableStateOf<String?>(null) }
                var secondaryError by remember { mutableStateOf<String?>(null) }

                Scaffold(
                    containerColor = scheme.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(title, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back))
                                }
                            },
                            actions = {
                                if (helpUrl != null) {
                                    IconButton(onClick = {
                                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl))) }
                                            .onFailure {
                                                Toast.makeText(this@UltimateAuthKeyActivity, getString(R.string.cannot_open_help_url), Toast.LENGTH_SHORT).show()
                                            }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.ult_help))
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = scheme.background,
                                titleContentColor = scheme.onSurface,
                                navigationIconContentColor = scheme.onSurface,
                                actionIconContentColor = scheme.onSurface,
                            ),
                        )
                    },
                ) { inner ->
                    Column(
                        Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.auth_key_explanation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it; keyError = null },
                            singleLine = true,
                            isError = keyError != null,
                            supportingText = keyError?.let { { Text(it) } },
                            label = { Text(stringResource(R.string.pref_title_authkey)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (secondaryPref != null) {
                            OutlinedTextField(
                                value = secondary,
                                onValueChange = { secondary = it; secondaryError = null },
                                singleLine = true,
                                isError = secondaryError != null,
                                supportingText = secondaryError?.let { { Text(it) } },
                                label = { if (secondaryHint != 0) Text(stringResource(secondaryHint)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Button(
                            onClick = {
                                val k = key.trim()
                                val s = secondary.trim()
                                if (k.isEmpty()) {
                                    keyError = getString(R.string.auth_key_required_message)
                                    return@Button
                                }
                                if (secondaryPref != null && s.isEmpty()) {
                                    secondaryError = getString(R.string.auth_key_required_message)
                                    return@Button
                                }
                                if (!coordinator.validateAuthKey(k)) {
                                    keyError = getString(R.string.invalid_auth_key_message)
                                    return@Button
                                }
                                val editor = prefs.edit().putString(DeviceSettingsPreferenceConst.PREF_AUTH_KEY, k)
                                if (secondaryPref != null) editor.putString(secondaryPref, s)
                                editor.apply()
                                setResult(
                                    RESULT_OK,
                                    Intent()
                                        .putExtra(AuthKeyActivity.EXTRA_AUTH_KEY_RESULT, k)
                                        .putExtra(AuthKeyActivity.EXTRA_DEVICE_CANDIDATE_RESULT, candidate),
                                )
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.submit_button_text)) }
                    }
                }
            }
        }
    }

    companion object {
        fun newIntent(
            context: Context,
            candidate: nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate,
        ): Intent = Intent(context, UltimateAuthKeyActivity::class.java)
            .putExtra(AuthKeyActivity.EXTRA_DEVICE_CANDIDATE, candidate)
    }
}
