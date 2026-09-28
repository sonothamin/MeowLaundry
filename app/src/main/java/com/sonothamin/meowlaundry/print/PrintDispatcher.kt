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
import com.sonothamin.meowlaundry.data.LabelCustomization
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

    /** The chosen method needs the MeowSpool app, which isn't installed - the caller should offer to install it. */
    data object MeowSpoolMissing : PrintOutcome()

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

    /** The ticket format currently chosen in Settings - lets a caller tailor its UI to it (e.g. the preview dialog's export action). */
    suspend fun currentFormat(): TicketFormat = printPreferences.labelCustomization.first().format

    /** Writes a single ticket as a real text PDF, sized for whatever paper is the locale default. */
    suspend fun writeTicketPdf(ticket: LaundryTicket, garments: List<ClothingItem>, output: java.io.OutputStream) {
        val customization = printPreferences.labelCustomization.first()
        val doc = PdfPageRenderer.buildDocument(ticket, garments, customization)
        try {
            doc.writeTo(output)
        } finally {
            doc.close()
        }
    }

    /**
     * Prints a single ticket, picking the right pipeline for the chosen [TicketFormat]: the
     * thermal bitmap path for Receipt/Rectangular, or a real text PDF for [TicketFormat.PAGE]
     * (which a thermal printer can't use, so that format always goes through Android's own
     * system print dialog instead of MeowSpool).
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

        val jobName = if (nonEmpty.size == 1) {
            "MeowLaundry ticket #${nonEmpty.first().first.id}"
        } else {
            "MeowLaundry tickets (${nonEmpty.size})"
        }

        // A thermal printer (MeowSpool) can't take a full page PDF, and a plain "share this
        // file" sheet is a worse experience than Android's own print dialog - which already
        // offers "Save as PDF" alongside every real printer the OS knows about - so Page format
        // always goes through the system print dialog regardless of the general Print method
        // setting above (that setting only governs the bitmap-label formats).
        printPdfNative(nonEmpty, customization, jobName)
        return PrintOutcome.Printed("Opening print dialog…")
    }

    /**
     * Hands the tickets to the system print dialog without pre-baking a page size: the actual
     * PDF is built inside [TicketPagePrintDocumentAdapter.onLayout] once the person has chosen
     * (or the OS has defaulted to) a paper size there, so the document always matches the paper
     * it's printed or saved to, on any size the dialog offers.
     */
    private fun printPdfNative(
        tickets: List<Pair<LaundryTicket, List<ClothingItem>>>,
        customization: LabelCustomization,
        jobName: String,
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(jobName, TicketPagePrintDocumentAdapter(tickets, customization, jobName), null)
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
            PrintMethod.SHARE_INTENT -> {
                // Check first, so we can offer to install MeowSpool instead of letting Android
                // answer with its generic "no app found to handle this action".
                if (!isMeowSpoolInstalled()) PrintOutcome.MeowSpoolMissing
                else PrintOutcome.ShareReady(buildShareIntent(bitmaps))
            }
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
    fun isMeowSpoolInstalled(): Boolean = MeowSpoolInstall.isInstalled(context)

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
 * Builds the real text PDF fresh in [onLayout], sized to whatever paper the system print dialog
 * actually asks for (`newAttributes.mediaSize`) - so "Save as PDF" on Letter and printing on A4
 * both come out correctly sized, instead of a page rendered once at a guessed size and cropped
 * or letterboxed to fit whatever the dialog later settled on.
 */
private class TicketPagePrintDocumentAdapter(
    private val tickets: List<Pair<LaundryTicket, List<ClothingItem>>>,
    private val customization: LabelCustomization,
    private val jobName: String,
) : PrintDocumentAdapter() {

    private var document: android.graphics.pdf.PdfDocument? = null

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
        // MediaSize is in thousandths of an inch; PDF points are 1/72 inch.
        val mediaSize = newAttributes.mediaSize
        val widthPt = mediaSize?.let { (it.widthMils * 72 / 1000) } ?: com.sonothamin.meowlaundry.data.defaultPageSize().widthPt
        val heightPt = mediaSize?.let { (it.heightMils * 72 / 1000) } ?: com.sonothamin.meowlaundry.data.defaultPageSize().heightPt

        document?.close()
        val built = PdfPageRenderer.buildMultiDocument(tickets, customization, widthPt, heightPt)
        document = built

        val info = PrintDocumentInfo.Builder("$jobName.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(built.pages.size)
            .build()
        // Always true: the page count/content is rebuilt from scratch for every layout pass
        // (paper size or orientation may have changed), so there is no "unchanged" case to skip.
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val doc = document
        if (doc == null) {
            callback.onWriteFailed("Not laid out")
            return
        }
        try {
            FileOutputStream(destination.fileDescriptor).use { out -> doc.writeTo(out) }
        } catch (e: IOException) {
            callback.onWriteFailed(e.message ?: "Print failed")
            return
        }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }

    override fun onFinish() {
        document?.close()
        document = null
    }
}
