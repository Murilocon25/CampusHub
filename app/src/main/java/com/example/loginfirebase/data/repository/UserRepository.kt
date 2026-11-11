package com.example.loginfirebase.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

interface UserProfileRepository {
    fun loadProfile(uid: String, onResult: (Result<UserProfile>) -> Unit)

    fun saveProfile(
        uid: String,
        name: String,
        course: String,
        campus: String,
        onResult: (Result<Unit>) -> Unit
    )
}

class UserRepository(
    private val firestore: FirebaseFirestore? = null,
    private val auth: FirebaseAuth? = null
) : UserProfileRepository {
    private val db get() = firestore ?: FirebaseFirestoreProvider.db

    override fun loadProfile(uid: String, onResult: (Result<UserProfile>) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(mapUserProfileFields(
                    name = snapshot.getString("name"),
                    course = snapshot.getString("course"),
                    campus = snapshot.getString("campus")
                )))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    override fun saveProfile(
        uid: String,
        name: String,
        course: String,
        campus: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val user = (auth ?: FirebaseAuth.getInstance()).currentUser
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
