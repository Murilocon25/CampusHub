package com.example.loginfirebase.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginInputTest {
    @Test
    fun validateLoginInput_rejectsMissingEmailOrPasswordBeforeEmailValidation() {
        val invalidInputs = listOf(
            "" to "senha123",
            "   " to "senha123",
            "ana@example.com" to "",
            "ana@example.com" to "   "
        )

        invalidInputs.forEach { (email, password) ->
            var emailValidatorCalled = false

            val result = validateLoginInput(email, password) {
                emailValidatorCalled = true
                true
            }

            assertSame(LoginInputValidation.MissingFields, result)
            assertFalse(emailValidatorCalled)
        }
    }

    @Test
    fun validateLoginInput_rejectsInvalidEmailAfterTrimmingIt() {
        val validatedEmails = mutableListOf<String>()

        val result = validateLoginInput("  email-invalido  ", " senha123 ") { email ->
            validatedEmails += email
            false
        }

        assertEquals(listOf("email-invalido"), validatedEmails)
        assertEquals(LoginInputValidation.InvalidEmail("email-invalido"), result)
    }

    @Test
    fun validateLoginInput_returnsTrimmedCredentialsWhenInputIsValid() {
        var validatedEmail: String? = null

        val result = validateLoginInput("  ana@example.com  ", " senha123 ") { email ->
            validatedEmail = email
            true
        }

        assertEquals("ana@example.com", validatedEmail)
        assertTrue(result is LoginInputValidation.Valid)
        assertEquals(
            LoginInputValidation.Valid("ana@example.com", "senha123"),
            result
        )
    }
}
