package com.qtekfun.ultimategadget.mapsdl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.qtekfun.ultimategadget.mapsdl.agnss.AgnssDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "mapsdl"
private const val KEY_FOLDER = "folder"

private val Scheme = darkColorScheme(
    primary = Color(0xFF8AB4FF), onPrimary = Color(0xFF0A2A5E),
    secondary = Color(0xFF7DD3C0),
    background = Color(0xFF0F1115), surface = Color(0xFF14171D),
    onBackground = Color(0xFFE6E8EE), onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF272B38), onSurfaceVariant = Color(0xFFAAB0C0),
    error = Color(0xFFFF8A80),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = Scheme) { App() } }
    }
}

// ----- Shared folder + navigation -----

private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
private fun savedFolder(c: Context): Uri? = prefs(c).getString(KEY_FOLDER, null)?.let { Uri.parse(it) }
private fun folderName(c: Context, uri: Uri?): String =
    uri?.let { runCatching { DocumentFile.fromTreeUri(c, it)?.name }.getOrNull() } ?: ""

@Composable
private fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var folder by remember { mutableStateOf(savedFolder(context)) }

    val pickFolder = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            prefs(context).edit().putString(KEY_FOLDER, uri.toString()).apply()
            folder = uri
        }
    }
    val onPick: () -> Unit = { pickFolder.launch(null) }

    when (screen) {
        "maps" -> MapsScreen(folder, onPick) { screen = "home" }
        "golf" -> GolfScreen(folder, onPick) { screen = "home" }
        "agnss" -> AgnssScreen(folder, onPick) { screen = "home" }
        "exercises" -> ExercisesScreen(folder, onPick) { screen = "home" }
        "authkey" -> AuthKeyScreen { screen = "home" }
        else -> HomeScreen(
            folderLabel = folderName(context, folder),
            onPick = onPick,
            onOpen = { screen = it },
            onQuit = { (context as? android.app.Activity)?.finishAndRemoveTask() },
        )
    }
}

// ----- Reusable UI pieces (keep every screen consistent) -----

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun bar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) =
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹  Back") }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Scheme.background,
            titleContentColor = Scheme.onBackground,
            actionIconContentColor = Scheme.onSurfaceVariant,
            navigationIconContentColor = Scheme.primary,
        ),
    )

@Composable
private fun SectionLabel(text: String) =
    Text(text, fontSize = 11.sp, letterSpacing = 1.sp, color = Scheme.onSurfaceVariant)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant)

@Composable
private fun StatusLine(status: String, isError: Boolean = false) {
    if (status.isNotBlank()) {
        Text(status, style = MaterialTheme.typography.bodySmall, color = if (isError) Scheme.error else Scheme.secondary)
    }
}

/** Folder-destination card shown on Home and (compact) on tool screens. */
@Composable
private fun FolderCard(folderLabel: String, onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Scheme.surface)) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Destination folder", color = Scheme.onSurface, fontWeight = FontWeight.SemiBold)
                Hint(if (folderLabel.isNotBlank()) folderLabel else "Not chosen — downloads need it")
            }
            OutlinedButton(onClick = onPick) { Text(if (folderLabel.isNotBlank()) "Cambiar" else "Elegir") }
        }
    }
}

private fun fmt(bytes: Long): String {
    if (bytes <= 0) return "—"
    val u = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return "%.1f %s".format(v, u[i])
}

/** Write bytes into [subdir] (created if needed) of the SAF tree, replacing any existing file. */
private fun writeInto(context: Context, folderUri: Uri, subdir: String?, name: String, bytes: ByteArray) {
    val root = DocumentFile.fromTreeUri(context, folderUri) ?: throw RuntimeException("carpeta no accesible")
    val dir = if (subdir == null) root
    else root.findFile(subdir)?.takeIf { it.isDirectory } ?: root.createDirectory(subdir)
        ?: throw RuntimeException("could not create the folder $subdir")
    dir.findFile(name)?.delete()
    val file = dir.createFile("application/octet-stream", name) ?: throw RuntimeException("could not create the file")
    context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) } ?: throw RuntimeException("no se pudo escribir")
}

