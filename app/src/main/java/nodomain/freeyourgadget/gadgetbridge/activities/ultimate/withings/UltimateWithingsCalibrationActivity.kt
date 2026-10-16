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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.withings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsSteelHRDeviceSupport

/**
 * Hands alignment for the Withings analog watches (Steel HR, ScanWatch): put each hand on the 12
 * with the arrow buttons. The commands go through the same broadcasts the device support already
 * listens for ([WithingsSteelHRDeviceSupport.HANDS_CALIBRATION_CMD]).
 */
class UltimateWithingsCalibrationActivity : ComponentActivity() {

    private enum class Hand(val code: Short, val label: Int, val hint: Int) {
        HOURS(1, R.string.ult_withings_hand_hours, R.string.withings_calibration_text_hours),
        MINUTES(0, R.string.ult_withings_hand_minutes, R.string.withings_calibration_text_minutes),
        ACTIVITY(2, R.string.ult_withings_hand_activity, R.string.withings_calibration_text_activity_target),
    }

    private lateinit var lbm: LocalBroadcastManager
    private var started = false
    private var connected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = GBApplication.app().deviceManager.devices.firstOrNull {
            it.type == DeviceType.WITHINGS_STEEL_HR || it.type == DeviceType.WITHINGS_SCANWATCH
        }
        connected = device?.isInitialized == true
        lbm = LocalBroadcastManager.getInstance(this)
        if (connected) {
            lbm.sendBroadcast(Intent(WithingsSteelHRDeviceSupport.START_HANDS_CALIBRATION_CMD))
            started = true
        } else {
            Toast.makeText(this, R.string.watch_not_connected, Toast.LENGTH_LONG).show()
        }
        setContent { UltimateTheme { CalibrationScreen() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (started) lbm.sendBroadcast(Intent(WithingsSteelHRDeviceSupport.STOP_HANDS_CALIBRATION_CMD))
    }

    private fun move(hand: Hand, amount: Int) {
        lbm.sendBroadcast(
            Intent(WithingsSteelHRDeviceSupport.HANDS_CALIBRATION_CMD)
                .putExtra("hand", hand.code)
                .putExtra("movementAmount", amount.toShort()),
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CalibrationScreen() {
        val palette = LocalUltimatePalette.current
        var selected by remember { mutableIntStateOf(0) }
        val hand = Hand.entries[selected]
        Scaffold(
            containerColor = palette.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.ult_withings_calibration_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.background,
                        titleContentColor = palette.onSurface,
                        navigationIconContentColor = palette.onSurface,
                    ),
                )
            },
        ) { inner ->
            Column(
                Modifier.padding(inner).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hand.entries.forEachIndexed { i, h ->
                        FilterChip(
                            selected = i == selected,
                            onClick = { selected = i },
                            label = { Text(stringResource(h.label)) },
                        )
                    }
                }
                if (!connected) {
                    Text(stringResource(R.string.watch_not_connected), color = palette.error)
                }
                Text(
                    stringResource(hand.hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceContainer, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.ult_withings_move_fine), color = palette.secondary, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(enabled = connected, onClick = { move(hand, -1) }) { Text(stringResource(R.string.ult_withings_back_1)) }
                        FilledTonalButton(enabled = connected, onClick = { move(hand, 1) }) { Text(stringResource(R.string.ult_withings_forward_1)) }
                    }
                    Text(stringResource(R.string.ult_withings_move_coarse), color = palette.secondary, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(enabled = connected, onClick = { move(hand, -10) }) { Text(stringResource(R.string.ult_withings_back_10)) }
                        FilledTonalButton(enabled = connected, onClick = { move(hand, 10) }) { Text(stringResource(R.string.ult_withings_forward_10)) }
                    }
                }
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ult_done))
                }
            }
        }
    }

    companion object {
        fun intent(context: android.content.Context): Intent =
            Intent(context, UltimateWithingsCalibrationActivity::class.java)
    }
}
