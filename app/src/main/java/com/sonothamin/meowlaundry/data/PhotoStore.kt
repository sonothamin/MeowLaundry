package com.sonothamin.meowlaundry.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Copies a picture the user picked or captured into the app's own files directory so it
 * survives independently of the source content:// URI (which can be revoked or point at
 * a photo the user later deletes from their gallery).
 */
class PhotoStore(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "clothing_photos").apply { mkdirs() }

    /** Copies [source] in and returns the app-relative path to store on the [ClothingItem]. */
    fun importPhoto(source: Uri): String {
        val fileName = "${UUID.randomUUID()}.jpg"
        val destination = File(photosDir, fileName)
        try {
            context.contentResolver.openInputStream(source).use { input ->
                destination.outputStream().use { output ->
                    requireNotNull(input) { "Could not open picked image" }.copyTo(output)
                }
            }
        } catch (e: Exception) {
            destination.delete() // never leave a half-copied photo behind
            throw e
        }
        return destination.absolutePath
    }

    /** Creates an empty destination file + content:// URI for the camera to write a capture into. */
    fun createCaptureTarget(): Pair<File, Uri> {
        val file = File(photosDir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.exists() && !file.delete()) Log.w("PhotoStore", "Couldn't delete a photo file")
    }

    fun fileFor(path: String): File = File(path)
}
