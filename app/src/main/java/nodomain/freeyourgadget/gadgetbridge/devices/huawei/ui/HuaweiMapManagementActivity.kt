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
package nodomain.freeyourgadget.gadgetbridge.devices.huawei.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.route.PhoneRouteStore
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.route.SavedRoute
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.route.UltimateRoutePlannerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiConstants
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.p2p.HuaweiP2PMapkitService
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * Offline maps and routes for a Huawei watch, in the UltimateGadget look. Lists the maps stored on
 * the watch (resolving the numeric map id to a region/country via offvmp's table, MIT), deletes
 * them, and sends a GPX route — all without the firmware install screen.
 */
class HuaweiMapManagementActivity : AppCompatActivity() {

    data class MapItem(val mapId: Long, val mapType: Byte, val version: Int)

    private var device: GBDevice? = null
    private var regions: JSONObject? = null

    private var items by mutableStateOf<List<MapItem>>(emptyList())
    private var loaded by mutableStateOf(false)
    private var routes by mutableStateOf<List<SavedRoute>>(emptyList())

    private val mapListReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ids = intent.getLongArrayExtra(HuaweiP2PMapkitService.EXTRA_MAP_IDS)
            val types = intent.getIntArrayExtra(HuaweiP2PMapkitService.EXTRA_MAP_TYPES)
            val versions = intent.getIntArrayExtra(HuaweiP2PMapkitService.EXTRA_MAP_VERSIONS)
            val list = mutableListOf<MapItem>()
            if (ids != null && types != null && versions != null) {
                for (i in ids.indices) list.add(MapItem(ids[i], types[i].toByte(), versions[i]))
            }
            items = list
            loaded = true
        }
    }

    private val pickGpx = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val d = device
        if (uri != null && d != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { /* some providers don't grant persistable */ }
            GBApplication.deviceService(d).onInstallApp(uri, Bundle())
            Toast.makeText(this, getString(R.string.huawei_route_sending), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE)
        regions = loadRegions()
        routes = PhoneRouteStore.list(this)

        setContent {
            UltimateTheme {
                DisposableEffect(Unit) {
                    val lbm = LocalBroadcastManager.getInstance(this@HuaweiMapManagementActivity)
                    lbm.registerReceiver(mapListReceiver, IntentFilter(HuaweiP2PMapkitService.ACTION_MAP_LIST))
                    requestMaps()
                    onDispose { lbm.unregisterReceiver(mapListReceiver) }
                }
                MapRouteScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A route may have been planned+saved while we were away; refresh the local list.
        routes = PhoneRouteStore.list(this)
    }

    /** Re-send an already-saved route through the proven installer flow. */
    private fun resendRoute(route: SavedRoute) {
        val d = device
        if (d == null) {
            Toast.makeText(this, R.string.huawei_offline_maps_not_connected, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.screenshot_provider", route.file)
        startActivity(
            nodomain.freeyourgadget.gadgetbridge.activities.ultimate.install.UltimateInstallActivity
                .forUri(this, uri, d),
        )
    }

    private fun deleteRoute(route: SavedRoute) {
        PhoneRouteStore.delete(route.file)
        routes = PhoneRouteStore.list(this)
    }

    private fun openPlanner() {
        startActivity(
            Intent(this, UltimateRoutePlannerActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device),
        )
    }

    private fun requestMaps() {
        val d = device
        if (d == null || !d.isInitialized) {
            loaded = true
            return
        }
        GBApplication.deviceService(d).onSendConfiguration(HuaweiConstants.PREF_HUAWEI_OFFLINE_MAP_QUERY)
    }

    private fun delete(item: MapItem) {
        val d = device ?: return
        GBApplication.deviceService(d).onSendConfiguration(
            HuaweiConstants.PREF_HUAWEI_OFFLINE_MAP_DELETE_PREFIX + item.mapId + ":" + item.mapType
        )
        Toast.makeText(this, R.string.huawei_offline_maps_deleting, Toast.LENGTH_SHORT).show()
        // Optimistic: drop it locally; the watch will re-broadcast the authoritative list shortly.
        items = items.filterNot { it.mapId == item.mapId && it.mapType == item.mapType }
    }

    private fun loadRegions(): JSONObject? = try {
        assets.open("offline_map_regions.json").use { input ->
            val buf = input.readBytes()
            JSONObject(String(buf, Charsets.UTF_8)).optJSONObject("regions")
        }
    } catch (e: Exception) {
        null
    }

    private fun regionName(mapId: Long): String {
        regions?.optJSONObject(mapId.toString())?.let { r ->
            val region = r.optString("region", "")
            val country = r.optString("country", "")
            if (region.isNotEmpty() && country.isNotEmpty() && region != country) return "$region, $country"
            if (region.isNotEmpty()) return region
        }
        return getString(R.string.huawei_offline_maps_unknown, mapId.toString())
    }

    private fun typeName(mapType: Byte): String = when (mapType.toInt()) {
        1 -> getString(R.string.huawei_offline_maps_type_contour)
        2 -> getString(R.string.huawei_offline_maps_type_global)
        else -> getString(R.string.huawei_offline_maps_type_map)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MapRouteScreen() {
        val palette = LocalUltimatePalette.current
        var tab by remember { mutableStateOf(intent.getIntExtra("ug_tab", 0).coerceIn(0, 1)) }
        var pendingDelete by remember { mutableStateOf<MapItem?>(null) }

        Scaffold(
            containerColor = palette.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            getString(R.string.huawei_offline_maps_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            Column(Modifier.padding(inner).fillMaxSize()) {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = palette.background,
                    contentColor = palette.primary,
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text(getString(R.string.huawei_tab_maps)) },
                        icon = { Icon(Icons.Filled.Map, null) })
                    Tab(selected = tab == 1, onClick = { tab = 1 },
                        text = { Text(getString(R.string.huawei_tab_routes)) },
                        icon = { Icon(Icons.Filled.Route, null) })
                }
                when (tab) {
                    0 -> MapsTab(onDelete = { pendingDelete = it })
                    else -> RoutesTab()
                }
            }
        }

        pendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = palette.surfaceContainer,
                title = { Text(getString(R.string.huawei_offline_maps_delete_title)) },
                text = { Text(getString(R.string.huawei_offline_maps_delete_confirm, regionName(item.mapId))) },
                confirmButton = {
                    TextButton(onClick = { delete(item); pendingDelete = null }) {
                        Text(getString(R.string.huawei_offline_maps_delete), color = palette.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text(getString(android.R.string.cancel)) }
                },
            )
        }
    }

    @Composable
    private fun MapsTab(onDelete: (MapItem) -> Unit) {
        val palette = LocalUltimatePalette.current
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (!loaded) getString(R.string.huawei_offline_maps_loading)
                    else if (device?.isInitialized != true) getString(R.string.huawei_offline_maps_not_connected)
                    else getString(R.string.huawei_offline_maps_none),
                    color = palette.onSurfaceVariant,
                )
            }
            return
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceContainer, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(regionName(item.mapId), style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                        Text("${typeName(item.mapType)} · v${item.version}", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Filled.Delete, contentDescription = getString(R.string.huawei_offline_maps_delete), tint = palette.error)
                    }
                }
            }
        }
    }

    @Composable
    private fun RoutesTab() {
        val palette = LocalUltimatePalette.current
        var pendingRouteDelete by remember { mutableStateOf<SavedRoute?>(null) }
        val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    getString(R.string.huawei_route_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
            }
            // Actions: plan on the map, or send an existing GPX file.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .background(palette.primary, RoundedCornerShape(16.dp))
                            .clickable { openPlanner() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Planificar en el mapa", color = palette.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .background(palette.surfaceHigh, RoundedCornerShape(16.dp))
                            .clickable(enabled = device?.isInitialized == true) { pickGpx.launch(arrayOf("*/*")) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(getString(R.string.huawei_route_pick), color = palette.onSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (device?.isInitialized != true) {
                item {
                    Text(
                        getString(R.string.huawei_offline_maps_not_connected),
                        color = palette.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Text(
                    "Mis rutas",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (routes.isEmpty()) {
                item {
                    Text(
                        "Aún no has guardado rutas. Planifícala o envía un GPX y aparecerá aquí para reenviarla o borrarla.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                    )
                }
            } else {
                items(routes, key = { it.file.path }) { route ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.surfaceContainer, RoundedCornerShape(16.dp))
                            .clickable { resendRoute(route) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Route, null, tint = palette.secondary, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(route.name, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                            Text(
                                dateFmt.format(Date(route.epochMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { resendRoute(route) }) { Text("Reenviar", color = palette.primary) }
                        IconButton(onClick = { pendingRouteDelete = route }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = palette.error)
                        }
                    }
                }
            }
        }

        pendingRouteDelete?.let { route ->
            AlertDialog(
                onDismissRequest = { pendingRouteDelete = null },
                containerColor = palette.surfaceContainer,
                title = { Text("Borrar ruta") },
                text = { Text("Se borrará la copia local de «${route.name}». La ruta que ya esté en el reloj se quita desde el propio reloj.") },
                confirmButton = {
                    TextButton(onClick = { deleteRoute(route); pendingRouteDelete = null }) {
                        Text(getString(R.string.huawei_offline_maps_delete), color = palette.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRouteDelete = null }) { Text(getString(android.R.string.cancel)) }
                },
            )
        }
    }
}
