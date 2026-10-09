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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dataio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBDatabaseManager
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.UltimateHomeActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only data export/import for the new UI. Reuses Gadgetbridge's own database backup
 * ([GBDatabaseManager.exportDB]/[GBDatabaseManager.importDB]); the file is chosen by the user
 * through the Storage Access Framework, so nothing leaves the device.
 *
 * TODO: per-workout GPX/FIT export (GPXExporter/FitExporter exist, one file per workout) and
 *  importing a Huawei Health export are left for a follow-up.
 */
class UltimateDataIOActivity : AppCompatActivity() {

    private var status by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var pendingImport by mutableStateOf<Uri?>(null)

    /** Set after a successful import: the running process still holds the old DB session and
     *  all the in-memory caches (device service, loaded workout lists, …), so the restored data
     *  only becomes visible after a clean process restart. */
    private var importDone by mutableStateOf(false)
    private var confirmRestart by mutableStateOf(false)

    private val exportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) doExport(uri) }

    private val importPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingImport = uri }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                DataIOScreen(
                    status = status,
                    busy = busy,
                    confirmImportUri = pendingImport,
                    importDone = importDone,
                    confirmRestart = confirmRestart,
                    onBack = { finish() },
                    onExport = {
                        val name = "ultimategadget-" +
                            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".db"
                        exportPicker.launch(name)
                    },
                    onPickImport = { importPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                    onConfirmImport = { uri -> pendingImport = null; doImport(uri) },
                    onCancelImport = { pendingImport = null },
                    onRestartRequest = { confirmRestart = true },
                    onConfirmRestart = { confirmRestart = false; restartApp() },
                    onCancelRestart = { confirmRestart = false },
                )
            }
        }
    }

    private fun doExport(uri: Uri) {
        busy = true
        status = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { GBDatabaseManager.exportDB(it) }
                        ?: throw Exception("No se pudo abrir el destino")
                }
            }
            busy = false
            status = result.fold(
                { "Copia exportada correctamente." },
                { "Error al exportar: ${it.localizedMessage}" },
            )
        }
    }

    private fun doImport(uri: Uri) {
        busy = true
        status = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { GBDatabaseManager.importDB(it) }
                        ?: throw Exception("No se pudo abrir el fichero")
                }
            }
            busy = false
            result.fold(
                {
                    importDone = true
                    status = "Base de datos importada (todos los datos: actividad, sueño, pulso y " +
                        "entrenos). Reinicia la app para que los cambios sean visibles."
                },
                {
                    importDone = false
                    status = "Error al importar: ${it.localizedMessage}"
                },
            )
        }
    }

    /**
     * Clean process restart so the freshly imported database is reopened from scratch.
     *
     * We launch the app's main entry point ([UltimateHomeActivity], the LAUNCHER activity, which
     * reconnects the last watch on start) as a fresh task and then kill the process. When Android
     * relaunches the task, [nodomain.freeyourgadget.gadgetbridge.GBApplication] re-initialises and
     * [GBDatabaseManager] opens the new database file, so every in-memory cache is rebuilt from the
     * imported data. This is the library-free ProcessPhoenix pattern (no extra dependencies).
     */
    private fun restartApp() {
        val launchComponent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.component
        val restartIntent = if (launchComponent != null) {
            Intent.makeRestartActivityTask(launchComponent)
        } else {
            Intent(this, UltimateHomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
        startActivity(restartIntent)
        // Tear down the current process; the task above is relaunched by the system with a fresh VM.
        Runtime.getRuntime().exit(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataIOScreen(
    status: String?,
    busy: Boolean,
    confirmImportUri: Uri?,
    importDone: Boolean,
    confirmRestart: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onPickImport: () -> Unit,
    onConfirmImport: (Uri) -> Unit,
    onCancelImport: () -> Unit,
    onRestartRequest: () -> Unit,
    onConfirmRestart: () -> Unit,
    onCancelRestart: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Exportar / Importar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
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
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(
                title = "Exportar copia",
                body = "Guarda una copia de seguridad de toda tu base de datos (actividad, sueño, " +
                    "pulso, entrenos, ajustes de dispositivos) en un fichero que tú eliges. Todo local.",
            ) {
                Button(onClick = onExport, enabled = !busy) { Text("Exportar copia…") }
            }
            SectionCard(
                title = "Importar copia",
                body = "Restaura una copia de seguridad exportada antes. SOBRESCRIBE la base de datos " +
                    "actual (todas las tablas, incluidos los entrenos). Tras importar hay que " +
                    "reiniciar la app para que los datos restaurados se vean.",
            ) {
                OutlinedButton(onClick = onPickImport, enabled = !busy) { Text("Elegir fichero…") }
            }

            SectionCard(
                title = "Reiniciar app",
                body = if (importDone) {
                    "Importación lista. Reinicia la app para aplicar los cambios y ver los datos " +
                        "restaurados. Al volver se reconectará tu reloj automáticamente."
                } else {
                    "Cierra y vuelve a abrir la app de forma limpia. Útil tras importar una copia."
                },
            ) {
                Button(onClick = onRestartRequest, enabled = !busy) { Text("Reiniciar app") }
            }

            if (busy) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = palette.primary)
                    Text("Trabajando…", color = palette.onSurfaceVariant)
                }
            }
            status?.let {
                Text(it, color = palette.onSurface)
            }
        }
    }

    if (confirmImportUri != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text("Sobrescribir la base de datos") },
            text = { Text("Esto reemplazará todos los datos actuales por los de la copia. ¿Continuar?") },
            confirmButton = { Button(onClick = { onConfirmImport(confirmImportUri) }) { Text("Importar") } },
            dismissButton = { OutlinedButton(onClick = onCancelImport) { Text("Cancelar") } },
            containerColor = palette.surfaceContainer,
            titleContentColor = palette.onSurface,
            textContentColor = palette.onSurfaceVariant,
        )
    }

    if (confirmRestart) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onCancelRestart,
            title = { Text("Reiniciar la app") },
            text = { Text("La app se cerrará y se reiniciará para aplicar la importación. ¿Continuar?") },
            confirmButton = { Button(onClick = onConfirmRestart) { Text("Reiniciar") } },
            dismissButton = { OutlinedButton(onClick = onCancelRestart) { Text("Cancelar") } },
            containerColor = palette.surfaceContainer,
            titleContentColor = palette.onSurface,
            textContentColor = palette.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(title: String, body: String, action: @Composable () -> Unit) {
    val palette = LocalUltimatePalette.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.onSurface, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
            action()
        }
    }
}
