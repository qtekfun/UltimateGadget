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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/**
 * Fetches the Bluetooth auth key of Amazfit/Zepp watches from the user's own account (huami-token
 * method), so they can be paired without the Zepp app. Opt-in, user-initiated.
 *
 * The email and password live only in this screen's in-memory state: no saved instance state,
 * no preferences, no logs. FLAG_SECURE keeps the screen out of screenshots and recents.
 */
class UltimateHuamiKeyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { UltimateTheme { KeyScreen(onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val scope = rememberCoroutineScope()
    // Plain remember (not rememberSaveable): credentials must never reach a saved-state Bundle.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var keys by remember { mutableStateOf<List<HuamiKey>?>(null) }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Clave Bluetooth Amazfit / Zepp") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
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
            Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Usa tu propia cuenta Zepp/Amazfit. El correo y la contraseña solo se envían por HTTPS a Huami/Zepp " +
                    "para iniciar sesión y no se guardan en ningún sitio.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurfaceVariant,
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Correo") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Contraseña") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InAppNetworkImpl.huamiRegions.forEachIndexed { i, label ->
                    FilterChip(selected = region == i, onClick = { region = i }, label = { Text(label) })
                }
            }
            Button(
                enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true; error = null; keys = null
                    val e = email.trim(); val p = password; val r = region
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { InAppNetworkImpl.fetchHuamiKeys(e, p, r) } }
                            .onSuccess { keys = it }
                            .onFailure { error = it.message ?: "Error de red" }
                        // Drop the password as soon as the call is over.
                        password = ""
                        busy = false
                    }
                },
            ) { Text(if (busy) "Consultando…" else "Obtener claves") }
            error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
            keys?.let { list ->
                if (list.isEmpty()) {
                    Text("La cuenta no tiene dispositivos con clave.", color = palette.onSurfaceVariant)
                }
                list.forEach { k ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(k.mac, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
                            Text(k.authKey, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
                            TextButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("auth key", k.authKey))
                                Toast.makeText(context, "Clave copiada", Toast.LENGTH_SHORT).show()
                            }) { Text("Copiar clave") }
                        }
                    }
                }
            }
        }
    }
}
