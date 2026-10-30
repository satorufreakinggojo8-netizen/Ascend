package com.cadence.fit.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSearchPolicyTest {

    @Test
    fun `a device without speech recognition never starts listening`() {
        val state = VoiceSearchPolicy.onMicTapped(available = false, permissionGranted = true)

        assertEquals(VoiceSearchPhase.UNAVAILABLE, state.phase)
        assertFalse(state.isListening)
        assertTrue(state.message!!.contains("type"))
    }

    @Test
    fun `missing microphone permission is explained before anything is recorded`() {
        val state = VoiceSearchPolicy.onMicTapped(available = true, permissionGranted = false)
        val message = state.message.orEmpty()

        assertEquals(VoiceSearchPhase.PERMISSION_DENIED, state.phase)
        assertFalse(state.isListening)
        assertTrue(message.contains("Microphone"))
        assertTrue(message.contains("type"))
    }

    @Test
    fun `available and permitted starts listening`() {
        val state = VoiceSearchPolicy.onMicTapped(available = true, permissionGranted = true)

        assertEquals(VoiceSearchPhase.LISTENING, state.phase)
        assertTrue(state.isListening)
    }

    @Test
    fun `permission denial after the system prompt keeps text search usable`() {
        val state = VoiceSearchPolicy.onPermissionResult(granted = false, available = true)

        assertEquals(VoiceSearchPhase.PERMISSION_DENIED, state.phase)
        assertTrue(state.hasNotice)
        assertTrue(state.message!!.contains("No microphone permission"))
    }

    @Test
    fun `a granted permission starts listening only when speech is available`() {
        assertEquals(
            VoiceSearchPhase.LISTENING,
            VoiceSearchPolicy.onPermissionResult(granted = true, available = true).phase,
        )
        assertEquals(
            VoiceSearchPhase.UNAVAILABLE,
            VoiceSearchPolicy.onPermissionResult(granted = true, available = false).phase,
        )
    }

    @Test
    fun `a recognised phrase becomes a normal editable query`() {
        val state = VoiceSearchPolicy.onResult("  Romanian   deadlift. ")

        assertEquals("Romanian deadlift", state.query)
        assertEquals(VoiceSearchPhase.IDLE, state.phase)
        assertFalse(state.hasNotice)
    }

    @Test
    fun `an empty result keeps the previous query and offers the fallback`() {
        val state = VoiceSearchPolicy.onResult("   ", previousQuery = "bench")

        assertEquals("bench", state.query)
        assertEquals(VoiceSearchPhase.ERROR, state.phase)
        assertTrue(state.message!!.contains("type"))
    }

    @Test
    fun `a permission error is surfaced as a permission problem`() {
        val state = VoiceSearchPolicy.onError(permissionMissing = true)
        assertEquals(VoiceSearchPhase.PERMISSION_DENIED, state.phase)
    }

    @Test
    fun `a recognition error does not claim the microphone is blocked`() {
        val state = VoiceSearchPolicy.onError(permissionMissing = false)
        assertEquals(VoiceSearchPhase.ERROR, state.phase)
        assertTrue(state.message!!.contains("did not finish"))
    }

    @Test
    fun `cancelling returns to the quiet idle state`() {
        val state = VoiceSearchPolicy.onCancelled()

        assertEquals(VoiceSearchPhase.IDLE, state.phase)
        assertEquals("", state.query)
        assertFalse(state.hasNotice)
    }

    @Test
    fun `query normalisation only tidies text and never turns it into a command`() {
        assertEquals("LAT pulldown", VoiceSearchPolicy.normalizeQuery("  LAT   pulldown!  "))
        assertEquals("squat", VoiceSearchPolicy.normalizeQuery("squat."))
        assertEquals("", VoiceSearchPolicy.normalizeQuery(" ... "))
    }
}
