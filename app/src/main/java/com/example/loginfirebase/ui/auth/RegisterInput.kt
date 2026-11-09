package com.example.loginfirebase.ui.auth

internal sealed class RegisterInputValidation {
    data object MissingFields : RegisterInputValidation()

    data class InvalidEmail(val email: String) : RegisterInputValidation()

    data object PasswordTooShort : RegisterInputValidation()

    data object PasswordMismatch : RegisterInputValidation()

    data class Valid(val email: String, val password: String) : RegisterInputValidation()
}

internal fun validateRegisterInput(
    emailInput: String,
    passwordInput: String,
    confirmationInput: String,
    isValidEmail: (String) -> Boolean
): RegisterInputValidation {
    val email = emailInput.trim()
    val password = passwordInput.trim()
    val confirmation = confirmationInput.trim()

    if (email.isEmpty() || password.isEmpty() || confirmation.isEmpty()) {
        return RegisterInputValidation.MissingFields
    }
    if (!isValidEmail(email)) {
        return RegisterInputValidation.InvalidEmail(email)
    }
    if (password.length < 6) {
        return RegisterInputValidation.PasswordTooShort
    }
    if (password != confirmation) {
        return RegisterInputValidation.PasswordMismatch
    }
    return RegisterInputValidation.Valid(email, password)
}
