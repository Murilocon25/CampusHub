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
    val location: GeoPoint? = null
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
