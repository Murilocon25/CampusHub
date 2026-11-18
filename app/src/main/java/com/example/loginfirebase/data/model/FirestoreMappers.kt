package com.example.loginfirebase.data.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.Timestamp

fun DocumentSnapshot.toCampusEvent() = mapCampusEventFields(
    id = id,
    title = getString("title"),
    date = getString("date"),
    place = getString("place"),
    description = getString("description"),
    category = getString("category"),
    startAt = getTimestamp("startAt"),
    endAt = getTimestamp("endAt"),
    creatorUid = getString("creatorUid"),
    location = getGeoPoint("location"),
    capacity = getLong("capacity")?.toInt() ?: 0,
    coverImageUrl = getString("coverImageUrl").orEmpty(),
    status = EventPublicationStatus.fromFirestore(getString("status")),
    confirmedCount = getLong("confirmedCount")?.toInt() ?: 0,
    waitingCount = getLong("waitingCount")?.toInt() ?: 0,
    reservedCount = getLong("reservedCount")?.toInt() ?: 0
)

internal fun mapCampusEventFields(
    id: String,
    title: String?,
    date: String?,
    place: String?,
    description: String?,
    category: String?,
    startAt: Timestamp?,
    endAt: Timestamp?,
    creatorUid: String?,
    location: GeoPoint?,
    capacity: Int = 0,
    coverImageUrl: String = "",
    status: EventPublicationStatus = EventPublicationStatus.PUBLISHED,
    confirmedCount: Int = 0,
    waitingCount: Int = 0,
    reservedCount: Int = 0
) = CampusEvent(
    id = id,
    title = title.orEmpty(),
    date = date.orEmpty(),
    place = place.orEmpty(),
    description = description.orEmpty(),
    category = category.orEmpty(),
    startAt = startAt,
    endAt = endAt,
    creatorUid = creatorUid.orEmpty(),
    location = location,
    capacity = capacity,
    coverImageUrl = coverImageUrl,
    status = status,
    confirmedCount = confirmedCount,
    waitingCount = waitingCount,
    reservedCount = reservedCount
)

fun DocumentSnapshot.toEventComment() = mapEventCommentFields(
    id = id,
    authorUid = getString("authorUid"),
    authorName = getString("authorName"),
    text = getString("text"),
    createdAt = getTimestamp("createdAt")
)

internal fun mapEventCommentFields(
    id: String,
    authorUid: String?,
    authorName: String?,
    text: String?,
    createdAt: Timestamp?
) = EventComment(
    id = id,
    authorUid = authorUid.orEmpty(),
    authorName = authorName.orEmpty(),
    text = text.orEmpty(),
    createdAt = createdAt?.toDate()
)
