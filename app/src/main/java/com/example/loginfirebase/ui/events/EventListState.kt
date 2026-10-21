package com.example.loginfirebase.ui.events

import com.example.loginfirebase.data.model.CampusEvent

sealed interface EventListState {
    data object Loading : EventListState
    data class Success(val events: List<CampusEvent>) : EventListState
    data object Empty : EventListState
    data class Error(val cause: Throwable) : EventListState
}
