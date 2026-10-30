package com.cadence.fit.domain.insight

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.units.LB_PER_KG
import com.cadence.fit.domain.units.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDataTest {

    private fun benchSession(
        id: String,
        atMs: Long,
        weightKg: Double,
        reps: Int,
        setCount: Int = 3,
    ): WorkoutSession = Fixtures.session(
        id = id,
        atMs = atMs,
        sets = Fixtures.sets(
            exerciseId = "barbell-bench-press",
            count = setCount,
            weightKg = weightKg,
            reps = reps,
            atMs = atMs,
            sessionId = id,
        ),
    )

    private fun series(
        sessions: List<WorkoutSession>,
        unit: WeightUnit = WeightUnit.KG,
        window: ChartWindow = ChartWindow.FOUR_WEEKS,
    ) = ChartData.strengthSeries(
        sessions = sessions,
        exerciseId = "barbell-bench-press",
        exerciseName = "Barbell bench press",
        unit = unit,
        window = window,
        nowMs = NOW,
        zone = Fixtures.UTC,
    )

    @Test
    fun `volume and consistency charts have one bar per week in the window`() {
        assertEquals(4, ChartData.volumeByWeek(emptyList(), ChartWindow.FOUR_WEEKS, NOW, Fixtures.UTC).size)
        assertEquals(12, ChartData.volumeByWeek(emptyList(), ChartWindow.TWELVE_WEEKS, NOW, Fixtures.UTC).size)
        assertEquals(26, ChartData.volumeByWeek(emptyList(), ChartWindow.SIX_MONTHS, NOW, Fixtures.UTC).size)
        assertEquals(52, ChartData.volumeByWeek(emptyList(), ChartWindow.ONE_YEAR, NOW, Fixtures.UTC).size)
    }

    @Test
    fun `volume is attributed to the week it happened in`() {
        val points = ChartData.volumeByWeek(
            listOf(
                benchSession("this-week", NOW, 60.0, 10),
                benchSession("last-week", NOW - 7 * DAY_MS, 50.0, 10),
            ),
            ChartWindow.FOUR_WEEKS,
            NOW,
            Fixtures.UTC,
        )

        assertEquals(1800.0, points[points.lastIndex].volumeKg, 1e-9)
        assertEquals(1500.0, points[points.lastIndex - 1].volumeKg, 1e-9)
        assertEquals(1, points[points.lastIndex].sessions)
        assertTrue(points[0].volumeKg == 0.0)
    }

    @Test
    fun `the consistency chart carries the weekly target`() {
        val points = ChartData.consistencyByWeek(
            listOf(benchSession("a", NOW, 60.0, 10)),
            ChartWindow.FOUR_WEEKS,
            NOW,
            targetSessionsPerWeek = 4,
            zone = Fixtures.UTC,
        )

        assertEquals(4, points.size)
        assertEquals(1, points.last().sessions)
        assertEquals(4, points.last().targetSessions)
    }

    @Test
    fun `strength series has one point per session and reports progress`() {
        val result = series(
            listOf(
                benchSession("a", NOW - 7 * DAY_MS, 60.0, 10),
                benchSession("b", NOW, 65.0, 8),
            ),
        )

        assertEquals(2, result.points.size)
        assertEquals(80.0, result.firstE1rmKg, 1e-6)
        assertEquals(65.0 * (1 + 8 / 30.0), result.lastE1rmKg, 1e-6)
        assertTrue(result.changeKg > 0)
        assertTrue(result.changeFraction!! > 0)
        assertEquals(65.0, result.points.last().displayTopSet, 1e-9)
        assertEquals(8, result.points.last().topSetReps)
    }

    @Test
    fun `strength series filters out sessions before the window`() {
        val result = series(
            listOf(
                benchSession("old", NOW - 200 * DAY_MS, 100.0, 5),
                benchSession("new", NOW, 60.0, 10),
            ),
        )

        assertEquals(1, result.points.size)
        assertEquals("new", result.points.first().sessionId)
    }

    @Test
    fun `strength series converts display values but keeps stored values in kilograms`() {
        val result = series(listOf(benchSession("a", NOW, 60.0, 10)), unit = WeightUnit.LB)

        assertEquals(60.0, result.points.first().topSetKg, 1e-9)
        assertEquals(60.0 * LB_PER_KG, result.points.first().displayTopSet, 1e-6)
        assertEquals(WeightUnit.LB, result.unit)
    }

    @Test
    fun `an empty strength series reports zero change rather than a fake improvement`() {
        val result = series(emptyList())

        assertTrue(result.points.isEmpty())
        assertEquals(0.0, result.changeKg, 1e-9)
        assertNull(result.changeFraction)
    }

    @Test
    fun `strength series ignores warmup sets`() {
        val warmups: List<LoggedSet> = Fixtures.sets(
            exerciseId = "barbell-bench-press",
            count = 2,
            weightKg = 100.0,
            reps = 10,
            atMs = NOW,
            sessionId = "s",
            isWarmup = true,
        )
        val working = Fixtures.sets(
            exerciseId = "barbell-bench-press",
            count = 3,
            weightKg = 60.0,
            reps = 10,
            atMs = NOW,
            sessionId = "s",
            startIndex = 3,
        )
        val session = Fixtures.session(id = "s", atMs = NOW, sets = warmups + working)

        val result = series(listOf(session))

        assertEquals(60.0, result.points.first().topSetKg, 1e-9)
        assertEquals(80.0, result.lastE1rmKg, 1e-6)
    }

    @Test
    fun `summary counts this week, last week, volume change and totals`() {
        val summary = ChartData.summary(
            sessions = listOf(
                benchSession("w1", NOW, 60.0, 10),
                benchSession("w2", NOW - DAY_MS, 60.0, 10),
                benchSession("l1", NOW - 7 * DAY_MS, 50.0, 10),
            ),
            nowMs = NOW,
            targetSessionsPerWeek = 3,
            zone = Fixtures.UTC,
        )

        assertEquals(2, summary.sessionsThisWeek)
        assertEquals(3, summary.targetSessionsPerWeek)
        assertEquals(3600.0, summary.volumeThisWeekKg, 1e-9)
        assertEquals(1500.0, summary.volumeLastWeekKg, 1e-9)
        assertEquals(1.4, summary.volumeChangeFraction!!, 1e-6)
        assertEquals(2, summary.streakWeeks)
        assertEquals(3, summary.totalSessions)
        assertEquals(45, summary.averageSessionMinutes)
        assertTrue(summary.recordsLast28Days > 0)
    }

    @Test
    fun `summary does not fabricate a change without a previous week`() {
        val summary = ChartData.summary(
            sessions = listOf(benchSession("w1", NOW, 60.0, 10)),
            nowMs = NOW,
            targetSessionsPerWeek = 3,
            zone = Fixtures.UTC,
        )

        assertEquals(1, summary.sessionsThisWeek)
        assertNull(summary.volumeChangeFraction)
        assertTrue(summary.weeklyTargetMet.not())
    }

    @Test
    fun `summary on an empty history is all zeroes`() {
        val summary = ChartData.summary(emptyList(), NOW, targetSessionsPerWeek = 3, zone = Fixtures.UTC)

        assertEquals(0, summary.sessionsThisWeek)
        assertEquals(0, summary.totalSessions)
        assertEquals(0, summary.sessionsLast28Days)
        assertNull(summary.volumeChangeFraction)
        assertNull(summary.averageSessionMinutes)
    }

    @Test
    fun `summary ignores unfinished sessions`() {
        val unfinished = Fixtures.session(
            id = "u",
            atMs = NOW,
            sets = Fixtures.sets("barbell-bench-press", 3, 60.0, 10),
            ended = false,
        )

        val summary = ChartData.summary(listOf(unfinished), NOW, targetSessionsPerWeek = 3, zone = Fixtures.UTC)

        assertEquals(0, summary.totalSessions)
        assertEquals(0, summary.sessionsThisWeek)
    }
}
