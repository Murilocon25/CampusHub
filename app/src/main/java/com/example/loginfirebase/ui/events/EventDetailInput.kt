package com.example.loginfirebase.ui.events

internal const val MAX_EVENT_COMMENT_LENGTH = 1000

internal fun prepareEventComment(input: String): String? = input.trim().takeIf {
    it.isNotEmpty() && it.length <= MAX_EVENT_COMMENT_LENGTH
}

internal fun isValidEventRating(score: Int): Boolean = score in 1..5

internal data class EventRegistrationState(val isRegistered: Boolean) {
    fun afterToggleUpdate(succeeded: Boolean): EventRegistrationState =
        if (succeeded) copy(isRegistered = !isRegistered) else this

    fun shouldShowRating(eventHasEnded: Boolean): Boolean = isRegistered && eventHasEnded
}