// ----- Home hub -----

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(folderLabel: String, onPick: () -> Unit, onOpen: (String) -> Unit, onQuit: () -> Unit) {
    Scaffold(
        containerColor = Scheme.background,
        topBar = { bar("UltimateGadget", onBack = null, actions = { TextButton(onClick = onQuit) { Text("Exit") } }) },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint("Online utilities for UltimateGadget: they download data to the folder, then it is used from the app or the watch.")
            FolderCard(folderLabel, onPick)
            Spacer(Modifier.height(4.dp))
            SectionLabel("HERRAMIENTAS")
            ToolTile("🗺️", "Offline maps", "Base maps (.pmtiles) for the watch") { onOpen("maps") }
            ToolTile("⛳", "Golf courses", "Mapas de campos para enviar al reloj") { onOpen("golf") }
            ToolTile("🛰️", "A-GNSS (GPS)", "Ephemeris so the watch fixes faster") { onOpen("agnss") }
            ToolTile("🏋️", "Exercise pack", "Exercise catalog with animations for Workouts") { onOpen("exercises") }
            ToolTile("🔑", "Auth key (Amazfit/Zepp/Xiaomi)", "Bluetooth key without the official app") { onOpen("authkey") }
        }
    }
}

@Composable
private fun ToolTile(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Scheme.surface),
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp, modifier = Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Scheme.onSurface, fontWeight = FontWeight.SemiBold)
                Hint(subtitle)
            }
            Text("›", color = Scheme.onSurfaceVariant, fontSize = 20.sp)
        }
    }
}

// ----- Maps -----

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MapsScreen(folder: Uri?, onPick: () -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var regions by remember { mutableStateOf<List<MapRegion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val progress = remember { mutableStateMapOf<String, Float>() }
    var installed by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    fun refreshInstalled() {
        val f = folder ?: run { installed = emptyList(); return }
        installed = DocumentFile.fromTreeUri(context, f)
            ?.listFiles()?.filter { it.isFile && (it.name ?: "").endsWith(".pmtiles") }
            ?.sortedBy { it.name } ?: emptyList()
    }
    androidx.compose.runtime.LaunchedEffect(folder) { refreshInstalled() }

    Scaffold(containerColor = Scheme.background, topBar = { bar("Offline maps", onBack) }) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint("Download base maps (.pmtiles), then import them in UltimateGadget.")
            FolderCard(folderName(context, folder), onPick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    loading = true; status = ""; error = false
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { Downloader.fetchCatalog() } }
                            .onSuccess { regions = it; status = "${it.size} regiones disponibles" }
                            .onFailure { status = "Error loading the catalog: ${it.message}"; error = true }
                        loading = false
                    }
                }) { Text("Load catalog") }
                if (loading) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            }
            StatusLine(status, error)

            if (installed.isNotEmpty()) {
                SectionLabel("INSTALADOS")
                installed.forEach { f ->
                    ItemCard(f.name ?: "", fmt(f.length())) {
                        OutlinedButton(onClick = { f.delete(); refreshInstalled() }) { Text("Delete") }
                    }
                }
            }

            SectionLabel("DISPONIBLES")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(regions, key = { it.id }) { r ->
                    val p = progress[r.id]
                    ItemCard(
                        title = r.name,
                        subtitle = fmt(r.size) + (p?.let { " · descargando ${(it * 100).toInt()}%" } ?: ""),
                    ) {
                        Button(
                            enabled = folder != null && p == null,
                            onClick = {
                                val f = folder ?: return@Button
                                progress[r.id] = 0f
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            Downloader.download(context, r, f) { done, total ->
                                                if (total > 0) progress[r.id] = (done.toFloat() / total).coerceIn(0f, 1f)
                                            }
                                        }
                                    }.onSuccess { status = "${r.name} descargado"; error = false }
                                        .onFailure { status = "${r.name}: ${it.message}"; error = true }
                                    progress.remove(r.id)
                                    refreshInstalled()
                                }
                            },
                        ) { Text(if (folder == null) "Choose folder" else "Download") }
                    }
                }
            }
        }
    }
}

/** A list row: title + subtitle on the left, a trailing action composable on the right. */
@Composable
private fun ItemCard(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Scheme.surface)) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Scheme.onSurface)
                if (subtitle.isNotBlank()) Hint(subtitle)
            }
            trailing()
        }
    }
}

