package com.example.loginfirebase.data.repository

data class UserProfile(val name: String = "", val course: String = "", val campus: String = "")

internal fun mapUserProfileFields(name: String?, course: String?, campus: String?) = UserProfile(
    name = name.orEmpty(),
    course = course.orEmpty(),
    campus = campus.orEmpty()
)
