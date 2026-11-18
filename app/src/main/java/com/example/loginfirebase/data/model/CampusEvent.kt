package com.example.loginfirebase.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class CampusEvent(
    val id: String,
    val title: String,
    val date: String,
    val place: String,
    val description: String,
    val category: String = "",
    val startAt: Timestamp? = null,
    val endAt: Timestamp? = null,
    val creatorUid: String = "",
    val location: GeoPoint? = null,
    val capacity: Int = 0,
    val coverImageUrl: String = "",
    val status: EventPublicationStatus = EventPublicationStatus.PUBLISHED,
    val confirmedCount: Int = 0,
    val waitingCount: Int = 0,
    val reservedCount: Int = 0
) {
    fun hasEnded(nowMillis: Long = System.currentTimeMillis()): Boolean =
        (endAt ?: startAt)?.toDate()?.time?.let { it <= nowMillis } ?: false

    fun displayDate(
        locale: Locale = Locale.forLanguageTag("pt-BR"),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = startAt?.toDate()?.let {
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", locale).apply { this.timeZone = timeZone }.format(it)
    }
            ?: date
}

enum class EventPublicationStatus(val firestoreValue: String) {
    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    ENDED("ENDED");

    companion object {
        fun fromFirestore(value: String?): EventPublicationStatus = entries.firstOrNull {
            it.firestoreValue == value
        } ?: EventPublicationStatus.PUBLISHED
    }
}
