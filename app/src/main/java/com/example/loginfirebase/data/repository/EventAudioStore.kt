package com.example.loginfirebase.data.repository

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID

data class EventAudio(
    val file: File,
    val ownerUid: String
)

/** Keeps event audio in app-private storage; recordings are not uploaded or synchronized. */
object EventAudioStore {
    fun list(context: Context, eventId: String): List<EventAudio> =
        eventDirectory(context, eventId)
            .listFiles { file -> file.isFile && file.extension == AUDIO_EXTENSION && file.length() > 0L }
            ?.mapNotNull { file ->
                val ownerUid = file.nameWithoutExtension.substringBefore(OWNER_SEPARATOR)
                if (ownerUid.isBlank()) null else EventAudio(file, ownerUid)
            }
            ?.sortedByDescending { it.file.lastModified() }
            .orEmpty()

    fun createRecordingFile(context: Context, eventId: String): File {
        val directory = eventDirectory(context, eventId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create the event audio directory")
        }
        return File(directory, ".recording_${UUID.randomUUID()}.tmp")
    }

    fun publishRecording(context: Context, eventId: String, ownerUid: String, recording: File): EventAudio {
        val directory = eventDirectory(context, eventId)
        if (recording.parentFile?.canonicalFile != directory.canonicalFile || !recording.isFile || recording.length() == 0L) {
            throw IOException("The recording is unavailable")
        }
        val safeOwnerUid = ownerUid.filter(Char::isLetterOrDigit)
        if (safeOwnerUid.isBlank()) throw IOException("The audio owner is unavailable")

        val destination = File(directory, "$safeOwnerUid$OWNER_SEPARATOR${UUID.randomUUID()}.$AUDIO_EXTENSION")
        if (!recording.renameTo(destination)) {
            try {
                recording.copyTo(destination, overwrite = false)
                recording.delete()
            } catch (error: Exception) {
                destination.delete()
                throw IOException("Cannot save the event audio", error)
            }
        }
        if (!destination.isFile || destination.length() == 0L) {
            destination.delete()
            throw IOException("Cannot save the event audio")
        }
        return EventAudio(destination, safeOwnerUid)
    }

    fun delete(context: Context, eventId: String, audio: EventAudio, currentUserUid: String): Boolean {
        if (audio.ownerUid != currentUserUid) return false
        val directory = eventDirectory(context, eventId).canonicalFile
        if (audio.file.canonicalFile.parentFile != directory) return false
        return !audio.file.exists() || audio.file.delete()
    }

    private fun eventDirectory(context: Context, eventId: String): File {
        val safeEventId = eventId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "event" }
        return File(File(context.filesDir, EVENT_AUDIOS_DIRECTORY), safeEventId)
    }

    private const val EVENT_AUDIOS_DIRECTORY = "event_audios"
    private const val OWNER_SEPARATOR = "__"
    private const val AUDIO_EXTENSION = "m4a"
}
