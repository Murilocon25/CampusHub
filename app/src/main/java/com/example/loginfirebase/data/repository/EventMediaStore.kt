package com.example.loginfirebase.data.repository

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.MimeTypeMap
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

enum class EventMediaType(val key: String) {
    PHOTO("photo"), AUDIO("audio"), VIDEO("video");

    companion object {
        fun fromKey(key: String?): EventMediaType? = entries.firstOrNull { it.key == key }
    }
}

data class EventMedia(
    val id: String,
    val eventId: String,
    val type: EventMediaType,
    val authorUid: String,
    val authorName: String,
    val createdAtMillis: Long,
    val storagePath: String,
    val mimeType: String,
    val durationMs: Long? = null,
    val sizeBytes: Long = 0L
)

/** Stores event media in app-private files on this device only. */
object EventMediaStore {
    private const val MEDIA_DIRECTORY = "event_media_local"
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun list(
        context: Context,
        eventId: String,
        type: EventMediaType,
        onResult: (Result<List<EventMedia>>) -> Unit
    ) {
        val uid = currentUid()
        if (uid == null) {
            onResult(Result.failure(SecurityException("A signed-in user is required")))
            return
        }

        runIo(onResult) {
            val directory = eventDirectory(context, uid, eventId)
            val metadataFiles = directory.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                .orEmpty()

            metadataFiles.mapNotNull(::readMedia)
                .filter { media ->
                    media.eventId == eventId && media.authorUid == uid && media.type == type &&
                        runCatching { mediaFile(context, media).isFile }.getOrDefault(false)
                }
                .sortedByDescending(EventMedia::createdAtMillis)
        }
    }

    fun get(
        context: Context,
        eventId: String,
        mediaId: String,
        onResult: (Result<EventMedia>) -> Unit
    ) {
        val uid = currentUid()
        if (uid == null) {
            onResult(Result.failure(SecurityException("A signed-in user is required")))
            return
        }

        runIo(onResult) {
            val record = readMedia(metadataFile(context, uid, eventId, mediaId))
                ?.takeIf { it.id == mediaId && it.eventId == eventId && it.authorUid == uid }
                ?: throw IOException("Media is unavailable on this device")
            if (!mediaFile(context, record).isFile) {
                throw IOException("Media file is unavailable on this device")
            }
            record
        }
    }

    fun publishFile(
        context: Context,
        eventId: String,
        type: EventMediaType,
        file: File,
        authorUid: String,
        authorName: String,
        mimeType: String,
        durationMs: Long? = null,
        onResult: (Result<EventMedia>) -> Unit
    ) {
        if (!file.isFile || file.length() <= 0L) {
            onResult(Result.failure(IOException("Media file is empty or unavailable")))
            return
        }
        publishUri(
            context = context,
            eventId = eventId,
            type = type,
            uri = Uri.fromFile(file),
            authorUid = authorUid,
            authorName = authorName,
            mimeType = mimeType,
            durationMs = durationMs,
            onResult = onResult
        )
    }

    fun publishUri(
        context: Context,
        eventId: String,
        type: EventMediaType,
        uri: Uri,
        authorUid: String,
        authorName: String,
        mimeType: String? = context.contentResolver.getType(uri),
        durationMs: Long? = null,
        onResult: (Result<EventMedia>) -> Unit
    ) {
        val uid = currentUid()
        val normalizedName = authorName.trim()
        val resolvedMime = mimeType?.takeIf { matchesType(type, it) }
        if (uid.isNullOrBlank() || uid != authorUid || normalizedName.isBlank() || resolvedMime == null) {
            onResult(Result.failure(SecurityException("Media author or type is invalid")))
            return
        }

        runIo(onResult) {
            val mediaId = UUID.randomUUID().toString()
            val filename = "$mediaId.${extensionFor(resolvedMime)}"
            val directory = eventDirectory(context, uid, eventId)
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Could not create local media directory")
            }
            val destination = File(directory, filename)

            try {
                openInputStream(context, uri).use { input ->
                    destination.outputStream().buffered().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE * 8)
                    }
                }
                val sizeBytes = destination.length()
                if (sizeBytes <= 0L) throw IOException("Media file is empty")

