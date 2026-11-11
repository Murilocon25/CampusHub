package com.example.loginfirebase.ui.profile

import com.example.loginfirebase.data.repository.UserProfile

internal fun profileForSave(nameInput: String, courseInput: String, campusInput: String): UserProfile? {
    val name = nameInput.trim()
    if (name.isBlank()) return null

    return UserProfile(
        name = name,
        course = courseInput.trim(),
        campus = campusInput.trim()
    )
}

internal fun profileDisplayName(displayName: String?, email: String?, fallback: String): String =
    displayName?.takeIf(String::isNotBlank)
        ?: email?.substringBefore('@')?.takeIf(String::isNotBlank)
        ?: fallback

internal fun profileInitials(name: String): String = name.trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercase() }
    .joinToString("")
    .ifBlank { "CH" }
