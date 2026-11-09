package com.example.loginfirebase.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class RegisterInputTest {
    @Test
    fun validateRegisterInput_rejectsMissingFieldsBeforeCheckingEmail() {
        val inputs = listOf(
            Triple("", "senha123", "senha123"),
            Triple("ana@example.com", "", "senha123"),
            Triple("ana@example.com", "senha123", "   ")
        )

        inputs.forEach { (email, password, confirmation) ->
            var emailValidatorCalled = false

            val result = validateRegisterInput(email, password, confirmation) {
                emailValidatorCalled = true
                true
            }

            assertSame(RegisterInputValidation.MissingFields, result)
            assertFalse(emailValidatorCalled)
        }
    }

    @Test
    fun validateRegisterInput_rejectsInvalidEmailAfterTrimmingIt() {
        val checkedEmails = mutableListOf<String>()

        val result = validateRegisterInput(" invalid-email ", "senha123", "senha123") { email ->
            checkedEmails += email
            false
        }

        assertEquals(listOf("invalid-email"), checkedEmails)
        assertEquals(RegisterInputValidation.InvalidEmail("invalid-email"), result)
    }

    @Test
    fun validateRegisterInput_rejectsPasswordsShorterThanSixCharacters() {
        val result = validateRegisterInput("ana@example.com", "12345", "12345") { true }

        assertSame(RegisterInputValidation.PasswordTooShort, result)
    }

    @Test
    fun validateRegisterInput_rejectsDifferentPasswords() {
        val result = validateRegisterInput("ana@example.com", "senha123", "senha321") { true }

        assertSame(RegisterInputValidation.PasswordMismatch, result)
    }

    @Test
    fun validateRegisterInput_acceptsMatchingPasswordsAtMinimumLength() {
        val result = validateRegisterInput(
            emailInput = " ana@example.com ",
            passwordInput = " 123456 ",
            confirmationInput = "123456 ",
            isValidEmail = { it == "ana@example.com" }
        )

        assertEquals(RegisterInputValidation.Valid("ana@example.com", "123456"), result)
    }
}