// ----- A-GNSS -----

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
private fun AgnssScreen(folder: Uri?, onPick: () -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // Huawei (public broadcast ephemeris)
    var hwLoading by remember { mutableStateOf(false) }
    var hwStatus by remember { mutableStateOf("") }
    var hwError by remember { mutableStateOf(false) }
    // Amazfit / Zepp OS (Huami EPO via account login)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var azLoading by remember { mutableStateOf(false) }
    var azStatus by remember { mutableStateOf("") }
    var azError by remember { mutableStateOf(false) }

    Scaffold(containerColor = Scheme.background, topBar = { bar("A-GNSS (GPS)", onBack) }) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint("Satellite data so the watch fixes GPS in seconds. Choose your watch's section.")
            FolderCard(folderName(context, folder), onPick)

            // --- Huawei ---
            SectionLabel("HUAWEI (GT RUNNER 2, GT…)")
            Hint("On Huawei watches this is automatic: do it from UltimateGadget (device → Update GPS). This button is a manual fallback: it saves 'agnss/ephemeris.zip'.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = folder != null && !hwLoading,
                    onClick = {
                        val f = folder ?: return@Button
                        hwLoading = true; hwStatus = "Downloading ephemeris…"; hwError = false
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val res = AgnssDownloader.generate()
                                    writeInto(context, f, "agnss", "ephemeris.zip", res.zip); res
                                }
                            }.onSuccess { res ->
                                val c = res.counts
                                hwStatus = "Generado: GPS ${c['G'] ?: 0}, GLONASS ${c['R'] ?: 0}, Galileo ${c['E'] ?: 0}, " +
                                    "BeiDou ${c['C'] ?: 0} · ${fmt(res.zip.size.toLong())} · ${res.source}"
                                hwError = false
                            }.onFailure { hwStatus = it.message ?: "Error"; hwError = true }
                            hwLoading = false
                        }
                    },
                ) { Text(if (folder == null) "Choose folder" else "Generar (Huawei)") }
                if (hwLoading) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            }
            StatusLine(hwStatus, hwError)

            // --- Amazfit / Zepp OS ---
            SectionLabel("AMAZFIT / ZEPP OS")
            Hint("Sign in to your Zepp/Amazfit account to download the A-GPS (EPO) from your account and generate the watch package. The email and password are used only for the login and not stored. Then install it in UltimateGadget (device → Update GPS → choose the file).")
            androidx.compose.material3.OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Zepp/Amazfit account email") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
            )
            androidx.compose.material3.OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = folder != null && !azLoading && email.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        val f = folder ?: return@Button
                        azLoading = true; azStatus = "Signing in and downloading A-GPS…"; azError = false
                        val e = email.trim(); val p = password
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val uihh = HuamiToken.fetchAgpsUihh(e, p)
                                    writeInto(context, f, "agnss", "amazfit-agps-uihh.bin", uihh); uihh.size
                                }
                            }.onSuccess { sz ->
                                azStatus = "Generated: amazfit-agps-uihh.bin (${fmt(sz.toLong())}). Install it in UltimateGadget."
                                azError = false
                            }.onFailure { azStatus = it.message ?: "Error"; azError = true }
                            azLoading = false
                        }
                    },
                ) { Text(if (folder == null) "Choose folder" else "Generate (Amazfit)") }
                if (azLoading) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            }
            StatusLine(azStatus, azError)
        }
    }
}

