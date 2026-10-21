package com.example.loginfirebase.data.repository

import com.google.firebase.auth.FirebaseAuth

class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val currentUser get() = auth.currentUser

    fun signIn(email: String, password: String) = auth.signInWithEmailAndPassword(email, password)
    fun register(email: String, password: String) = auth.createUserWithEmailAndPassword(email, password)
    fun signOut() = auth.signOut()
    fun sendPasswordReset(email: String) = auth.sendPasswordResetEmail(email)
}
