package com.example.loginfirebase.util

import com.example.loginfirebase.R
import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class FirebaseErrorMessagesTest {
    @Test
    fun firebaseErrorMessageResource_mapsKnownFirebaseFailuresToSpecificMessages() {
        assertEquals(
            R.string.password_too_short,
            firebaseErrorMessageResource(Mockito.mock(FirebaseAuthWeakPasswordException::class.java))
        )
        assertEquals(
            R.string.auth_email_in_use,
            firebaseErrorMessageResource(Mockito.mock(FirebaseAuthUserCollisionException::class.java))
        )
        assertEquals(
            R.string.auth_network_error,
            firebaseErrorMessageResource(Mockito.mock(FirebaseNetworkException::class.java))
        )
        assertEquals(
            R.string.login_error,
            firebaseErrorMessageResource(Mockito.mock(FirebaseAuthInvalidUserException::class.java))
        )
    }

    @Test
    fun firebaseErrorMessageResource_usesGenericMessageForUnknownOrMissingErrors() {
        assertEquals(R.string.auth_generic_error, firebaseErrorMessageResource(IllegalStateException("unknown")))
        assertEquals(R.string.auth_generic_error, firebaseErrorMessageResource(null))
    }

    @Test
    fun firebaseErrorMessage_resolvesTheMappedResourceThroughContext() {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getString(R.string.auth_network_error)).thenReturn("Network issue")

        assertEquals(
            "Network issue",
            context.firebaseErrorMessage(Mockito.mock(FirebaseNetworkException::class.java))
        )
    }
}
