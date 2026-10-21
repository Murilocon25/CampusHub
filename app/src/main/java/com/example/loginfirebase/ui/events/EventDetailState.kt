package com.example.loginfirebase.ui.events

import com.example.loginfirebase.data.model.CampusEvent

sealed interface EventDetailState {
    data object Loading : EventDetailState
    data class Success(val event: CampusEvent) : EventDetailState
    data object Empty : EventDetailState
    data class Error(val cause: Throwable) : EventDetailState
}