                val media = EventMedia(
                    id = mediaId,
                    eventId = eventId,
                    type = type,
                    authorUid = uid,
                    authorName = normalizedName,
                    createdAtMillis = System.currentTimeMillis(),
                    storagePath = filename,
                    mimeType = resolvedMime,
                    durationMs = durationMs,
                    sizeBytes = sizeBytes
                )
                writeMetadata(context, media)
                media
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
        }
    }

    fun downloadUrl(
        context: Context,
        media: EventMedia,
        onResult: (Result<Uri>) -> Unit
    ) {
        runIo(onResult) {
            requireCurrentOwner(media)
            val file = mediaFile(context, media)
            if (!file.isFile) throw IOException("Media file is unavailable on this device")
            Uri.fromFile(file)
        }
    }

    fun downloadToFile(
        context: Context,
        media: EventMedia,
        destination: File,
        onResult: (Result<File>) -> Unit
    ) {
        runIo(onResult) {
            requireCurrentOwner(media)
            val source = mediaFile(context, media)
            if (!source.isFile) throw IOException("Media file is unavailable on this device")
            if (source.canonicalFile != destination.canonicalFile) {
                source.inputStream().buffered().use { input ->
                    destination.outputStream().buffered().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE * 8)
                    }
                }
            }
            destination
        }
    }

    fun delete(
        context: Context,
        media: EventMedia,
        currentUserUid: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        runIo(onResult) {
            requireCurrentOwner(media)
            if (!isOwnedBy(media, currentUserUid)) {
                throw SecurityException("Only the local author can delete this media")
            }
            val file = mediaFile(context, media)
            val record = metadataFile(context, currentUserUid, media.eventId, media.id)
            if (file.exists() && !file.delete()) throw IOException("Could not delete local media file")
            if (record.exists() && !record.delete()) throw IOException("Could not delete local media record")
            Unit
        }
    }

    fun isOwnedBy(media: EventMedia, uid: String): Boolean =
        uid.isNotBlank() && media.authorUid == uid

    internal fun matchesType(type: EventMediaType, mimeType: String): Boolean = when (type) {
        EventMediaType.PHOTO -> mimeType.startsWith("image/", ignoreCase = true)
        EventMediaType.AUDIO -> mimeType.startsWith("audio/", ignoreCase = true)
        EventMediaType.VIDEO -> mimeType.startsWith("video/", ignoreCase = true)
    }

    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun requireCurrentOwner(media: EventMedia) {
        if (!isOwnedBy(media, currentUid().orEmpty())) {
            throw SecurityException("Media belongs to another local user")
        }
    }

    private fun eventDirectory(context: Context, uid: String, eventId: String): File {
        val root = File(context.applicationContext.filesDir, MEDIA_DIRECTORY)
        val userDirectory = File(root, encodePathSegment(uid))
        return File(userDirectory, encodePathSegment(eventId))
    }

    private fun metadataFile(context: Context, uid: String, eventId: String, mediaId: String): File {
        require(mediaId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid local media id" }
        return File(eventDirectory(context, uid, eventId), "$mediaId.json")
    }

    private fun mediaFile(context: Context, media: EventMedia): File {
        if (media.storagePath.isBlank() || media.storagePath != File(media.storagePath).name) {
            throw SecurityException("Invalid local media path")
        }
        val directory = eventDirectory(context, media.authorUid, media.eventId).canonicalFile
        val file = File(directory, media.storagePath).canonicalFile
        if (file.parentFile != directory) throw SecurityException("Invalid local media path")
        return file
    }

    private fun encodePathSegment(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private fun openInputStream(context: Context, uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.inputStream()
            ?: throw IOException("Media file is unavailable")
        else -> context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open selected media")
    }

    private fun writeMetadata(context: Context, media: EventMedia) {
        val record = metadataFile(context, media.authorUid, media.eventId, media.id)
        val temporaryRecord = File(record.parentFile, "${media.id}.json.tmp")
        val json = JSONObject()
            .put("id", media.id)
            .put("eventId", media.eventId)
            .put("type", media.type.key)
            .put("authorUid", media.authorUid)
            .put("authorName", media.authorName)
            .put("createdAtMillis", media.createdAtMillis)
            .put("storagePath", media.storagePath)
            .put("mimeType", media.mimeType)
            .put("durationMs", media.durationMs ?: 0L)
            .put("sizeBytes", media.sizeBytes)

        try {
            temporaryRecord.writeText(json.toString(), Charsets.UTF_8)
            if (!temporaryRecord.renameTo(record)) throw IOException("Could not save local media record")
        } finally {
            temporaryRecord.delete()
        }
    }

    private fun readMedia(record: File): EventMedia? = runCatching {
        val json = JSONObject(record.readText(Charsets.UTF_8))
        val id = json.optString("id")
        val type = EventMediaType.fromKey(json.optString("type")) ?: return@runCatching null
        if (id != record.name.removeSuffix(".json")) return@runCatching null
        EventMedia(
            id = id,
            eventId = json.optString("eventId"),
            type = type,
            authorUid = json.optString("authorUid"),
            authorName = json.optString("authorName"),
            createdAtMillis = json.optLong("createdAtMillis"),
            storagePath = json.optString("storagePath"),
            mimeType = json.optString("mimeType"),
            durationMs = json.optLong("durationMs").takeIf { it > 0L },
            sizeBytes = json.optLong("sizeBytes")
        ).takeIf { it.eventId.isNotBlank() && it.authorUid.isNotBlank() && it.storagePath.isNotBlank() }
    }.getOrNull()

    private fun extensionFor(mimeType: String): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{2,8}")) }
            ?.lowercase()
            ?: "bin"

    private fun <T> runIo(onResult: (Result<T>) -> Unit, action: () -> T) {
        ioExecutor.execute {
            val result = runCatching(action)
            mainHandler.post { onResult(result) }
        }
    }
}
