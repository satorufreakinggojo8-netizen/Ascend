package com.cadence.fit.domain.schedule

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.Fixtures.UTC
import com.cadence.fit.domain.core.SequentialIdGenerator
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePlannerTest {

    // 2023-11-13 is a Monday, which keeps the dates readable below.
    private val monday = LocalDate.of(2023, 11, 13)

    private fun planner() = SchedulePlanner(SequentialIdGenerator("slot"))

    private fun routines(count: Int) = (1..count).map { index ->
        Fixtures.routine(name = "Day $index", exercises = listOf(Fixtures.routineExercise("goblet-squat")))
    }

    @Test
    fun `a two day plan trains Monday and Thursday`() {
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            SchedulePlanner.trainingDays(2),
        )
    }

    @Test
    fun `a three day plan trains Monday Wednesday Friday`() {
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            SchedulePlanner.trainingDays(3),
        )
    }

    @Test
    fun `a full week plan stays out of Sunday`() {
        val days = SchedulePlanner.trainingDays(6)
        assertEquals(6, days.size)
        assertFalse(days.contains(DayOfWeek.SUNDAY))
    }

    @Test
    fun `planning a week creates one slot per routine on the right dates`() {
        val slots = planner().planWeek(routines(3), monday, emptyList())

        assertEquals(3, slots.size)
        assertEquals(setOf("Day 1", "Day 2", "Day 3"), slots.map { it.routineName }.toSet())
        assertEquals(
            listOf(monday, monday.plusDays(2), monday.plusDays(4)),
            slots.map { it.date },
        )
        assertTrue(slots.all { it.status == ScheduleStatus.PLANNED })
    }

    @Test
    fun `planning an already planned week changes nothing`() {
        val planner = planner()
        val first = planner.planWeek(routines(3), monday, emptyList())
        val second = planner.planWeek(routines(3), monday, first)

        assertEquals(first, second)
    }

    @Test
    fun `a plan is not back-filled with days that already passed`() {
        val slots = planner().planWeek(
            routines = routines(3),
            weekStart = monday,
            existing = emptyList(),
            notBefore = monday.plusDays(3),
        )

        assertTrue(slots.none { it.date.isBefore(monday.plusDays(3)) })
        assertEquals(listOf(monday.plusDays(4)), slots.map { it.date })
    }

    @Test
    fun `planning with no routines does nothing`() {
        assertTrue(planner().planWeek(emptyList(), monday, emptyList()).isEmpty())
    }

    @Test
    fun `a single routine repeats across the chosen training days`() {
        val slots = planner().planWeek(routines(1), monday, emptyList(), daysPerWeek = 3)

        assertEquals(3, slots.size)
        assertTrue(slots.all { it.routineName == "Day 1" })
    }

    @Test
    fun `week slots are limited to the requested week`() {
        val planner = planner()
        val thisWeek = planner.planWeek(routines(2), monday, emptyList())
        val nextWeek = planner.planWeek(routines(2), monday.plusWeeks(1), thisWeek)

        assertEquals(2, planner.slotsInWeek(nextWeek, monday).size)
        assertEquals(2, planner.slotsInWeek(nextWeek, monday.plusWeeks(1)).size)
        assertTrue(planner.hasWeek(nextWeek, monday))
        assertFalse(planner.hasWeek(thisWeek, monday.plusWeeks(3)))
    }

    @Test
    fun `the next open slot skips earlier days and completed ones`() {
        val planner = planner()
        val slots = planner.planWeek(routines(3), monday, emptyList())
        val completed = planner.complete(slots, slots.first().id, "session-1", NOW)

        val next = planner.nextSlot(completed, monday)
        assertEquals(monday.plusDays(2), next?.date)
        assertEquals(monday.plusDays(2), planner.todaySlot(completed, monday.plusDays(2))?.date)
        assertNull(planner.todaySlot(completed, monday.plusDays(3)))
    }

    @Test
    fun `rescheduling moves a planned session and keeps its identity`() {
        val planner = planner()
        val slots = planner.planWeek(routines(3), monday, emptyList())
        val target = slots.first()

        val moved = planner.move(slots, target.id, monday.plusDays(1))

        val updated = moved.first { it.id == target.id }
        assertEquals(monday.plusDays(1), updated.date)
        assertEquals(ScheduleStatus.PLANNED, updated.status)
        assertEquals(target.routineName, updated.routineName)
        assertEquals(target.routineId, updated.routineId)
        assertEquals(slots.size, moved.size)
    }

    @Test
    fun `a completed day cannot be moved or skipped`() {
        val planner = planner()
        val slots = planner.planWeek(routines(2), monday, emptyList())
        val target = slots.first()
        val completed = planner.complete(slots, target.id, "session-9", NOW)

        assertEquals(completed, planner.move(completed, target.id, monday.plusDays(3)))
        assertEquals(completed, planner.skip(completed, target.id))
    }

    @Test
    fun `skipping a day keeps it visible and can be undone`() {
        val planner = planner()
        val slots = planner.planWeek(routines(2), monday, emptyList())
        val target = slots.first()

        val skipped = planner.skip(slots, target.id)
        assertEquals(ScheduleStatus.SKIPPED, skipped.first { it.id == target.id }.status)
        assertEquals(slots.size, skipped.size)

        val reopened = planner.reopen(skipped, target.id)
        assertEquals(ScheduleStatus.PLANNED, reopened.first { it.id == target.id }.status)
    }

    @Test
    fun `completing a day records the session it points at`() {
        val planner = planner()
        val slots = planner.planWeek(routines(2), monday, emptyList())
        val target = slots.first()

        val done = planner.complete(slots, target.id, "session-7", NOW)
        val updated = done.first { it.id == target.id }

        assertEquals(ScheduleStatus.COMPLETED, updated.status)
        assertEquals("session-7", updated.sessionId)
        assertEquals(NOW, updated.completedAtMs)
    }

    @Test
    fun `a session on a planned day completes that day`() {
        val planner = planner()
        val slots = planner.planWeek(routines(2), monday, emptyList())
        val sessionDate = slots.first().date
        val atMs = com.cadence.fit.domain.time.TimeRanges.startOfDayMs(sessionDate, UTC) + 10 * 60 * 60 * 1000
        val session = Fixtures.session(
            id = "session-11",
            atMs = atMs,
            routineId = slots.first().routineId,
            routineName = slots.first().routineName,
        )

        val linked = planner.linkSession(slots, session, UTC)
        val slot = linked.first { it.id == slots.first().id }

        assertEquals(ScheduleStatus.COMPLETED, slot.status)
        assertEquals("session-11", slot.sessionId)
    }

    @Test
    fun `a workout finished after midnight still completes the day it started`() {
        val planner = planner()
        val slots = planner.planWeek(routines(1), monday, emptyList())
        val target = slots.first()
        val startMs = com.cadence.fit.domain.time.TimeRanges.startOfDayMs(monday, UTC) + 23 * 60 * 60 * 1000
        val session = Fixtures.session(
            id = "session-31",
            atMs = startMs + 75 * 60 * 1000,
            routineId = target.routineId,
            routineName = target.routineName,
        ).copy(startedAtMs = startMs)

        val linked = planner.linkSession(slots, session, UTC)

        assertEquals(ScheduleStatus.COMPLETED, linked.first { it.id == target.id }.status)
    }

    @Test
    fun `a late unrelated workout does not claim another routine's day`() {
        val planner = planner()
        val slots = planner.planWeek(routines(1), monday, emptyList())
        val startMs = com.cadence.fit.domain.time.TimeRanges.startOfDayMs(monday, UTC) + 23 * 60 * 60 * 1000
        val session = Fixtures.session(
            id = "session-32",
            atMs = startMs + 75 * 60 * 1000,
            routineId = "routine-other",
        ).copy(startedAtMs = startMs)

        assertEquals(slots, planner.linkSession(slots, session, UTC))
    }

    @Test
    fun `an unfinished session never completes a day`() {
        val planner = planner()
        val slots = planner.planWeek(routines(2), monday, emptyList())
        val atMs = com.cadence.fit.domain.time.TimeRanges.startOfDayMs(monday, UTC) + 8 * 60 * 60 * 1000
        val session = Fixtures.session(id = "session-12", atMs = atMs, ended = false)

        assertEquals(slots, planner.linkSession(slots, session, UTC))
    }

    @Test
    fun `a session on an unplanned day is left alone`() {
        val planner = planner()
        val slots = planner.planWeek(routines(2), monday, emptyList())
        val atMs = com.cadence.fit.domain.time.TimeRanges.startOfDayMs(monday.plusDays(2), UTC) + 8 * 60 * 60 * 1000
        val session = Fixtures.session(id = "session-13", atMs = atMs)

        assertEquals(slots, planner.linkSession(slots, session, UTC))
    }

    @Test
    fun `the week tally counts planned completed and skipped days`() {
        val planner = planner()
        val slots = planner.planWeek(routines(3), monday, emptyList())
        val done = planner.complete(slots, slots[0].id, "session-1", NOW)
        val mixed = planner.skip(done, slots[1].id)

        val tally = planner.tally(mixed)
        assertEquals(1, tally.completed)
        assertEquals(1, tally.skipped)
        assertEquals(1, tally.planned)
        assertEquals(3, tally.total)
        assertFalse(tally.isEmpty)
    }

    @Test
    fun `two planned days on one date link the one matching the routine`() {
        val planner = planner()
        val base = planner.planWeek(routines(2), monday, emptyList())
        val moved = planner.move(base, base[1].id, base[0].date)
        val atMs = com.cadence.fit.domain.time.TimeRanges.startOfDayMs(base[0].date, UTC) + 9 * 60 * 60 * 1000
        val session = Fixtures.session(
            id = "session-21",
            atMs = atMs,
            routineId = base[1].routineId,
            routineName = base[1].routineName,
        )

        val linked = planner.linkSession(moved, session, UTC)

        assertEquals(ScheduleStatus.COMPLETED, linked.first { it.id == base[1].id }.status)
        assertEquals(ScheduleStatus.PLANNED, linked.first { it.id == base[0].id }.status)
    }

    @Test
    fun `deleting a routine drops its planned days but keeps completed ones`() {
        val planner = planner()
        val slots = planner.planWeek(routines(3), monday, emptyList())
        val done = planner.complete(slots, slots[0].id, "session-1", NOW)

        val cleaned = planner.dropSlotsForRoutine(done, slots[1].routineId!!)

        assertEquals(2, cleaned.size)
        assertTrue(cleaned.none { it.id == slots[1].id })
        assertTrue(cleaned.any { it.id == slots[0].id && it.status == ScheduleStatus.COMPLETED })
    }

    @Test
    fun `a deleted routine's completed day keeps its history link but loses the routine id`() {
        val planner = planner()
        val slots = planner.planWeek(routines(1), monday, emptyList())
        val target = slots.first()
        val done = planner.complete(slots, target.id, "session-1", NOW)

        val cleaned = planner.dropSlotsForRoutine(done, target.routineId!!)
        val kept = cleaned.single()

        assertNull(kept.routineId)
        assertEquals("session-1", kept.sessionId)
        assertEquals(target.routineName, kept.routineName)
    }

    @Test
    fun `scheduled records keep dates as plain calendar days`() {
        val slot = ScheduledWorkout(
            id = "slot-1",
            date = monday,
            routineId = null,
            routineName = "Freestyle",
        )
        assertTrue(slot.isOpen)
        assertEquals("Planned", slot.statusLabel)
        assertEquals(monday, SchedulePlanner.weekStart(monday.plusDays(3)))
    }
}
