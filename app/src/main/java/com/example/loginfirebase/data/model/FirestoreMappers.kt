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
    location = getGeoPoint("location")
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
    location: GeoPoint?
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
    location = location
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
