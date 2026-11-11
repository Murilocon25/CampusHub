package com.example.loginfirebase.data.repository

import com.example.loginfirebase.data.model.CampusEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class EventRepositoryTransformsTest {
    @Test
    fun manuallyCreatedEvents_excludesEventsWithoutACreatorAndPreservesOrder() {
        val events = listOf(
            event("event-1", creatorUid = "user-1"),
            event("event-2", creatorUid = ""),
            event("event-3", creatorUid = "  "),
            event("event-4", creatorUid = "user-2")
        )

        assertEquals(listOf(events[0], events[3]), manuallyCreatedEvents(events))
    }

    @Test
    fun batchEventIds_splitsAtFirestoreLimitWithoutReorderingIds() {
        val ids = (1..EVENT_IDS_PER_FIRESTORE_QUERY + 1).map { "event-$it" }

        val batches = batchEventIds(ids)

        assertEquals(listOf(ids.take(EVENT_IDS_PER_FIRESTORE_QUERY), ids.takeLast(1)), batches)
        assertEquals(emptyList<List<String>>(), batchEventIds(emptyList()))
    }

    @Test
    fun eventsInRequestedOrder_preservesSavedOrderAndSkipsMissingEvents() {
        val requestedIds = listOf("event-3", "event-missing", "event-1", "event-3")
        val availableEvents = listOf(
            event("event-1", creatorUid = "user-1"),
            event("event-3", creatorUid = "user-3")
        )

        assertEquals(
            listOf(availableEvents[1], availableEvents[0], availableEvents[1]),
            eventsInRequestedOrder(requestedIds, availableEvents)
        )
    }

    private fun event(id: String, creatorUid: String) = CampusEvent(
        id = id,
        title = "Event $id",
        date = "",
        place = "",
        description = "",
        creatorUid = creatorUid
    )
}
