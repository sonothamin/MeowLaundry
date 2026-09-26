package com.sonothamin.meowlaundry.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LabelCustomization
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.ServiceType
import com.sonothamin.meowlaundry.data.TicketFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a laundry ticket as a black-and-white bitmap matching the print head's native
 * 384-dot (48 mm) width, so MeowSpool needs no extra scaling.
 *
 * Laid out like a real receipt rather than a wall of text: a generous quiet margin, a centred
 * masthead, dashed rules between sections (not a heavy solid bar), a right-aligned provider
 * name next to the service, and each garment as a number + name with its category as a smaller
 * line underneath instead of crammed into parentheses after the name.
 */
object LabelRenderer {

    private const val WIDTH = 384
    private const val MARGIN = 28

    private fun serviceLabel(type: ServiceType): String = when (type) {
        ServiceType.WASH -> "Wash"
        ServiceType.PRESS -> "Press"
        ServiceType.WASH_AND_PRESS -> "Wash & press"
        ServiceType.DRY_CLEAN -> "Dry clean"
    }

    fun renderTicket(
        ticket: LaundryTicket,
        garments: List<ClothingItem>,
        customization: LabelCustomization = LabelCustomization(),
    ): Bitmap {
        val rectangular = customization.format == TicketFormat.RECTANGULAR
        // Rectangular gets a slightly deeper margin so the border drawn around it afterwards
        // doesn't crowd the content.
        val margin = if (rectangular) MARGIN + 12 else MARGIN

        val titlePaint = textPaint(size = 30f, bold = true, align = Paint.Align.CENTER)
        val servicePaint = textPaint(size = 26f, bold = true)
        val metaPaint = textPaint(size = 19f, align = Paint.Align.CENTER)
        val indexPaint = textPaint(size = 23f, bold = true)
        val namePaint = textPaint(size = 23f)
        val typePaint = textPaint(size = 18f, color = Color.rgb(90, 90, 90))
        val footerPaint = textPaint(size = 18f, color = Color.rgb(90, 90, 90))

        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        val usableWidth = WIDTH - 2 * margin
        val indexColumnWidth = 34

        data class GarmentLines(val nameLines: List<String>, val typeLine: String?)
        val garmentLines = garments.map { garment ->
            GarmentLines(
                nameLines = wrapText(garment.title, namePaint, usableWidth - indexColumnWidth),
                typeLine = if (customization.showGarmentType) {
                    garment.type.name.lowercase().replaceFirstChar { it.uppercase() }
                } else {
                    null
                },
            )
        }

        // First pass: measure total height so the bitmap is exactly as tall as it needs to be.
        var y = margin
        y += 30 // masthead title
        y += 26 // masthead subtitle
        y += 24 // gap + rule
        y += 34 // service/provider row
        y += 22 + 24 // item-count line + gap/rule
        garmentLines.forEach { g -> y += g.nameLines.size * 30 + (if (g.typeLine != null) 24 else 6) + 10 }
        y += 20 // gap + rule
        y += 22 // footer line
        val height = y + margin / 2

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var cursorY = margin + 28
        canvas.drawCenteredText(customization.headerText.ifBlank { "MeowLaundry" }, cursorY.toFloat(), titlePaint)
        cursorY += 30
        canvas.drawCenteredText(
            "Ticket #${ticket.id} · ${dateFormat.format(Date(ticket.sentAt))}",
            cursorY.toFloat(),
            metaPaint,
        )
        cursorY += 24
        cursorY = canvas.drawRule(cursorY, margin, dashed = !rectangular)

        canvas.drawText(serviceLabel(ticket.serviceType), margin.toFloat(), cursorY.toFloat(), servicePaint)
        ticket.providerName?.takeIf { it.isNotBlank() }?.let { provider ->
            val w = servicePaint.measureText(provider)
            canvas.drawText(provider, WIDTH - margin - w, cursorY.toFloat(), servicePaint)
        }
        cursorY += 34
        canvas.drawText(
            "${garments.size} garment${if (garments.size == 1) "" else "s"} · check each one on return",
            margin.toFloat(),
            cursorY.toFloat(),
            footerPaint,
        )
        cursorY += 22
        cursorY = canvas.drawRule(cursorY, margin, dashed = !rectangular)

        garmentLines.forEachIndexed { index, g ->
            val rowTop = cursorY
            canvas.drawText("${index + 1}.", margin.toFloat(), rowTop.toFloat(), indexPaint)
            var lineY = rowTop
            g.nameLines.forEach { line ->
                canvas.drawText(line, (margin + indexColumnWidth).toFloat(), lineY.toFloat(), namePaint)
                lineY += 30
            }
            g.typeLine?.let { canvas.drawText(it, (margin + indexColumnWidth).toFloat(), lineY.toFloat(), typePaint) }
            cursorY = lineY + (if (g.typeLine != null) 24 else 6) + 10
        }

        cursorY = canvas.drawRule(cursorY - 10 + 6, margin, dashed = !rectangular)
        canvas.drawCenteredText(
            customization.footerText.ifBlank { "Please keep this ticket until pickup" },
            cursorY.toFloat(),
            metaPaint,
        )

        if (rectangular) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(40, 40, 40)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val inset = 6f
            canvas.drawRoundRect(inset, inset, WIDTH - inset, height - inset, 16f, 16f, borderPaint)
        }

