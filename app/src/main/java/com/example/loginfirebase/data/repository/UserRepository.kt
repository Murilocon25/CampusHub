package com.example.loginfirebase.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.SetOptions

class UserRepository {
    private val db get() = FirebaseFirestoreProvider.db

    fun loadProfile(uid: String, onResult: (Result<UserProfile>) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(UserProfile(
                    name = snapshot.getString("name").orEmpty(),
                    course = snapshot.getString("course").orEmpty(),
                    campus = snapshot.getString("campus").orEmpty()
                )))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun saveProfile(
        uid: String,
        name: String,
        course: String,
        campus: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        val updateName = user?.takeIf { it.uid == uid }?.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name).build()
        )
        val writeProfile = {
            db.collection("users").document(uid).set(
                mapOf("name" to name, "course" to course, "campus" to campus),
                SetOptions.merge()
            ).addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
        if (updateName == null) {
            writeProfile()
        } else {
            updateName.addOnSuccessListener { writeProfile() }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
    }

    fun createEmptyProfile(uid: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("users").document(uid).set(
            mapOf("name" to "", "course" to "", "campus" to "")
        ).addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}
