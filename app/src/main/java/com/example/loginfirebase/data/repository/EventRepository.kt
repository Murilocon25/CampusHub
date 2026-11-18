package com.example.loginfirebase.data.repository

import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.model.toCampusEvent
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult

data class EventRegistrationResult(val data: Map<String, Any?>)

enum class EventRegistrationFailureReason {
    ACCOUNT_INVALID,
    EVENT_CLOSED,
    EVENT_FULL,
    WAITLIST_REQUIRES_SERVER,
    REGISTRATION_MISSING
}

class EventRegistrationException(val reason: EventRegistrationFailureReason) : Exception()

interface EventListDataSource {
    fun getEvents(onResult: (Result<List<CampusEvent>>) -> Unit)

    fun getEventsForUser(uid: String, isOrganizer: Boolean, onResult: (Result<List<CampusEvent>>) -> Unit) =
        getEvents(onResult)

    fun getUserEvents(uid: String, collection: String, onResult: (Result<List<CampusEvent>>) -> Unit)
}

interface EventDetailDataSource {
    fun loadEvent(eventId: String, onResult: (Result<CampusEvent?>) -> Unit)
}

class EventRepository(
    private val firestore: FirebaseFirestore? = null,
    private val functions: FirebaseFunctions? = null
) : EventListDataSource, EventDetailDataSource {
    private val db get() = firestore ?: FirebaseFirestoreProvider.db
    private val cloudFunctions get() = functions ?: FirebaseFunctions.getInstance("southamerica-east1")

    private fun event(eventId: String) = db.collection("events").document(eventId)

    fun getEvent(eventId: String): Task<DocumentSnapshot> = event(eventId).get()

    fun observeEvent(
        eventId: String,
        onResult: (Result<DocumentSnapshot>) -> Unit
    ): ListenerRegistration = event(eventId).addSnapshotListener { snapshot, error ->
        if (error != null) onResult(Result.failure(error))
        else if (snapshot == null) onResult(Result.failure(IllegalStateException("Event snapshot was empty")))
        else onResult(Result.success(snapshot))
    }

    override fun loadEvent(eventId: String, onResult: (Result<CampusEvent?>) -> Unit) {
        getEvent(eventId)
            .addOnSuccessListener { snapshot ->
                val campusEvent = if (snapshot.exists()) snapshot.toCampusEvent() else null
                onResult(Result.success(campusEvent))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun createEvent(
        title: String,
        date: String,
        place: String,
        description: String,
        category: String,
        startAt: Timestamp,
        creatorUid: String,
        location: GeoPoint,
        capacity: Int = 100,
        coverImageUrl: String = "https://example.test/event-cover.png"
    ): Task<DocumentReference> = db.collection("events").add(mapOf(
        "title" to title,
        "date" to date,
        "place" to place,
        "description" to description,
        "category" to category,
        "startAt" to startAt,
        "creatorUid" to creatorUid,
        "location" to location,
        "capacity" to capacity,
        "coverImageUrl" to coverImageUrl,
        "status" to "DRAFT",
        "confirmedCount" to 0,
        "waitingCount" to 0,
        "reservedCount" to 0,
        "registrationSequence" to 0,
        "createdAt" to FieldValue.serverTimestamp()
    ))

    fun updateEvent(
        eventId: String,
        title: String,
        date: String,
        place: String,
        description: String,
        category: String,
        startAt: Timestamp,
        location: GeoPoint,
        capacity: Int = 100,
        coverImageUrl: String = "https://example.test/event-cover.png"
    ): Task<Void> = event(eventId).update(mapOf(
        "title" to title,
        "date" to date,
        "place" to place,
        "description" to description,
        "category" to category,
        "startAt" to startAt,
        "location" to location,
        "capacity" to capacity,
        "coverImageUrl" to coverImageUrl
    ))

    fun publishEvent(eventId: String): Task<Void> = event(eventId).update(mapOf(
        "status" to "PUBLISHED",
        "publishedAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ))

    fun endEvent(eventId: String): Task<Void> = event(eventId).update(mapOf(
        "status" to "ENDED",
        "endedAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
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

    fun observeUserRegistrations(
        uid: String,
        onResult: (Result<QuerySnapshot>) -> Unit
    ): ListenerRegistration = db.collection("users").document(uid).collection("registrations")
        .addSnapshotListener { snapshot, error ->
            if (error != null) onResult(Result.failure(error))
            else if (snapshot == null) onResult(Result.failure(IllegalStateException("Registration snapshot was empty")))
            else onResult(Result.success(snapshot))
        }

    fun register(uid: String, campusEvent: CampusEvent): Task<EventRegistrationResult> = db.runTransaction { transaction ->
        val eventRef = event(campusEvent.id)
        val eventRegistrationRef = eventRef.collection("registrations").document(uid)
        val userRegistrationRef = db.collection("users").document(uid)
            .collection("registrations").document(campusEvent.id)
        val profileRef = db.collection("users").document(uid)
        val eventSnapshot = transaction.get(eventRef)
        val profile = transaction.get(profileRef)
        val eventRegistration = transaction.get(eventRegistrationRef)
        val userRegistration = transaction.get(userRegistrationRef)

        if (!eventSnapshot.exists() || !profile.exists() ||
            UserRole.fromFirestore(profile.getString("role")) != UserRole.STUDENT ||
            FirebaseAuth.getInstance().currentUser?.uid != uid
        ) throw EventRegistrationException(EventRegistrationFailureReason.ACCOUNT_INVALID)

        val activeStatuses = setOf("CONFIRMADA", "LISTA_ESPERA", "AGUARDANDO_CONFIRMACAO", "PRESENTE")
        val currentStatus = eventRegistration.getString("status")
        val mirroredStatus = userRegistration.getString("status")
        if (currentStatus in activeStatuses || mirroredStatus in activeStatuses) {
            if (currentStatus != mirroredStatus || currentStatus !in activeStatuses) {
                throw EventRegistrationException(EventRegistrationFailureReason.REGISTRATION_MISSING)
            }
            return@runTransaction EventRegistrationResult(mapOf("status" to currentStatus!!))
        }

        val startAt = eventSnapshot.getTimestamp("startAt")
        if (eventSnapshot.getString("status") != "PUBLISHED" || startAt == null ||
            startAt.toDate().time <= System.currentTimeMillis()
        ) throw EventRegistrationException(EventRegistrationFailureReason.EVENT_CLOSED)

        val capacity = eventSnapshot.getLong("capacity")?.toInt() ?: 0
        val confirmed = eventSnapshot.getLong("confirmedCount")?.toInt() ?: 0
        val waiting = eventSnapshot.getLong("waitingCount")?.toInt() ?: 0
        val reserved = eventSnapshot.getLong("reservedCount")?.toInt() ?: 0
        if (waiting > 0 || reserved > 0) {
            throw EventRegistrationException(EventRegistrationFailureReason.WAITLIST_REQUIRES_SERVER)
        }
        if (capacity < 1 || confirmed + reserved >= capacity) {
            throw EventRegistrationException(EventRegistrationFailureReason.EVENT_FULL)
        }

        val nextSequence = (eventSnapshot.getLong("registrationSequence") ?: 0L) + 1L
        val profileName = profile.getString("name").orEmpty()
        val name = profileName.ifEmpty { "Aluno" }
        val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        val eventTitle = eventSnapshot.getString("title").orEmpty()
        val registeredAt = FieldValue.serverTimestamp()
        val updatedAt = FieldValue.serverTimestamp()
        transaction.set(eventRegistrationRef, mapOf(
            "eventId" to campusEvent.id,
            "eventTitle" to eventTitle,
            "userId" to uid,
            "studentName" to name,
            "studentEmail" to email,
            "recordType" to "event",
            "status" to "CONFIRMADA",
            "queuePosition" to nextSequence,
            "registeredAt" to registeredAt,
            "updatedAt" to updatedAt,
            "confirmationExpiresAt" to null,
            "checkedInAt" to null,
            "qrTokenHash" to null
        ))
        transaction.set(userRegistrationRef, mapOf(
            "eventId" to campusEvent.id,
            "eventTitle" to eventTitle,
            "userId" to uid,
            "recordType" to "user",
            "status" to "CONFIRMADA",
            "registeredAt" to FieldValue.serverTimestamp(),
            "confirmationExpiresAt" to null,
            "checkedInAt" to null,
            "updatedAt" to FieldValue.serverTimestamp()
        ))
        transaction.update(eventRef, mapOf(
            "confirmedCount" to confirmed + 1,
            "registrationSequence" to nextSequence,
            "updatedAt" to FieldValue.serverTimestamp()
        ))
        EventRegistrationResult(mapOf("status" to "CONFIRMADA"))
    }

    fun cancelRegistration(uid: String, eventId: String): Task<EventRegistrationResult> = db.runTransaction { transaction ->
        val eventRef = event(eventId)
        val eventRegistrationRef = eventRef.collection("registrations").document(uid)
        val userRegistrationRef = db.collection("users").document(uid)
            .collection("registrations").document(eventId)
        val attendanceRequestRef = eventRef.collection("attendanceRequests").document(uid)
        val eventSnapshot = transaction.get(eventRef)
        val eventRegistration = transaction.get(eventRegistrationRef)
        val userRegistration = transaction.get(userRegistrationRef)
        val attendanceRequest = transaction.get(attendanceRequestRef)
        if (!eventSnapshot.exists() || !eventRegistration.exists() || !userRegistration.exists() ||
            FirebaseAuth.getInstance().currentUser?.uid != uid
        ) throw EventRegistrationException(EventRegistrationFailureReason.REGISTRATION_MISSING)

        val status = eventRegistration.getString("status").orEmpty()
        if (userRegistration.getString("status") != status || status == "PRESENTE" ||
            status !in setOf("CONFIRMADA", "LISTA_ESPERA", "AGUARDANDO_CONFIRMACAO")
        ) throw EventRegistrationException(EventRegistrationFailureReason.REGISTRATION_MISSING)

        val confirmed = eventSnapshot.getLong("confirmedCount")?.toInt() ?: 0
        val waiting = eventSnapshot.getLong("waitingCount")?.toInt() ?: 0
        val reserved = eventSnapshot.getLong("reservedCount")?.toInt() ?: 0
        val nextConfirmed = confirmed - if (status == "CONFIRMADA") 1 else 0
        val nextWaiting = waiting - if (status == "LISTA_ESPERA") 1 else 0
        val nextReserved = reserved - if (status == "AGUARDANDO_CONFIRMACAO") 1 else 0
        if (nextConfirmed < 0 || nextWaiting < 0 || nextReserved < 0) {
            throw EventRegistrationException(EventRegistrationFailureReason.REGISTRATION_MISSING)
        }

        transaction.update(eventRef, mapOf(
            "confirmedCount" to nextConfirmed,
            "waitingCount" to nextWaiting,
            "reservedCount" to nextReserved,
            "updatedAt" to FieldValue.serverTimestamp()
        ))
        transaction.update(eventRegistrationRef, mapOf(
            "status" to "CANCELADA",
            "cancellationReason" to "student_cancelled",
            "confirmationExpiresAt" to null,
            "qrTokenHash" to null,
            "updatedAt" to FieldValue.serverTimestamp()
        ))
        transaction.update(userRegistrationRef, mapOf(
            "status" to "CANCELADA",
            "confirmationExpiresAt" to null,
            "updatedAt" to FieldValue.serverTimestamp()
        ))
        if (attendanceRequest.exists()) transaction.delete(attendanceRequestRef)
        EventRegistrationResult(mapOf("status" to "CANCELADA"))
    }

    fun confirmWaitlist(eventId: String): Task<HttpsCallableResult> =
        cloudFunctions.getHttpsCallable("confirmWaitlist").call(mapOf("eventId" to eventId))

    fun requestAttendance(eventId: String, studentUid: String): Task<String> = db.runTransaction { transaction ->
        val eventRef = event(eventId)
        val profileRef = db.collection("users").document(studentUid)
        val registrationRef = eventRef.collection("registrations").document(studentUid)
        val requestRef = eventRef.collection("attendanceRequests").document(studentUid)
        val eventSnapshot = transaction.get(eventRef)
        val profile = transaction.get(profileRef)
        val registration = transaction.get(registrationRef)
        val existingRequest = transaction.get(requestRef)

        if (!profile.exists() || FirebaseAuth.getInstance().currentUser?.uid != studentUid ||
            UserRole.fromFirestore(profile.getString("role")) != UserRole.STUDENT
        ) throw SecurityException("Somente o aluno autenticado pode solicitar presença.")
        if (!eventSnapshot.exists() || !registration.exists()) {
            throw IllegalStateException("Inscrição não encontrada.")
        }
        if (eventSnapshot.getString("status") !in setOf("PUBLISHED", "ENDED")) {
            throw IllegalStateException("O evento não está disponível para check-in.")
        }

        when (registration.getString("status")) {
            "PRESENTE" -> "PRESENTE"
            "CONFIRMADA" -> {
                if (existingRequest.exists() && existingRequest.getString("status") == "PENDING") {
                    return@runTransaction "PENDING"
                }
                val profileName = profile.getString("name")?.takeIf(String::isNotBlank) ?: "Aluno"
                transaction.set(requestRef, mapOf(
                    "eventId" to eventId,
                    "userId" to studentUid,
                    "studentName" to profileName,
                    "studentEmail" to FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                    "status" to "PENDING",
                    "requestedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ))
                "PENDING"
            }
            else -> throw IllegalStateException("Somente inscrições confirmadas podem solicitar presença.")
        }
    }

    fun confirmAttendanceRequest(eventId: String, studentUid: String, organizerUid: String): Task<Void> {
        val eventRef = event(eventId)
        val eventRegistrationRef = eventRef.collection("registrations").document(studentUid)
        val userRegistrationRef = db.collection("users").document(studentUid)
            .collection("registrations").document(eventId)
        val requestRef = eventRef.collection("attendanceRequests").document(studentUid)
        val serverTime = FieldValue.serverTimestamp()
        return db.batch()
            .update(eventRegistrationRef, mapOf(
                "status" to "PRESENTE",
                "checkedInAt" to serverTime,
                "checkedInBy" to organizerUid,
                "qrTokenHash" to null,
                "updatedAt" to serverTime,
            ))
            .update(userRegistrationRef, mapOf(
                "status" to "PRESENTE",
                "checkedInAt" to serverTime,
                "updatedAt" to serverTime,
            ))
            .update(requestRef, mapOf(
                "status" to "APPROVED",
                "decidedAt" to serverTime,
                "decidedBy" to organizerUid,
                "updatedAt" to serverTime,
            ))
            .commit()
    }

    fun observeAttendanceRequests(
        eventId: String,
        onResult: (Result<QuerySnapshot>) -> Unit
    ): ListenerRegistration = event(eventId)
        .collection("attendanceRequests")
        .whereEqualTo("status", "PENDING")
        .addSnapshotListener { snapshot, error ->
            if (error != null) onResult(Result.failure(error))
            else if (snapshot == null) onResult(Result.failure(IllegalStateException("Attendance request snapshot was empty")))
            else onResult(Result.success(snapshot))
        }

    fun getAttendanceRequest(eventId: String, studentUid: String): Task<DocumentSnapshot> =
        event(eventId).collection("attendanceRequests").document(studentUid).get()

    fun getRegistrationQr(eventId: String): Task<HttpsCallableResult> =
        cloudFunctions.getHttpsCallable("getRegistrationQr").call(mapOf("eventId" to eventId))

    fun validateCheckIn(eventId: String, qrPayload: String): Task<HttpsCallableResult> =
        cloudFunctions.getHttpsCallable("validateCheckIn").call(mapOf("eventId" to eventId, "qrPayload" to qrPayload))

    fun getParticipants(eventId: String): Task<QuerySnapshot> = event(eventId)
        .collection("registrations")
        .orderBy("registeredAt", Query.Direction.ASCENDING)
        .get()

    fun observeParticipants(
        eventId: String,
        onResult: (Result<QuerySnapshot>) -> Unit
    ): ListenerRegistration = event(eventId)
        .collection("registrations")
        .orderBy("registeredAt", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) onResult(Result.failure(error))
            else if (snapshot == null) onResult(Result.failure(IllegalStateException("Registration snapshot was empty")))
            else onResult(Result.success(snapshot))
        }

    fun getRating(uid: String, eventId: String): Task<DocumentSnapshot> =
        event(eventId).collection("ratings").document(uid).get()

    fun saveRating(uid: String, eventId: String, score: Int): Task<Void> =
        event(eventId).collection("ratings").document(uid).set(mapOf(
            "userId" to uid,
            "score" to score,
            "updatedAt" to FieldValue.serverTimestamp()
        ))

    override fun getEvents(onResult: (Result<List<CampusEvent>>) -> Unit) {
        db.collection("events").whereEqualTo("status", "PUBLISHED").get()
            .addOnSuccessListener { snapshot ->
                val events = snapshot.documents.map { it.toCampusEvent() }
                onResult(Result.success(manuallyCreatedEvents(events).filter { it.status.firestoreValue == "PUBLISHED" }))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun getEventsForUser(
        uid: String,
        isOrganizer: Boolean,
        onResult: (Result<List<CampusEvent>>) -> Unit
    ) {
        val query = if (isOrganizer) db.collection("events").whereEqualTo("creatorUid", uid)
        else db.collection("events").whereEqualTo("status", "PUBLISHED")
        query.get()
            .addOnSuccessListener { snapshot ->
                val events = snapshot.documents.map { it.toCampusEvent() }
                onResult(Result.success(manuallyCreatedEvents(events).filter { event ->
                    isOrganizer || event.status.firestoreValue == "PUBLISHED"
                }))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun getUserEvents(uid: String, collection: String, onResult: (Result<List<CampusEvent>>) -> Unit) {
        db.collection("users").document(uid).collection(collection).get()
            .addOnSuccessListener { snapshot ->
                val eventIds = snapshot.documents.filter { registration ->
                    registration.getString("status")?.let { status ->
                        status in setOf("CONFIRMADA", "LISTA_ESPERA", "AGUARDANDO_CONFIRMACAO", "PRESENTE")
                    } ?: true
                }.map { it.id }
                if (eventIds.isEmpty()) {
                    onResult(Result.success(emptyList()))
                    return@addOnSuccessListener
                }
                val reads = eventIds.map { event(it).get() }
                Tasks.whenAllSuccess<DocumentSnapshot>(reads)
                    .addOnSuccessListener { snapshots ->
                        val events = snapshots.filter { it.exists() }.map { it.toCampusEvent() }
                        onResult(Result.success(eventsInRequestedOrder(eventIds, events)))
                    }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}
