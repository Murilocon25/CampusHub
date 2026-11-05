package com.example.loginfirebase.data.repository

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EventVideoInputTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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

    @Test
    fun copyToPendingFile_copiesBytesAndUsesRequestedExtension() {
        val directory = temporaryFolder.newFolder("pending")
        val content = byteArrayOf(1, 2, 3, 4)

        val file = copyEventVideoToPendingFile(
            directory = directory,
            extension = "mp4",
            input = ByteArrayInputStream(content),
            maxBytes = content.size.toLong()
        )

        assertTrue(file.isFile)
        assertTrue(file.name.endsWith(".mp4"))
        assertArrayEquals(content, file.readBytes())
    }

    @Test
    fun copyToPendingFile_rejectsEmptyInputAndRemovesTemporaryFile() {
        val directory = temporaryFolder.newFolder("empty")

        val error = assertThrows(EventVideoInputException::class.java) {
            copyEventVideoToPendingFile(directory, "mp4", ByteArrayInputStream(byteArrayOf()))
        }

        assertEquals(EventVideoInputException.Reason.INVALID_VIDEO, error.reason)
        assertEquals(0, directory.listFiles()?.size ?: 0)
    }

    @Test
    fun copyToPendingFile_rejectsInputOverLimitAndRemovesTemporaryFile() {
        val directory = temporaryFolder.newFolder("too-large")

        val error = assertThrows(EventVideoInputException::class.java) {
            copyEventVideoToPendingFile(
                directory = directory,
                extension = "mp4",
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                maxBytes = 4L
            )
        }

        assertEquals(EventVideoInputException.Reason.TOO_LARGE, error.reason)
        assertEquals(0, directory.listFiles()?.size ?: 0)
    }

    @Test
    fun copyToPendingFile_removesPartialFileWhenInputReadFails() {
        val directory = temporaryFolder.newFolder("unreadable")
        val failingInput = object : InputStream() {
            private var firstRead = true

            override fun read(): Int = throw IOException("Input stream failed")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (firstRead) {
                    firstRead = false
                    buffer[offset] = 1
                    buffer[offset + 1] = 2
                    return 2
                }
                throw IOException("Input stream failed")
            }
        }

        val error = assertThrows(EventVideoInputException::class.java) {
            copyEventVideoToPendingFile(directory, "mp4", failingInput)
        }

        assertEquals(EventVideoInputException.Reason.UNREADABLE, error.reason)
        assertEquals(0, directory.listFiles()?.size ?: 0)
    }
}
