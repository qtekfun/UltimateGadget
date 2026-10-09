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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.reports

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a [ReportSummary] to a one-page PDF with the platform [PdfDocument] — no third-party
 * libraries — and shares it through the app's own FileProvider or writes it to a SAF document.
 *
 * Everything is 100% local: the PDF is drawn here from the already-loaded report data and handed to
 * the system share sheet; no network, no third-party service. All numbers are formatted with
 * [Locale.US] so a device locale such as es_ES never leaks decimal commas into the document.
 *
 * The visual language mirrors the Reports screen (UltimateGadget dark palette accents) but on a
 * light page for printing: a titled header, a metrics table (steps / distance / sleep / resting HR
 * / workouts) and a simple bar chart reproducing the per-bucket steps chart of the report.
 */
object ReportPdfExporter {

    /** FileProvider authority declared in the manifest; serves cache/pdf among others. */
    private fun authority(context: Context) =
        context.applicationContext.packageName + ".screenshot_provider"

    // Page geometry — A4 at 72dpi, in points.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 42f

    // Palette (UltimatePalette accents, print-friendly on a white page).
    private val INK = 0xFF14171D.toInt()        // near-black text
    private val MUTED = 0xFF6B7280.toInt()       // secondary text
    private val ACCENT = 0xFF1F4A8F.toInt()      // primaryContainer blue (bars)
    private val HIGHLIGHT = 0xFF1B8A74.toInt()   // teal (goal-met bars)
    private val TRACK = 0xFFE3E6EC.toInt()       // empty bar / separators
    private val HAIRLINE = 0xFFD5D9E2.toInt()

