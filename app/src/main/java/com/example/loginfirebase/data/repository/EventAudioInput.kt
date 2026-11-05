package com.example.loginfirebase.data.repository

import java.io.File
import java.io.IOException

data class PendingEventAudioRecording(
    val eventId: String,
    val file: File
)

class EventAudioInputException(
    val reason: Reason,
    cause: Throwable? = null
) : IOException(cause) {
    enum class Reason {
        TOO_SHORT,
        INVALID_RECORDING,
        UNREADABLE
    }
}

/** Creates and validates temporary audio recordings for event posts. */
object EventAudioInput {
    const val MAX_DURATION_MS = 60_000L

    fun createCaptureFile(cacheDir: File, eventId: String): File {
        val directory = eventDirectory(cacheDir, eventId)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw EventAudioInputException(EventAudioInputException.Reason.UNREADABLE)
        }
        return try {
            File.createTempFile("recording_", ".m4a", directory)
        } catch (error: Exception) {
            throw EventAudioInputException(EventAudioInputException.Reason.UNREADABLE, error)
        }
    }

    fun validateCaptureFile(
        cacheDir: File,
        eventId: String,
        file: File
    ): PendingEventAudioRecording {
        val (expectedDirectory, actualParent) = try {
            eventDirectory(cacheDir, eventId).canonicalFile to file.canonicalFile.parentFile
        } catch (error: Exception) {
            throw EventAudioInputException(EventAudioInputException.Reason.UNREADABLE, error)
        }
        if (actualParent != expectedDirectory) {
            throw EventAudioInputException(EventAudioInputException.Reason.INVALID_RECORDING)
        }
        if (!file.isFile) {
            throw EventAudioInputException(EventAudioInputException.Reason.INVALID_RECORDING)
        }
        if (file.length() <= 0L) {
            throw EventAudioInputException(EventAudioInputException.Reason.TOO_SHORT)
        }
        return PendingEventAudioRecording(eventId, file)
    }

    private fun eventDirectory(cacheDir: File, eventId: String): File {
        val safeEventId = eventId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "event" }
        return File(File(cacheDir, PENDING_DIRECTORY), safeEventId)
    }

    private const val PENDING_DIRECTORY = "event_audio_pending"
}
