package com.sonothamin.meowlaundry.print

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.FileProvider
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.PrintMethod
import com.sonothamin.meowlaundry.data.PrintPreferences
import com.sonothamin.meowlaundry.data.TicketFormat
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Package name of the MeowSpool app (https://github.com/sonothamin/MeowSpool). */
const val MEOWSPOOL_PACKAGE = "dev.meowspool"

/** What happened when we tried to print, or what the caller must do next. */
sealed class PrintOutcome {
    /** Printed straight away over the network API. */
    data class Printed(val message: String) : PrintOutcome()

    /** Caller must launch this share intent (targeting MeowSpool) to finish the print. */
    data class ShareReady(val intent: Intent) : PrintOutcome()

    data class Failed(val message: String) : PrintOutcome()
}

/**
 * Routes a rendered label bitmap to the printer using whichever [PrintMethod] the user
 * picked in Settings: either the MeowSpool HTTP API directly, or a share (ACTION_SEND)
 * intent handed off to the MeowSpool app - the same intent filter MeowSpool exposes for
 * sharing a photo or PDF to it (see dev.meowspool.MainActivity's SEND intent filters).
 */
class PrintDispatcher(
    private val context: Context,
    private val printPreferences: PrintPreferences,
) {

    /** Renders one ticket's label using whatever customization the person set in Settings. */
    suspend fun renderTicket(ticket: LaundryTicket, garments: List<ClothingItem>): Bitmap {
        val customization = printPreferences.labelCustomization.first()
        return if (customization.format == TicketFormat.PAGE) {
            PdfPageRenderer.renderPreviewBitmap(ticket, garments, customization)
        } else {
            LabelRenderer.renderTicket(ticket, garments, customization)
        }
    }

    /**
     * Prints a single ticket, picking the right pipeline for the chosen [TicketFormat]: the
     * thermal bitmap path for Receipt/Rectangular, or a real text PDF for [TicketFormat.PAGE]
     * (which a thermal printer can't use, so that format always goes through the system print
     * dialog or a generic "share this PDF" sheet instead of MeowSpool).
     */
    suspend fun dispatchTicket(ticket: LaundryTicket, garments: List<ClothingItem>): PrintOutcome =
        dispatchTickets(listOf(ticket to garments))

    /**
     * Prints several tickets at once. For Receipt/Rectangular this keeps the old behaviour of
     * stitching every label into one perforated image (MeowSpool's share intent only takes a
     * single file). For [TicketFormat.PAGE] it builds one real-text PDF where each ticket starts
     * on its own page (see [PdfPageRenderer.buildMultiDocument]) - proper page breaks per order,
     * not everything crammed onto a single sheet.
     */
    suspend fun dispatchTickets(tickets: List<Pair<LaundryTicket, List<ClothingItem>>>): PrintOutcome {
        val nonEmpty = tickets.filter { it.second.isNotEmpty() }
        if (nonEmpty.isEmpty()) return PrintOutcome.Failed("Nothing to print")
        val customization = printPreferences.labelCustomization.first()

        if (customization.format != TicketFormat.PAGE) {
            val bitmaps = nonEmpty.map { (ticket, garments) -> LabelRenderer.renderTicket(ticket, garments, customization) }
            return dispatchMultiple(bitmaps)
        }

        val settings = printPreferences.settings.first()
        val pdfDir = File(context.cacheDir, "print_share").apply { mkdirs() }
        val jobName = if (nonEmpty.size == 1) {
            "MeowLaundry ticket #${nonEmpty.first().first.id}"
        } else {
            "MeowLaundry tickets (${nonEmpty.size})"
        }
        val fileName = if (nonEmpty.size == 1) "ticket_${nonEmpty.first().first.id}" else "tickets_${nonEmpty.size}"
        val file = File(pdfDir, "${fileName}_${System.currentTimeMillis()}.pdf")
        PdfPageRenderer.writeMultiToFile(nonEmpty, customization, file)

        return if (settings.printMethod == PrintMethod.NATIVE) {
            printPdfNative(file, jobName)
            PrintOutcome.Printed("Opening print dialog…")
        } else {
            // A thermal printer (MeowSpool) can't take a full-page PDF, so Page format always
            // goes out as a plain PDF share instead of the MeowSpool-only intent used elsewhere.
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/pdf"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            PrintOutcome.ShareReady(intent)
        }
    }

    /** Hands an already-rendered real PDF file straight to the system print dialog, unchanged. */
    private fun printPdfNative(file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(jobName, FilePrintDocumentAdapter(file, jobName), null)
    }

    /** Renders and prints a single label. */
    suspend fun dispatch(bitmap: Bitmap): PrintOutcome = dispatchMultiple(listOf(bitmap))

    /** Renders and prints several labels in one go (used by multiselect actions). */
    suspend fun dispatchMultiple(bitmaps: List<Bitmap>): PrintOutcome {
        if (bitmaps.isEmpty()) return PrintOutcome.Failed("Nothing to print")
        val settings = printPreferences.settings.first()
        return when (settings.printMethod) {
            PrintMethod.NETWORK -> {
                if (settings.host.isBlank()) {
                    return PrintOutcome.Failed("Set up your MeowSpool printer in Settings first")
                }
                val client = MeowSpoolClient(settings)
                var printed = 0
                var lastError: String? = null
                bitmaps.forEach { bmp ->
                    when (val result = client.printBitmap(bmp)) {
                        is MeowSpoolResult.Success -> printed++
                        is MeowSpoolResult.Failure -> lastError = result.message
                    }
                }
                when {
                    lastError == null -> PrintOutcome.Printed(
                        if (printed == 1) "Printed" else "Printed $printed label(s)"
                    )
                    printed > 0 -> PrintOutcome.Failed("Printed $printed, then failed: $lastError")
                    else -> PrintOutcome.Failed("Print failed: $lastError")
                }
            }
            PrintMethod.SHARE_INTENT -> PrintOutcome.ShareReady(buildShareIntent(bitmaps))
            PrintMethod.NATIVE -> {
                printNative(bitmaps)
                PrintOutcome.Printed("Opening print dialog…")
            }
        }
    }

    /**
     * Builds (without launching) the MeowSpool share intent, e.g. for a "share" action.
     *
     * MeowSpool's SEND intent filter only accepts a single image - it doesn't support
     * ACTION_SEND_MULTIPLE - so for a multiselect print we stitch every label into one tall
     * bitmap with perforation lines between them (see [LabelRenderer.combineWithPerforation])
     * and share that as a single image instead.
     */
    fun buildShareIntent(bitmaps: List<Bitmap>): Intent {
        val combined = LabelRenderer.combineWithPerforation(bitmaps)
        val cacheDir = File(context.cacheDir, "print_share").apply { mkdirs() }
        val file = File(cacheDir, "label_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> combined.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        return Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
            setPackage(MEOWSPOOL_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** True if the MeowSpool app is installed and can receive the share intent. */
    fun isMeowSpoolInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(MEOWSPOOL_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Hands the label(s) to Android's own print framework instead of MeowSpool: opens the
     * system print dialog, which lists whatever printers/services the OS already knows about
     * (Wi-Fi printers, "Save as PDF", cloud print services...). Each bitmap becomes one page.
     */
    private fun printNative(bitmaps: List<Bitmap>, jobName: String = "MeowLaundry ticket") {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(jobName, BitmapPrintDocumentAdapter(context, jobName, bitmaps), null)
    }
}

/**
 * Draws each label bitmap onto its own PDF page, scaled to fit the page while keeping its
 * aspect ratio and anchored to the top, then hands that PDF to the print spooler - the
 * standard "print my own custom-drawn content" recipe from Android's print framework.
 */
private class BitmapPrintDocumentAdapter(
    private val context: Context,
    private val jobName: String,
    private val bitmaps: List<Bitmap>,
) : PrintDocumentAdapter() {

    private var pdfDocument: PrintedPdfDocument? = null

    override fun onLayout(
        oldAttributes: PrintAttributes,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle,
    ) {
        pdfDocument = PrintedPdfDocument(context, newAttributes)
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        if (bitmaps.isEmpty()) {
            callback.onLayoutFailed("Nothing to print")
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(bitmaps.size)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val doc = pdfDocument
        if (doc == null) {
            callback.onWriteFailed("Not laid out")
            return
        }
        bitmaps.forEachIndexed { index, bitmap ->
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                doc.close()
                pdfDocument = null
                return
            }
            val page = doc.startPage(index)
            val pageWidth = doc.pageWidth.toFloat()
            val pageHeight = doc.pageHeight.toFloat()
            val scale = minOf(pageWidth / bitmap.width, pageHeight / bitmap.height)
            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val left = (pageWidth - scaledWidth) / 2f
            val destRect = android.graphics.RectF(left, 0f, left + scaledWidth, scaledHeight)
            page.canvas.drawBitmap(bitmap, null, destRect, null)
            doc.finishPage(page)
        }
        try {
            FileOutputStream(destination.fileDescriptor).use { out -> doc.writeTo(out) }
        } catch (e: IOException) {
            callback.onWriteFailed(e.message ?: "Print failed")
            return
        } finally {
            doc.close()
            pdfDocument = null
        }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
}

/**
 * Streams an already-rendered real PDF file (real text, built by [PdfPageRenderer]) straight to
 * the print spooler as-is - no bitmap re-rasterization, so the printed/saved result keeps its
 * selectable text. `newAttributes`/paper size are ignored: the page size was already baked in
 * when the PDF was built from the person's chosen [com.sonothamin.meowlaundry.data.PageSize].
 */
private class FilePrintDocumentAdapter(
    private val file: File,
    private val jobName: String,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        try {
            file.inputStream().use { input ->
                FileOutputStream(destination.fileDescriptor).use { out -> input.copyTo(out) }
            }
        } catch (e: IOException) {
            callback.onWriteFailed(e.message ?: "Print failed")
            return
        }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
}
