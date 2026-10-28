package com.cadence.fit.domain.insight

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.model.SleepSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryAdvisorTest {

    private fun assess(sessions: List<SleepSummary>, enabled: Boolean = true) =
        RecoveryAdvisor.assess(sessions, NOW, enabled, Fixtures.UTC)

    private fun nights(vararg minutes: Int): List<SleepSummary> {
        // Oldest first, one night apart, ending with the most recent night.
        val count = minutes.size
        return minutes.mapIndexed { index, value ->
            val endMs = NOW - (count - 1 - index) * DAY_MS
            Fixtures.sleep(endMs = endMs, minutes = value, id = "night$index")
        }
    }

    @Test
    fun `no context is produced when the feature is switched off`() {
        assertNull(assess(nights(480, 470, 460), enabled = false))
    }

    @Test
    fun `no context is produced without any sleep sessions`() {
        assertNull(assess(emptyList()))
    }

    @Test
    fun `a short night is described as shorter than usual without blaming training`() {
        val context = assess(nights(480, 490, 500, 360))!!

        assertEquals(RecoveryLevel.BELOW_USUAL, context.level)
        assertEquals(360, context.lastNightMinutes)
        assertEquals(490, context.recentAverageMinutes)
        assertTrue(context.detail.contains("less than your recent average"))
        assertTrue(context.detail.contains("reasonable option"))
        // The suggestion must stay an option, and must not assert a cause.
        assertTrue(context.detail.contains("It is also fine to train as planned"))
    }

    @Test
    fun `an average night is reported as close to usual`() {
        val context = assess(nights(480, 490, 500, 470))!!

        assertEquals(RecoveryLevel.ABOUT_USUAL, context.level)
        assertTrue(context.detail.contains("close to your recent average"))
        assertTrue(context.detail.contains("Nothing here suggests changing your plan"))
    }

    @Test
    fun `a longer night is reported as well rested without promising a result`() {
        val context = assess(nights(480, 490, 500, 560))!!

        assertEquals(RecoveryLevel.WELL_RESTED, context.level)
        assertTrue(context.detail.contains("more than your recent average"))
        assertTrue(context.detail.contains("warm-up"))
    }

    @Test
    fun `a single night has no average to compare against`() {
        val context = assess(nights(420))!!

        assertEquals(RecoveryLevel.ABOUT_USUAL, context.level)
        assertNull(context.recentAverageMinutes)
        assertTrue(context.detail.contains("close to your recent average"))
    }

    @Test
    fun `the trend only covers the most recent fortnight and runs forwards in time`() {
        val manyNights = (0 until 20).map { index ->
            val endMs = NOW - (19 - index) * DAY_MS
            Fixtures.sleep(endMs = endMs, minutes = 420 + index, id = "n$index")
        }

        val context = RecoveryAdvisor.assess(manyNights, NOW, true, Fixtures.UTC)!!

        assertEquals(14, context.trend.size)
        assertTrue(context.trend.zipWithNext().all { (a, b) -> a.epochMs < b.epochMs })
    }

    @Test
    fun `recovery context always carries a non-medical disclaimer`() {
        val context = assess(nights(480, 470))!!

        assertTrue(context.disclaimer.contains("not medical advice"))
        assertTrue(context.disclaimer.contains("never"))
        assertTrue(context.isSuggestionOnly)
    }

    @Test
    fun `the session timing and source are available for transparency`() {
        val summary = Fixtures.sleep(endMs = NOW, minutes = 400, sourcePackage = "com.example.nocta")
        val context = assess(listOf(summary, Fixtures.sleep(endMs = NOW - DAY_MS, minutes = 400)))!!

        assertEquals(summary.endMs, context.lastSessionEndMs)
        assertEquals(summary.startMs, context.lastSessionStartMs)
        assertEquals("com.example.nocta", context.sourceLabel)
    }

    @Test
    fun `duration formatting is readable`() {
        assertEquals("7h", RecoveryAdvisor.formatMinutes(420))
        assertEquals("7h 40m", RecoveryAdvisor.formatMinutes(460))
        assertEquals("45m", RecoveryAdvisor.formatMinutes(45))
    }
}
