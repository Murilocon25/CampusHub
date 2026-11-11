package com.example.loginfirebase.ui.events

import com.example.loginfirebase.data.model.CampusEvent

internal enum class EventTimeFilter {
    ALL,
    UPCOMING,
    ENDED
}

internal fun filterCampusEvents(
    events: List<CampusEvent>,
    query: String,
    selectedCategory: String?,
    timeFilter: EventTimeFilter,
    nowMillis: Long = System.currentTimeMillis()
): List<CampusEvent> {
    val normalizedQuery = query.trim()

    return events.filter { event ->
        val matchesText = event.title.contains(normalizedQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == null || selectedCategory == event.category
        val ended = event.hasEnded(nowMillis)
        val matchesTime = when (timeFilter) {
            EventTimeFilter.ALL -> true
            EventTimeFilter.UPCOMING -> !ended
            EventTimeFilter.ENDED -> ended
        }
        matchesText && matchesCategory && matchesTime
    }
}
