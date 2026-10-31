package com.example.loginfirebase.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EventVideoInputTest {
    @Test
    fun validateMetadata_acceptsVideoWithPositiveDuration() {
        val metadata = validateEventVideoMetadata("video/mp4", 12_500L)

        assertEquals("video/mp4", metadata.mimeType)
        assertEquals(12_500L, metadata.durationMs)
    }

    @Test
    fun validateMetadata_acceptsDurationAtTheLimit() {
        val metadata = validateEventVideoMetadata("VIDEO/MP4", EventVideoInput.MAX_DURATION_MS)

        assertEquals("VIDEO/MP4", metadata.mimeType)
        assertEquals(EventVideoInput.MAX_DURATION_MS, metadata.durationMs)
    }

    @Test
    fun validateMetadata_rejectsDurationOverTheLimit() {
        val error = assertThrows(EventVideoInputException::class.java) {
            validateEventVideoMetadata("video/mp4", EventVideoInput.MAX_DURATION_MS + 1L)
        }

        assertEquals(EventVideoInputException.Reason.TOO_LONG, error.reason)
    }

    @Test
    fun validateMetadata_rejectsMissingOrNonVideoMimeType() {
        listOf(null, "", "image/jpeg").forEach { mimeType ->
            val error = assertThrows(EventVideoInputException::class.java) {
                validateEventVideoMetadata(mimeType, 1_000L)
            }
            assertEquals(EventVideoInputException.Reason.INVALID_VIDEO, error.reason)
        }
    }

    @Test
    fun validateMetadata_rejectsMissingOrNonPositiveDuration() {
        listOf(null, 0L, -1L).forEach { durationMs ->
            val error = assertThrows(EventVideoInputException::class.java) {
                validateEventVideoMetadata("video/mp4", durationMs)
            }
            assertEquals(EventVideoInputException.Reason.INVALID_VIDEO, error.reason)
        }
    }

    @Test
    fun validateSize_acceptsPositiveFilesAtTheLimit() {
        validateEventVideoSize(1L)
        validateEventVideoSize(EventVideoInput.MAX_SIZE_BYTES)
    }

    @Test
    fun validateSize_rejectsEmptyFiles() {
        val error = assertThrows(EventVideoInputException::class.java) {
            validateEventVideoSize(0L)
        }

        assertEquals(EventVideoInputException.Reason.INVALID_VIDEO, error.reason)
    }

    @Test
    fun validateSize_rejectsFilesOverTheLimit() {
        val error = assertThrows(EventVideoInputException::class.java) {
            validateEventVideoSize(EventVideoInput.MAX_SIZE_BYTES + 1L)
        }

        assertEquals(EventVideoInputException.Reason.TOO_LARGE, error.reason)
    }
}
