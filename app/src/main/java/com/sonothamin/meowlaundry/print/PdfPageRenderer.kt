package com.sonothamin.meowlaundry.print

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.TextPaint
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LabelCustomization
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.ServiceType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws laundry tickets as real text documents on a standard paper size (A4/Letter/Legal), using
 * actual `Canvas.drawText` calls onto [PdfDocument] pages - the text stays selectable and
 * searchable in the resulting PDF, unlike [LabelRenderer]'s thermal-printer output, which is a
 * fixed-width rasterized bitmap.
 *
 * A multi-ticket document (see [buildMultiDocument]) always starts each ticket on a fresh page -
 * one order per page (or more, if it doesn't fit) - rather than packing several short tickets
 * onto one sheet, so a stack of printed pages can be torn apart by order without re-sorting them.
 */
object PdfPageRenderer {

    private const val MARGIN = 40f
    private const val BAND_HEIGHT = 60f
    private const val STAT_BOX_HEIGHT = 46f
    private const val ROW_HEIGHT = 26f
    private const val FOOTER_RESERVE = 46f // divider + footer line + page number, reserved on every page

    private val ACCENT = Color.rgb(103, 80, 164) // matches the launcher icon's purple
    private val ACCENT_LIGHT = Color.rgb(232, 226, 246)
    private val ROW_SHADE = Color.rgb(248, 248, 251)
    private val HAIRLINE = Color.rgb(210, 210, 216)
    private val MUTED = Color.rgb(110, 110, 118)

    private fun serviceLabel(type: ServiceType): String = when (type) {
        ServiceType.WASH -> "Wash"
        ServiceType.PRESS -> "Press"
        ServiceType.WASH_AND_PRESS -> "Wash & press"
        ServiceType.DRY_CLEAN -> "Dry clean"
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            textAlign = align
            if (bold) typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

    private fun linePaint(color: Int = HAIRLINE, width: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = width
        style = Paint.Style.STROKE
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    /** One ticket's garments, split into the chunks that will fit on each of its pages. */
    private fun paginate(garmentCount: Int, firstPageAvailable: Float, laterPageAvailable: Float): List<Int> {
        if (garmentCount == 0) return listOf(0)
        val chunks = mutableListOf<Int>()
        var remaining = garmentCount
        var available = firstPageAvailable
        while (remaining > 0) {
            val fit = maxOf(1, (available / ROW_HEIGHT).toInt())
            val take = minOf(fit, remaining)
            chunks.add(take)
            remaining -= take
            available = laterPageAvailable
        }
        return chunks
    }

    /**
     * Builds a document with one page break per ticket: each entry in [tickets] always starts on
     * a new page (continuing onto further pages only if its own garment list doesn't fit).
     */
    fun buildMultiDocument(
        tickets: List<Pair<LaundryTicket, List<ClothingItem>>>,
        customization: LabelCustomization,
    ): PdfDocument {
        val pageWidth = customization.pageSize.widthPt
        val pageHeight = customization.pageSize.heightPt
        val contentWidth = pageWidth - 2 * MARGIN
        val firstPageAvailable = pageHeight - MARGIN - BAND_HEIGHT - 24f - STAT_BOX_HEIGHT - 20f - 22f - FOOTER_RESERVE
        val laterPageAvailable = pageHeight - MARGIN - 40f - FOOTER_RESERVE

        // Pre-paginate every ticket so page numbers ("Page X of Y") are known before drawing.
        data class TicketPlan(val ticket: LaundryTicket, val garments: List<ClothingItem>, val chunkSizes: List<Int>)
        val plans = tickets.map { (ticket, garments) ->
            TicketPlan(ticket, garments, paginate(garments.size, firstPageAvailable, laterPageAvailable))
        }
        val totalPages = plans.sumOf { it.chunkSizes.size }

        val doc = PdfDocument()
        var globalPage = 0

        plans.forEach { plan ->
            var offset = 0
            plan.chunkSizes.forEachIndexed { chunkIndex, count ->
                globalPage++
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, globalPage).create()
                val page = doc.startPage(info)
                drawTicketPage(
                    canvas = page.canvas,
                    pageWidth = pageWidth.toFloat(),
                    pageHeight = pageHeight.toFloat(),
                    contentWidth = contentWidth.toFloat(),
                    ticket = plan.ticket,
                    allGarmentsCount = plan.garments.size,
                    pageGarments = plan.garments.subList(offset, offset + count),
                    startIndex = offset,
                    isFirstPageOfTicket = chunkIndex == 0,
                    isLastPageOfTicket = chunkIndex == plan.chunkSizes.lastIndex,
                    customization = customization,
                    globalPageNumber = globalPage,
                    totalPages = totalPages,
                )
                offset += count
                doc.finishPage(page)
            }
        }
        return doc
    }

    fun buildDocument(ticket: LaundryTicket, garments: List<ClothingItem>, customization: LabelCustomization): PdfDocument =
        buildMultiDocument(listOf(ticket to garments), customization)

    private fun drawTicketPage(
        canvas: android.graphics.Canvas,
        pageWidth: Float,
        pageHeight: Float,
        contentWidth: Float,
        ticket: LaundryTicket,
        allGarmentsCount: Int,
        pageGarments: List<ClothingItem>,
        startIndex: Int,
        isFirstPageOfTicket: Boolean,
        isLastPageOfTicket: Boolean,
        customization: LabelCustomization,
        globalPageNumber: Int,
        totalPages: Int,
    ) {
        var y: Float

        if (isFirstPageOfTicket) {
            // Header band
            canvas.drawRect(RectF(0f, 0f, pageWidth, BAND_HEIGHT), fillPaint(ACCENT))
            canvas.drawText(
                customization.headerText.ifBlank { "MeowLaundry" },
                MARGIN,
                BAND_HEIGHT / 2f + 8f,
                paint(22f, bold = true, color = Color.WHITE),
            )
            val meta = "Ticket #${ticket.id}  \u00b7  ${dateFormat.format(Date(ticket.sentAt))}"
            val metaPaint = paint(10f, color = Color.WHITE, align = Paint.Align.RIGHT)
            canvas.drawText(meta, pageWidth - MARGIN, BAND_HEIGHT / 2f + 4f, metaPaint)

            y = BAND_HEIGHT + 24f

            // Three stat boxes: service, provider, garment count - makes the page useful at a
            // glance instead of just a plain list.
            val boxGap = 12f
            val boxWidth = (contentWidth - 2 * boxGap) / 3f
            val boxes = listOf(
                "SERVICE" to serviceLabel(ticket.serviceType),
                "PROVIDER" to (ticket.providerName?.takeIf { it.isNotBlank() } ?: "\u2014"),
                "GARMENTS" to "$allGarmentsCount",
            )
            boxes.forEachIndexed { index, pair ->
                val label = pair.first
                val value = pair.second
                val left = MARGIN + index * (boxWidth + boxGap)
                val rect = RectF(left, y, left + boxWidth, y + STAT_BOX_HEIGHT)
                canvas.drawRoundRect(rect, 8f, 8f, fillPaint(ROW_SHADE))
                canvas.drawRoundRect(rect, 8f, 8f, linePaint())
                canvas.drawText(label, left + 10f, y + 18f, paint(8f, bold = true, color = MUTED))
                val valuePaint = paint(if (value.length > 14) 11f else 13f, bold = true)
                canvas.drawText(value, left + 10f, y + 36f, valuePaint)
            }
            y += STAT_BOX_HEIGHT + 20f

            canvas.drawText("Garments \u00b7 check each one on return", MARGIN, y, paint(13f, bold = true))
            y += 10f
            canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, linePaint())
            y += 16f
        } else {
            canvas.drawText(
                "${customization.headerText.ifBlank { "MeowLaundry" }} \u2014 Ticket #${ticket.id} (continued)",
                MARGIN,
                MARGIN,
                paint(12f, bold = true, color = MUTED),
            )
            y = MARGIN + 20f
            canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, linePaint())
            y += 16f
        }

        val circleR = 10f
        pageGarments.forEachIndexed { rowOffset, garment ->
            val index = startIndex + rowOffset
            val rowTop = y + rowOffset * ROW_HEIGHT
            if (index % 2 == 1) {
                canvas.drawRect(RectF(MARGIN, rowTop, pageWidth - MARGIN, rowTop + ROW_HEIGHT), fillPaint(ROW_SHADE))
            }
            val centerY = rowTop + ROW_HEIGHT / 2f
            canvas.drawCircle(MARGIN + 14f, centerY, circleR, fillPaint(ACCENT_LIGHT))
            canvas.drawText(
                "${index + 1}",
                MARGIN + 14f,
                centerY + 4f,
                paint(10f, bold = true, color = ACCENT, align = Paint.Align.CENTER),
            )

            val namePaint = paint(12.5f)
            canvas.drawText(garment.title, MARGIN + 34f, centerY + 4f, namePaint)

            if (customization.showGarmentType) {
                val typeLabel = garment.type.name.lowercase().replaceFirstChar { it.uppercase() }
                val tagPaint = paint(9f, color = MUTED, align = Paint.Align.RIGHT)
                val tagWidth = tagPaint.measureText(typeLabel) + 16f
                val tagRight = pageWidth - MARGIN - 4f
                val tagRect = RectF(tagRight - tagWidth, centerY - 9f, tagRight, centerY + 9f)
                canvas.drawRoundRect(tagRect, 9f, 9f, fillPaint(ROW_SHADE))
                canvas.drawText(typeLabel, tagRight - 8f, centerY + 3f, tagPaint)
            }
        }

        // Footer: divider + note (last page of this ticket only) + page number (every page).
        val footerY = pageHeight - MARGIN
        canvas.drawLine(MARGIN, footerY - 24f, pageWidth - MARGIN, footerY - 24f, linePaint())
        if (isLastPageOfTicket) {
            canvas.drawText(
                customization.footerText.ifBlank { "Please keep this ticket until pickup" },
                MARGIN,
                footerY - 6f,
                paint(9.5f, color = MUTED),
            )
        }
        if (totalPages > 1) {
            canvas.drawText(
                "Page $globalPageNumber of $totalPages",
                pageWidth - MARGIN,
                footerY - 6f,
                paint(9f, color = MUTED, align = Paint.Align.RIGHT),
            )
        }
    }

    /** Writes [buildDocument]'s output to [file] and closes the document. */
    fun writeToFile(ticket: LaundryTicket, garments: List<ClothingItem>, customization: LabelCustomization, file: File) {
        writeMultiToFile(listOf(ticket to garments), customization, file)
    }

    /** Writes [buildMultiDocument]'s output (one page break per ticket) to [file] and closes it. */
    fun writeMultiToFile(
        tickets: List<Pair<LaundryTicket, List<ClothingItem>>>,
        customization: LabelCustomization,
        file: File,
    ) {
        val doc = buildMultiDocument(tickets, customization)
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
