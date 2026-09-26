package com.sonothamin.meowlaundry.print

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.data.PrintMethod
import com.sonothamin.meowlaundry.data.PrintPreferences
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

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
    suspend fun renderTicket(ticket: LaundryTicket, garments: List<ClothingItem>): Bitmap =
        LabelRenderer.renderTicket(ticket, garments, printPreferences.labelCustomization.first())

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
}