    /**
     * Builds the report PDF in {@code cacheDir/pdf} and returns the file. Blocking but cheap (one
     * page). Call it on any thread; it does no UI work.
     */
    fun export(context: Context, summary: ReportSummary): File {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, suggestedName(summary))
        file.outputStream().use { writeTo(it, summary) }
        return file
    }

    /** Renders the report PDF straight into [out] (used for SAF). Closes nothing; caller owns [out]. */
    fun writeTo(out: OutputStream, summary: ReportSummary) {
        val doc = render(summary)
        try {
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Suggested download name, e.g. {@code ultimategadget-informe-semanal-2026-10-09.pdf}. */
    fun suggestedName(summary: ReportSummary): String {
        val kind = if (summary.period == ReportPeriod.WEEK) "semanal" else "mensual"
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "ultimategadget-informe-$kind-$day.pdf"
    }

    /**
     * Fires the Android share sheet (ACTION_SEND, application/pdf) for [file]. Safe from any
     * context; adds NEW_TASK so it also works from a non-activity caller.
     */
    fun shareFile(context: Context, file: File, chooserTitle: String? = "Compartir informe") {
        val uri: Uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.applicationContext.startActivity(chooser)
    }

    /** Convenience: build the PDF and immediately open the share sheet. Blocking; see [export]. */
    fun exportAndShare(context: Context, summary: ReportSummary) {
        shareFile(context, export(context, summary))
    }

    // --- drawing ---

    private fun render(summary: ReportSummary): PdfDocument {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        val c = page.canvas

        c.drawColor(Color.WHITE)

        val left = MARGIN
        val right = PAGE_W - MARGIN
        var y = MARGIN + 6f

        val brand = paint(ACCENT, 11f, bold = true, letterSpacing = 0.08f)
        c.drawText("ULTIMATEGADGET", left, y, brand)
        y += 28f

        val title = paint(INK, 24f, bold = true)
        val heading = if (summary.period == ReportPeriod.WEEK) "Informe semanal" else "Informe mensual"
        c.drawText(heading, left, y, title)
        y += 18f

        val sub = paint(MUTED, 10.5f)
        val generated = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        c.drawText("Generado el $generated · ${summary.daysWithData} días con datos", left, y, sub)
        y += 16f

        hairline(c, left, right, y)
        y += 26f

        // Metrics table.
        y = drawTable(c, left, right, y, buildRows(summary))
        y += 18f

        // Steps bar chart.
        if (summary.points.isNotEmpty()) {
            val label = paint(ACCENT, 12f, bold = true, letterSpacing = 0.04f)
            val chartHeading = if (summary.period == ReportPeriod.WEEK)
                "Pasos por día" else "Pasos (media diaria por semana)"
            c.drawText(chartHeading.uppercase(Locale.US), left, y, label)
            y += 16f
            drawStepsChart(c, left, right, y, summary)
        }

        // Footer.
        val footer = paint(MUTED, 9f)
        val fy = PAGE_H - MARGIN + 8f
        hairline(c, left, right, fy - 16f)
        c.drawText("© 2026 UltimateGadget contributors · AGPL-3.0 · Generado localmente", left, fy, footer)

        doc.finishPage(page)
        return doc
    }

    private fun buildRows(s: ReportSummary): List<Pair<String, String>> {
        val periodLabel = if (s.period == ReportPeriod.WEEK) "Semana" else "Mes"
        val distance = String.format(Locale.US, "%.1f km", s.totalDistanceKm)
        val sleep = if (s.avgSleepMinutes > 0)
            String.format(Locale.US, "%dh %02dm", s.avgSleepMinutes / 60, s.avgSleepMinutes % 60)
        else "—"
        val rhr = if (s.avgRestingHr > 0) "${s.avgRestingHr} bpm" else "—"
        return listOf(
            "Periodo" to periodLabel,
            "Pasos (media / día)" to String.format(Locale.US, "%,d", s.avgSteps),
            "Pasos (total)" to String.format(Locale.US, "%,d", s.totalSteps),
            "Distancia total" to distance,
            "Sueño medio" to sleep,
            "FC en reposo (media)" to rhr,
            "Entrenos" to s.workoutCount.toString(),
            "Días con meta" to "${s.goalDays} / ${s.daysWithData}",
        )
    }

    private fun drawTable(c: Canvas, left: Float, right: Float, top: Float, rows: List<Pair<String, String>>): Float {
        val rowH = 26f
        val labelPaint = paint(MUTED, 12f)
        val valuePaint = paint(INK, 13f, bold = true)
        valuePaint.textAlign = Paint.Align.RIGHT
        var y = top
        rows.forEach { (label, value) ->
            val baseline = y + rowH * 0.66f
            c.drawText(label, left + 2f, baseline, labelPaint)
            c.drawText(value, right - 2f, baseline, valuePaint)
            hairline(c, left, right, y + rowH)
            y += rowH
        }
        return y
    }

    private fun drawStepsChart(c: Canvas, left: Float, right: Float, top: Float, s: ReportSummary) {
        val chartH = 150f
        val labelH = 16f
        val plotTop = top
        val plotBottom = top + chartH
        val width = right - left
        val n = s.points.size
        val maxV = s.points.maxOf { it.steps }.coerceAtLeast(1)

        val gap = width * (if (n > 12) 0.015f else 0.05f)
        val barW = (width - gap * (n - 1)) / n

        val barPaint = paint(ACCENT, 0f)
        val highlightPaint = paint(HIGHLIGHT, 0f)
        val trackPaint = paint(TRACK, 0f)
        val axisPaint = paint(MUTED, 8f)
        axisPaint.textAlign = Paint.Align.CENTER

        s.points.forEachIndexed { i, p ->
            val x = left + i * (barW + gap)
            val norm = p.steps.toFloat() / maxV
            val h = if (p.steps <= 0) 0f else (chartH * norm).coerceAtLeast(2f)
            val barTop = plotBottom - h
            val r = barW / 3f
            if (p.steps <= 0) {
                c.drawRoundRect(RectF(x, plotBottom - 2f, x + barW, plotBottom), r, r, trackPaint)
            } else {
                val pnt = if (p.goalMet) highlightPaint else barPaint
                c.drawRoundRect(RectF(x, barTop, x + barW, plotBottom), r, r, pnt)
            }
            c.drawText(p.label, x + barW / 2f, plotBottom + labelH - 4f, axisPaint)
        }
    }

    private fun hairline(c: Canvas, left: Float, right: Float, y: Float) {
        val p = paint(HAIRLINE, 0f)
        p.strokeWidth = 1f
        c.drawLine(left, y, right, y, p)
    }

    private fun paint(color: Int, textSize: Float, bold: Boolean = false, letterSpacing: Float = 0f): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.letterSpacing = letterSpacing
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
}
