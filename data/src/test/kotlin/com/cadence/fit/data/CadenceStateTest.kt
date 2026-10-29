package com.cadence.fit.data

import com.cadence.fit.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resume rule for an interrupted workout: the active session id from
 * preferences must resolve only to a workout that is still open.
 */
class CadenceStateTest {

    private fun session(id: String, ended: Boolean) = WorkoutSession(
        id = id,
        routineId = "routine-1",
        routineName = "Lower A",
        startedAtMs = 1_000L,
        endedAtMs = if (ended) 2_000L else null,
    )

    @Test
    fun `an open session is resolvable so the app can resume it`() {
        val state = CadenceState(
            loaded = true,
            sessions = listOf(session("open", ended = false)),
            activeSessionId = "open",
        )

        assertEquals("open", state.activeSession?.id)
        assertTrue(state.completedSessions.isEmpty())
    }

    @Test
    fun `an id that points at a finished workout is ignored`() {
        val state = CadenceState(
            loaded = true,
            sessions = listOf(session("done", ended = true)),
            activeSessionId = "done",
        )

        assertNull(state.activeSession)
        assertEquals(1, state.completedSessions.size)
    }

    @Test
    fun `an id that no longer exists is ignored`() {
        val state = CadenceState(loaded = true, activeSessionId = "deleted")

        assertNull(state.activeSession)
    }

    @Test
    fun `a state without an active id never resumes anything`() {
        val state = CadenceState(
            loaded = true,
            sessions = listOf(session("open", ended = false)),
        )

        assertNull(state.activeSession)
        assertTrue(state.sessions.size == 1)
    }

    @Test
    fun `an empty state has no data and is not confused with loading`() {
        val state = CadenceState()

        assertTrue(!state.loaded)
        assertTrue(!state.hasData)

        val loaded = CadenceState(loaded = true)
        assertTrue(loaded.loaded)
        assertTrue(!loaded.hasData)
    }
}
