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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
 * Local-only backup/restore for the new UI.
 *
 * The primary flow is a FULL, ENCRYPTED, PORTABLE backup ([UltimateBackup]): a single `.ugbak`
 * file that bundles the Gadgetbridge database, every SharedPreferences file and the saved routes,
 * encrypted with the user's password (AES-256-GCM, PBKDF2 key derivation; see
 * [UltimateBackupCrypto]). Restored on a clean phone it brings the app back to the same state
 * (workouts, settings, paired-device row), except for the Bluetooth bonding which lives in the OS
 * and requires re-pairing the watch.
 *
 * A legacy "database only, unencrypted" export is kept for interoperability with the old format and
 * with plain Gadgetbridge DB backups. Everything is chosen by the user through the Storage Access
 * Framework, so nothing ever leaves the device.
 */
class UltimateDataIOActivity : AppCompatActivity() {

    private var status by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)

    // Full encrypted backup flow.
    private var askExportPassword by mutableStateOf(false)
    private var pendingImportUri by mutableStateOf<Uri?>(null)
    private var pendingExportPassword: CharArray? = null

    /** Set after a successful import: the running process still holds the old DB session and
     *  all the in-memory caches (device service, loaded workout lists, prefs, …), so the restored
     *  data only becomes visible after a clean process restart. */
    private var importDone by mutableStateOf(false)
    private var confirmRestart by mutableStateOf(false)

    // Full encrypted backup (.ugbak).
    private val exportFullPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val pwd = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && pwd != null) doFullExport(uri, pwd) else pwd?.fill('\u0000')
    }

    private val importFullPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingImportUri = uri }

    // Legacy: database-only, unencrypted.
    private val exportDbPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) doLegacyDbExport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                DataIOScreen(
                    status = status,
                    busy = busy,
                    askExportPassword = askExportPassword,
                    askImportPassword = pendingImportUri != null,
                    importDone = importDone,
                    confirmRestart = confirmRestart,
                    onBack = { finish() },
                    onExportFull = { askExportPassword = true },
                    onExportPasswordConfirmed = { pwd ->
                        askExportPassword = false
                        pendingExportPassword = pwd.toCharArray()
                        val name = "ultimategadget-" +
                            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) +
                            "." + UltimateBackup.FILE_EXTENSION
                        exportFullPicker.launch(name)
                    },
                    onExportPasswordCancel = { askExportPassword = false },
                    onPickImport = {
                        importFullPicker.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    onImportPasswordConfirmed = { pwd ->
                        val uri = pendingImportUri
                        pendingImportUri = null
                        if (uri != null) doFullImport(uri, pwd.toCharArray())
                    },
                    onImportPasswordCancel = { pendingImportUri = null },
                    onExportDbOnly = {
                        val name = "ultimategadget-" +
                            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".db"
                        exportDbPicker.launch(name)
                    },
                    onRestartRequest = { confirmRestart = true },
                    onConfirmRestart = { confirmRestart = false; restartApp() },
                    onCancelRestart = { confirmRestart = false },
                )
            }
        }
    }

    private fun doFullExport(uri: Uri, password: CharArray) {
        busy = true
        status = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use {
                        UltimateBackup.exportEncrypted(applicationContext, it, password)
                    } ?: throw Exception("No se pudo abrir el destino")
                }
            }
            password.fill('\u0000')
            busy = false
            status = result.fold(
                {
                    "Copia completa cifrada creada correctamente. Incluye base de datos " +
                        "(entrenos, actividad, sueño, pulso), ajustes de la app y por dispositivo, " +
                        "y rutas guardadas. Guárdala y recuerda la contraseña: sin ella no se puede " +
                        "restaurar."
                },
                { "Error al exportar: ${it.localizedMessage}" },
            )
        }
    }

    private fun doFullImport(uri: Uri, password: CharArray) {
        busy = true
        status = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use {
                        UltimateBackup.importEncrypted(applicationContext, it, password)
                    } ?: throw Exception("No se pudo abrir el fichero")
                }
            }
            password.fill('\u0000')
            busy = false
            result.fold(
                { res ->
                    importDone = true
                    status = "Copia restaurada: base de datos + ${res.prefsFiles} ficheros de ajustes" +
                        (if (res.routeFiles > 0) " + ${res.routeFiles} rutas" else "") +
                        ". Reinicia la app para aplicar los cambios. Nota: el emparejamiento " +
                        "Bluetooth no se puede restaurar (es del sistema Android); vuelve a conectar " +
                        "el reloj desde la app."
                },
                {
                    importDone = false
                    status = "Error al importar: ${it.localizedMessage}"
                },
            )
        }
    }

    /** Legacy, unencrypted: exports just the database file (old behaviour / plain GB backups). */
    private fun doLegacyDbExport(uri: Uri) {
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
                { "Base de datos exportada (sin cifrar, solo BD)." },
                { "Error al exportar: ${it.localizedMessage}" },
            )
        }
    }

    /**
     * Clean process restart so the freshly imported database AND the restored SharedPreferences are
     * reloaded from scratch.
     *
     * We launch the app's main entry point ([UltimateHomeActivity], the LAUNCHER activity, which
     * reconnects the last watch on start) as a fresh task and then kill the process. When Android
     * relaunches the task, [nodomain.freeyourgadget.gadgetbridge.GBApplication] re-initialises,
     * re-reads the preference files from disk and [GBDatabaseManager] opens the new database file,
     * so every in-memory cache is rebuilt from the imported data. This is the library-free
     * ProcessPhoenix pattern (no extra dependencies).
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
    askExportPassword: Boolean,
    askImportPassword: Boolean,
    importDone: Boolean,
    confirmRestart: Boolean,
    onBack: () -> Unit,
    onExportFull: () -> Unit,
    onExportPasswordConfirmed: (String) -> Unit,
    onExportPasswordCancel: () -> Unit,
    onPickImport: () -> Unit,
    onImportPasswordConfirmed: (String) -> Unit,
    onImportPasswordCancel: () -> Unit,
    onExportDbOnly: () -> Unit,
    onRestartRequest: () -> Unit,
    onConfirmRestart: () -> Unit,
    onCancelRestart: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Copia de seguridad", fontWeight = FontWeight.Bold) },
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
                title = "Copia completa cifrada",
                body = "Crea un único fichero .ugbak que puedes llevar a otro teléfono. Incluye la " +
                    "base de datos (entrenos, actividad, sueño, pulso), todos los ajustes (de la app " +
                    "y de cada dispositivo) y las rutas guardadas. Se cifra con tu contraseña " +
                    "(AES-256). Sin la contraseña no se puede restaurar, así que no la olvides.",
            ) {
                Button(onClick = onExportFull, enabled = !busy) { Text("Crear copia cifrada…") }
            }

            SectionCard(
                title = "Restaurar copia completa",
                body = "Elige un fichero .ugbak y escribe su contraseña. SOBRESCRIBE la base de " +
                    "datos y los ajustes actuales con los de la copia. Después hay que reiniciar la " +
                    "app para que se apliquen los cambios.",
            ) {
                OutlinedButton(onClick = onPickImport, enabled = !busy) { Text("Elegir copia…") }
            }

            SectionCard(
                title = "Al pasar a otro teléfono",
                body = "El emparejamiento Bluetooth (las claves de vinculación del reloj) lo guarda el " +
                    "sistema Android, NO la app, así que no se puede exportar. En el teléfono nuevo, " +
                    "tras restaurar la copia tendrás que volver a conectar/emparejar el reloj desde " +
                    "la app. Los entrenos, los ajustes y el dispositivo guardado SÍ se restauran.",
            ) {}

            SectionCard(
                title = "Reiniciar app",
                body = if (importDone) {
                    "Restauración lista. Reinicia la app para aplicar los cambios y ver los datos " +
                        "restaurados. Al volver se reconectará tu reloj automáticamente."
                } else {
                    "Cierra y vuelve a abrir la app de forma limpia. Útil tras restaurar una copia."
                },
            ) {
                Button(onClick = onRestartRequest, enabled = !busy) { Text("Reiniciar app") }
            }

            SectionCard(
                title = "Exportar solo base de datos (sin cifrar)",
                body = "Opción heredada: guarda únicamente el fichero de la base de datos, sin " +
                    "ajustes ni cifrado. Útil para interoperar con copias de Gadgetbridge.",
            ) {
                OutlinedButton(onClick = onExportDbOnly, enabled = !busy) { Text("Exportar solo BD…") }
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

    if (askExportPassword) {
        ExportPasswordDialog(
            onConfirm = onExportPasswordConfirmed,
            onCancel = onExportPasswordCancel,
        )
    }

    if (askImportPassword) {
        ImportPasswordDialog(
            onConfirm = onImportPasswordConfirmed,
            onCancel = onImportPasswordCancel,
        )
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = onCancelRestart,
            title = { Text("Reiniciar la app") },
            text = { Text("La app se cerrará y se reiniciará para aplicar la restauración. ¿Continuar?") },
            confirmButton = { Button(onClick = onConfirmRestart) { Text("Reiniciar") } },
            dismissButton = { OutlinedButton(onClick = onCancelRestart) { Text("Cancelar") } },
            containerColor = palette.surfaceContainer,
            titleContentColor = palette.onSurface,
            textContentColor = palette.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExportPasswordDialog(onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    val palette = LocalUltimatePalette.current
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    val tooShort = pwd.length < 6
    val mismatch = pwd != confirm
    val error = when {
        pwd.isEmpty() -> null
        tooShort -> "La contraseña debe tener al menos 6 caracteres."
        mismatch && confirm.isNotEmpty() -> "Las contraseñas no coinciden."
        else -> null
    }
    val canConfirm = !tooShort && !mismatch && confirm.isNotEmpty()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Contraseña de la copia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Elige una contraseña para cifrar la copia. La necesitarás para restaurarla; " +
                        "no se puede recuperar si la pierdes.",
                    color = palette.onSurfaceVariant,
                )
                PasswordField(pwd, { pwd = it }, "Contraseña", reveal)
                PasswordField(confirm, { confirm = it }, "Repite la contraseña", reveal)
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "Ocultar" else "Mostrar", color = palette.primary)
                }
                error?.let { Text(it, color = palette.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pwd) }, enabled = canConfirm) { Text("Cifrar y guardar") }
        },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancelar") } },
        containerColor = palette.surfaceContainer,
        titleContentColor = palette.onSurface,
        textContentColor = palette.onSurfaceVariant,
    )
}

@Composable
private fun ImportPasswordDialog(onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    val palette = LocalUltimatePalette.current
    var pwd by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Contraseña de la copia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Escribe la contraseña con la que se cifró esta copia para poder restaurarla.",
                    color = palette.onSurfaceVariant,
                )
                PasswordField(pwd, { pwd = it }, "Contraseña", reveal)
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "Ocultar" else "Mostrar", color = palette.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pwd) }, enabled = pwd.isNotEmpty()) { Text("Restaurar") }
        },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancelar") } },
        containerColor = palette.surfaceContainer,
        titleContentColor = palette.onSurface,
        textContentColor = palette.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, reveal: Boolean) {
    val palette = LocalUltimatePalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.onSurface,
            unfocusedTextColor = palette.onSurface,
            focusedBorderColor = palette.primary,
            unfocusedBorderColor = palette.onSurfaceVariant,
            focusedLabelColor = palette.primary,
            unfocusedLabelColor = palette.onSurfaceVariant,
            cursorColor = palette.primary,
        ),
    )
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
