package com.example.loginfirebase.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EventAudioInputTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createCaptureFile_createsM4aInsideEventPendingDirectory() {
        val file = EventAudioInput.createCaptureFile(temporaryFolder.root, "event-1")

        assertTrue(file.isFile)
        assertTrue(file.name.startsWith("recording_"))
        assertTrue(file.name.endsWith(".m4a"))
        assertEquals(
            temporaryFolder.root.resolve("event_audio_pending/event-1").canonicalFile,
            file.parentFile?.canonicalFile
        )
    }

    @Test
    fun createCaptureFile_sanitizesEventIdAndUsesFallbackWhenBlank() {
        val unsafeIdFile = EventAudioInput.createCaptureFile(temporaryFolder.root, "../event-1/")
        val blankIdFile = EventAudioInput.createCaptureFile(temporaryFolder.root, "!!!")

        assertEquals("event-1", unsafeIdFile.parentFile?.name)
        assertEquals("event", blankIdFile.parentFile?.name)
    }

    @Test
    fun validateCaptureFile_acceptsNonEmptyRecordingInExpectedDirectory() {
        val file = EventAudioInput.createCaptureFile(temporaryFolder.root, "event-1")
        file.writeBytes(byteArrayOf(1, 2, 3))

        val recording = EventAudioInput.validateCaptureFile(temporaryFolder.root, "event-1", file)

        assertEquals("event-1", recording.eventId)
        assertEquals(file, recording.file)
    }

    @Test
    fun validateCaptureFile_rejectsEmptyRecordingAsTooShort() {
        val file = EventAudioInput.createCaptureFile(temporaryFolder.root, "event-1")

        val error = assertThrows(EventAudioInputException::class.java) {
            EventAudioInput.validateCaptureFile(temporaryFolder.root, "event-1", file)
        }

        assertEquals(EventAudioInputException.Reason.TOO_SHORT, error.reason)
    }

    @Test
    fun validateCaptureFile_rejectsFileOutsideExpectedDirectory() {
        val file = temporaryFolder.newFile("outside.m4a").apply { writeBytes(byteArrayOf(1)) }

        val error = assertThrows(EventAudioInputException::class.java) {
            EventAudioInput.validateCaptureFile(temporaryFolder.root, "event-1", file)
        }

        assertEquals(EventAudioInputException.Reason.INVALID_RECORDING, error.reason)
    }

    @Test
    fun audioMimeType_acceptsAudioAndRejectsOtherMediaTypes() {
        assertTrue(EventMediaStore.matchesType(EventMediaType.AUDIO, "audio/mp4"))
        assertTrue(EventMediaStore.matchesType(EventMediaType.AUDIO, "AUDIO/AAC"))
        assertFalse(EventMediaStore.matchesType(EventMediaType.AUDIO, "video/mp4"))
        assertFalse(EventMediaStore.matchesType(EventMediaType.AUDIO, "application/octet-stream"))
    }
}
