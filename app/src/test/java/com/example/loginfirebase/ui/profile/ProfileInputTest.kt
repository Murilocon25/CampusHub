package com.example.loginfirebase.ui.profile

import com.example.loginfirebase.data.repository.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileInputTest {
    @Test
    fun profileForSave_rejectsBlankName() {
        listOf("", "   ", "\t\n").forEach { name ->
            assertNull(profileForSave(name, "Computer Science", "North Campus"))
        }
    }

    @Test
    fun profileForSave_trimsAllFieldsAndAllowsOptionalFieldsToBeBlank() {
        assertEquals(
            UserProfile(name = "Ana Silva", course = "Computer Science", campus = "North Campus"),
            profileForSave(" Ana Silva ", " Computer Science ", " North Campus ")
        )
        assertEquals(
            UserProfile(name = "Ana", course = "", campus = ""),
            profileForSave("Ana", "  ", "")
        )
    }

    @Test
    fun profileDisplayName_prefersDisplayNameThenEmailLocalPartThenFallback() {
        assertEquals("Ana Silva", profileDisplayName("Ana Silva", "ana@example.com", "Student"))
        assertEquals("ana", profileDisplayName("  ", "ana@example.com", "Student"))
        assertEquals("Student", profileDisplayName(null, "@example.com", "Student"))
        assertEquals("Student", profileDisplayName(null, null, "Student"))
    }

    @Test
    fun profileInitials_usesFirstTwoWordsAndFallbackForBlankNames() {
        assertEquals("AS", profileInitials("  Ana   Silva Costa "))
        assertEquals("J", profileInitials("joão"))
        assertEquals("CH", profileInitials(" \t "))
    }
}
