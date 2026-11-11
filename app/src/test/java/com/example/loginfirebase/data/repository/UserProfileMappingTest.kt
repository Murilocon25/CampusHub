package com.example.loginfirebase.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileMappingTest {
    @Test
    fun mapUserProfileFields_mapsAllValues() {
        assertEquals(
            UserProfile("Ana", "Design", "North Campus"),
            mapUserProfileFields("Ana", "Design", "North Campus")
        )
    }

    @Test
    fun mapUserProfileFields_defaultsMissingValuesToEmptyStrings() {
        assertEquals(UserProfile(), mapUserProfileFields(null, null, null))
        assertEquals(UserProfile("Ana", "", "South Campus"), mapUserProfileFields("Ana", null, "South Campus"))
    }
}
