package com.cadence.fit.domain.insight

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.model.ProgressLevel
import com.cadence.fit.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelAssessorTest {

    private fun session(daysAgo: Int, index: Int): WorkoutSession {
        val at = NOW - daysAgo * DAY_MS - index * 60_000L
        return Fixtures.session(
            id = "s-$daysAgo-$index",
            atMs = at,
            sets = Fixtures.sets("goblet-squat", 3, 20.0, 10, atMs = at, sessionId = "s-$daysAgo-$index"),
        )
    }

    /** [sessionsPerWeek] sessions in each of [weeks] consecutive weeks. */
    private fun blocks(weeks: Int, sessionsPerWeek: Int, startDaysAgo: Int = 0): List<WorkoutSession> =
        (0 until weeks).flatMap { week ->
            (0 until sessionsPerWeek).map { index ->
                session(daysAgo = startDaysAgo + week * 7, index = index)
            }
        }

    private fun assess(sessions: List<WorkoutSession>, enabled: Boolean = true) =
        LevelAssessor.assess(sessions, NOW, Fixtures.UTC, enabled)

    @Test
    fun `nothing is returned when the user hides the level`() {
        assertNull(assess(blocks(weeks = 12, sessionsPerWeek = 3), enabled = false))
    }

    @Test
    fun `an empty history sits at the starting tier`() {
        val assessment = assess(emptyList())!!
        assertEquals(ProgressLevel.STARTING, assessment.level)
        assertTrue(assessment.nextStepHint.contains("4 more"))
    }

    @Test
    fun `fewer than four sessions is still the starting tier`() {
        val assessment = assess(blocks(weeks = 1, sessionsPerWeek = 3))!!
        assertEquals(ProgressLevel.STARTING, assessment.level)
        assertTrue(assessment.nextStepHint.contains("1 more"))
    }

    @Test
    fun `regular training under eight weeks is beginner`() {
        val assessment = assess(blocks(weeks = 4, sessionsPerWeek = 3))!!
        assertEquals(ProgressLevel.BEGINNER, assessment.level)
        assertEquals(4, assessment.consistencyWeeks)
        assertTrue(assessment.nextStepHint.contains("Intermediate"))
    }

    @Test
    fun `eight active weeks at twice a week is intermediate`() {
        val assessment = assess(blocks(weeks = 8, sessionsPerWeek = 2))!!
        assertEquals(ProgressLevel.INTERMEDIATE, assessment.level)
        assertEquals(8, assessment.consistencyWeeks)
        assertEquals(2.0, assessment.sessionsPerWeek, 1e-9)
    }

    @Test
    fun `long consistent training at three sessions a week is advanced`() {
        val recent = blocks(weeks = 8, sessionsPerWeek = 3)
        val older = blocks(weeks = 24, sessionsPerWeek = 1, startDaysAgo = 63)
        val assessment = assess(recent + older)!!

        assertEquals(ProgressLevel.ADVANCED, assessment.level)
        assertTrue(assessment.consistencyWeeks >= 24)
        assertTrue(assessment.nextStepHint.contains("top tier"))
    }

    @Test
    fun `a long history at one session a week does not reach intermediate`() {
        val recent = blocks(weeks = 8, sessionsPerWeek = 1)
        val older = blocks(weeks = 24, sessionsPerWeek = 1, startDaysAgo = 63)
        val assessment = assess(recent + older)!!

        // Intermediate requires averaging two sessions a week, which the app states
        // in the benchmark text and repeats as the next step for the user.
        assertEquals(ProgressLevel.BEGINNER, assessment.level)
        assertTrue(assessment.consistencyWeeks >= 24)
        assertEquals(1.0, assessment.sessionsPerWeek, 1e-9)
        assertTrue(assessment.benchmark.contains("averaging 2 sessions per week"))
    }

    @Test
    fun `the benchmark is always stated in plain language`() {
        val assessment = assess(blocks(weeks = 10, sessionsPerWeek = 2))!!

        assertTrue(assessment.benchmark.contains("Intermediate"))
        assertTrue(assessment.benchmark.contains("Advanced"))
        assertTrue(assessment.benchmark.contains("Only sessions you logged are counted"))
    }

    @Test
    fun `the detail line quotes the user's own numbers`() {
        val assessment = assess(blocks(weeks = 4, sessionsPerWeek = 3))!!
        assertTrue(assessment.detail.contains("12 sessions"))
        assertTrue(assessment.detail.contains("4 active weeks"))
    }

    @Test
    fun `the disclaimer refuses to be a medical or comparative claim`() {
        val assessment = assess(blocks(weeks = 4, sessionsPerWeek = 3))!!
        assertTrue(assessment.disclaimer.contains("not a medical assessment"))
        assertTrue(assessment.disclaimer.contains("does not compare you with other people"))
        assertNotNull(assessment.level)
    }
}
