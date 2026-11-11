package com.example.loginfirebase.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.util.Date
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class FirestoreMappersTest {
    @Test
    fun mapCampusEventFields_mapsAllSupportedEventFields() {
        val startAt = Timestamp(Date(1_000L))
        val endAt = Timestamp(Date(2_000L))
        val location = GeoPoint(-23.55, -46.63)

        val event = mapCampusEventFields(
            id = "event-1",
            title = "Film night",
            date = "01/01/1970",
            place = "Auditorium",
            description = "A campus screening",
            category = "Arts",
            startAt = startAt,
            endAt = endAt,
            creatorUid = "user-1",
            location = location
        )

        assertEquals("event-1", event.id)
        assertEquals("Film night", event.title)
        assertEquals("01/01/1970", event.date)
        assertEquals("Auditorium", event.place)
        assertEquals("A campus screening", event.description)
        assertEquals("Arts", event.category)
        assertEquals(startAt, event.startAt)
        assertEquals(endAt, event.endAt)
        assertEquals("user-1", event.creatorUid)
        assertEquals(location, event.location)
    }

    @Test
    fun mapCampusEventFields_defaultsMissingStringsAndKeepsOptionalFieldsNull() {
        val event = mapCampusEventFields(
            id = "event-2",
            title = null,
            date = null,
            place = null,
            description = null,
            category = null,
            startAt = null,
            endAt = null,
            creatorUid = null,
            location = null
        )

        assertEquals("", event.title)
        assertEquals("", event.date)
        assertEquals("", event.place)
        assertEquals("", event.description)
        assertEquals("", event.category)
        assertNull(event.startAt)
        assertNull(event.endAt)
        assertEquals("", event.creatorUid)
        assertNull(event.location)
    }

    @Test
    fun mapEventCommentFields_mapsAuthorTextAndTimestamp() {
        val createdAt = Timestamp(Date(3_000L))

        val comment = mapEventCommentFields(
            id = "comment-1",
            authorUid = "user-1",
            authorName = "Ana",
            text = "Thanks!",
            createdAt = createdAt
        )

        assertEquals("comment-1", comment.id)
        assertEquals("user-1", comment.authorUid)
        assertEquals("Ana", comment.authorName)
        assertEquals("Thanks!", comment.text)
        assertEquals(createdAt.toDate(), comment.createdAt)
    }

    @Test
    fun mapEventCommentFields_defaultsMissingTextFieldsAndTimestamp() {
        val comment = mapEventCommentFields(
            id = "comment-2",
            authorUid = null,
            authorName = null,
            text = null,
            createdAt = null
        )

        assertEquals("", comment.authorUid)
        assertEquals("", comment.authorName)
        assertEquals("", comment.text)
        assertNull(comment.createdAt)
    }

    @Test
    fun documentSnapshotExtensions_readTheExpectedFirestoreFields() {
        val startAt = Timestamp(Date(4_000L))
        val location = GeoPoint(-23.55, -46.63)
        val eventSnapshot = Mockito.mock(com.google.firebase.firestore.DocumentSnapshot::class.java)
        Mockito.`when`(eventSnapshot.id).thenReturn("event-3")
        Mockito.`when`(eventSnapshot.getString("title")).thenReturn("Open day")
        Mockito.`when`(eventSnapshot.getTimestamp("startAt")).thenReturn(startAt)
        Mockito.`when`(eventSnapshot.getGeoPoint("location")).thenReturn(location)

        val event = eventSnapshot.toCampusEvent()

        assertEquals("event-3", event.id)
        assertEquals("Open day", event.title)
        assertEquals(startAt, event.startAt)
        assertEquals(location, event.location)

        val commentSnapshot = Mockito.mock(com.google.firebase.firestore.DocumentSnapshot::class.java)
        Mockito.`when`(commentSnapshot.id).thenReturn("comment-3")
        Mockito.`when`(commentSnapshot.getString("authorName")).thenReturn("Ana")
        Mockito.`when`(commentSnapshot.getString("text")).thenReturn("Welcome!")
        Mockito.`when`(commentSnapshot.getTimestamp("createdAt")).thenReturn(startAt)

        val comment = commentSnapshot.toEventComment()

        assertEquals("comment-3", comment.id)
        assertEquals("Ana", comment.authorName)
        assertEquals("Welcome!", comment.text)
        assertEquals(startAt.toDate(), comment.createdAt)
        assertNotNull(comment.createdAt)
    }
}
