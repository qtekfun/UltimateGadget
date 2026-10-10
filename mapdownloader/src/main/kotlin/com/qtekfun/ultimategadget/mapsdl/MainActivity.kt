package com.qtekfun.ultimategadget.mapsdl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
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

@Composable
private fun App() {
    var screen by remember { mutableStateOf("maps") }
    when (screen) {
        "authkey" -> AuthKeyScreen(onBack = { screen = "maps" })
        "golf" -> GolfScreen(onBack = { screen = "maps" })
        else -> Screen(onOpenAuthKey = { screen = "authkey" }, onOpenGolf = { screen = "golf" })
    }
}

private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun savedFolder(c: Context): Uri? =
    prefs(c).getString(KEY_FOLDER, null)?.let { Uri.parse(it) }

/**
 * Human-friendly file name for a golf course map: "<Country> - <Course> (<id>).bin".
 * The id is kept so UltimateGadget can recover the course when importing/sending to the watch.
 */
private fun golfFileName(country: String, name: String, id: Long, version: String): String {
    fun clean(s: String) = s.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val base = listOf(clean(country), clean(name))
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .ifBlank { "golf_$id" }
    // The id is needed to send it to the watch; the version lets UltimateGadget send the right one.
    val ver = version.filter { it.isDigit() }
    return if (ver.isNotEmpty()) "$base ($id) v$ver.bin" else "$base ($id).bin"
}

/** Save a golf course `.bin` into a `golf/` subfolder of the user-picked SAF tree. */
private fun writeGolfBin(context: Context, folderUri: Uri, name: String, bytes: ByteArray) {
    val root = DocumentFile.fromTreeUri(context, folderUri) ?: throw RuntimeException("carpeta no accesible")
    val dir = root.findFile("golf")?.takeIf { it.isDirectory } ?: root.createDirectory("golf")
        ?: throw RuntimeException("no se pudo crear la carpeta golf")
    dir.findFile(name)?.delete()
    val file = dir.createFile("application/octet-stream", name)
        ?: throw RuntimeException("no se pudo crear el fichero")
    context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
        ?: throw RuntimeException("no se pudo escribir")
}

