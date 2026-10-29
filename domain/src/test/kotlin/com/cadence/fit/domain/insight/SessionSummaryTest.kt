package com.cadence.fit.domain.insight

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.library.ExerciseLibrary
import com.cadence.fit.domain.math.PersonalRecordType
import com.cadence.fit.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {

    private fun session(
        id: String,
        atMs: Long,
        sets: List<com.cadence.fit.domain.model.LoggedSet>,
        routineId: String? = "routine-lower",
        routineName: String = "Lower A",
    ): WorkoutSession = Fixtures.session(
        id = id,
        atMs = atMs,
        sets = sets,
        routineId = routineId,
        routineName = routineName,
    )

    private fun build(session: WorkoutSession, all: List<WorkoutSession>) =
        SessionSummaries.build(session, all, ExerciseLibrary)

    @Test
    fun `a finished session summarises measured totals`() {
        val current = session(
            "s2",
            NOW,
            Fixtures.sets("barbell-back-squat", 3, 100.0, 5, atMs = NOW, sessionId = "s2") +
                Fixtures.set("barbell-back-squat", 60.0, 5, atMs = NOW, sessionId = "s2", setIndex = 4, isWarmup = true),
        )

        val summary = build(current, listOf(current))!!

        assertEquals(1500.0, summary.volumeKg, 1e-9)
        assertEquals(3, summary.workingSets)
        assertEquals(1, summary.warmupSets)
        assertEquals(15, summary.totalReps)
        assertEquals(1, summary.exerciseCount)
        assertEquals(45, summary.durationMinutes)
    }

    @Test
    fun `an unfinished session has no summary`() {
        val open = Fixtures.session(
            id = "open",
            atMs = NOW,
            sets = Fixtures.sets("goblet-squat", 3, 20.0, 10),
            ended = false,
        )

        assertNull(build(open, listOf(open)))
    }

    @Test
    fun `a session without working sets has no summary`() {
        val warmupsOnly = session("s1", NOW, Fixtures.sets("goblet-squat", 2, 20.0, 10, isWarmup = true))

        assertNull(build(warmupsOnly, listOf(warmupsOnly)))
    }

    @Test
    fun `the first session sets a baseline instead of claiming a record`() {
        val current = session("s1", NOW, Fixtures.sets("barbell-back-squat", 3, 100.0, 5))

        val summary = build(current, listOf(current))!!

        assertTrue(summary.records.isEmpty())
        assertNull(summary.previousVolumeKg)
        assertTrue(summary.comparisonNote.contains("baseline"))
    }

    @Test
    fun `a heavier set is reported as a new record with the old value`() {
        val before = session("s1", NOW - 7 * DAY_MS, Fixtures.sets("barbell-back-squat", 3, 100.0, 5, atMs = NOW - 7 * DAY_MS, sessionId = "s1"))
        val current = session("s2", NOW, Fixtures.sets("barbell-back-squat", 3, 105.0, 5, atMs = NOW, sessionId = "s2"))

        val summary = build(current, listOf(before, current))!!
        val heaviest = summary.records.firstOrNull { it.type == PersonalRecordType.HEAVIEST_WEIGHT }

        assertTrue(heaviest != null)
        assertEquals(105.0, heaviest!!.value, 1e-9)
        assertEquals(100.0, heaviest.previousValue!!, 1e-9)
    }

    @Test
    fun `repeating the same session does not invent records`() {
        val before = session("s1", NOW - 7 * DAY_MS, Fixtures.sets("barbell-back-squat", 3, 100.0, 5, atMs = NOW - 7 * DAY_MS, sessionId = "s1"))
        val current = session("s2", NOW, Fixtures.sets("barbell-back-squat", 3, 100.0, 5, atMs = NOW, sessionId = "s2"))

        val summary = build(current, listOf(before, current))!!

        assertTrue(summary.records.isEmpty())
    }

    @Test
    fun `more reps at the same weight is a record`() {
        val before = session("s1", NOW - 7 * DAY_MS, Fixtures.sets("pull-up", 3, 0.0, 6, atMs = NOW - 7 * DAY_MS, sessionId = "s1"))
        val current = session("s2", NOW, Fixtures.sets("pull-up", 3, 0.0, 8, atMs = NOW, sessionId = "s2"))

        val summary = build(current, listOf(before, current))!!

        assertTrue(summary.records.any { it.type == PersonalRecordType.MOST_REPS && it.value == 8.0 })
    }

    @Test
    fun `bodyweight work never claims a volume record at zero kilograms`() {
        val current = session("s1", NOW, Fixtures.sets("push-up", 3, 0.0, 15))

        val summary = build(current, listOf(current))!!

        assertFalse(summary.records.any { it.type == PersonalRecordType.MOST_VOLUME })
    }

    @Test
    fun `volume is compared with the last session of the same routine`() {
        val before = session("s1", NOW - 7 * DAY_MS, Fixtures.sets("barbell-back-squat", 3, 100.0, 5, atMs = NOW - 7 * DAY_MS, sessionId = "s1"))
        val other = session(
            id = "other",
            atMs = NOW - 3 * DAY_MS,
            sets = Fixtures.sets("barbell-bench-press", 3, 80.0, 8, atMs = NOW - 3 * DAY_MS, sessionId = "other"),
            routineId = "routine-upper",
            routineName = "Upper A",
        )
        val current = session("s2", NOW, Fixtures.sets("barbell-back-squat", 3, 110.0, 5, atMs = NOW, sessionId = "s2"))

        val summary = build(current, listOf(before, other, current))!!

        assertEquals(1500.0, summary.previousVolumeKg!!, 1e-9)
        assertEquals("Lower A", summary.comparisonRoutineName)
        assertTrue(summary.volumeChangeFraction!! > 0.0)
    }

    @Test
    fun `the comparison falls back to the most recent session when the routine is new`() {
        val before = session(
            id = "s1",
            atMs = NOW - 2 * DAY_MS,
            sets = Fixtures.sets("barbell-bench-press", 3, 80.0, 8, atMs = NOW - 2 * DAY_MS, sessionId = "s1"),
            routineId = "routine-upper",
            routineName = "Upper A",
        )
        val current = session(
            id = "s2",
            atMs = NOW,
            sets = Fixtures.sets("barbell-back-squat", 3, 100.0, 5, atMs = NOW, sessionId = "s2"),
            routineId = "routine-new",
            routineName = "Legs B",
        )

        val summary = build(current, listOf(before, current))!!

        assertEquals("Upper A", summary.comparisonRoutineName)
        assertTrue(summary.comparisonNote.contains("Upper A"))
    }

    @Test
    fun `muscle shares add up to one and follow the logged volume`() {
        val current = session(
            "s1",
            NOW,
            Fixtures.sets("barbell-back-squat", 3, 100.0, 5) +
                Fixtures.sets("barbell-curl", 3, 20.0, 10, startIndex = 4),
        )

        val summary = build(current, listOf(current))!!
        val shares = summary.muscleShares()

        assertTrue(shares.isNotEmpty())
        assertEquals(1.0, shares.sumOf { it.second }, 1e-6)
        assertEquals("Quads", shares.first().first.displayName)
    }

    @Test
    fun `the comparison sentence is plain and never medical`() {
        val before = session("s1", NOW - 7 * DAY_MS, Fixtures.sets("barbell-back-squat", 3, 100.0, 5, atMs = NOW - 7 * DAY_MS, sessionId = "s1"))
        val current = session("s2", NOW, Fixtures.sets("barbell-back-squat", 2, 100.0, 5, atMs = NOW, sessionId = "s2"))

        val text = SessionSummaries.volumeComparisonText(build(current, listOf(before, current))!!)

        assertTrue(text.contains("less volume"))
        assertFalse(text.contains("overtraining"))
        assertFalse(text.contains("injury"))
    }

    @Test
    fun `the measurement note separates measured values from the estimate`() {
        assertTrue(SessionSummaries.MEASUREMENT_NOTE.contains("measured"))
        assertTrue(SessionSummaries.MEASUREMENT_NOTE.contains("Estimated 1RM"))
        assertTrue(SessionSummaries.MEASUREMENT_NOTE.contains("not a tested maximum"))
    }
}
