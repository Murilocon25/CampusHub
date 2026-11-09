package com.example.loginfirebase.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class PasswordResetInputTest {
    @Test
    fun validatePasswordResetEmail_rejectsBlankEmailBeforeValidation() {
        var emailValidatorCalled = false

        val result = validatePasswordResetEmail("   ") {
            emailValidatorCalled = true
            true
        }

        assertSame(PasswordResetEmailValidation.MissingEmail, result)
        assertFalse(emailValidatorCalled)
    }

    @Test
    fun validatePasswordResetEmail_rejectsInvalidEmailAfterTrimmingIt() {
        val result = validatePasswordResetEmail(" bad-email ") { false }

        assertEquals(PasswordResetEmailValidation.InvalidEmail("bad-email"), result)
    }

    @Test
    fun validatePasswordResetEmail_returnsTrimmedEmailWhenValid() {
        val result = validatePasswordResetEmail(" ana@example.com ") { it == "ana@example.com" }

        assertEquals(PasswordResetEmailValidation.Valid("ana@example.com"), result)
    }
}
