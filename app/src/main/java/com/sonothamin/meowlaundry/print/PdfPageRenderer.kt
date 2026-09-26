package com.sonothamin.meowlaundry.print

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.TextPaint
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LabelCustomization
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.PageSize
import com.sonothamin.meowlaundry.data.ServiceType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws a ticket as an actual text document on a standard paper size (A4/Letter/Legal), using
 * real `Canvas.drawText` calls onto a [PdfDocument] page - the text stays selectable and
 * searchable in the resulting PDF, unlike [LabelRenderer]'s thermal-printer output, which is a
 * fixed-width rasterized bitmap wrapped in a PDF page only when the system print dialog needs one.
 */
object PdfPageRenderer {

    private const val MARGIN = 54f // 0.75in
    private const val ROW_GAP = 8f

    private fun serviceLabel(type: ServiceType): String = when (type) {
        ServiceType.WASH -> "Wash"
        ServiceType.PRESS -> "Press"
        ServiceType.WASH_AND_PRESS -> "Wash & press"
        ServiceType.DRY_CLEAN -> "Dry clean"
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            if (bold) typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }

    /** Builds the whole (possibly multi-page) PDF document. Caller must close it after writing. */
    fun buildDocument(
        ticket: LaundryTicket,
        garments: List<ClothingItem>,
        customization: LabelCustomization,
    ): PdfDocument {
        val size = customization.pageSize
        val pageWidth = size.widthPt
        val pageHeight = size.heightPt
        val maxY = pageHeight - MARGIN

        val titlePaint = textPaint(24f, bold = true)
        val metaPaint = textPaint(11f, color = Color.rgb(90, 90, 90))
        val servicePaint = textPaint(16f, bold = true)
        val indexPaint = textPaint(13f, bold = true)
        val namePaint = textPaint(13f)
        val typePaint = textPaint(10f, color = Color.rgb(110, 110, 110))
        val footerPaint = textPaint(10f, color = Color.rgb(110, 110, 110))

        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

        val doc = PdfDocument()
        var pageIndex = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            doc.finishPage(page)
            pageIndex++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > maxY) newPage()
        }

        // Masthead
        canvas.drawText(customization.headerText.ifBlank { "MeowLaundry" }, MARGIN, y + 20f, titlePaint)
        y += 30f
        canvas.drawText("Ticket #${ticket.id} \u00b7 ${dateFormat.format(Date(ticket.sentAt))}", MARGIN, y, metaPaint)
        y += 20f
        canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1f })
        y += 26f

        // Service + provider
        canvas.drawText(serviceLabel(ticket.serviceType), MARGIN, y, servicePaint)
        ticket.providerName?.takeIf { it.isNotBlank() }?.let { provider ->
            val w = metaPaint.measureText(provider)
            canvas.drawText(provider, pageWidth - MARGIN - w, y, metaPaint)
        }
        y += 22f
        canvas.drawText(
            "${garments.size} garment${if (garments.size == 1) "" else "s"} \u00b7 check each one on return",
            MARGIN,
            y,
            footerPaint,
        )
        y += 18f
        canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1f })
        y += 26f

        val indexColumnWidth = 28f
        garments.forEachIndexed { index, garment ->
            val typeLine = if (customization.showGarmentType) {
                garment.type.name.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                null
            }
            val rowHeight = 18f + (if (typeLine != null) 14f else 0f) + ROW_GAP
            ensureSpace(rowHeight)

            canvas.drawText("${index + 1}.", MARGIN, y, indexPaint)
            canvas.drawText(garment.title, MARGIN + indexColumnWidth, y, namePaint)
            if (typeLine != null) {
                canvas.drawText(typeLine, MARGIN + indexColumnWidth, y + 14f, typePaint)
            }
            y += rowHeight
        }

        ensureSpace(30f)
        y += 8f
        canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1f })
        y += 20f
        canvas.drawText(customization.footerText.ifBlank { "Please keep this ticket until pickup" }, MARGIN, y, footerPaint)

        doc.finishPage(page)
        return doc
    }

    /** Writes [buildDocument]'s output to [file] and closes the document. */
    fun writeToFile(ticket: LaundryTicket, garments: List<ClothingItem>, customization: LabelCustomization, file: File) {
        val doc = buildDocument(ticket, garments, customization)
        try {
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /**
     * Rasterizes just the first page of the real PDF for on-screen preview (Compose can only
     * show a Bitmap) - the actual file written by [writeToFile] stays real vector text.
     */
    fun renderPreviewBitmap(
        ticket: LaundryTicket,
        garments: List<ClothingItem>,
        customization: LabelCustomization,
        targetWidthPx: Int = 720,
    ): Bitmap {
        val tempFile = File.createTempFile("ticket_preview", ".pdf")
        try {
            writeToFile(ticket, garments, customization, tempFile)
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val scale = targetWidthPx / page.width.toFloat()
                        val bitmap = Bitmap.createBitmap(
                            targetWidthPx,
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(Color.WHITE)
                        val matrix = Matrix().apply { setScale(scale, scale) }
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap
                    }
                }
            }
        } finally {
            tempFile.delete()
        }
    }
}
