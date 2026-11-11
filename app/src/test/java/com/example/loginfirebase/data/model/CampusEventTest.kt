package com.example.loginfirebase.data.model

import com.google.firebase.Timestamp
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusEventTest {
    @Test
    fun hasEnded_usesEndTimeWhenPresentAndTreatsBoundaryAsEnded() {
        val event = campusEvent(startAtMillis = 500L, endAtMillis = 1_000L)

        assertFalse(event.hasEnded(nowMillis = 999L))
        assertTrue(event.hasEnded(nowMillis = 1_000L))
    }

    @Test
    fun hasEnded_fallsBackToStartTimeAndReturnsFalseWhenNoTimeExists() {
        assertTrue(campusEvent(startAtMillis = 1_000L).hasEnded(nowMillis = 1_000L))
        assertFalse(campusEvent().hasEnded(nowMillis = 1_000L))
    }

    @Test
    fun hasEnded_usesCurrentTimeWhenNoClockIsProvided() {
        assertTrue(campusEvent(endAtMillis = 0L).hasEnded())
    }

    @Test
    fun displayDate_formatsStartTimeInTheRequestedTimeZone() {
        val event = campusEvent(startAtMillis = 0L)

        assertEquals(
            "01/01/1970 às 00:00",
            event.displayDate(
                locale = Locale.forLanguageTag("pt-BR"),
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun displayDate_usesTheStoredDateWhenStartTimeIsMissing() {
        assertEquals("12/08/2025", campusEvent(date = "12/08/2025").displayDate())
    }

    private fun campusEvent(
        date: String = "",
        startAtMillis: Long? = null,
        endAtMillis: Long? = null
    ) = CampusEvent(
        id = "event-1",
        title = "Campus event",
        date = date,
        place = "Library",
        description = "",
        startAt = startAtMillis?.let { Timestamp(Date(it)) },
        endAt = endAtMillis?.let { Timestamp(Date(it)) }
    )
}
