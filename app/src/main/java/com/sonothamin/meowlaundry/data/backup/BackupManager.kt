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

    private companion object {
        /** backup.json is text; 32 MB is far beyond any real closet and stops zip bombs. */
        const val MAX_JSON_BYTES = 32L * 1024 * 1024
        /** Per-photo and whole-archive ceilings, enforced while streaming (declared sizes can lie). */
        const val MAX_PHOTO_BYTES = 64L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    }

    /** Copies at most [limit] bytes; throws if the stream holds more. Returns bytes copied. */
    private fun java.io.InputStream.copyLimited(out: java.io.OutputStream, limit: Long): Long {
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) return total
            total += n
            if (total > limit) error("Backup is too large or corrupt")
            out.write(buf, 0, n)
        }
    }

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

    /**
     * Restores from a .zip created by [exportTo]. This replaces all current data - but only once
     * the whole backup has been read and validated: if anything is wrong, the current closet is
     * left untouched and the photos copied so far are removed again.
     */
    suspend fun importFrom(source: Uri) {
        val photosDir = File(context.filesDir, "clothing_photos").apply { mkdirs() }
        val photosRoot = photosDir.canonicalFile
        val addedPhotos = mutableListOf<File>()
        var payload: BackupPayload? = null
        var totalBytes = 0L

        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "backup.json" -> {
                                val buf = java.io.ByteArrayOutputStream()
                                totalBytes += zip.copyLimited(buf, MAX_JSON_BYTES)
                                payload = json.decodeFromString(BackupPayload.serializer(), buf.toString(Charsets.UTF_8.name()))
                            }
                            entry.name.startsWith("photos/") -> {
                                // Only the file's own name is used, never the path inside the zip, and the
                                // result must stay inside the photos folder (a crafted "../" entry could
                                // otherwise overwrite the app's database or settings).
                                val name = File(entry.name.removePrefix("photos/")).name
                                val dest = File(photosDir, name)
                                if (name.isNotBlank() && dest.canonicalFile.parentFile == photosRoot) {
                                    val existed = dest.exists()
                                    if (!existed) addedPhotos += dest
                                    dest.outputStream().use { totalBytes += zip.copyLimited(it, MAX_PHOTO_BYTES) }
                                    if (totalBytes > MAX_TOTAL_BYTES) error("Backup is too large or corrupt")
                                }
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

            // The restore replaced everything, so photos the backup doesn't use are now orphans.
            val keep = remapped.clothingItems
                .flatMap { item -> item.photos.map { it.path } + listOfNotNull(item.imagePath) }
                .toSet()
            photosDir.listFiles()?.filter { it.isFile && it.absolutePath !in keep }?.forEach { it.delete() }
        } catch (e: Exception) {
            addedPhotos.forEach { it.delete() }
            throw e
        }
    }
}
