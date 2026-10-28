package com.example.loginfirebase.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.InputStream

data class PendingEventVideo(
    val eventId: String,
    val file: File,
    val mimeType: String,
    val durationMs: Long
)

class EventVideoInputException(
    val reason: Reason,
    cause: Throwable? = null
) : IOException(cause) {
    enum class Reason {
        INVALID_VIDEO,
        TOO_LARGE,
        TOO_LONG,
        UNREADABLE
    }
}

/** Copies and validates event videos in temporary app-private storage. */
object EventVideoInput {
    const val MAX_DURATION_MS = 60_000L
    const val MAX_SIZE_BYTES = 100L * 1024L * 1024L

    fun createCaptureFile(context: Context, eventId: String): File {
        val directory = eventDirectory(context, eventId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE)
        }
        return try {
            File.createTempFile("capture_", ".mp4", directory)
        } catch (error: Exception) {
            throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE, error)
        }
    }

    fun copyFromUri(context: Context, eventId: String, uri: Uri): PendingEventVideo {
        val resolver = context.contentResolver
        val reportedMimeType = resolver.getType(uri)
        if (reportedMimeType != null && !reportedMimeType.startsWith("video/", ignoreCase = true)) {
            throw EventVideoInputException(EventVideoInputException.Reason.INVALID_VIDEO)
        }

        val input = try {
            resolver.openInputStream(uri)
                ?: throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE)
        } catch (error: EventVideoInputException) {
            throw error
        } catch (error: Exception) {
            throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE, error)
        }

        val extension = extensionFor(context, uri, reportedMimeType)
        val copiedFile = try {
            input.use { copyToTemporaryFile(context, eventId, extension, it) }
        } catch (error: EventVideoInputException) {
            throw error
        } catch (error: Exception) {
            throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE, error)
        }
        return validate(eventId, copiedFile, reportedMimeType)
    }

    fun validateCaptureFile(context: Context, eventId: String, file: File): PendingEventVideo {
        val expectedDirectory = eventDirectory(context, eventId).canonicalFile
        if (file.canonicalFile.parentFile != expectedDirectory) {
            file.delete()
            throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE)
        }
        return validate(eventId, file, "video/mp4")
    }

    fun isManagedPendingFile(context: Context, eventId: String, file: File): Boolean =
        try {
            file.canonicalFile.parentFile == eventDirectory(context, eventId).canonicalFile
        } catch (_: Exception) {
            false
        }

    fun delete(pendingVideo: PendingEventVideo) {
        pendingVideo.file.delete()
    }

    private fun validate(
        eventId: String,
        file: File,
        reportedMimeType: String?
    ): PendingEventVideo {
        try {
            if (!file.isFile || file.length() <= 0L) {
                throw EventVideoInputException(EventVideoInputException.Reason.INVALID_VIDEO)
            }
            if (file.length() > MAX_SIZE_BYTES) {
                throw EventVideoInputException(EventVideoInputException.Reason.TOO_LARGE)
            }

            val retriever = MediaMetadataRetriever()
            val mimeType: String?
            val durationMs: Long?
            try {
                retriever.setDataSource(file.absolutePath)
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?: reportedMimeType
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
            } catch (error: Exception) {
                throw EventVideoInputException(EventVideoInputException.Reason.INVALID_VIDEO, error)
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }

            if (mimeType.isNullOrBlank() || !mimeType.startsWith("video/", ignoreCase = true) ||
                durationMs == null || durationMs <= 0L
            ) {
                throw EventVideoInputException(EventVideoInputException.Reason.INVALID_VIDEO)
            }
            if (durationMs > MAX_DURATION_MS) {
                throw EventVideoInputException(EventVideoInputException.Reason.TOO_LONG)
            }

            return PendingEventVideo(eventId, file, mimeType, durationMs)
        } catch (error: EventVideoInputException) {
            file.delete()
            throw error
        } catch (error: Exception) {
            file.delete()
            throw EventVideoInputException(EventVideoInputException.Reason.INVALID_VIDEO, error)
        }
    }

    private fun copyToTemporaryFile(
        context: Context,
        eventId: String,
        extension: String,
        input: InputStream
    ): File {
        val directory = eventDirectory(context, eventId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE)
        }

        val destination = try {
            File.createTempFile("selected_", ".$extension", directory)
        } catch (error: Exception) {
            throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE, error)
        }
        try {
            destination.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > MAX_SIZE_BYTES) {
                        throw EventVideoInputException(EventVideoInputException.Reason.TOO_LARGE)
                    }
                    output.write(buffer, 0, count)
                }
            }
            if (destination.length() == 0L) {
                throw EventVideoInputException(EventVideoInputException.Reason.INVALID_VIDEO)
            }
            return destination
        } catch (error: EventVideoInputException) {
            destination.delete()
            throw error
        } catch (error: Exception) {
            destination.delete()
            throw EventVideoInputException(EventVideoInputException.Reason.UNREADABLE, error)
        }
    }

    private fun extensionFor(context: Context, uri: Uri, mimeType: String?): String {
        val fromMimeType = mimeType?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
        val displayName = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
        } catch (_: Exception) {
            null
        }
        val fromName = displayName?.substringAfterLast('.', "")
        return (fromMimeType ?: fromName)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{2,8}")) }
            ?.lowercase()
            ?: "mp4"
    }

    private fun eventDirectory(context: Context, eventId: String): File {
        val safeEventId = eventId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "event" }
        return File(File(context.cacheDir, PENDING_DIRECTORY), safeEventId)
    }

    private const val PENDING_DIRECTORY = "event_video_pending"
}
