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
                    } ?: throw Exception("Could not open the destination")
                }
            }
            password.fill('\u0000')
            busy = false
            status = result.fold(
                {
                    "Encrypted full backup created successfully. Includes the database " +
                        "(workouts, activity, sleep, heart rate), app and per-device settings, " +
                        "and saved routes. Keep it and remember the password: without it, it cannot be " +
                        "restored."
                },
                { "Export failed: ${it.localizedMessage}" },
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
                    } ?: throw Exception("Could not open the file")
                }
            }
            password.fill('\u0000')
            busy = false
            result.fold(
                { res ->
                    importDone = true
                    status = "Backup restored: database + ${res.prefsFiles} settings files" +
                        (if (res.routeFiles > 0) " + ${res.routeFiles} routes" else "") +
                        ". Restart the app to apply the changes. Note: the Bluetooth " +
                        "pairing cannot be restored (it belongs to Android); reconnect " +
                        "the watch from the app."
                },
                {
                    importDone = false
                    status = "Import failed: ${it.localizedMessage}"
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
                        ?: throw Exception("Could not open the destination")
                }
            }
            busy = false
            status = result.fold(
                { "Database exported (unencrypted, DB only)." },
                { "Export failed: ${it.localizedMessage}" },
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
                title = { Text("Backup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                title = "Encrypted full backup",
                body = "Creates a single .ugbak file you can carry to another phone. Includes the " +
                    "database (workouts, activity, sleep, heart rate), all settings (app " +
                    "and per-device) and saved routes. It is encrypted with your password " +
                    "(AES-256). Without the password it cannot be restored, so do not forget it.",
            ) {
                Button(onClick = onExportFull, enabled = !busy) { Text("Create encrypted backup…") }
            }

            SectionCard(
                title = "Restore full backup",
                body = "Choose a .ugbak file and type its password. It OVERWRITES the current " +
                    "database and settings with those from the backup. Afterwards you must restart the " +
                    "app for the changes to take effect.",
            ) {
                OutlinedButton(onClick = onPickImport, enabled = !busy) { Text("Choose backup…") }
            }

            SectionCard(
                title = "When moving to another phone",
                body = "The Bluetooth pairing (the watch's bonding keys) is stored by " +
                    "Android, NOT the app, so it cannot be exported. On the new phone, " +
                    "after restoring the backup you will have to reconnect/pair the watch from " +
                    "the app. Workouts, settings and the saved device ARE restored.",
            ) {}

            SectionCard(
                title = "Restart app",
                body = if (importDone) {
                    "Restore ready. Restart the app to apply the changes and see the restored " +
                        "data. On return, your watch reconnects automatically."
                } else {
                    "Closes and reopens the app cleanly. Useful after restoring a backup."
                },
            ) {
                Button(onClick = onRestartRequest, enabled = !busy) { Text("Restart app") }
            }

            SectionCard(
                title = "Export database only (unencrypted)",
                body = "Legacy option: saves only the database file, without " +
                    "settings or encryption. Useful for interoperating with Gadgetbridge backups.",
            ) {
                OutlinedButton(onClick = onExportDbOnly, enabled = !busy) { Text("Export DB only…") }
            }

            if (busy) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = palette.primary)
                    Text("Working…", color = palette.onSurfaceVariant)
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
            title = { Text("Restart the app") },
            text = { Text("The app will close and restart to apply the restore. Continue?") },
            confirmButton = { Button(onClick = onConfirmRestart) { Text("Restart") } },
            dismissButton = { OutlinedButton(onClick = onCancelRestart) { Text("Cancel") } },
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
        tooShort -> "The password must be at least 6 characters."
        mismatch && confirm.isNotEmpty() -> "The passwords do not match."
        else -> null
    }
    val canConfirm = !tooShort && !mismatch && confirm.isNotEmpty()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Choose a password to encrypt the backup. You will need it to restore it; " +
                        "it cannot be recovered if you lose it.",
                    color = palette.onSurfaceVariant,
                )
                PasswordField(pwd, { pwd = it }, "Password", reveal)
                PasswordField(confirm, { confirm = it }, "Repeat the password", reveal)
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "Hide" else "Show", color = palette.primary)
                }
                error?.let { Text(it, color = palette.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pwd) }, enabled = canConfirm) { Text("Encrypt and save") }
        },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancel") } },
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
        title = { Text("Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Type the password this backup was encrypted with to restore it.",
                    color = palette.onSurfaceVariant,
                )
                PasswordField(pwd, { pwd = it }, "Password", reveal)
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "Hide" else "Show", color = palette.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pwd) }, enabled = pwd.isNotEmpty()) { Text("Restore") }
        },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancel") } },
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
