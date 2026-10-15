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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.heartrate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCard
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.SectionLabel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZones
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesConfig
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesSpec

/**
 * Native heart-rate zones and high-heart-rate alert editor (replaces the classic
 * `HeartRateSettingsActivity`). Same model and same persistence as the classic screen: the
 * coordinator's [HeartRateZonesSpec] supplies the per-posture configurations, the calculation
 * method is chosen per configuration, the high-HR warning is toggled, and "Save" writes the
 * configuration back through [HeartRateZonesSpec.saveConfig].
 */
class UltimateHeartRateZonesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        val spec = runCatching { device?.deviceCoordinator?.getHeartRateZonesSpec(device) }.getOrNull()
        if (device == null || spec == null) {
            finish()
            return
        }
        setContent {
            UltimateTheme { HeartRateZonesScreen(spec = spec, onBack = { finish() }) }
        }
    }

    companion object {
        @JvmStatic
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateHeartRateZonesActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeartRateZonesScreen(spec: HeartRateZonesSpec, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var config by remember { mutableStateOf<List<HeartRateZonesConfig>>(spec.deviceConfig.orEmpty()) }
    var tab by remember { mutableIntStateOf(0) }
    // The configuration objects are mutated in place (like the classic screen); bump to recompose.
    var version by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.heart_rate_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmReset = true }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.hr_settings_zones_remove_all))
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
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (config.isEmpty()) {
                Text(
                    stringResource(R.string.n_a),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            val index = tab.coerceIn(0, config.lastIndex)
            if (config.size > 1) {
                ScrollableTabRow(selectedTabIndex = index, containerColor = scheme.background) {
                    config.forEachIndexed { i, c ->
                        Tab(
                            selected = i == index,
                            onClick = { tab = i },
                            text = { Text(spec.getNameByType(ctx, c.type)) },
                        )
                    }
                }
            }
            val cfg = config[index]
            @Suppress("UNUSED_EXPRESSION") version
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ZonesCard(cfg) { version++ }
                LimitCard(cfg) { version++ }
                Button(
                    onClick = {
                        spec.saveConfig(cfg)
                        Toast.makeText(ctx, ctx.getString(R.string.ult_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save_configuration)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.hr_settings_zones_remove_all)) },
            text = { Text(stringResource(R.string.hr_settings_zones_remove_all_description)) },
            confirmButton = {
                TextButton(onClick = {
                    spec.clearConfig()
                    config = spec.deviceConfig.orEmpty()
                    tab = 0
                    version++
                    confirmReset = false
                }) { Text(stringResource(R.string.ult_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.ult_cancel)) }
            },
        )
    }
}

@Composable
private fun ZonesCard(cfg: HeartRateZonesConfig, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val methods: List<HeartRateZones> = cfg.configByMethods.orEmpty()
    val current = cfg.currentCalculationMethod
    val selected = methods.firstOrNull { it.method == current } ?: methods.firstOrNull()
    var menuOpen by remember { mutableStateOf(false) }

    DashboardCard {
        SectionLabel(stringResource(R.string.hr_settings_zones_calculation_method))
        Spacer(Modifier.height(8.dp))
        Column {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { HeartRateZones.methodToString(ctx, it.method) } ?: stringResource(R.string.n_a))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                methods.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(HeartRateZones.methodToString(ctx, m.method)) },
                        onClick = {
                            cfg.currentCalculationMethod = m.method
                            menuOpen = false
                            onChanged()
                        },
                    )
                }
            }
        }
        if (selected == null) return@DashboardCard
        val lthr = selected.method == HeartRateZones.CalculationMethod.LTHR
        Spacer(Modifier.height(12.dp))
        InfoLine(
            stringResource(if (lthr) R.string.hr_settings_zones_lactate_threshold_hr else R.string.hr_settings_zones_max_hr),
            "${selected.getHRThreshold()} ${stringResource(R.string.ult_bpm)}",
        )
        if (selected.method == HeartRateZones.CalculationMethod.HRR) {
            InfoLine(
                stringResource(R.string.hr_settings_zones_resting_hr),
                "${selected.getHRResting()} ${stringResource(R.string.ult_bpm)}",
            )
        }
        Spacer(Modifier.height(10.dp))
        val bpm = stringResource(R.string.ult_bpm)
        if (!lthr) {
            ZoneLine(stringResource(R.string.ult_hr_zone_max), "${selected.getHRThreshold()} $bpm", "100 %", scheme.error)
        }
        listOf(5 to selected.zone5, 4 to selected.zone4, 3 to selected.zone3, 2 to selected.zone2, 1 to selected.zone1)
            .forEach { (n, v) ->
                ZoneLine(
                    stringResource(R.string.ult_hr_zone_n, n),
                    "$v $bpm",
                    "${selected.getPercentage(v)} %",
                    scheme.primary,
                )
            }
    }
}

@Composable
private fun LimitCard(cfg: HeartRateZonesConfig, onChanged: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.pref_heart_rate_high_alert),
                    style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
                )
                Text(
                    "${stringResource(R.string.hr_settings_hr_limit)}: ${cfg.getWarningHRLimit()} ${stringResource(R.string.ult_bpm)}",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
            Switch(checked = cfg.getWarningEnable(), onCheckedChange = {
                cfg.setWarningEnable(it)
                onChanged()
            })
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ZoneLine(label: String, value: String, percent: String, accent: androidx.compose.ui.graphics.Color) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = accent, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text(
            percent,
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
