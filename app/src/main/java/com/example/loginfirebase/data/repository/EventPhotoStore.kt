package com.example.loginfirebase.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.util.UUID

/** Keeps event photos in app-private storage; photos are not uploaded or synchronized. */
object EventPhotoStore {
    fun list(context: Context, eventId: String): List<File> =
        eventDirectory(context, eventId)
            .listFiles { file -> file.isFile && file.length() > 0L }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()

    fun saveFromUri(context: Context, eventId: String, uri: Uri): File {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            throw IOException("The selected file is not an image")
        }
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) }
            ?: "jpg"
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot read the selected image")
        return input.use { saveStream(context, eventId, extension, it) }
    }

    fun copyCapturedPhoto(context: Context, eventId: String, photo: File): File =
        photo.inputStream().use { saveStream(context, eventId, "jpg", it) }

    private fun saveStream(context: Context, eventId: String, extension: String, input: java.io.InputStream): File {
        val directory = eventDirectory(context, eventId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create the event photo directory")
        }
        val destination = File(directory, "${UUID.randomUUID()}.$extension")
        try {
            destination.outputStream().use { output -> input.copyTo(output) }
            if (destination.length() == 0L) throw IOException("The selected image is empty")
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun eventDirectory(context: Context, eventId: String): File {
        val safeEventId = eventId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "event" }
        return File(File(context.filesDir, EVENT_PHOTOS_DIRECTORY), safeEventId)
    }

    private const val EVENT_PHOTOS_DIRECTORY = "event_photos"
}
