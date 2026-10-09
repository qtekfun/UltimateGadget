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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.SectionLabelStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * "Exportar entreno" sheet: export one workout to GPX or FIT and either share it (ACTION_SEND) or
 * save it to a user-chosen file (SAF). 100% local — see [WorkoutExport].
 *
 * Opened with [intent]; it carries the workout's summary id and, optionally, the owning device.
 * Rendered as a dimmed bottom sheet so it reads as a modal over whatever opened it.
 */
class UltimateWorkoutExportActivity : AppCompatActivity() {

    private var summary: BaseActivitySummary? = null
    private var device: GBDevice? = null

    /** The file waiting to be written once the user has picked a SAF destination. */
    private var pendingSaveFile: File? = null

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
            val file = pendingSaveFile
            pendingSaveFile = null
            if (uri == null || file == null) {
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { WorkoutExport.writeToUri(this@UltimateWorkoutExportActivity, file, uri) }
                toast(if (ok) "Entreno guardado" else "No se pudo guardar el entreno")
                if (ok) finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val summaryId = intent.getLongExtra(EXTRA_SUMMARY_ID, -1L)
        device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        summary = runCatching {
            GBApplication.acquireDB().use { it.daoSession.baseActivitySummaryDao.load(summaryId) }
        }.getOrNull()

        val s = summary
        if (s == null) {
            toast("No se encontró el entreno")
            finish()
            return
        }

        val kind = s.activityKind?.let { k -> runCatching { ActivityKind.fromCode(k).getLabel(this) }.getOrNull() }
        val title = s.name?.takeIf { it.isNotBlank() } ?: kind ?: "Entreno"
        val subtitle = buildList {
            kind?.let { add(it) }
            s.startTime?.let { add(FMT.format(it)) }
        }.joinToString(" · ")

        setContent {
            UltimateTheme {
                var busy by remember { mutableStateOf(false) }
                ExportSheet(
                    title = title,
                    subtitle = subtitle,
                    busy = busy,
                    onScrim = { if (!busy) finish() },
                    onShare = { format ->
                        busy = true
                        lifecycleScope.launch {
                            val file = withContext(Dispatchers.IO) { WorkoutExport.buildFile(applicationContext, s, format, device) }
                            busy = false
                            if (file == null) {
                                toast("Este entreno no tiene datos para exportar")
                            } else {
                                WorkoutExport.shareFile(this@UltimateWorkoutExportActivity, file, format, "Compartir entreno")
                                finish()
                            }
                        }
                    },
                    onSave = { format ->
                        busy = true
                        lifecycleScope.launch {
                            val file = withContext(Dispatchers.IO) { WorkoutExport.buildFile(applicationContext, s, format, device) }
                            busy = false
                            if (file == null) {
                                toast("Este entreno no tiene datos para exportar")
                            } else {
                                pendingSaveFile = file
                                createDocument.launch(WorkoutExport.suggestedName(applicationContext, s, format))
                            }
                        }
                    },
                )
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_SUMMARY_ID = "ug_summary_id"

        private val FMT = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())

        /** [device] is optional; when absent the owning device is resolved from the summary. */
        fun intent(context: Context, summaryId: Long, device: GBDevice? = null): Intent =
            Intent(context, UltimateWorkoutExportActivity::class.java)
                .putExtra(EXTRA_SUMMARY_ID, summaryId)
                .apply { device?.let { putExtra(GBDevice.EXTRA_DEVICE, it) } }
    }
}

@Composable
private fun ExportSheet(
    title: String,
    subtitle: String,
    busy: Boolean,
    onScrim: () -> Unit,
    onShare: (WorkoutExport.Format) -> Unit,
    onSave: (WorkoutExport.Format) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = !busy, onClick = onScrim),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(palette.surfaceContainer)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Grab handle
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.outlineVariant),
            )

            Text("EXPORTAR ENTRENO", style = SectionLabelStyle, color = palette.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleLarge, color = palette.onSurface)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
            }

            FormatRow(WorkoutExport.Format.GPX, "GPX", "Ruta y sensores", busy, onShare, onSave)
            FormatRow(WorkoutExport.Format.FIT, "FIT", "Garmin / Endurain", busy, onShare, onSave)

            if (busy) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = palette.primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Preparando archivo…", style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FormatRow(
    format: WorkoutExport.Format,
    label: String,
    hint: String,
    busy: Boolean,
    onShare: (WorkoutExport.Format) -> Unit,
    onSave: (WorkoutExport.Format) -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        }
        ActionButton(Icons.Filled.Share, "Compartir $label", palette.primaryContainer, palette.onPrimaryContainer, busy) { onShare(format) }
        Spacer(Modifier.width(10.dp))
        ActionButton(Icons.Filled.SaveAlt, "Guardar $label", palette.secondaryContainer, palette.onSecondaryContainer, busy) { onSave(format) }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    container: Color,
    content: Color,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = content, modifier = Modifier.size(22.dp))
    }
}
