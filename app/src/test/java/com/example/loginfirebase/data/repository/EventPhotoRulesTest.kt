package com.example.loginfirebase.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPhotoRulesTest {
    @Test
    fun photoMimeType_acceptsImageTypesCaseInsensitively() {
        assertTrue(EventMediaStore.matchesType(EventMediaType.PHOTO, "image/jpeg"))
        assertTrue(EventMediaStore.matchesType(EventMediaType.PHOTO, "IMAGE/PNG"))
    }

    @Test
    fun photoMimeType_rejectsNonImagesAndMalformedValues() {
        assertFalse(EventMediaStore.matchesType(EventMediaType.PHOTO, "video/mp4"))
        assertFalse(EventMediaStore.matchesType(EventMediaType.PHOTO, "application/pdf"))
        assertFalse(EventMediaStore.matchesType(EventMediaType.PHOTO, "image"))
        assertFalse(EventMediaStore.matchesType(EventMediaType.PHOTO, ""))
    }

    @Test
    fun photoOwnership_isGrantedOnlyToTheAuthor() {
        val photo = EventMedia(
            id = "photo-1",
            eventId = "event-1",
            type = EventMediaType.PHOTO,
            authorUid = "user-1",
            authorName = "Ana",
            createdAtMillis = 1L,
            storagePath = "event_media/event-1/user-1/photo-1.jpg",
            mimeType = "image/jpeg"
        )

        assertTrue(EventMediaStore.isOwnedBy(photo, "user-1"))
        assertFalse(EventMediaStore.isOwnedBy(photo, "user-2"))
        assertFalse(EventMediaStore.isOwnedBy(photo, ""))
    }
}
