package com.example.loginfirebase.util

import android.content.Context
import com.example.loginfirebase.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

fun Context.firebaseErrorMessage(error: Throwable?): String = getString(firebaseErrorMessageResource(error))

internal fun firebaseErrorMessageResource(error: Throwable?): Int = when (error) {
    is FirebaseAuthWeakPasswordException -> R.string.password_too_short
    is FirebaseAuthUserCollisionException -> R.string.auth_email_in_use
    is FirebaseNetworkException -> R.string.auth_network_error
    is FirebaseAuthInvalidUserException -> R.string.login_error
    else -> R.string.auth_generic_error
}
