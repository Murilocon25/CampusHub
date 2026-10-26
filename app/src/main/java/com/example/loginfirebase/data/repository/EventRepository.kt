package com.example.loginfirebase.data.repository

import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.model.toCampusEvent
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.Timestamp
import com.google.android.gms.tasks.Task

class EventRepository {
    private val db get() = FirebaseFirestoreProvider.db

    private fun event(eventId: String) = db.collection("events").document(eventId)

    fun getEvent(eventId: String): Task<DocumentSnapshot> = event(eventId).get()

    fun createEvent(
        title: String,
        date: String,
        place: String,
        description: String,
        category: String,
        startAt: Timestamp,
        creatorUid: String,
        location: GeoPoint
    ): Task<DocumentReference> = db.collection("events").add(mapOf(
        "title" to title,
        "date" to date,
        "place" to place,
        "description" to description,
        "category" to category,
        "startAt" to startAt,
        "creatorUid" to creatorUid,
        "location" to location,
        "createdAt" to FieldValue.serverTimestamp()
    ))

    fun getRatings(eventId: String): Task<QuerySnapshot> = event(eventId).collection("ratings").get()
    fun getComments(eventId: String): Task<QuerySnapshot> =
        event(eventId).collection("comments").orderBy("createdAt", Query.Direction.ASCENDING).get()
    fun getProfile(uid: String): Task<DocumentSnapshot> = db.collection("users").document(uid).get()

    fun publishComment(eventId: String, uid: String, authorName: String, text: String): Task<DocumentReference> =
        event(eventId).collection("comments").add(mapOf(
            "authorUid" to uid,
            "authorName" to authorName,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ))

    fun updateComment(eventId: String, commentId: String, text: String): Task<Void> =
        event(eventId).collection("comments").document(commentId).update(mapOf(
            "text" to text,
            "updatedAt" to FieldValue.serverTimestamp()
        ))

    fun deleteComment(eventId: String, commentId: String): Task<Void> =
        event(eventId).collection("comments").document(commentId).delete()

    fun getFavorite(uid: String, eventId: String): Task<DocumentSnapshot> =
        db.collection("users").document(uid).collection("favorites").document(eventId).get()

    fun saveFavorite(uid: String, eventId: String): Task<Void> =
        db.collection("users").document(uid).collection("favorites").document(eventId).set(mapOf(
            "eventId" to eventId,
            "savedAt" to FieldValue.serverTimestamp()
        ))

    fun removeFavorite(uid: String, eventId: String): Task<Void> =
        db.collection("users").document(uid).collection("favorites").document(eventId).delete()

    fun getRegistration(uid: String, eventId: String): Task<DocumentSnapshot> =
        db.collection("users").document(uid).collection("registrations").document(eventId).get()

    fun register(uid: String, event: CampusEvent): Task<Void> =
        db.collection("users").document(uid).collection("registrations").document(event.id).set(mapOf(
            "eventId" to event.id,
            "eventTitle" to event.title,
            "registeredAt" to FieldValue.serverTimestamp()
        ))

    fun cancelRegistration(uid: String, eventId: String): Task<Void> =
        db.collection("users").document(uid).collection("registrations").document(eventId).delete()

    fun getRating(uid: String, eventId: String): Task<DocumentSnapshot> =
        event(eventId).collection("ratings").document(uid).get()

    fun saveRating(uid: String, eventId: String, score: Int): Task<Void> =
        event(eventId).collection("ratings").document(uid).set(mapOf(
            "userId" to uid,
            "score" to score,
            "updatedAt" to FieldValue.serverTimestamp()
        ))

    fun getEvents(onResult: (Result<List<CampusEvent>>) -> Unit) {
        db.collection("events").get()
            .addOnSuccessListener { snapshot ->
                val manuallyCreatedEvents = snapshot.documents
                    .map { it.toCampusEvent() }
                    .filter { it.creatorUid.isNotBlank() }
                onResult(Result.success(manuallyCreatedEvents))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun getUserEvents(uid: String, collection: String, onResult: (Result<List<CampusEvent>>) -> Unit) {
        db.collection("users").document(uid).collection(collection).get()
            .addOnSuccessListener { snapshot ->
                val eventIds = snapshot.documents.map { it.id }
                if (eventIds.isEmpty()) {
                    onResult(Result.success(emptyList()))
                    return@addOnSuccessListener
                }
                val queries = eventIds.chunked(30).map { ids ->
                    db.collection("events").whereIn(FieldPath.documentId(), ids).get()
                }
                Tasks.whenAllSuccess<QuerySnapshot>(queries)
                    .addOnSuccessListener { batches ->
                        val eventsById = batches.flatMap { it.documents }
                            .associate { document -> document.id to document.toCampusEvent() }
                        onResult(Result.success(eventIds.mapNotNull(eventsById::get)))
                    }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}
