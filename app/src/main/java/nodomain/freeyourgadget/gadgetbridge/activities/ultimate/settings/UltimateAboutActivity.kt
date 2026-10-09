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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.activities.licenses.LicensesActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

private const val REPO_URL = "https://github.com/qtekfun/UltimateGadget"
private const val LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.html"
private const val GADGETBRIDGE_URL = "https://gadgetbridge.org"

/** Ultimate-styled "About" screen: version, source repo, license and credits. No network required. */
class UltimateAboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltimateTheme { UltimateAboutScreen(onBack = { finish() }) } }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, UltimateAboutActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltimateAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val scroll = rememberScrollState()

    val version = BuildConfig.VERSION_NAME
    val hash = BuildConfig.GIT_HASH_SHORT + BuildConfig.GIT_DIRTY_STATUS

    fun openUrl(url: String) =
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    fun copyBuildDetails() {
        val details = "Version: ${BuildConfig.VERSION_NAME}" +
            "\nCommit: $hash" +
            "\nFlavor: ${BuildConfig.FLAVOR}"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Build data", details))
        Toast.makeText(context, "Detalles de compilación copiados", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Acerca de") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    "UltimateGadget",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface,
                )
                Text(
                    "Versión $version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
                Text(
                    "Commit $hash · ${BuildConfig.FLAVOR}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier.clickable { copyBuildDetails() },
                )
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
            ) {
                Text(
                    "UltimateGadget es un fork de Gadgetbridge centrado en los relojes Huawei " +
                        "(golf, mapas y A-GNSS) sin Huawei Health ni HMS en el móvil. " +
                        "Software libre bajo licencia GNU AGPL-3.0.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    AboutRow(Icons.Filled.Code, "Código fuente", REPO_URL) { openUrl(REPO_URL) }
                    AboutRow(Icons.Filled.Description, "Licencia AGPL-3.0", LICENSE_URL) { openUrl(LICENSE_URL) }
                    AboutRow(Icons.Filled.Info, "Basado en Gadgetbridge", GADGETBRIDGE_URL) { openUrl(GADGETBRIDGE_URL) }
                    AboutRow(Icons.Filled.Description, "Licencias de terceros", null) {
                        context.startActivity(Intent(context, LicensesActivity::class.java))
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = palette.secondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }
        }
    }
}