// ----- Golf -----

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GolfScreen(folder: Uri?, onPick: () -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var level by remember { mutableStateOf("countries") }
    var countries by remember { mutableStateOf<List<GolfApi.Country>>(emptyList()) }
    var cities by remember { mutableStateOf<List<GolfApi.City>>(emptyList()) }
    var courses by remember { mutableStateOf<List<GolfApi.Course>>(emptyList()) }
    var country by remember { mutableStateOf<GolfApi.Country?>(null) }
    var city by remember { mutableStateOf<GolfApi.City?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun load(block: suspend () -> Unit) {
        loading = true; status = ""; error = false
        scope.launch {
            runCatching { block() }.onFailure { status = it.message ?: "Error"; error = true }
            loading = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (countries.isEmpty()) {
            loading = true
            runCatching { withContext(Dispatchers.IO) { GolfApi.countries() } }
                .onSuccess { countries = it }.onFailure { status = it.message ?: "Error"; error = true }
            loading = false
        }
    }

    val title = when (level) {
        "cities" -> country?.name ?: "Ciudades"
        "courses" -> city?.name ?: "Courses"
        else -> "Golf courses"
    }
    val back: () -> Unit = {
        when (level) {
            "courses" -> level = "cities"
            "cities" -> level = "countries"
            else -> onBack()
        }
    }

    Scaffold(containerColor = Scheme.background, topBar = { bar(title, back) }) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (level == "countries") {
                Hint("Browse and download courses (anonymous Huawei data). They are saved as .bin in 'golf/', ready to send to the watch from UltimateGadget.")
                FolderCard(folderName(context, folder), onPick)
            }
            if (loading) CircularProgressIndicator(Modifier.padding(4.dp))
            StatusLine(status, error)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (level) {
                    "countries" -> items(countries, key = { it.id }) { c ->
                        RowCard("${c.name} (${c.code})") {
                            country = c; cities = emptyList(); level = "cities"
                            load {
                                val list = withContext(Dispatchers.IO) { GolfApi.cities(c.id, country = c.code) }
                                cities = list; status = "${list.size} ciudades"
                            }
                        }
                    }
                    "cities" -> items(cities, key = { it.id }) { ci ->
                        RowCard(if (ci.province.isBlank()) ci.name else "${ci.name} · ${ci.province}") {
                            city = ci; courses = emptyList(); level = "courses"
                            val cc = country?.code ?: "ES"
                            load {
                                val list = withContext(Dispatchers.IO) { GolfApi.courses(ci.id, country = cc) }
                                courses = list; status = "${list.size} campos"
                            }
                        }
                    }
                    else -> items(courses, key = { it.id }) { co ->
                        ItemCard(co.name, "${co.totalLength} m · v${co.version} · id ${co.id}") {
                            OutlinedButton(
                                enabled = folder != null && !loading,
                                onClick = {
                                    val countryName = country?.name ?: ""
                                    load {
                                        val saved = withContext(Dispatchers.IO) {
                                            val map = GolfApi.courseMap(co.id, country = country?.code ?: "ES")
                                                ?: throw RuntimeException("no map data")
                                            val (_, bytes) = GolfApi.downloadMapBin(map.url)
                                            val fname = golfFileName(countryName, co.name, co.id, co.version)
                                            writeInto(context, folder!!, "golf", fname, bytes)
                                            fname
                                        }
                                        status = "Guardado: $saved"
                                    }
                                },
                            ) { Text(if (folder == null) "Choose folder" else "Download") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Human-friendly golf map file name: "<Country> - <Course> (<id>) v<version>.bin". The id and
 * version let UltimateGadget recover and send the right course to the watch.
 */
private fun golfFileName(country: String, name: String, id: Long, version: String): String {
    fun clean(s: String) = s.trim().replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim()
    val base = listOf(clean(country), clean(name)).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "golf_$id" }
    val ver = version.filter { it.isDigit() }
    return if (ver.isNotEmpty()) "$base ($id) v$ver.bin" else "$base ($id).bin"
}

@Composable
private fun RowCard(text: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Scheme.surface),
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Scheme.onSurface, modifier = Modifier.weight(1f))
            Text("›", color = Scheme.onSurfaceVariant)
        }
    }
}

// ----- Exercise pack -----

private const val EXERCISE_PACK_URL =
    "https://github.com/qtekfun/ultimate-workouts-data/releases/latest/download/pack-wger.zip"
private const val EXERCISE_PACK_FILE = "ultimate-exercises-pack.zip"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExercisesScreen(folder: Uri?, onPick: () -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Scaffold(containerColor = Scheme.background, topBar = { bar("Exercise pack", onBack) }) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint(
                "Download the exercise catalog (names, steps and animations in English and Spanish), " +
                    "then import it in UltimateGadget: Workouts → menu → Import exercise pack.",
            )
            FolderCard(folderName(context, folder), onPick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy && folder != null,
                    onClick = {
                        val f = folder ?: return@Button
                        busy = true; status = "Downloading…"; error = false
                        scope.launch {
                            val pack = MapRegion(
                                id = "exercises", name = "Exercise pack", parent = "",
                                url = EXERCISE_PACK_URL, size = 0, sha256 = "", file = EXERCISE_PACK_FILE,
                            )
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    Downloader.download(context, pack, f) { done, _ ->
                                        status = "Downloading… ${fmt(done)}"
                                    }
                                }
                            }
                                .onSuccess { status = "Saved $EXERCISE_PACK_FILE in the destination folder" }
                                .onFailure { status = it.message ?: "Error"; error = true }
                            busy = false
                        }
                    },
                ) { Text("Download pack") }
                if (busy) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            }
            StatusLine(status, error)
        }
    }
}

