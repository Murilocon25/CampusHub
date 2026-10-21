package com.example.loginfirebase.data.model

import java.util.Date

data class EventComment(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val text: String,
    val createdAt: Date?
)
