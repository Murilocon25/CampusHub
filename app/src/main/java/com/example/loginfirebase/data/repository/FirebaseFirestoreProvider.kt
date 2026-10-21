package com.example.loginfirebase.data.repository

import com.google.firebase.firestore.FirebaseFirestore

object FirebaseFirestoreProvider {
    val db: FirebaseFirestore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FirebaseFirestore.getInstance()
    }
}
