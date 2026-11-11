package com.example.loginfirebase.ui.events

import com.example.loginfirebase.data.model.CampusEvent
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class EventFilteringTest {
    @Test
    fun filterCampusEvents_trimsQueryAndMatchesTitlesWithoutCaseSensitivity() {
        val events = listOf(
            event("1", "Campus Assembly", "Campus"),
            event("2", "Art Workshop", "Arts")
        )

        assertEquals(
            listOf(events[0]),
            filterCampusEvents(events, "  cAmPuS  ", null, EventTimeFilter.ALL, nowMillis = 10L)
        )
    }

    @Test
    fun filterCampusEvents_matchesTheSelectedCategoryExactly() {
        val events = listOf(
            event("1", "Jazz Night", "Music"),
            event("2", "Sports Fair", "Sports"),
            event("3", "Choir Practice", "music")
        )

        assertEquals(
            listOf(events[0]),
            filterCampusEvents(events, "", "Music", EventTimeFilter.ALL, nowMillis = 10L)
        )
    }

    @Test
    fun filterCampusEvents_treatsEndTimeEqualToNowAsEnded() {
        val now = 1_000L
        val events = listOf(
            event("past", "Past event", "A", endAtMillis = now - 1),
            event("current", "Event ending now", "A", endAtMillis = now),
            event("future", "Future event", "A", endAtMillis = now + 1)
        )

        assertEquals(
            listOf(events[2]),
            filterCampusEvents(events, "", null, EventTimeFilter.UPCOMING, nowMillis = now)
        )
        assertEquals(
            listOf(events[0], events[1]),
            filterCampusEvents(events, "", null, EventTimeFilter.ENDED, nowMillis = now)
        )
    }

    @Test
    fun filterCampusEvents_treatsUndatedEventsAsUpcomingUsingTheDefaultClock() {
        val undatedEvent = event("undated", "Campus meetup", "Community")

        assertEquals(
            listOf(undatedEvent),
            filterCampusEvents(listOf(undatedEvent), "", null, EventTimeFilter.UPCOMING)
        )
    }

    @Test
    fun filterCampusEvents_requiresTextCategoryAndTimeFiltersToAllMatch() {
        val events = listOf(
            event("match", "Campus Film Night", "Arts", endAtMillis = 2_000L),
            event("text", "Campus Market", "Arts", endAtMillis = 2_000L),
            event("category", "Campus Film Night", "Music", endAtMillis = 2_000L),
            event("time", "Campus Film Night", "Arts", endAtMillis = 500L)
        )

        assertEquals(
            listOf(events[0]),
            filterCampusEvents(
                events = events,
                query = "film",
                selectedCategory = "Arts",
                timeFilter = EventTimeFilter.UPCOMING,
                nowMillis = 1_000L
            )
        )
    }

    private fun event(
        id: String,
        title: String,
        category: String,
        endAtMillis: Long? = null
    ) = CampusEvent(
        id = id,
        title = title,
        date = "",
        place = "",
        description = "",
        category = category,
        endAt = endAtMillis?.let { Timestamp(Date(it)) }
    )
}
