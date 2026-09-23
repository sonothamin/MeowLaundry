package com.sonothamin.meowlaundry.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a laundry ticket as a black-and-white bitmap matching the print head's native
 * 384-dot (48 mm) width, so MeowSpool needs no extra scaling.
 */
object LabelRenderer {

    private const val WIDTH = 384
    private const val MARGIN = 16

    fun renderTicket(ticket: LaundryTicket, garments: List<ClothingItem>): Bitmap {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val usableWidth = WIDTH - 2 * MARGIN

        // First pass: measure total height.
        var y = MARGIN + 40
        y += 30 // subtitle
        y += 16 // divider gap
        garments.forEach { garment ->
            y += wrappedLineCount("${garment.title} (${garment.type})", bodyPaint, usableWidth) * 30
        }
        y += 30 // footer note
        val height = y + MARGIN

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var cursorY = MARGIN + 34
        canvas.drawText("MeowLaundry ticket #${ticket.id}", MARGIN.toFloat(), cursorY.toFloat(), titlePaint)
        cursorY += 34
        canvas.drawText(
            "${ticket.serviceType} · sent ${dateFormat.format(Date(ticket.sentAt))}",
            MARGIN.toFloat(),
            cursorY.toFloat(),
            smallPaint,
        )
        cursorY += 24
        canvas.drawLine(MARGIN.toFloat(), cursorY.toFloat(), (WIDTH - MARGIN).toFloat(), cursorY.toFloat(), linePaint)
        cursorY += 30

        garments.forEachIndexed { index, garment ->
            val line = "${index + 1}. ${garment.title} (${garment.type})"
            val wrapped = wrapText(line, bodyPaint, usableWidth)
            wrapped.forEach { part ->
                canvas.drawText(part, MARGIN.toFloat(), cursorY.toFloat(), bodyPaint)
                cursorY += 30
            }
        }

        cursorY += 6
        canvas.drawLine(MARGIN.toFloat(), cursorY.toFloat(), (WIDTH - MARGIN).toFloat(), cursorY.toFloat(), linePaint)
        cursorY += 24
        canvas.drawText("${garments.size} item(s) - check on return", MARGIN.toFloat(), cursorY.toFloat(), smallPaint)

        return bitmap
    }

    private fun wrappedLineCount(text: String, paint: Paint, maxWidth: Int): Int = wrapText(text, paint, maxWidth).size

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
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