        return bitmap
    }

    /**
     * Stitches several already-rendered labels into a single tall bitmap, separated by a
     * perforation line (punched holes plus a dashed cut line). MeowSpool's share target only
     * accepts one image per share intent, so a multiselect "print" that goes through the share
     * path renders every ticket onto one strip instead, which the user tears apart after
     * printing - same as a real perforated receipt roll.
     */
    fun combineWithPerforation(bitmaps: List<Bitmap>): Bitmap {
        if (bitmaps.isEmpty()) return Bitmap.createBitmap(WIDTH, 1, Bitmap.Config.ARGB_8888)
        if (bitmaps.size == 1) return bitmaps.first()

        val width = bitmaps.maxOf { it.width }
        val perforationHeight = 34
        val totalHeight = bitmaps.sumOf { it.height } + perforationHeight * (bitmaps.size - 1)

        val combined = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(Color.WHITE)

        var y = 0
        bitmaps.forEachIndexed { index, bmp ->
            canvas.drawBitmap(bmp, 0f, y.toFloat(), null)
            y += bmp.height
            if (index != bitmaps.lastIndex) {
                y = canvas.drawPerforationLine(y, width, perforationHeight)
            }
        }
        return combined
    }

    /** Draws a "tear here" perforation (punched holes + dashed cut line) and returns the next cursor Y. */
    private fun Canvas.drawPerforationLine(y: Int, width: Int, bandHeight: Int): Int {
        val lineY = y + bandHeight / 2
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(225, 225, 225) }
        val dashPaint = Paint().apply { color = Color.rgb(120, 120, 120); strokeWidth = 2f }

        var hx = 9
        while (hx < width - 9) {
            drawCircle(hx.toFloat(), lineY.toFloat(), 4f, holePaint)
            hx += 18
        }

        val dash = 9
        val gap = 7
        var x = MARGIN
        while (x < width - MARGIN) {
            val end = (x + dash).coerceAtMost(width - MARGIN)
            drawLine(x.toFloat(), lineY.toFloat(), end.toFloat(), lineY.toFloat(), dashPaint)
            x += dash + gap
        }

        val scissorsPaint = textPaint(size = 16f, color = Color.rgb(120, 120, 120), align = Paint.Align.CENTER)
        drawText("✂", width / 2f, lineY + 5f, scissorsPaint)

        return y + bandHeight
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            if (bold) typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

    // Draws at the canvas's horizontal center regardless of the paint's own alignment, so a
    // caller that forgets to set Paint.Align.CENTER can't silently clip text off the label edge.
    private fun Canvas.drawCenteredText(text: String, y: Float, paint: Paint) {
        val original = paint.textAlign
        paint.textAlign = Paint.Align.CENTER
        drawText(text, WIDTH / 2f, y, paint)
        paint.textAlign = original
    }

    /** Draws a section break and returns the next cursor Y: dashed for a receipt, a solid thin rule for a card. */
    private fun Canvas.drawRule(y: Int, margin: Int, dashed: Boolean): Int {
        val paint = Paint().apply { color = Color.rgb(70, 70, 70); strokeWidth = 2f }
        if (!dashed) {
            drawLine(margin.toFloat(), y.toFloat(), (WIDTH - margin).toFloat(), y.toFloat(), paint)
            return y + 24
        }
        var x = margin
        val dash = 6
        val gap = 6
        while (x < WIDTH - margin) {
            val end = (x + dash).coerceAtMost(WIDTH - margin)
            drawLine(x.toFloat(), y.toFloat(), end.toFloat(), y.toFloat(), paint)
            x += dash + gap
        }
        return y + 24
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf("") }
    }
}
