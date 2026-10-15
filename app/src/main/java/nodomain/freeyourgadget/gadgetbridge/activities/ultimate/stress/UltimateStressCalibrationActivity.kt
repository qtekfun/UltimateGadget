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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.stress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.MetricValueStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiConstants
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiStressParser
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.stress.HuaweiStressScoreCalculation
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.util.Locale

/**
 * Native Huawei stress calibration (replaces the classic `HuaweiStressCalibrationActivity`): start a
 * 60 s measurement on the watch, follow the countdown, then optionally adjust the result against
 * your own rating and store it. Same events, same preference keys and same score maths as before.
 */
class UltimateStressCalibrationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null || !device.isInitialized) {
            Toast.makeText(this, getString(R.string.watch_not_connected), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            UltimateTheme {
                StressScreen(
                    device = device,
                    keepScreenOn = { on ->
                        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val ACTION_STRESS_UPDATE = "nodomain.freeyourgadget.gadgetbridge.huawei.stress.start"
        private const val ACTION_STRESS_RESULT = "nodomain.freeyourgadget.gadgetbridge.huawei.stress.result"
        private const val EXTRA_PROGRESS = "huawei_stress_progress"
        private const val EXTRA_DATA = "huawei_stress_data"
        private const val EXTRA_ERROR = "huawei_stress_error"
        private const val TOTAL_MS = 60_000L

        @JvmStatic
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateStressCalibrationActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)

        internal const val UPDATE = ACTION_STRESS_UPDATE
        internal const val RESULT = ACTION_STRESS_RESULT
        internal const val X_PROGRESS = EXTRA_PROGRESS
        internal const val X_DATA = EXTRA_DATA
        internal const val X_ERROR = EXTRA_ERROR
        internal const val TOTAL = TOTAL_MS
    }
}

private enum class Phase { IDLE, MEASURING, RESULT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StressScreen(device: GBDevice, keepScreenOn: (Boolean) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var remainingMs by remember { mutableLongStateOf(UltimateStressCalibrationActivity.TOTAL) }
    var data by remember { mutableStateOf<HuaweiStressParser.StressData?>(null) }
    var calibrate by remember { mutableStateOf(false) }
    var userScore by remember { mutableFloatStateOf(55f) }
    var tick by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    UltimateStressCalibrationActivity.UPDATE -> {
                        phase = Phase.MEASURING
                        remainingMs = intent.getLongExtra(UltimateStressCalibrationActivity.X_PROGRESS, 0)
                    }
                    UltimateStressCalibrationActivity.RESULT -> {
                        keepScreenOn(false)
                        val error = intent.getBooleanExtra(UltimateStressCalibrationActivity.X_ERROR, true)
                        if (error) {
                            phase = Phase.IDLE
                            remainingMs = UltimateStressCalibrationActivity.TOTAL
                            Toast.makeText(context, context.getString(R.string.huawei_stress_calibrate_error), Toast.LENGTH_LONG).show()
                        } else {
                            val json = intent.getStringExtra(UltimateStressCalibrationActivity.X_DATA)
                            data = if (json.isNullOrEmpty()) null else HuaweiStressParser.stressDataFromJsonStr(json)
                            phase = Phase.RESULT
                            tick++
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UltimateStressCalibrationActivity.UPDATE)
            addAction(UltimateStressCalibrationActivity.RESULT)
        }
        LocalBroadcastManager.getInstance(ctx).registerReceiver(receiver, filter)
        onDispose {
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver)
            keepScreenOn(false)
        }
    }

    // Recompute the shown score from the raw factor and the optional calibration, exactly like before.
    fun applyScore(d: HuaweiStressParser.StressData) {
        d.score = if (calibrate) {
            val adjusted = HuaweiStressScoreCalculation.calibrateScoreFactor(userScore, d.scoreFactor)
            HuaweiStressScoreCalculation.calculateNormalizedFinalScore(adjusted)
        } else {
            HuaweiStressScoreCalculation.calculateNormalizedFinalScore(d.scoreFactor)
        }
        d.level = HuaweiStressScoreCalculation.calculateLevel(d.score.toInt())
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.huawei_stress_calibrate), fontWeight = FontWeight.Bold) },
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
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (phase) {
                Phase.IDLE, Phase.MEASURING -> {
                    Text(
                        stringResource(R.string.huawei_stress_calibrate_information),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { 1f - remainingMs / UltimateStressCalibrationActivity.TOTAL.toFloat() },
                            modifier = Modifier.size(180.dp),
                            strokeWidth = 10.dp,
                        )
                        val s = (remainingMs / 1000).toInt()
                        Text(String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60), style = MetricValueStyle, color = scheme.onSurface)
                    }
                    if (phase == Phase.IDLE) {
                        Button(onClick = {
                            phase = Phase.MEASURING
                            keepScreenOn(true)
                            GBApplication.deviceService(device).onSendConfiguration(HuaweiConstants.PREF_HUAWEI_STRESS_CALIBRATE)
                        }) { Text(stringResource(R.string.huawei_stress_calibrate_start)) }
                    }
                }
                Phase.RESULT -> {
                    val d = data
                    if (d == null) {
                        Text(stringResource(R.string.huawei_stress_calibrate_error), color = scheme.error)
                        OutlinedButton(onClick = { phase = Phase.IDLE; remainingMs = UltimateStressCalibrationActivity.TOTAL }) {
                            Text(stringResource(R.string.ult_stress_again))
                        }
                    } else {
                        @Suppress("UNUSED_EXPRESSION") tick
                        applyScore(d)
                        Text(String.format(Locale.ROOT, "%02d", d.score), style = MetricValueStyle, color = scheme.onSurface)
                        Text(
                            stringResource(
                                when (d.level.toInt()) {
                                    1 -> R.string.stress_relaxed
                                    2 -> R.string.stress_mild
                                    3 -> R.string.stress_moderate
                                    4 -> R.string.stress_high
                                    else -> R.string.n_a
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = calibrate, onCheckedChange = { calibrate = it; tick++ })
                            Text(stringResource(R.string.huawei_stress_calibrate), color = scheme.onSurface)
                        }
                        Slider(
                            value = userScore,
                            onValueChange = { userScore = it; tick++ },
                            valueRange = 40f..70f,
                            steps = 29,
                            enabled = calibrate,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                data = null
                                phase = Phase.IDLE
                                remainingMs = UltimateStressCalibrationActivity.TOTAL
                            }) { Text(stringResource(R.string.ult_stress_again)) }
                            Button(onClick = {
                                d.endTime = System.currentTimeMillis()
                                val json = HuaweiStressParser.stressDataToJsonStr(d)
                                if (json.isNullOrEmpty()) {
                                    Toast.makeText(ctx, ctx.getString(R.string.huawei_stress_calibrate_error), Toast.LENGTH_LONG).show()
                                } else {
                                    GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
                                        .putString(HuaweiConstants.PREF_HUAWEI_STRESS_LAST_DATA, json)
                                        .putBoolean(HuaweiConstants.PREF_HUAWEI_STRESS_SWITCH, true)
                                        .apply()
                                    GBApplication.deviceService(device).onSendConfiguration(HuaweiConstants.PREF_HUAWEI_STRESS_SWITCH)
                                    onBack()
                                }
                            }) { Text(stringResource(R.string.huawei_stress_calibrate_finish)) }
                        }
                    }
                }
            }
        }
    }
}
