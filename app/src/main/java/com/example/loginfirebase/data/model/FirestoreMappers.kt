package com.example.loginfirebase.data.model

import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toCampusEvent() = CampusEvent(
    id = id,
    title = getString("title").orEmpty(),
    date = getString("date").orEmpty(),
    place = getString("place").orEmpty(),
    description = getString("description").orEmpty(),
    category = getString("category").orEmpty(),
    startAt = getTimestamp("startAt"),
    endAt = getTimestamp("endAt"),
    creatorUid = getString("creatorUid").orEmpty()
)

fun DocumentSnapshot.toEventComment() = EventComment(
    id = id,
    authorUid = getString("authorUid").orEmpty(),
    authorName = getString("authorName").orEmpty(),
    text = getString("text").orEmpty(),
    createdAt = getTimestamp("createdAt")?.toDate()
)
