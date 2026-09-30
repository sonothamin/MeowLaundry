package com.sonothamin.meowlaundry.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import com.sonothamin.meowlaundry.data.ClothingItem
import com.sonothamin.meowlaundry.data.LaundryTicket
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small helpers for the closet/history "export" and "share" multiselect actions. */
object ExportUtils {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    fun clothingCsv(items: List<ClothingItem>): String {
        val header = "Title,Brand,Garment type,Color,Category,Status,Quantity,Price,Currency,Notes"
        val rows = items.joinToString("\n") { item ->
            listOf(
                item.title,
                item.brand ?: "",
                item.garmentType ?: "",
                item.color ?: "",
                item.type.name,
                item.status.name,
                item.quantity.toString(),
                item.price?.toString() ?: "",
                item.currency,
                item.notes ?: "",
            ).joinToString(",") { csvField(it) }
        }
        return "$header\n$rows"
    }

    fun ticketsCsv(tickets: List<LaundryTicket>): String {
        val header = "Ticket,Service,Provider,Sent,Received,Status"
        val rows = tickets.joinToString("\n") { ticket ->
            listOf(
                "#${ticket.id}",
                ticket.serviceType.name,
                ticket.providerName ?: "",
                dateFormat.format(Date(ticket.sentAt)),
                ticket.receivedAt?.let { dateFormat.format(Date(it)) } ?: "",
                ticket.status.name,
            ).joinToString(",") { csvField(it) }
        }
        return "$header\n$rows"
    }

    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    /** Writes [content] to a cache file and returns a chooser-ready ACTION_SEND intent for it. */
    fun shareFileIntent(context: Context, fileName: String, content: String, mimeType: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareTextIntent(text: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }

    fun clothingSummaryText(items: List<ClothingItem>): String =
        "MeowLaundry closet (${items.size} item(s)):\n" + items.joinToString("\n") { "- ${it.title} (${it.type})" }

    fun ticketsSummaryText(tickets: List<LaundryTicket>): String =
        "MeowLaundry history (${tickets.size} ticket(s)):\n" + tickets.joinToString("\n") {
            "- #${it.id} ${it.serviceType} · ${it.status}"
        }
}

/**
 * Writes to a file the person just created with the system "save as" picker. Returns whether it
 * worked; on failure the empty file the picker made is removed again so it doesn't look like a
 * successful export.
 */
suspend fun Context.saveToUri(uri: Uri, write: suspend (OutputStream) -> Boolean): Boolean {
    val saved = withContext(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(uri)?.use { write(it) } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Export", "Saving the file failed", e)
            false
        }
    }
    if (!saved) runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
    return saved
}
