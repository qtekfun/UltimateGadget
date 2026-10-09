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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltimateHomeScreen(
    devices: List<DeviceCardUi>,
    onOpenDevice: (DeviceCardUi) -> Unit,
    onAddDevice: () -> Unit,
    onExportImport: () -> Unit = {},
    onPhoneMaps: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onReports: () -> Unit = {},
    onPerformance: () -> Unit = {},
    onClassicMode: () -> Unit = {},
) {
    val palette = LocalUltimatePalette.current
    val connected = devices.firstOrNull { it.connected }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "UltimateGadget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Exportar / Importar") }, onClick = { menuOpen = false; onExportImport() })
                            DropdownMenuItem(text = { Text("Mapas del móvil") }, onClick = { menuOpen = false; onPhoneMaps() })
                            DropdownMenuItem(text = { Text("Notificaciones") }, onClick = { menuOpen = false; onNotifications() })
                            DropdownMenuItem(text = { Text("Informes y objetivos") }, onClick = { menuOpen = false; onReports() })
                            DropdownMenuItem(text = { Text("Rendimiento") }, onClick = { menuOpen = false; onPerformance() })
                            DropdownMenuItem(text = { Text("Modo clásico") }, onClick = { menuOpen = false; onClassicMode() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.onSurface,
                    actionIconContentColor = palette.onSurfaceVariant,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDevice,
                containerColor = palette.primary,
                contentColor = palette.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Añadir dispositivo", fontWeight = FontWeight.Bold) },
            )
        },
    ) { inner ->
        if (devices.isEmpty()) {
            EmptyState(Modifier.padding(inner))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (connected != null) {
                item { HeroCard(connected, onClick = { onOpenDevice(connected) }) }
            }
            item { SectionHeader("Tus dispositivos") }
            items(devices, key = { it.address }) { d ->
                DeviceCard(d, onClick = { onOpenDevice(d) })
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    val palette = LocalUltimatePalette.current
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = palette.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
    )
}

fun accentColors(seed: Int): Pair<Color, Color> {
    val palettes = listOf(
        Color(0xFF54627C) to Color(0xFF12161F),   // blue-grey
        Color(0xFF6B5A49) to Color(0xFF2A2420),   // warm
        Color(0xFF3F5E57) to Color(0xFF16231F),   // teal
        Color(0xFF5D4A6B) to Color(0xFF1F1828),   // violet
    )
    val idx = ((seed % palettes.size) + palettes.size) % palettes.size
    return palettes[idx]
}

@Composable
fun HeroCard(d: DeviceCardUi, onClick: () -> Unit) {
    val palette = LocalUltimatePalette.current
    val (c1, c2) = accentColors(d.accentSeed)
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(c1, c2)))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, palette.background.copy(alpha = 0.92f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                (if (d.busy) "Sincronizando" else "Conectado").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary,
            )
            Text(
                d.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.batteryLevel in 0..100) BatteryPill(d.batteryLevel)
                Text(
                    d.model ?: d.typeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD4D8E4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun DeviceCard(d: DeviceCardUi, onClick: () -> Unit) {
    val palette = LocalUltimatePalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            DeviceIcon(d.iconRes, tint = palette.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                d.name,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(d.stateLabel, d.connected, d.connecting)
                if (d.batteryLevel in 0..100) BatteryPill(d.batteryLevel)
            }
        }
    }
}

@Composable
fun DeviceIcon(iconRes: Int, tint: Color) {
    if (iconRes != 0) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    } else {
        Icon(Icons.Filled.Watch, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun StatusChip(label: String, connected: Boolean, connecting: Boolean = false) {
    val palette = LocalUltimatePalette.current
    val bg = when {
        connected -> palette.secondaryContainer
        connecting -> palette.tertiary.copy(alpha = 0.20f)
        else -> palette.surfaceHigh
    }
    val fg = when {
        connected -> palette.onSecondaryContainer
        connecting -> palette.tertiary
        else -> palette.onSurfaceVariant
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 2.dp,
                color = fg,
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun BatteryPill(level: Int) {
    val palette = LocalUltimatePalette.current
    val tint = when {
        level <= 15 -> palette.error
        level <= 35 -> palette.tertiary
        else -> palette.secondary
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surfaceHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            "$level%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = palette.onSurface,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val palette = LocalUltimatePalette.current
    Column(
        modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Watch, contentDescription = null, tint = palette.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Aún no hay dispositivos", style = MaterialTheme.typography.titleLarge, color = palette.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            "Añade tu primer reloj o pulsera para empezar.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115L)
@Composable
private fun HomePreview() {
    val sample = listOf(
        DeviceCardUi("A", "HUAWEI WATCH GT Runner 2", "Huawei", "Conectado", true, false, 82, "GT Runner 2", R.drawable.ic_device_default, 0),
        DeviceCardUi("B", "HUAWEI WATCH GT 7", "Huawei", "Desconectado", false, false, -1, "GT 7", R.drawable.ic_device_default, 1),
        DeviceCardUi("C", "Amazfit Bip", "Huami", "Desconectado", false, false, 40, "Bip", R.drawable.ic_device_default, 2),
    )
    UltimateTheme {
        UltimateHomeScreen(sample, onOpenDevice = {}, onAddDevice = {})
    }
}
