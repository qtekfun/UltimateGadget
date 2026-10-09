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
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploader
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.ActivitySummaryUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Locale

/**
 * Export a single workout to GPX or FIT and hand it off, either through the Android share sheet
 * (ACTION_SEND) or by copying it into a user-chosen document via SAF.
 *
 * Everything is 100% local: the file is built with Gadgetbridge's own exporters
 * ([ActivitySummaryUtils.getShareableGpxFile] for GPX, [WorkoutExport] delegating to
 * `WorkoutUploader.buildFitFile` for FIT) and shared through the app's own FileProvider. No
 * network, no third-party service.
 *
 * The exporters write numbers and coordinates with [Locale.ROOT], so a device locale such as
 * es_ES never leaks decimal commas into the GPX/FIT.
 */
object WorkoutExport {
    private val LOG = LoggerFactory.getLogger(WorkoutExport::class.java)

    /** FileProvider authority declared in the manifest; serves cache/gpx, cache/raw and filesDir. */
    private fun authority(context: Context) =
        context.applicationContext.packageName + ".screenshot_provider"

    enum class Format(val extension: String, val mimeType: String) {
        /** GPS track as GPX 1.1 (route + per-point HR/cadence/power when recorded). */
        GPX("gpx", "application/gpx+xml"),

        /**
         * Garmin FIT. Native .fit is copied verbatim for FIT devices; for the rest it is
         * synthesized from the summary and track. No external libraries: reuses Gadgetbridge's
         * bundled FitExporter.
         */
        FIT("fit", "application/octet-stream"),
    }

    /**
     * The GBDevice that owns [summary], matched by address to the paired devices, falling back to
     * the active device. Needed because the GPX/FIT exporters resolve the track through the
     * device's coordinator.
     */
    fun resolveDevice(context: Context, summary: BaseActivitySummary): GBDevice? {
        val address = runCatching { summary.device?.identifier }.getOrNull()
        val dm = (context.applicationContext as GBApplication).deviceManager
        return dm.devices.firstOrNull { address != null && it.address == address }
            ?: dm.devices.firstOrNull { it.isInitialized }
            ?: dm.devices.firstOrNull()
    }

    /**
     * Builds the export file for [summary] in the app cache and returns it, or null when the track
     * could not be produced (no device, empty track, I/O error). Blocking: call off the main
     * thread. [device] is resolved from the summary when not supplied.
     */
    fun buildFile(
        context: Context,
        summary: BaseActivitySummary,
        format: Format,
        device: GBDevice? = null,
    ): File? {
        val gbDevice = device ?: resolveDevice(context, summary)
        if (gbDevice == null) {
            LOG.warn("No device to export summary {}", summary.id)
            return null
        }
        return try {
            when (format) {
                Format.GPX -> {
                    val provider = gbDevice.deviceCoordinator.getActivityTrackProvider(gbDevice, context)
                    ActivitySummaryUtils.getShareableGpxFile(provider, summary)
                }
                Format.FIT -> WorkoutUploader.buildFitFile(context, gbDevice, summary)
            }
        } catch (e: Exception) {
            LOG.error("Failed to build {} export for summary {}", format, summary.id, e)
            null
        }
    }

    /** Suggested download name, e.g. {@code 2026-10-09T18.30.00-running.gpx}. */
    fun suggestedName(context: Context, summary: BaseActivitySummary, format: Format): String =
        ActivitySummaryUtils.getExportBaseName(context, summary) + "." + format.extension

    /**
     * Fires the Android share sheet (ACTION_SEND) for [file]. Safe to call from any thread and from
     * a non-activity context (adds NEW_TASK), so it also works straight from a notification action.
     */
    fun shareFile(context: Context, file: File, format: Format, chooserTitle: String? = null) {
        val uri: Uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.applicationContext.startActivity(chooser)
    }

    /**
     * Copies [file] into the document the user picked via SAF ([uri]). Blocking: call off the main
     * thread. Returns true on success.
     */
    fun writeToUri(context: Context, file: File, uri: Uri): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } != null
    } catch (e: Exception) {
        LOG.error("Failed to copy export to {}", uri, e)
        false
    }

    /**
     * Convenience: build the GPX for [summary] on a background thread and open the share sheet.
     * Fire-and-forget, callable from anywhere (e.g. a button handler). For a progress-aware flow
     * with a Save option, open [UltimateWorkoutExportActivity] instead.
     */
    fun shareGpx(context: Context, summary: BaseActivitySummary, device: GBDevice? = null) {
        val appContext = context.applicationContext
        Thread {
            val file = buildFile(appContext, summary, Format.GPX, device)
            if (file == null) {
                LOG.warn("shareGpx: no GPX produced for summary {}", summary.id)
                return@Thread
            }
            Handler(Looper.getMainLooper()).post {
                runCatching { shareFile(appContext, file, Format.GPX) }
                    .onFailure { LOG.error("shareGpx: failed to launch share sheet", it) }
            }
        }.start()
    }
}
