package com.example.loginfirebase.data.repository

enum class UserRole(val firestoreValue: String) {
    STUDENT("student"),
    ORGANIZER("organizer");

    companion object {
        fun fromFirestore(value: String?): UserRole = values().firstOrNull { it.firestoreValue == value }
            ?: STUDENT
    }
}

data class UserProfile(
    val name: String = "",
    val course: String = "",
    val campus: String = "",
    val role: UserRole = UserRole.STUDENT
)

internal fun mapUserProfileFields(
    name: String?,
    course: String?,
    campus: String?,
    role: String? = null
) = UserProfile(
    name = name.orEmpty(),
    course = course.orEmpty(),
    campus = campus.orEmpty(),
    role = UserRole.fromFirestore(role)
)
