package com.cadence.fit.domain.schedule

import com.cadence.fit.domain.core.IdGenerator
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import com.cadence.fit.domain.model.WeekTally
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.time.TimeRanges
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.ZoneId

/**
 * Pure calendar arithmetic for the training week.
 *
 * The planner never invents history: it only moves, skips and links its own
 * [ScheduledWorkout] records. Logged sessions are passed in read-only so a week can
 * be described accurately.
 */
class SchedulePlanner(private val ids: IdGenerator) {

    /**
     * Fills a week from the user's routines.
     *
     * Days that already have a slot keep it, days before [notBefore] are left alone
     * (a plan is not back-filled with training that never happened), and the routine
     * order is cycled so a three-day plan runs A, B, C across Monday, Wednesday and
     * Friday.
     */
    fun planWeek(
        routines: List<Routine>,
        weekStart: LocalDate,
        existing: List<ScheduledWorkout>,
        daysPerWeek: Int = routines.size,
        notBefore: LocalDate? = null,
    ): List<ScheduledWorkout> {
        if (routines.isEmpty()) return existing
        val week = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val occupied = existing.filter { it.date in week..week.plusDays(6) }.map { it.date }.toSet()
        val additions = mutableListOf<ScheduledWorkout>()

        trainingDays(daysPerWeek).forEachIndexed { index, day ->
            val date = week.plusDays((day.value - 1).toLong())
            if (date in occupied) return@forEachIndexed
            if (notBefore != null && date.isBefore(notBefore)) return@forEachIndexed
            val routine = routines[index % routines.size]
            additions += ScheduledWorkout(
                id = ids.next(),
                date = date,
                routineId = routine.id,
                routineName = routine.name,
                status = ScheduleStatus.PLANNED,
            )
        }

        return (existing + additions).sortedWith(compareBy({ it.date }, { it.id }))
    }

    fun slotsInWeek(slots: List<ScheduledWorkout>, weekStart: LocalDate): List<ScheduledWorkout> {
        val week = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return slots.filter { !it.date.isBefore(week) && !it.date.isAfter(week.plusDays(6)) }
            .sortedWith(compareBy({ it.date }, { it.id }))
    }

    fun hasWeek(slots: List<ScheduledWorkout>, weekStart: LocalDate): Boolean =
        slotsInWeek(slots, weekStart).isNotEmpty()

    /** The first still-open slot on [today], if the week has one. */
    fun todaySlot(slots: List<ScheduledWorkout>, today: LocalDate): ScheduledWorkout? =
        slots.filter { it.date == today && it.isOpen }.minByOrNull { it.id }

    /** The next open slot today or later. */
    fun nextSlot(slots: List<ScheduledWorkout>, today: LocalDate): ScheduledWorkout? =
        slots.filter { it.isOpen && !it.date.isBefore(today) }
            .minWithOrNull(compareBy({ it.date }, { it.id }))

    /**
     * Moves a planned or skipped day to another date.
     *
     * Completed days are refused: moving one would rewrite what actually happened,
     * and the session it points at is already in history.
     */
    fun move(slots: List<ScheduledWorkout>, slotId: String, newDate: LocalDate): List<ScheduledWorkout> =
        update(slots, slotId) { slot ->
            if (slot.status == ScheduleStatus.COMPLETED) {
                slot
            } else {
                slot.copy(date = newDate, status = ScheduleStatus.PLANNED)
            }
        }

    fun skip(slots: List<ScheduledWorkout>, slotId: String): List<ScheduledWorkout> =
        update(slots, slotId) { slot ->
            if (slot.status == ScheduleStatus.PLANNED) {
                slot.copy(status = ScheduleStatus.SKIPPED)
            } else {
                slot
            }
        }

    /** Puts a skipped day back on the plan. */
    fun reopen(slots: List<ScheduledWorkout>, slotId: String): List<ScheduledWorkout> =
        update(slots, slotId) { slot ->
            if (slot.status == ScheduleStatus.SKIPPED) {
                slot.copy(status = ScheduleStatus.PLANNED)
            } else {
                slot
            }
        }

