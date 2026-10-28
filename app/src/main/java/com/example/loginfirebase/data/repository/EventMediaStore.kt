package com.example.loginfirebase.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.io.IOException
import java.util.UUID

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

/** Stores shared event media in Firebase Storage and its public metadata in Firestore. */
object EventMediaStore {
    private val db get() = FirebaseFirestoreProvider.db
    private val storage get() = FirebaseStorage.getInstance()

    fun list(
        eventId: String,
        type: EventMediaType,
        onResult: (Result<List<EventMedia>>) -> Unit
    ) {
        eventMediaCollection(eventId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents.mapNotNull(::fromDocument)
                    .filter { it.type == type }
                onResult(Result.success(items))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun get(
        eventId: String,
        mediaId: String,
        onResult: (Result<EventMedia>) -> Unit
    ) {
        eventMediaCollection(eventId).document(mediaId).get()
            .addOnSuccessListener { snapshot ->
                val media = fromDocument(snapshot)
                if (media == null) onResult(Result.failure(IOException("Media is unavailable")))
                else onResult(Result.success(media))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
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
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val normalizedName = authorName.trim()
        val resolvedMime = mimeType?.takeIf { matchesType(type, it) }
        if (uid.isNullOrBlank() || uid != authorUid || normalizedName.isBlank() || resolvedMime == null) {
            onResult(Result.failure(SecurityException("Media author or type is invalid")))
            return
        }

        val mediaId = UUID.randomUUID().toString()
        val extension = extensionFor(resolvedMime)
        val storagePath = "event_media/$eventId/$uid/$mediaId.$extension"
        val storageReference = storage.reference.child(storagePath)
        val metadata = StorageMetadata.Builder().setContentType(resolvedMime).build()
        val media = EventMedia(
            id = mediaId,
            eventId = eventId,
            type = type,
            authorUid = uid,
            authorName = normalizedName,
            createdAtMillis = System.currentTimeMillis(),
            storagePath = storagePath,
            mimeType = resolvedMime,
            durationMs = durationMs
        )

        storageReference.putFile(uri, metadata)
            .addOnSuccessListener { upload ->
                val savedMedia = media.copy(sizeBytes = upload.metadata?.sizeBytes ?: 0L)
                eventMediaCollection(eventId).document(mediaId)
                    .set(toDocument(savedMedia))
                    .addOnSuccessListener { onResult(Result.success(savedMedia)) }
                    .addOnFailureListener { writeError ->
                        storageReference.delete().addOnCompleteListener {
                            onResult(Result.failure(writeError))
                        }
                    }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun downloadUrl(media: EventMedia, onResult: (Result<Uri>) -> Unit) {
        storage.reference.child(media.storagePath).downloadUrl
            .addOnSuccessListener { onResult(Result.success(it)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun downloadToFile(media: EventMedia, destination: File, onResult: (Result<File>) -> Unit) {
        storage.reference.child(media.storagePath).getFile(destination)
            .addOnSuccessListener { onResult(Result.success(destination)) }
            .addOnFailureListener {
                destination.delete()
                onResult(Result.failure(it))
            }
    }

    fun delete(media: EventMedia, currentUserUid: String, onResult: (Result<Unit>) -> Unit) {
        if (!isOwnedBy(media, currentUserUid) ||
            media.storagePath.substringBeforeLast('/') != "event_media/${media.eventId}/$currentUserUid"
        ) {
            onResult(Result.failure(SecurityException("Only the author can delete this media")))
            return
        }

        storage.reference.child(media.storagePath).delete()
            .addOnSuccessListener {
                eventMediaCollection(media.eventId).document(media.id).delete()
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun isOwnedBy(media: EventMedia, uid: String): Boolean =
        uid.isNotBlank() && media.authorUid == uid

    private fun eventMediaCollection(eventId: String) =
        db.collection("events").document(eventId).collection("media")

    private fun toDocument(media: EventMedia): Map<String, Any> = mapOf(
        "type" to media.type.key,
        "authorUid" to media.authorUid,
        "authorName" to media.authorName,
        "createdAt" to FieldValue.serverTimestamp(),
        "storagePath" to media.storagePath,
        "mimeType" to media.mimeType,
        "sizeBytes" to media.sizeBytes,
        "durationMs" to (media.durationMs ?: 0L)
    )

    private fun fromDocument(snapshot: com.google.firebase.firestore.DocumentSnapshot): EventMedia? {
        if (!snapshot.exists()) return null
        val type = EventMediaType.fromKey(snapshot.getString("type")) ?: return null
        val eventId = snapshot.reference.parent.parent?.id ?: return null
        val createdAt = snapshot.getTimestamp("createdAt")
            ?: snapshot.get("createdAt") as? Timestamp
        return EventMedia(
            id = snapshot.id,
            eventId = eventId,
            type = type,
            authorUid = snapshot.getString("authorUid").orEmpty(),
            authorName = snapshot.getString("authorName").orEmpty().ifBlank { "Autor desconhecido" },
            createdAtMillis = createdAt?.toDate()?.time ?: 0L,
            storagePath = snapshot.getString("storagePath").orEmpty(),
            mimeType = snapshot.getString("mimeType").orEmpty(),
            durationMs = snapshot.getLong("durationMs")?.takeIf { it > 0L },
            sizeBytes = snapshot.getLong("sizeBytes") ?: 0L
        ).takeIf { it.authorUid.isNotBlank() && it.storagePath.isNotBlank() }
    }

    private fun matchesType(type: EventMediaType, mimeType: String): Boolean = when (type) {
        EventMediaType.PHOTO -> mimeType.startsWith("image/", ignoreCase = true)
        EventMediaType.AUDIO -> mimeType.startsWith("audio/", ignoreCase = true)
        EventMediaType.VIDEO -> mimeType.startsWith("video/", ignoreCase = true)
    }

    private fun extensionFor(mimeType: String): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{2,8}")) }
            ?.lowercase()
            ?: "bin"
}
