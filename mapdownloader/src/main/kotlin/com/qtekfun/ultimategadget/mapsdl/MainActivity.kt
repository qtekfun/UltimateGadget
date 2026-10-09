package com.qtekfun.ultimategadget.mapsdl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
        setContent { MaterialTheme(colorScheme = Scheme) { Screen() } }
    }
}

private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun savedFolder(c: Context): Uri? =
    prefs(c).getString(KEY_FOLDER, null)?.let { Uri.parse(it) }

private fun fmt(bytes: Long): String {
    if (bytes <= 0) return "—"
    val u = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return "%.1f %s".format(v, u[i])
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Screen() {
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
