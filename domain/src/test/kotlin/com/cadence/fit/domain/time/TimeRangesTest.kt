package com.cadence.fit.domain.time

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TimeRangesTest {

    private fun session(daysAgo: Int, index: Int = 0): WorkoutSession {
        val at = NOW - daysAgo * DAY_MS - index * 60_000L
        return Fixtures.session(
            id = "s-$daysAgo-$index",
            atMs = at,
            sets = Fixtures.sets("goblet-squat", 3, 20.0, 10, atMs = at, sessionId = "s-$daysAgo-$index"),
        )
    }

    @Test
    fun `weeks start on monday by default`() {
        // 2023-11-14 is a Tuesday.
        assertEquals(LocalDate.of(2023, 11, 13), TimeRanges.startOfWeek(LocalDate.of(2023, 11, 14)))
        assertEquals(DayOfWeek.MONDAY, TimeRanges.startOfWeek(LocalDate.of(2023, 11, 14)).dayOfWeek)
    }

    @Test
    fun `day conversion respects the supplied zone`() {
        assertEquals(LocalDate.of(2023, 11, 14), TimeRanges.toLocalDate(NOW, Fixtures.UTC))
    }

    @Test
    fun `daily buckets include days without sessions and end with today`() {
        val buckets = TimeRanges.dailyBuckets(listOf(session(daysAgo = 0)), days = 7, nowMs = NOW, zone = Fixtures.UTC)

        assertEquals(7, buckets.size)
        assertEquals(1, buckets.last().sessions)
        assertEquals(0, buckets.first().sessions)
        assertEquals(LocalDate.of(2023, 11, 14), buckets.last().day)
        // 3 sets of 20 kg x 10 reps.
        assertEquals(600.0, buckets.last().volumeKg, 1e-9)
    }

    @Test
    fun `weekly buckets are oldest first and sized to the request`() {
        val buckets = TimeRanges.weeklyBuckets(
            listOf(session(daysAgo = 0), session(daysAgo = 7)),
            weeks = 3,
            nowMs = NOW,
            zone = Fixtures.UTC,
        )

        assertEquals(3, buckets.size)
        assertEquals(1, buckets.last().sessions)
        assertEquals(1, buckets[buckets.lastIndex - 1].sessions)
        assertEquals(0, buckets.first().sessions)
        assertTrue(buckets.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
    }

    @Test
    fun `relative day labels today and yesterday`() {
        assertEquals("Today", TimeRanges.relativeDay(NOW, NOW, Fixtures.UTC))
        assertEquals("Yesterday", TimeRanges.relativeDay(NOW - DAY_MS, NOW, Fixtures.UTC))
        assertEquals("Tue 7 Nov", TimeRanges.relativeDay(NOW - 7 * DAY_MS, NOW, Fixtures.UTC))
    }

    @Test
    fun `sessions within filters by age and sorts newest first`() {
        val recent = TimeRanges.sessionsWithin(
            listOf(session(daysAgo = 0), session(daysAgo = 10), session(daysAgo = 40)),
            days = 28,
            nowMs = NOW,
        )

        assertEquals(2, recent.size)
        assertEquals("s-0-0", recent.first().id)
    }

    @Test
    fun `a streak counts consecutive weeks ending this week`() {
        val sessions = listOf(session(daysAgo = 0), session(daysAgo = 7), session(daysAgo = 14))
        assertEquals(3, TimeRanges.currentStreakWeeks(sessions, NOW, Fixtures.UTC))
    }

    @Test
    fun `an untrained current week does not break the streak`() {
        val sessions = listOf(session(daysAgo = 7), session(daysAgo = 14), session(daysAgo = 21))
        assertEquals(3, TimeRanges.currentStreakWeeks(sessions, NOW, Fixtures.UTC))
    }

    @Test
    fun `a missed week ends the streak`() {
        val sessions = listOf(session(daysAgo = 0), session(daysAgo = 14))
        assertEquals(1, TimeRanges.currentStreakWeeks(sessions, NOW, Fixtures.UTC))
    }

    @Test
    fun `an empty history has no streak`() {
        assertEquals(0, TimeRanges.currentStreakWeeks(emptyList(), NOW, Fixtures.UTC))
    }

    @Test
    fun `active week count counts distinct weeks`() {
        val sessions = listOf(session(daysAgo = 0, index = 0), session(daysAgo = 0, index = 1), session(daysAgo = 7))
        assertEquals(2, TimeRanges.activeWeekCount(sessions, Fixtures.UTC))
    }

    @Test
    fun `start of day is midnight in the given zone`() {
        val start = TimeRanges.startOfDayMs(NOW, Fixtures.UTC)
        assertEquals(LocalDate.of(2023, 11, 14), TimeRanges.toLocalDate(start, Fixtures.UTC))
        assertTrue(start <= NOW)
    }
}
