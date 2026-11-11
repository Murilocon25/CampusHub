package com.example.loginfirebase.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito

class FirestorePathsTest {
    @Test
    fun favoritesPath_buildsTheUserFavoritesCollectionPath() {
        assertEquals("users/user-7/favorites", favoritesPath("user-7"))
    }

    @Test
    fun registrationsPath_buildsTheUserRegistrationsCollectionPath() {
        assertEquals("users/user-7/registrations", registrationsPath("user-7"))
    }

    @Test
    fun firestorePathExtensionsSelectTheMatchingCollection() {
        val firestore = Mockito.mock(com.google.firebase.firestore.FirebaseFirestore::class.java)
        val favorites = Mockito.mock(com.google.firebase.firestore.CollectionReference::class.java)
        val registrations = Mockito.mock(com.google.firebase.firestore.CollectionReference::class.java)
        Mockito.`when`(firestore.collection("users/user-7/favorites")).thenReturn(favorites)
        Mockito.`when`(firestore.collection("users/user-7/registrations")).thenReturn(registrations)

        assertSame(favorites, firestore.favorites("user-7"))
        assertSame(registrations, firestore.registrations("user-7"))
    }
}
