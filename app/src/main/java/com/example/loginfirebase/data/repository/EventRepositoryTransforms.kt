package com.example.loginfirebase.data.repository

import com.example.loginfirebase.data.model.CampusEvent

internal const val EVENT_IDS_PER_FIRESTORE_QUERY = 30

internal fun manuallyCreatedEvents(events: List<CampusEvent>): List<CampusEvent> =
    events.filter { it.creatorUid.isNotBlank() }

internal fun batchEventIds(eventIds: List<String>): List<List<String>> =
    eventIds.chunked(EVENT_IDS_PER_FIRESTORE_QUERY)

internal fun eventsInRequestedOrder(
    requestedIds: List<String>,
    availableEvents: List<CampusEvent>
): List<CampusEvent> {
    val eventsById = availableEvents.associateBy(CampusEvent::id)
    return requestedIds.mapNotNull(eventsById::get)
}
