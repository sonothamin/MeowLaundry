package com.sonothamin.meowlaundry.data.backup

import android.content.Context
import android.net.Uri
import com.sonothamin.meowlaundry.data.BackupPayload
import com.sonothamin.meowlaundry.data.ClosetRepository
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Export/import as a single .zip: `backup.json` (the [BackupPayload]) plus every referenced
 * photo under `photos/`. Fully offline - the user picks the destination/source file via the
 * system file picker (Storage Access Framework), nothing leaves the device on its own.
 */
class BackupManager(
    private val context: Context,
    private val repository: ClosetRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportTo(destination: Uri) {
        val payload = repository.exportAll()
        val jsonBytes = json.encodeToString(BackupPayload.serializer(), payload).toByteArray()

        context.contentResolver.openOutputStream(destination)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(jsonBytes)
                zip.closeEntry()

                val allPaths = payload.clothingItems.flatMap { item ->
                    (item.photos.map { it.path } + listOfNotNull(item.imagePath)).distinct()
                }.distinct()
                allPaths.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry("photos/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("Could not open destination file for writing")
    }

    /** Restores from a .zip created by [exportTo]. This replaces all current data. */
    suspend fun importFrom(source: Uri) {
        val photosDir = File(context.filesDir, "clothing_photos").apply { mkdirs() }
        var payload: BackupPayload? = null
        val pathRemap = mutableMapOf<String, String>() // original absolute path -> restored path

        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "backup.json" -> {
                            val bytes = zip.readBytes()
                            payload = json.decodeFromString(BackupPayload.serializer(), String(bytes))
                        }
                        entry.name.startsWith("photos/") -> {
                            val fileName = entry.name.removePrefix("photos/")
                            val destFile = File(photosDir, fileName)
                            destFile.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Could not open backup file for reading")

        val loaded = payload ?: error("backup.json missing from archive")

        // Re-point every path at the freshly restored file in this install's files dir.
        fun remapPath(original: String?): String? {
            if (original.isNullOrBlank()) return null
            val restored = File(photosDir, File(original).name)
            return if (restored.exists()) restored.absolutePath else null
        }
        val remapped = loaded.copy(
            clothingItems = loaded.clothingItems.map { item ->
                item.copy(
                    imagePath = remapPath(item.imagePath),
                    photos = item.photos.mapNotNull { photo ->
                        remapPath(photo.path)?.let { photo.copy(path = it) }
                    },
                )
            }
        )

        repository.importAll(remapped)
    }
}
