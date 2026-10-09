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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.onboarding.UltimatePermissionsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dataio.UltimateDataIOActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.notifications.UltimateNotificationsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.phonemaps.PhoneMapsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateThemeState

/**
 * Ultimate-styled hub for the app's GLOBAL settings, meant to replace the classic
 * [nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity] for everyday use.
 *
 * It does NOT re-implement any logic: every control reads and writes the exact same
 * SharedPreferences keys that the classic preference screens use ([GBApplication.getPrefs]),
 * so both UIs stay perfectly in sync. Only the common, side-effect-free preferences are
 * surfaced here (toggles, selects); obscure or logic-heavy ones (GPS acquisition, RTL tuning,
 * weather, intent API, deprecated media control, per-device settings) stay in the classic
 * screen and are documented as TODO in the accompanying report.
 */
class UltimateSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltimateTheme { UltimateSettingsScreen(onBack = { finish() }) } }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, UltimateSettingsActivity::class.java)
    }
}

// ---------------------------------------------------------------------------
// Preference access helpers — thin wrappers over GBApplication.getPrefs().
// We never duplicate defaults that live elsewhere; defaults here mirror
// res/xml/preferences.xml so an unset key shows the same value as the classic UI.
// ---------------------------------------------------------------------------

/**
 * Theme preference keys that affect [UltimateTheme]. Writing any of these must bump
 * [UltimateThemeState] so every live Compose screen recomposes with the new look.
 */
private val THEME_PREF_KEYS = setOf(
    "pref_key_theme",
    "pref_key_theme_dynamic",
    "pref_key_theme_amoled_black",
)

private fun readBool(key: String, def: Boolean): Boolean =
    GBApplication.getPrefs().getBoolean(key, def)

private fun writeBool(key: String, value: Boolean) {
    GBApplication.getPrefs().preferences.edit().putBoolean(key, value).apply()
    if (key in THEME_PREF_KEYS) UltimateThemeState.bump()
}

private fun readString(key: String, def: String): String =
    GBApplication.getPrefs().getString(key, def) ?: def

