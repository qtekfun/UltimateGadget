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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
fun UltimateDeviceScreen(
    device: DeviceCardUi,
    options: List<DeviceOptionUi>,
    onBack: () -> Unit,
    onOption: (DeviceOptionUi) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    navigationIconContentColor = palette.onSurface,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(palette.background),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { DeviceHeader(device) }
            // Group rows by section, keeping the order in which each section first appears.
            val grouped = options.groupBy { it.section }
            grouped.keys.forEach { section ->
                item(key = "hdr_$section") {
                    SectionHeader(section.ifBlank { "Opciones" })
                }
                items(grouped.getValue(section), key = { it.id }) { opt ->
                    OptionRow(opt, onClick = { onOption(opt) })
                }
            }
        }
    }
}

@Composable
private fun DeviceHeader(d: DeviceCardUi) {
    val (c1, c2) = accentColors(d.accentSeed)
    // Like the home hero, this header is a self-contained dark accent card in BOTH themes: the
    // overlay is a fixed dark scrim (not the theme background), so white / light text keeps its
    // contrast in light mode. Fixed light teal/amber accents read on the dark card in either theme.
    val onHero = Color.White
    val onHeroMuted = Color.White.copy(alpha = 0.78f)
    val heroConnected = Color(0xFF7DD3C0)
    val heroConnecting = Color(0xFFF5B971)
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(c1, c2))),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                d.stateLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    d.connected -> heroConnected
                    d.connecting -> heroConnecting
                    else -> onHeroMuted
                },
            )
            Text(
                d.name,
                style = MaterialTheme.typography.headlineSmall,
                color = onHero,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (d.batteryLevel in 0..100) BatteryPill(d.batteryLevel)
                Text(d.model ?: d.typeName, style = MaterialTheme.typography.bodySmall, color = onHeroMuted)
            }
        }
    }
}

@Composable
private fun OptionRow(opt: DeviceOptionUi, onClick: () -> Unit) {
    val palette = LocalUltimatePalette.current
    val titleColor = if (opt.destructive) palette.error else palette.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceContainer)
            .clickable(enabled = opt.enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(opt.title, style = MaterialTheme.typography.titleMedium, color = if (opt.enabled) titleColor else palette.onSurfaceVariant)
            if (opt.subtitle != null) {
                Text(opt.subtitle, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.onSurfaceVariant)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115L)
@Composable
private fun DeviceScreenPreview() {
    val d = DeviceCardUi("A", "HUAWEI WATCH GT Runner 2", "Huawei", "Conectado", true, false, 82, "GT Runner 2", R.drawable.ic_device_default, 0)
    val opts = listOf(
        DeviceOptionUi("disconnect", "Desconectar", "Cortar la conexión"),
        DeviceOptionUi("settings", "Ajustes del dispositivo", "Notificaciones, alarmas, pantallas…"),
        DeviceOptionUi("maps", "Mapas offline", "Instalar y borrar mapas del reloj"),
        DeviceOptionUi("routes", "Rutas", "Enviar una ruta GPX"),
        DeviceOptionUi("sync", "Sincronizar actividad", "Descargar datos nuevos"),
        DeviceOptionUi("find", "Buscar dispositivo", null),
        DeviceOptionUi("remove", "Quitar dispositivo", null, destructive = true),
    )
    UltimateTheme { UltimateDeviceScreen(d, opts, onBack = {}, onOption = {}) }
}
