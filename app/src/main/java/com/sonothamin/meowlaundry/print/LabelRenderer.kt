package com.sonothamin.meowlaundry.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.ServiceType
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

    fun renderTicket(ticket: LaundryTicket, garments: List<ClothingItem>): Bitmap {
        val titlePaint = textPaint(size = 30f, bold = true)
        val servicePaint = textPaint(size = 26f, bold = true)
        val metaPaint = textPaint(size = 19f, align = Paint.Align.CENTER)
        val indexPaint = textPaint(size = 23f, bold = true)
        val namePaint = textPaint(size = 23f)
        val typePaint = textPaint(size = 18f, color = Color.rgb(90, 90, 90))
        val footerPaint = textPaint(size = 18f, color = Color.rgb(90, 90, 90))

        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        val usableWidth = WIDTH - 2 * MARGIN
        val indexColumnWidth = 34

        data class GarmentLines(val nameLines: List<String>, val typeLine: String)
        val garmentLines = garments.map { garment ->
            GarmentLines(
                nameLines = wrapText(garment.title, namePaint, usableWidth - indexColumnWidth),
                typeLine = garment.type.name.lowercase().replaceFirstChar { it.uppercase() },
            )
        }

        // First pass: measure total height so the bitmap is exactly as tall as it needs to be.
        var y = MARGIN
        y += 30 // masthead title
        y += 26 // masthead subtitle
        y += 24 // gap + rule
        y += 34 // service/provider row
        y += 22 + 24 // item-count line + gap/rule
        garmentLines.forEach { g -> y += g.nameLines.size * 30 + 24 + 10 }
        y += 20 // gap + rule
        y += 22 // footer line
        val height = y + MARGIN / 2

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var cursorY = MARGIN + 28
        canvas.drawCenteredText("MeowLaundry", cursorY.toFloat(), titlePaint)
        cursorY += 30
        canvas.drawCenteredText(
            "Ticket #${ticket.id} · ${dateFormat.format(Date(ticket.sentAt))}",
            cursorY.toFloat(),
            metaPaint,
        )
        cursorY += 24
        cursorY = canvas.drawDashedRule(cursorY)

        canvas.drawText(serviceLabel(ticket.serviceType), MARGIN.toFloat(), cursorY.toFloat(), servicePaint)
        ticket.providerName?.takeIf { it.isNotBlank() }?.let { provider ->
            val w = servicePaint.measureText(provider)
            canvas.drawText(provider, WIDTH - MARGIN - w, cursorY.toFloat(), servicePaint)
        }
        cursorY += 34
        canvas.drawText(
            "${garments.size} garment${if (garments.size == 1) "" else "s"} · check each one on return",
            MARGIN.toFloat(),
            cursorY.toFloat(),
            footerPaint,
        )
        cursorY += 22
        cursorY = canvas.drawDashedRule(cursorY)

        garmentLines.forEachIndexed { index, g ->
            val rowTop = cursorY
            canvas.drawText("${index + 1}.", MARGIN.toFloat(), rowTop.toFloat(), indexPaint)
            var lineY = rowTop
            g.nameLines.forEach { line ->
                canvas.drawText(line, (MARGIN + indexColumnWidth).toFloat(), lineY.toFloat(), namePaint)
                lineY += 30
            }
            canvas.drawText(g.typeLine, (MARGIN + indexColumnWidth).toFloat(), lineY.toFloat(), typePaint)
            cursorY = lineY + 24 + 10
        }

        cursorY = canvas.drawDashedRule(cursorY - 10 + 6)
        canvas.drawCenteredText("Please keep this ticket until pickup", cursorY.toFloat(), metaPaint)

        return bitmap
    }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            if (bold) typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

    private fun Canvas.drawCenteredText(text: String, y: Float, paint: Paint) {
        drawText(text, WIDTH / 2f, y, paint)
    }

    /** Draws a dashed rule (a quieter section break than a solid bar) and returns the next cursor Y. */
    private fun Canvas.drawDashedRule(y: Int): Int {
        val paint = Paint().apply { color = Color.rgb(70, 70, 70); strokeWidth = 2f }
        var x = MARGIN
        val dash = 6
        val gap = 6
        while (x < WIDTH - MARGIN) {
            val end = (x + dash).coerceAtMost(WIDTH - MARGIN)
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
