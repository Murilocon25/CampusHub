package com.example.loginfirebase.ui.auth

internal sealed class LoginInputValidation {
    object MissingFields : LoginInputValidation()

    data class InvalidEmail(val email: String) : LoginInputValidation()

    data class Valid(val email: String, val password: String) : LoginInputValidation()
}

internal fun validateLoginInput(
    emailInput: String,
    passwordInput: String,
    isValidEmail: (String) -> Boolean
): LoginInputValidation {
    val email = emailInput.trim()
    val password = passwordInput.trim()

    if (email.isEmpty() || password.isEmpty()) {
        return LoginInputValidation.MissingFields
    }
    if (!isValidEmail(email)) {
        return LoginInputValidation.InvalidEmail(email)
    }
    return LoginInputValidation.Valid(email, password)
}
