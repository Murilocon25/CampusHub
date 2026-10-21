package com.example.loginfirebase.util

import android.content.Context
import com.example.loginfirebase.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

fun Context.firebaseErrorMessage(error: Throwable?): String = when (error) {
    is FirebaseAuthWeakPasswordException -> getString(R.string.password_too_short)
    is FirebaseAuthUserCollisionException -> getString(R.string.auth_email_in_use)
    is FirebaseNetworkException -> getString(R.string.auth_network_error)
    is FirebaseAuthInvalidUserException -> getString(R.string.login_error)
    else -> getString(R.string.auth_generic_error)
}