private fun fmt(bytes: Long): String {
    if (bytes <= 0) return "—"
    val u = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return "%.1f %s".format(v, u[i])
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Screen(onOpenAuthKey: () -> Unit, onOpenGolf: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var folder by remember { mutableStateOf(savedFolder(context)) }
    var folderLabel by remember { mutableStateOf(folder?.let { DocumentFile.fromTreeUri(context, it)?.name } ?: "") }
    var regions by remember { mutableStateOf<List<MapRegion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val progress = remember { mutableStateMapOf<String, Float>() }
    var installed by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    fun refreshInstalled() {
        val f = folder ?: run { installed = emptyList(); return }
        installed = DocumentFile.fromTreeUri(context, f)
            ?.listFiles()?.filter { it.isFile && (it.name ?: "").endsWith(".pmtiles") }
            ?.sortedBy { it.name } ?: emptyList()
    }

    val pickFolder = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            prefs(context).edit().putString(KEY_FOLDER, uri.toString()).apply()
            folder = uri
            folderLabel = DocumentFile.fromTreeUri(context, uri)?.name ?: ""
            refreshInstalled()
        }
    }

    androidx.compose.runtime.LaunchedEffect(folder) { refreshInstalled() }

    Scaffold(
        containerColor = Scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Descargar mapas", fontWeight = FontWeight.Bold) },
                actions = {
                    androidx.compose.material3.TextButton(onClick = onOpenGolf) { Text("Golf") }
                    androidx.compose.material3.TextButton(onClick = onOpenAuthKey) { Text("Auth key") }
                    androidx.compose.material3.TextButton(onClick = {
                        (context as? android.app.Activity)?.finishAndRemoveTask()
                    }) { Text("Salir") }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Scheme.background, titleContentColor = Scheme.onBackground,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Descarga mapas base (.pmtiles) a una carpeta y luego impórtalos en UltimateGadget.",
                style = MaterialTheme.typography.bodyMedium, color = Scheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickFolder.launch(null) }) { Text("Elegir carpeta") }
                Text(
                    if (folderLabel.isNotBlank()) "Carpeta: $folderLabel" else "Sin carpeta elegida",
                    style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    loading = true; status = ""
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { Downloader.fetchCatalog() } }
                            .onSuccess { regions = it; status = "${it.size} regiones disponibles" }
                            .onFailure { status = "Error al cargar el catálogo: ${it.message}" }
                        loading = false
                    }
                }) { Text("Cargar catálogo") }
                if (loading) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant)

            if (installed.isNotEmpty()) {
                Text("INSTALADOS", fontSize = 11.sp, letterSpacing = 1.sp, color = Scheme.onSurfaceVariant)
                installed.forEach { f ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name ?: "", color = Scheme.onSurface)
                                Text(fmt(f.length()), style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { f.delete(); refreshInstalled() }) { Text("Borrar") }
                        }
                    }
                }
            }

            Text("DISPONIBLES", fontSize = 11.sp, letterSpacing = 1.sp, color = Scheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(regions, key = { it.id }) { r ->
                    val p = progress[r.id]
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, color = Scheme.onSurface)
                                Text(fmt(r.size), style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant)
                                if (p != null) Text("Descargando… ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Scheme.secondary)
                            }
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
                                        }.onSuccess { status = "${r.name} descargado" }
                                            .onFailure { status = "${r.name}: ${it.message}" }
                                        progress.remove(r.id)
                                        refreshInstalled()
                                    }
                                },
                            ) { Text(if (folder == null) "Elige carpeta" else "Descargar") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
private fun AuthKeyScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(HuamiRegion.GLOBAL) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<HuamiDevice>>(emptyList()) }

    Scaffold(
        containerColor = Scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Auth key (Amazfit/Zepp)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.TextButton(onClick = onBack) { Text("Atrás") }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Scheme.background, titleContentColor = Scheme.onBackground,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Obtén la clave Bluetooth de tus relojes Amazfit/Zepp desde los servidores de Huami, " +
                    "para emparejarlos en UltimateGadget sin la app Zepp. Luego pega la clave en " +
                    "UltimateGadget (mantén pulsado el dispositivo al descubrir → Auth key).",
                style = MaterialTheme.typography.bodyMedium, color = Scheme.onSurfaceVariant,
            )
            Text(
                "Tu email y contraseña se usan solo para el login contra Huami/Zepp (HTTPS) y no se " +
                    "guardan en ningún sitio. Las cuentas de regiones fuera de EE. UU. pueden fallar.",
                style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant,
            )

            androidx.compose.material3.OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email de la cuenta Zepp/Amazfit") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Región de la cuenta (si no aparecen dispositivos, prueba otra):",
                style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HuamiRegion.entries.forEach { r ->
                    androidx.compose.material3.FilterChip(
                        selected = region == r,
                        onClick = { region = r },
                        label = { Text(r.label) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        loading = true; status = ""; devices = emptyList()
                        val e = email.trim(); val p = password; val reg = region
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { HuamiToken.fetchAuthKeys(e, p, reg) } }
                                .onSuccess { devices = it; status = "${it.size} dispositivo(s) encontrados" }
                                .onFailure { status = it.message ?: "Error" }
                            loading = false
                        }
                    },
                ) { Text("Obtener claves") }
                if (loading) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant)

            devices.forEach { d ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.mac, color = Scheme.onSurface)
                            Text(d.pasteKey, style = MaterialTheme.typography.bodySmall, color = Scheme.secondary)
                        }
                        OutlinedButton(onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(d.pasteKey))
                            status = "Clave copiada"
                        }) { Text("Copiar") }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GolfScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val folder = savedFolder(context)

    // Simple country -> city -> course drill-down.
    var level by remember { mutableStateOf("countries") }
    var countries by remember { mutableStateOf<List<GolfApi.Country>>(emptyList()) }
    var cities by remember { mutableStateOf<List<GolfApi.City>>(emptyList()) }
    var courses by remember { mutableStateOf<List<GolfApi.Course>>(emptyList()) }
    var country by remember { mutableStateOf<GolfApi.Country?>(null) }
    var city by remember { mutableStateOf<GolfApi.City?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun load(block: suspend () -> Unit) {
        loading = true; status = ""
        scope.launch {
            runCatching { block() }.onFailure { status = it.message ?: "Error" }
            loading = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (countries.isEmpty()) {
            loading = true
            runCatching { withContext(Dispatchers.IO) { GolfApi.countries() } }
                .onSuccess { countries = it }.onFailure { status = it.message ?: "Error" }
            loading = false
        }
    }

    val title = when (level) {
        "cities" -> country?.name ?: "Ciudades"
        "courses" -> city?.name ?: "Campos"
        else -> "Golf — países"
    }
    val back: () -> Unit = {
        when (level) {
            "courses" -> { level = "cities" }
            "cities" -> { level = "countries" }
            else -> onBack()
        }
    }

    Scaffold(
        containerColor = Scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.TextButton(onClick = back) { Text("Atrás") }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Scheme.background, titleContentColor = Scheme.onBackground,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Explora y descarga campos de golf (datos anónimos de Huawei). El mapa se guarda como " +
                    ".bin en la subcarpeta 'golf' de la carpeta elegida, listo para importar en UltimateGadget. " +
                    "Elige la carpeta en la pantalla de mapas si aún no lo has hecho.",
                style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant,
            )
            if (loading) CircularProgressIndicator(Modifier.padding(4.dp))
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = Scheme.error)

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
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(co.name, color = Scheme.onSurface)
                                    Text(
                                        "${co.totalLength} m · v${co.version} · id ${co.id}",
                                        style = MaterialTheme.typography.bodySmall, color = Scheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    enabled = folder != null && !loading,
                                    onClick = {
                                        val countryName = country?.name ?: ""
                                        load {
                                            val saved = withContext(Dispatchers.IO) {
                                                val map = GolfApi.courseMap(co.id, country = country?.code ?: "ES")
                                                    ?: throw RuntimeException("sin datos de mapa")
                                                val (_, bytes) = GolfApi.downloadMapBin(map.url)
                                                val fname = golfFileName(countryName, co.name, co.id, co.version)
                                                writeGolfBin(context, folder!!, fname, bytes)
                                                fname
                                            }
                                            status = "Guardado: $saved"
                                        }
                                    },
                                ) { Text(if (folder == null) "Elige carpeta" else "Descargar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowCard(text: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Scheme.onSurface, modifier = Modifier.weight(1f))
            Text("›", color = Scheme.onSurfaceVariant)
        }
    }
}
