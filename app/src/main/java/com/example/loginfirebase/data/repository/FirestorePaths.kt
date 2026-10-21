package com.example.loginfirebase.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

fun FirebaseFirestore.registrations(uid: String): CollectionReference =
    collection("users").document(uid).collection("registrations")

fun FirebaseFirestore.favorites(uid: String): CollectionReference =
    collection("users").document(uid).collection("favorites")