// ----- Auth key (Amazfit/Zepp) -----

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
private fun AuthKeyScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // Amazfit / Zepp (Huami servers)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(HuamiRegion.GLOBAL) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<HuamiDevice>>(emptyList()) }
    // Xiaomi (Mi Fitness servers)
    var xEmail by remember { mutableStateOf("") }
    var xPassword by remember { mutableStateOf("") }
    var xLoading by remember { mutableStateOf(false) }
    var xStatus by remember { mutableStateOf("") }
    var xError by remember { mutableStateOf(false) }
    var xDevices by remember { mutableStateOf<List<XiaomiDevice>>(emptyList()) }

    fun copy(key: String, set: (String) -> Unit) {
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(key))
        set("Key copied")
    }

    Scaffold(containerColor = Scheme.background, topBar = { bar("Auth key", onBack) }) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint(
                "Get the Bluetooth key of your watch/band from the vendor's servers, to pair it " +
                    "in UltimateGadget without the official app (long-press the device during discovery → Auth key). " +
                    "The email and password are used only for the login (HTTPS) and not stored.",
            )

            // --- Amazfit / Zepp ---
            SectionLabel("AMAZFIT / ZEPP (Y MI BAND VÍA ZEPP)")
            androidx.compose.material3.OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Zepp/Amazfit account email") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("Account region (if no devices appear, try another):")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HuamiRegion.entries.forEach { r ->
                    androidx.compose.material3.FilterChip(selected = region == r, onClick = { region = r }, label = { Text(r.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        loading = true; status = ""; error = false; devices = emptyList()
                        val e = email.trim(); val p = password; val reg = region
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { HuamiToken.fetchAuthKeys(e, p, reg) } }
                                .onSuccess { devices = it; status = "${it.size} dispositivo(s) encontrados" }
                                .onFailure { status = it.message ?: "Error"; error = true }
                            loading = false
                        }
                    },
                ) { Text("Get keys (Zepp)") }
                if (loading) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            }
            StatusLine(status, error)
            devices.forEach { d ->
                ItemCard(d.mac, d.pasteKey) {
                    OutlinedButton(onClick = { copy(d.pasteKey) { status = it; error = false } }) { Text("Copiar") }
                }
            }

            // --- Xiaomi (Mi Fitness) ---
            SectionLabel("XIAOMI (MI FITNESS: MI BAND 8/9, RELOJES XIAOMI)")
            Hint("For devices you paired with the Mi Fitness / Xiaomi Wear app. 2FA and captcha are not supported.")
            androidx.compose.material3.OutlinedTextField(
                value = xEmail, onValueChange = { xEmail = it },
                label = { Text("Xiaomi account email") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.OutlinedTextField(
                value = xPassword, onValueChange = { xPassword = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !xLoading && xEmail.isNotBlank() && xPassword.isNotBlank(),
                    onClick = {
                        xLoading = true; xStatus = ""; xError = false; xDevices = emptyList()
                        val e = xEmail.trim(); val p = xPassword
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { XiaomiToken.fetchAuthKeys(e, p) } }
                                .onSuccess { xDevices = it; xStatus = "${it.size} dispositivo(s) encontrados" }
                                .onFailure { xStatus = it.message ?: "Error"; xError = true }
                            xLoading = false
                        }
                    },
                ) { Text("Get keys (Xiaomi)") }
                if (xLoading) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            }
            StatusLine(xStatus, xError)
            xDevices.forEach { d ->
                ItemCard(if (d.name.isBlank()) d.mac else "${d.name} · ${d.mac}", d.pasteKey) {
                    OutlinedButton(onClick = { copy(d.pasteKey) { xStatus = it; xError = false } }) { Text("Copiar") }
                }
            }
        }
    }
}
