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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.SectionLabelStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/**
 * Branded entry screen for adding a device. Handles Bluetooth/location runtime permissions and then
 * hands off to Gadgetbridge's existing, battle-tested discovery/pairing flow (DiscoveryActivityV2).
 */
@Composable
fun AddDeviceScreen(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onStartDiscovery: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Scaffold(containerColor = palette.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = palette.primaryContainer,
                modifier = Modifier.size(104.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = palette.onPrimaryContainer,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "AÑADIR DISPOSITIVO",
                style = SectionLabelStyle,
                color = palette.secondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Empareja tu reloj o pulsera",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Pon el dispositivo en modo de emparejamiento y mantenlo cerca. " +
                    "Buscaremos dispositivos Bluetooth cercanos.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            if (!permissionsGranted) {
                PermissionNote(
                    text = "Necesitamos permiso de Bluetooth (y ubicación en versiones antiguas) para buscar dispositivos.",
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primary,
                        contentColor = palette.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Conceder permisos", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStartDiscovery,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primary,
                        contentColor = palette.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Buscar dispositivos", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionNote(text: String) {
    val palette = LocalUltimatePalette.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = palette.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun AddDeviceScreenPreviewNeedsPermission() {
    UltimateTheme { AddDeviceScreen(permissionsGranted = false, onRequestPermissions = {}, onStartDiscovery = {}) }
}

@Preview
@Composable
private fun AddDeviceScreenPreviewReady() {
    UltimateTheme { AddDeviceScreen(permissionsGranted = true, onRequestPermissions = {}, onStartDiscovery = {}) }
}
