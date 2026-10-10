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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.agnss

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiAgnssHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A-GNSS (GPS assistance) for Huawei watches, UltimateGadget look. Downloads public broadcast
 * ephemeris through the map companion and keeps a package ready for the watch to pull, so the GPS
 * fixes in seconds. No Huawei Health, no Huawei servers.
 */
class UltimateAgnssActivity : AppCompatActivity() {

    private var device: GBDevice? = null
    private var status by mutableStateOf<HuaweiAgnssHelper.Status?>(null)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var isError by mutableStateOf(false)
    private var autoOnConnect by mutableStateOf(true)
    private var wifiOnly by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE)
        val p = getSharedPreferences(HuaweiAgnssHelper.PREFS, Context.MODE_PRIVATE)
        autoOnConnect = p.getBoolean(HuaweiAgnssHelper.PREF_ENABLED, false)
        wifiOnly = p.getBoolean(HuaweiAgnssHelper.PREF_WIFI_ONLY, false)
        reloadStatus()
        setContent { UltimateTheme { Screen() } }
    }

    private fun reloadStatus() { status = HuaweiAgnssHelper.status(this) }

    private fun setPref(key: String, value: Boolean) {
        getSharedPreferences(HuaweiAgnssHelper.PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    private fun refresh() {
        if (busy) return
        if (!HuaweiAgnssHelper.isCompanionInstalled(this)) {
            isError = true; message = "Instala la app de mapas de UltimateGadget para descargar A-GNSS"
            return
        }
        busy = true; isError = false; message = "Descargando efemérides…"
        HuaweiAgnssHelper.refresh(this, wifiOnly) { ok, msg ->
            runOnUiThread {
                busy = false; isError = !ok; message = msg
                reloadStatus()
            }
        }
    }

    private fun fmtTime(ms: Long): String =
        if (ms <= 0) "—" else SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(ms))

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        val palette = LocalUltimatePalette.current
        val s = status
        Scaffold(
            containerColor = palette.background,
            topBar = {
                TopAppBar(
                    title = { Text("Actualizar GPS (A-GNSS)", fontWeight = FontWeight.Bold) },
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
            Column(Modifier.padding(inner).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Descarga datos de satélites (efemérides públicas) a través de la app de mapas y los " +
                        "prepara para que el reloj fije el GPS en segundos. El reloj los recoge al conectarse; " +
                        "no se envía nada a la fuerza.",
                    style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant,
                )

                // Status card
                Box(
                    Modifier.fillMaxWidth().background(palette.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (s != null && s.hasData) {
                            Text("Paquete listo", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text(
                                "GPS ${s.gps} · GLONASS ${s.glonass} · Galileo ${s.galileo} · BeiDou ${s.beidou}",
                                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant,
                            )
                            Text("Actualizado: ${fmtTime(s.fetchedAt)}", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                            val expired = System.currentTimeMillis() > s.validUntil
                            Text(
                                if (expired) "Caducado — actualiza antes de salir" else "Válido hasta: ${fmtTime(s.validUntil)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (expired) palette.error else palette.secondary,
                            )
                            if (s.source.isNotBlank()) Text("Fuente: ${s.source}", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                        } else {
                            Text("Sin datos todavía", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Pulsa «Actualizar ahora» para descargar.", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                        }
                    }
                }

                Box(
                    Modifier.fillMaxWidth().background(palette.primary, RoundedCornerShape(16.dp))
                        .clickable(enabled = !busy) { refresh() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (busy) "Actualizando…" else "Actualizar ahora", color = palette.onPrimary, fontWeight = FontWeight.Bold)
                }

                if (message.isNotBlank()) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = if (isError) palette.error else palette.secondary)
                }

                // Options
                ToggleRow("Actualizar al conectar el reloj", autoOnConnect, palette) {
                    autoOnConnect = it; setPref(HuaweiAgnssHelper.PREF_ENABLED, it)
                }
                ToggleRow("Descargar solo por Wi-Fi", wifiOnly, palette) {
                    wifiOnly = it; setPref(HuaweiAgnssHelper.PREF_WIFI_ONLY, it)
                }
            }
        }
    }

    @Composable
    private fun ToggleRow(label: String, checked: Boolean, palette: nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimatePalette, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().background(palette.surfaceContainer, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = palette.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    companion object {
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateAgnssActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}
