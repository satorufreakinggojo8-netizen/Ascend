package com.cadence.fit.domain.model

import java.time.LocalDate

/**
 * State of a planned training day.
 *
 * A slot is plan data, never history. Deleting or rescheduling it cannot remove a
 * logged workout, and a completed slot keeps the id of the session it points at so
 * the week view can open the real session.
 */
enum class ScheduleStatus { PLANNED, COMPLETED, SKIPPED }

/**
 * One training day on the calendar.
 *
 * [routineId] is optional because a routine can be deleted while its planned days
 * remain; the day keeps its name so the week view does not silently lose it.
 */
data class ScheduledWorkout(
    val id: String,
    val date: LocalDate,
    val routineId: String?,
    val routineName: String,
    val status: ScheduleStatus = ScheduleStatus.PLANNED,
    val sessionId: String? = null,
    val completedAtMs: Long? = null,
) {
    val isOpen: Boolean get() = status == ScheduleStatus.PLANNED

    val statusLabel: String
        get() = when (status) {
            ScheduleStatus.PLANNED -> "Planned"
            ScheduleStatus.COMPLETED -> "Done"
            ScheduleStatus.SKIPPED -> "Skipped"
        }
}

/** Planned/skipped/done counts for one week, used by the week header. */
data class WeekTally(
    val planned: Int,
    val completed: Int,
    val skipped: Int,
) {
    val total: Int get() = planned + completed + skipped

    val isEmpty: Boolean get() = total == 0
}