    /**
     * Marks a day as trained by pointing it at the session that was logged.
     *
     * [sessionId] comes from the session itself, so the week view can open the same
     * workout that is in history.
     */
    fun complete(
        slots: List<ScheduledWorkout>,
        slotId: String,
        sessionId: String,
        completedAtMs: Long,
    ): List<ScheduledWorkout> = update(slots, slotId) { slot ->
        if (slot.status == ScheduleStatus.COMPLETED) {
            slot
        } else {
            slot.copy(
                status = ScheduleStatus.COMPLETED,
                sessionId = sessionId,
                completedAtMs = completedAtMs,
            )
        }
    }

    /**
     * Links a finished session to the planned day it belongs to.
     *
     * Only a day that is currently planned on the session's local date is claimed,
     * and a matching routine id wins over a same-day slot for a different routine.
     * If nothing sensible matches, the plan is left alone rather than being given a
     * completion it did not earn.
     */
    fun linkSession(
        slots: List<ScheduledWorkout>,
        session: WorkoutSession,
        zone: ZoneId = TimeRanges.zone(),
    ): List<ScheduledWorkout> {
        if (!session.isComplete) return slots
        val date = TimeRanges.toLocalDate(session.completedAtMs, zone)
        val candidates = slots.filter { it.date == date && it.isOpen }
        val target = candidates.firstOrNull { it.routineId != null && it.routineId == session.routineId }
            ?: candidates.singleOrNull()
            ?: lateSessionTarget(slots, session, zone)
            ?: return slots
        return complete(slots, target.id, session.id, session.completedAtMs)
    }

    /**
     * A workout that started before midnight and finished after it still belongs to
     * the day it started. Only an exact routine match is accepted across the day
     * boundary, so an unrelated late session cannot claim tomorrow's plan.
     */
    private fun lateSessionTarget(
        slots: List<ScheduledWorkout>,
        session: WorkoutSession,
        zone: ZoneId,
    ): ScheduledWorkout? {
        val startDate = TimeRanges.toLocalDate(session.startedAtMs, zone)
        val endDate = TimeRanges.toLocalDate(session.completedAtMs, zone)
        if (startDate == endDate || session.routineId == null) return null
        return slots.firstOrNull { it.date == startDate && it.isOpen && it.routineId == session.routineId }
    }

    fun tally(slots: List<ScheduledWorkout>): WeekTally = WeekTally(
        planned = slots.count { it.status == ScheduleStatus.PLANNED },
        completed = slots.count { it.status == ScheduleStatus.COMPLETED },
        skipped = slots.count { it.status == ScheduleStatus.SKIPPED },
    )

    /** Removes days that point at a deleted routine, so the week does not show ghosts. */
    fun dropSlotsForRoutine(slots: List<ScheduledWorkout>, routineId: String): List<ScheduledWorkout> =
        slots.filterNot { it.routineId == routineId && it.status != ScheduleStatus.COMPLETED }
            .map { slot ->
                if (slot.routineId == routineId) slot.copy(routineId = null) else slot
            }

    private fun update(
        slots: List<ScheduledWorkout>,
        slotId: String,
        transform: (ScheduledWorkout) -> ScheduledWorkout,
    ): List<ScheduledWorkout> =
        slots.map { slot -> if (slot.id == slotId) transform(slot) else slot }
            .sortedWith(compareBy({ it.date }, { it.id }))

    companion object {
        /**
         * Which weekday a plan with [routines] routines uses.
         *
         * Gaps are placed between sessions rather than in one block at the end, so a
         * two-day plan trains Monday and Thursday instead of Monday and Tuesday.
         */
        fun trainingDays(routines: Int): List<DayOfWeek> {
            val count = routines.coerceIn(0, 6)
            return when (count) {
                0 -> emptyList()
                1 -> listOf(DayOfWeek.MONDAY)
                2 -> listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
                3 -> listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                4 -> listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                5 -> listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                else -> listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SATURDAY,
                )
            }
        }

        fun weekStart(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
