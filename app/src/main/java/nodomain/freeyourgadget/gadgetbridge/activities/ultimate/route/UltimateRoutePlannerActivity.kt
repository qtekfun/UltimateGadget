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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.route

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.install.GpxRouteInstallerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import nodomain.freeyourgadget.gadgetbridge.mapcore.LatLon
import nodomain.freeyourgadget.gadgetbridge.mapcore.MapTheme
import nodomain.freeyourgadget.gadgetbridge.mapcore.MyLocationButton
import nodomain.freeyourgadget.gadgetbridge.mapcore.UltimateMapEngine
import nodomain.freeyourgadget.gadgetbridge.mapcore.UltimateMapView
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Route planner: tap on the map to add waypoints, then send the drawn route to the connected watch as
 * a GPX through the existing install flow. The map is the shared MapLibre+PMTiles core
 * ([UltimateMapView]); it renders offline regions from `filesDir/maps` and never shows a blank page.
 * Straight segments between waypoints (snap-to-roads is a TODO).
 */
class UltimateRoutePlannerActivity : AppCompatActivity() {

    private val waypoints = mutableStateListOf<LatLon>()
    private var engine: UltimateMapEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UltimateTheme {
                val palette = LocalUltimatePalette.current
                var showName by remember { mutableStateOf(false) }
                var routeName by remember { mutableStateOf("UltimateGadget route") }
                val count = waypoints.size
                val distanceKm = remember(count) { totalKm(waypoints) }

                Box(Modifier.fillMaxSize().background(palette.background)) {
                    UltimateMapView(
                        modifier = Modifier.fillMaxSize(),
                        tilesDir = File(filesDir, "maps"),
                        theme = MapTheme.DARK,
                        onReady = { eng ->
                            engine = eng
                            eng.setCamera(LatLon(40.4168, -3.7038), 5.0)
                            eng.onMapTap { p -> waypoints.add(p); pushMap() }
                            pushMap()
                        },
                    )

                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = palette.onSurface)
                        }
                        Text("Planificar ruta", style = MaterialTheme.typography.titleLarge, color = palette.onSurface)
                    }

                    MyLocationButton(
                        onLocation = { p ->
                            engine?.setUserLocation(p)
                            engine?.setCamera(p, 15.0)
                        },
                        onUnavailable = { msg -> Toast.makeText(this@UltimateRoutePlannerActivity, msg, Toast.LENGTH_SHORT).show() },
                        containerColor = palette.surfaceContainer,
                        contentColor = palette.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )

                    if (!this@UltimateRoutePlannerActivity.hasBaseMap()) {
                        PillButton("Importar mapa base", Icons.Filled.Send, palette.secondaryContainer, palette.onSecondaryContainer, enabled = true, modifier = Modifier.align(Alignment.Center)) {
                            startActivity(Intent().setClassName(this@UltimateRoutePlannerActivity, PHONE_MAPS))
                        }
                    }

                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.background(palette.surfaceContainer, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (count == 0) "Toca el mapa para añadir puntos"
                                else "$count puntos · ${"%.2f".format(distanceKm)} km",
                                color = palette.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Deshacer", Icons.Filled.Undo, palette.surfaceHigh, palette.onSurface, enabled = count > 0) {
                                if (waypoints.isNotEmpty()) { waypoints.removeAt(waypoints.lastIndex); pushMap() }
                            }
                            PillButton("Limpiar", Icons.Filled.Clear, palette.surfaceHigh, palette.onSurface, enabled = count > 0) {
                                waypoints.clear(); pushMap()
                            }
                            PillButton("Enviar", Icons.Filled.Send, palette.primary, palette.onPrimary, enabled = count >= 2) {
                                showName = true
                            }
                        }
                    }

                    if (showName) {
                        AlertDialog(
                            onDismissRequest = { showName = false },
                            confirmButton = {
                                TextButton(onClick = { showName = false; sendRoute(routeName) }) { Text("Enviar") }
                            },
                            dismissButton = { TextButton(onClick = { showName = false }) { Text("Cancelar") } },
                            title = { Text("Nombre de la ruta") },
                            text = {
                                OutlinedTextField(value = routeName, onValueChange = { routeName = it }, singleLine = true)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun pushMap() {
        val pts = waypoints.toList()
        engine?.drawRoute(pts)
        engine?.markers(pts)
    }

    private fun hasBaseMap(): Boolean =
        File(filesDir, "maps").listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") }?.isNotEmpty() == true

    /** The watch we plan for: the one we were launched with, else any initialized device. */
    private fun plannerDevice(): GBDevice? {
        val manager = GBApplication.app().deviceManager
        val fromIntent = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        return fromIntent?.let { want -> manager.devices.firstOrNull { it.address == want.address } }
            ?: manager.devices.firstOrNull { it.isInitialized }
            ?: manager.devices.firstOrNull()
    }

    private fun sendRoute(name: String) {
        val device = plannerDevice()
        if (device == null) {
            Toast.makeText(this, "Empareja un reloj compatible primero", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val gpx = buildGpx(name, waypoints)
            // Keep a local copy in the route registry (manage / resend / delete).
            PhoneRouteStore.save(this, name, gpx)
            // Hand the GPX to the proven installer flow: it waits for the connection, validates that
            // the device supports route upload, and shows upload progress — unlike a blind fire-and-forget.
            val dir = File(cacheDir, "gpx").apply { mkdirs() }
            val file = File(dir, "planned_${System.currentTimeMillis()}.gpx")
            file.writeText(gpx)
            val uri = FileProvider.getUriForFile(this, "$packageName.screenshot_provider", file)
            val intent = Intent(this, GpxRouteInstallerActivity::class.java).apply {
                data = uri
                putExtra(GBDevice.EXTRA_DEVICE, device)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo preparar la ruta: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        // Phone base-map manager (lives in main; referenced by name so this builds without it here).
        private const val PHONE_MAPS = "nodomain.freeyourgadget.gadgetbridge.activities.ultimate.phonemaps.PhoneMapsActivity"

        private fun totalKm(points: List<LatLon>): Double {
            var m = 0.0
            for (i in 1 until points.size) m += haversine(points[i - 1], points[i])
            return m / 1000.0
        }

        private fun haversine(a: LatLon, b: LatLon): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val la1 = Math.toRadians(a.lat)
            val la2 = Math.toRadians(b.lat)
            val h = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * atan2(sqrt(h), sqrt(1 - h))
        }

        private fun buildGpx(name: String, points: List<LatLon>): String {
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            sb.append("<gpx version=\"1.1\" creator=\"UltimateGadget\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            sb.append(" <trk>\n  <name>").append(name.replace("<", "").replace("&", "")).append("</name>\n  <trkseg>\n")
            for (p in points) {
                // GPX coordinates MUST use '.' as decimal separator; force Locale.US (es_ES would emit commas,
                // which the parser reads as 0.0 → a route of (0,0) points that never lands on the watch).
                sb.append("   <trkpt lat=\"").append(String.format(java.util.Locale.US, "%.6f", p.lat))
                    .append("\" lon=\"").append(String.format(java.util.Locale.US, "%.6f", p.lon)).append("\"></trkpt>\n")
            }
            sb.append("  </trkseg>\n </trk>\n</gpx>\n")
            return sb.toString()
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    fg: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .background(if (enabled) bg else bg.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (enabled) fg else fg.copy(alpha = 0.6f), modifier = Modifier.padding(end = 2.dp))
            Text(label, color = if (enabled) fg else fg.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
        }
    }
}
