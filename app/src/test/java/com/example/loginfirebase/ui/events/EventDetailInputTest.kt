package com.example.loginfirebase.ui.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetailInputTest {
    @Test
    fun prepareEventComment_rejectsEmptyAndWhitespaceOnlyText() {
        assertNull(prepareEventComment(""))
        assertNull(prepareEventComment(" \t\n "))
    }

    @Test
    fun prepareEventComment_trimsTextAndAcceptsTheMaximumLength() {
        assertEquals("Useful feedback", prepareEventComment("  Useful feedback  "))
        assertEquals("x".repeat(MAX_EVENT_COMMENT_LENGTH), prepareEventComment("x".repeat(MAX_EVENT_COMMENT_LENGTH)))
    }

    @Test
    fun prepareEventComment_rejectsTextLongerThanTheMaximum() {
        assertNull(prepareEventComment("x".repeat(MAX_EVENT_COMMENT_LENGTH + 1)))
    }

    @Test
    fun isValidEventRating_acceptsOnlyWholeScoresFromOneThroughFive() {
        assertFalse(isValidEventRating(0))
        assertTrue(isValidEventRating(1))
        assertTrue(isValidEventRating(5))
        assertFalse(isValidEventRating(6))
    }

    @Test
    fun registrationState_changesOnlyAfterSuccessfulUpdate() {
        val unregistered = EventRegistrationState(isRegistered = false)
        val registered = unregistered.afterToggleUpdate(succeeded = true)

        assertTrue(registered.isRegistered)
        assertEquals(unregistered, unregistered.afterToggleUpdate(succeeded = false))
        assertEquals(registered, registered.afterToggleUpdate(succeeded = false))
        assertFalse(registered.afterToggleUpdate(succeeded = true).isRegistered)
    }

    @Test
    fun registrationState_showsRatingOnlyForRegisteredEndedEvents() {
        assertFalse(EventRegistrationState(isRegistered = false).shouldShowRating(eventHasEnded = true))
        assertFalse(EventRegistrationState(isRegistered = true).shouldShowRating(eventHasEnded = false))
        assertTrue(EventRegistrationState(isRegistered = true).shouldShowRating(eventHasEnded = true))
    }
}
