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

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.devices.GpxRouteInstallHandler
import nodomain.freeyourgadget.gadgetbridge.util.maps.MapDataLoader
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Route planner: tap on a map to add waypoints, then send the drawn route to the connected watch as
 * a GPX through the existing install flow. The base map is the offline mapsforge map (reusing
 * [MapDataLoader]); if no offline map is installed the background is blank but planning still works.
 * Straight segments between waypoints (snap-to-roads is a TODO).
 */
class UltimateRoutePlannerActivity : AppCompatActivity() {

    private val waypoints = mutableStateListOf<LatLong>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidGraphicFactory.createInstance(application)

        setContent {
            UltimateTheme {
                val palette = LocalUltimatePalette.current
                var showName by remember { mutableStateOf(false) }
                var routeName by remember { mutableStateOf("UltimateGadget route") }
                // recomposition ping when the waypoint list changes
                val count = waypoints.size
                val distanceKm = remember(count) { totalKm(waypoints) }

                Box(Modifier.fillMaxSize().background(palette.background)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val mapView = MapView(ctx)
                            mapView.isClickable = true
                            mapView.mapScaleBar.isVisible = false
                            mapView.setBuiltInZoomControls(true)

                            val loader = MapDataLoader(ctx)
                            val result = loader.loadMultiMapDataStore()
                            val cache = AndroidUtil.createTileCache(
                                ctx, "ug_planner_cache",
                                mapView.model.displayModel.tileSize, 1f,
                                mapView.model.frameBufferModel.overdrawFactor,
                            )
                            val tiles = TileRendererLayer(
                                cache, result.dataStore, mapView.model.mapViewPosition,
                                true, false, false, AndroidGraphicFactory.INSTANCE,
                            )
                            tiles.setXmlRenderTheme(loader.resolveTheme())
                            mapView.layerManager.layers.add(tiles)

                            val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                                color = 0xFF8AB4FF.toInt()
                                strokeWidth = ctx.resources.displayMetrics.density * 5f
                                setStyle(Style.STROKE)
                            }
                            val polyline = Polyline(paint, AndroidGraphicFactory.INSTANCE)
                            mapView.layerManager.layers.add(polyline)
                            mapView.setTag(polyline)

                            mapView.model.mapViewPosition.setCenter(LatLong(40.4168, -3.7038))
                            mapView.setZoomLevel(5)

                            val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    val ll = mapView.mapViewProjection.fromPixels(e.x.toDouble(), e.y.toDouble())
                                    if (ll != null) {
                                        waypoints.add(ll)
                                        redraw(mapView)
                                    }
                                    return true
                                }
                            })
                            mapView.setOnTouchListener { _, ev -> gd.onTouchEvent(ev); false }
                            mapView
                        },
                        update = { mapView -> redraw(mapView) },
                        onRelease = { it.destroyAll() },
                    )

                    // Top bar
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = palette.onSurface)
                        }
                        Text("Planificar ruta", style = MaterialTheme.typography.titleLarge, color = palette.onSurface)
                    }

                    // Bottom controls
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(16.dp),
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
                                if (waypoints.isNotEmpty()) waypoints.removeAt(waypoints.lastIndex)
                            }
                            PillButton("Limpiar", Icons.Filled.Clear, palette.surfaceHigh, palette.onSurface, enabled = count > 0) {
                                waypoints.clear()
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

    private fun redraw(mapView: MapView) {
        val poly = mapView.tag as? Polyline ?: return
        poly.latLongs.clear()
        poly.latLongs.addAll(waypoints)
        mapView.layerManager.redrawLayers()
    }

    private fun sendRoute(name: String) {
        val device = GBApplication.app().deviceManager.devices.firstOrNull { it.isInitialized }
        if (device == null) {
            Toast.makeText(this, "Conecta un reloj primero", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val gpx = buildGpx(name, waypoints)
            val dir = File(cacheDir, "gpx").apply { mkdirs() }
            val file = File(dir, "planned_${System.currentTimeMillis()}.gpx")
            file.writeText(gpx)
            val uri = FileProvider.getUriForFile(this, "$packageName.screenshot_provider", file)
            val bundle = Bundle().apply { putString(GpxRouteInstallHandler.EXTRA_TRACK_NAME, name) }
            GBApplication.deviceService(device).onInstallApp(uri, bundle)
            Toast.makeText(this, "Enviando ruta al reloj…", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo enviar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private fun totalKm(points: List<LatLong>): Double {
            var m = 0.0
            for (i in 1 until points.size) m += haversine(points[i - 1], points[i])
            return m / 1000.0
        }

        private fun haversine(a: LatLong, b: LatLong): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val la1 = Math.toRadians(a.latitude)
            val la2 = Math.toRadians(b.latitude)
            val h = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * atan2(sqrt(h), sqrt(1 - h))
        }

        private fun buildGpx(name: String, points: List<LatLong>): String {
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            sb.append("<gpx version=\"1.1\" creator=\"UltimateGadget\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            sb.append(" <trk>\n  <name>").append(name.replace("<", "").replace("&", "")).append("</name>\n  <trkseg>\n")
            for (p in points) {
                sb.append("   <trkpt lat=\"").append("%.6f".format(p.latitude))
                    .append("\" lon=\"").append("%.6f".format(p.longitude)).append("\"></trkpt>\n")
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
    onClick: () -> Unit,
) {
    Box(
        Modifier
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