private fun writeString(key: String, value: String) {
    GBApplication.getPrefs().preferences.edit().putString(key, value).apply()
    if (key in THEME_PREF_KEYS) UltimateThemeState.bump()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltimateSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val scroll = rememberScrollState()

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // --- General ---
            SettingsSection("General") {
                ToggleRow("Iniciar al arrancar el teléfono", "general_autostartonboot", true)
                ToggleRow("Conectar automáticamente por Bluetooth", "general_autoconnectonbluetooth", false)
                ToggleRow(
                    "Reconectar solo a dispositivos ya conectados",
                    "general_reconnectonlytoconnected", true,
                )
                ToggleRow("Avisos de permisos", "permission_pestering", true)
                ToggleRow("Mostrar registro de cambios tras actualizar", "show_changelog", true)
                ToggleRow(
                    "Ordenar la lista por última conexión",
                    "general_prefs_key_sort_by_last_connected_ts", false,
                )
            }

            // --- Language & units ---
            SettingsSection("Idioma y unidades") {
                SelectRow(
                    title = "Idioma de la aplicación",
                    key = "language",
                    default = "default",
                    entriesRes = R.array.pref_language_options,
                    valuesRes = R.array.pref_language_values,
                ) { value ->
                    // Apply immediately, exactly like the classic screen does.
                    GBApplication.setLanguage(value)
                }
                SelectRow(
                    title = "Distancia",
                    key = "unit_distance",
                    default = "metric",
                    entriesRes = R.array.pref_entries_unit_system,
                    valuesRes = R.array.pref_values_unit_system,
                )
                SelectRow(
                    title = "Temperatura",
                    key = "unit_temperature",
                    default = "metric",
                    entriesRes = R.array.temperature_scales,
                    valuesRes = R.array.temperature_scales_values,
                )
                SelectRow(
                    title = "Peso",
                    key = "unit_weight",
                    default = "metric",
                    entriesRes = R.array.weight_scale_units,
                    valuesRes = R.array.weight_scale_values,
                )
                ToggleRow("Unidades náuticas", "units_nautical", true)
            }

            // --- Date & time ---
            SettingsSection("Fecha y hora") {
                ToggleRow("Sincronizar la hora al conectar", "datetime_synconconnect", true)
            }

            // --- Interface ---
            SettingsSection("Interfaz") {
                SelectRow(
                    title = "Tema (claro / oscuro / sistema)",
                    key = "pref_key_theme",
                    default = context.getString(R.string.pref_theme_value_system),
                    entriesRes = R.array.pref_ultimate_theme_options,
                    valuesRes = R.array.pref_ultimate_theme_values,
                )
                ToggleRow("Colores dinámicos (Material You)", "pref_key_theme_dynamic", false)
                ToggleRow("Fondo negro (AMOLED)", "pref_key_theme_amoled_black", false)
                ToggleRow("Bloquear capturas de pantalla", "block_screenshots", false)
                ToggleRow("Refrescar al deslizar hacia abajo", "pref_refresh_on_swipe", true)
                ToggleRow("Botón flotante para añadir dispositivo", "display_add_device_fab", true)
                ToggleRow("Barra de navegación inferior", "display_bottom_navigation_bar", true)
            }

            // --- Developer ---
            SettingsSection("Desarrollador") {
                ToggleRow("Notificación al producirse un fallo", "crash_notification", false)
                ToggleRow(
                    "Guardar registros en archivo (requiere reiniciar la app)",
                    "log_to_file", false,
                )
                ToggleRow(
                    "Nivel de registro detallado (requiere reiniciar la app)",
                    "log_level_trace", false,
                )
            }

            // --- Links to existing Ultimate / classic screens (not reimplemented here) ---
            SettingsSection("Más") {
                NavRow("Exportar / importar datos") {
                    context.startActivity(Intent(context, UltimateDataIOActivity::class.java))
                }
                NavRow("Notificaciones") {
                    context.startActivity(UltimateNotificationsActivity.intent(context))
                }
                NavRow("Mapas del móvil") {
                    context.startActivity(Intent(context, PhoneMapsActivity::class.java))
                }
                NavRow("Permisos") {
                    context.startActivity(UltimatePermissionsActivity.intent(context))
                }
                NavRow("Acerca de UltimateGadget") {
                    context.startActivity(UltimateAboutActivity.intent(context))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val palette = LocalUltimatePalette.current
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = palette.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
    )
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, key: String, default: Boolean) {
    val palette = LocalUltimatePalette.current
    var checked by remember { mutableStateOf(readBool(key, default)) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                writeBool(key, checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                writeBool(key, it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onPrimary,
                checkedTrackColor = palette.primary,
                uncheckedThumbColor = palette.onSurfaceVariant,
                uncheckedTrackColor = palette.surfaceHigh,
            ),
        )
    }
}

@Composable
private fun SelectRow(
    title: String,
    key: String,
    default: String,
    entriesRes: Int,
    valuesRes: Int,
    onApplied: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val entries = remember(entriesRes) { context.resources.getStringArray(entriesRes) }
    val values = remember(valuesRes) { context.resources.getStringArray(valuesRes) }

    var current by remember { mutableStateOf(readString(key, default)) }
    var dialogOpen by remember { mutableStateOf(false) }

    val currentLabel = entries.getOrNull(values.indexOf(current)) ?: current

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.onSurface)
            Text(
                currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.onSurfaceVariant,
        )
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            containerColor = palette.surfaceHigh,
            titleContentColor = palette.onSurface,
            textContentColor = palette.onSurface,
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                    values.forEachIndexed { i, value ->
                        val selected = value == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected, onClick = {
                                    current = value
                                    writeString(key, value)
                                    onApplied(value)
                                    dialogOpen = false
                                })
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(
                                entries.getOrNull(i) ?: value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text("Cancelar", color = palette.primary)
                }
            },
        )
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    val palette = LocalUltimatePalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
