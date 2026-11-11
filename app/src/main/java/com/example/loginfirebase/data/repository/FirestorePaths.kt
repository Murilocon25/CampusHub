package com.example.loginfirebase.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

fun FirebaseFirestore.registrations(uid: String): CollectionReference =
    collection(registrationsPath(uid))

fun FirebaseFirestore.favorites(uid: String): CollectionReference =
    collection(favoritesPath(uid))

internal fun registrationsPath(uid: String): String = "users/$uid/registrations"

internal fun favoritesPath(uid: String): String = "users/$uid/favorites"
