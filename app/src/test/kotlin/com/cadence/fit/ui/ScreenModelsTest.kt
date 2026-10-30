package com.cadence.fit.ui

import com.cadence.fit.data.CadenceState
import com.cadence.fit.domain.coach.CoachExpression
import com.cadence.fit.domain.library.ExerciseLibrary
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.domain.units.WeightUnit
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenModelsTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2023, 11, 14) // Tuesday
    private val now: Long = TimeRanges.startOfDayMs(today, zone) + 10 * 60 * 60 * 1000

    private fun routine(id: String, name: String): Routine = Routine(
        id = id,
        name = name,
        focus = "Full body",
        goal = com.cadence.fit.domain.model.TrainingGoal.GENERAL_FITNESS,
        location = com.cadence.fit.domain.model.TrainingLocation.GYM,
        exercises = listOf(
            RoutineExercise(
                id = "slot-$id",
                exerciseId = "goblet-squat",
                targetSets = 3,
                repRange = 8..12,
                restSeconds = 90,
            ),
        ),
        createdAtMs = now - 30 * 86_400_000L,
    )

    private fun completedSession(at: Long, routineId: String, name: String): WorkoutSession =
        WorkoutSession(
            id = "session-$name",
            routineId = routineId,
            routineName = name,
            startedAtMs = at - 45 * 60_000L,
            endedAtMs = at,
            sets = listOf(
                LoggedSet(
                    id = "set-$name",
                    sessionId = "session-$name",
                    exerciseId = "goblet-squat",
                    setIndex = 1,
                    weightKg = 24.0,
                    reps = 10,
                    completedAtMs = at,
                ),
            ),
        )

    private fun slot(
        id: String,
        date: LocalDate,
        routine: Routine,
        status: ScheduleStatus = ScheduleStatus.PLANNED,
        sessionId: String? = null,
    ) = ScheduledWorkout(
        id = id,
        date = date,
        routineId = routine.id,
        routineName = routine.name,
        status = status,
        sessionId = sessionId,
        completedAtMs = if (sessionId != null) now else null,
    )

    private fun state(
        routines: List<Routine>,
        sessions: List<WorkoutSession> = emptyList(),
        schedule: List<ScheduledWorkout> = emptyList(),
        activeSessionId: String? = null,
    ) = CadenceState(
        loaded = true,
        profile = TrainingProfile(onboardingComplete = true, daysPerWeek = 3),
        routines = routines,
        sessions = sessions,
        schedule = schedule,
        activeSessionId = activeSessionId,
    )

    @Test
    fun `today's planned workout is the one clear action`() {
        val dayA = routine("routine-a", "Full body A")
        val model = ScreenModels.home(
            state(listOf(dayA), schedule = listOf(slot("s1", today, dayA))),
            ExerciseLibrary,
            now,
            zone,
        )

        assertEquals(NextActionType.START_WORKOUT, model.nextAction.type)
        assertEquals("Start Full body A", model.nextAction.title)
        assertEquals(dayA.id, model.nextAction.routineId)
        assertTrue(model.nextAction.subtitle.contains("Scheduled today"))
    }

    @Test
    fun `a finished workout today opens the session instead of starting another`() {
        val dayA = routine("routine-a", "Full body A")
        val session = completedSession(now - 60 * 60_000L, dayA.id, dayA.name)
        val model = ScreenModels.home(
            state(
                listOf(dayA),
                sessions = listOf(session),
                schedule = listOf(
                    slot("s1", today, dayA, ScheduleStatus.COMPLETED, session.id),
                ),
            ),
            ExerciseLibrary,
            now,
            zone,
        )

        assertEquals(NextActionType.VIEW_SESSION, model.nextAction.type)
        assertEquals(session.id, model.nextAction.sessionId)
    }

    @Test
    fun `a skipped day becomes a rest day that points at the next planned one`() {
        val dayA = routine("routine-a", "Full body A")
        val dayB = routine("routine-b", "Full body B")
        val model = ScreenModels.home(
            state(
                listOf(dayA, dayB),
                schedule = listOf(
                    slot("s1", today, dayA, ScheduleStatus.SKIPPED),
                    slot("s2", today.plusDays(2), dayB),
                ),
            ),
            ExerciseLibrary,
            now,
            zone,
        )

        assertEquals(NextActionType.REST_DAY, model.nextAction.type)
        assertTrue(model.nextAction.subtitle.contains("Full body B"))
        assertNotNull(model.nextUpcoming)
    }

    @Test
    fun `an in-progress workout always wins`() {
        val dayA = routine("routine-a", "Full body A")
        val open = WorkoutSession(
            id = "open",
            routineId = dayA.id,
            routineName = dayA.name,
            startedAtMs = now - 10 * 60_000L,
            sets = emptyList(),
        )
        val model = ScreenModels.home(
            state(
                listOf(dayA),
                sessions = listOf(open),
                schedule = listOf(slot("s1", today, dayA)),
                activeSessionId = open.id,
            ),
            ExerciseLibrary,
            now,
            zone,
        )

        assertEquals(NextActionType.RESUME_WORKOUT, model.nextAction.type)
        assertEquals(open.id, model.activeSession?.id)
    }

    @Test
    fun `a profile without routines is asked to build a plan first`() {
        val model = ScreenModels.home(state(emptyList()), ExerciseLibrary, now, zone)

        assertEquals(NextActionType.BUILD_PLAN, model.nextAction.type)
        assertEquals(0, model.weekTally.total)
    }

    @Test
    fun `the week strip holds seven days starting on Monday`() {
        val dayA = routine("routine-a", "Full body A")
        val model = ScreenModels.home(
            state(
                listOf(dayA),
                schedule = listOf(
                    slot("s1", today.minusDays(1), dayA, ScheduleStatus.COMPLETED, "session-x"),
                    slot("s2", today, dayA),
                    slot("s3", today.plusDays(2), dayA, ScheduleStatus.SKIPPED),
                ),
            ),
            ExerciseLibrary,
            now,
            zone,
        )

        assertEquals(7, model.weekDays.size)
        assertEquals(LocalDate.of(2023, 11, 13), model.weekDays.first().date)
        assertEquals(LocalDate.of(2023, 11, 19), model.weekDays.last().date)
        assertTrue(model.weekDays.first { it.isToday }.isToday)
        assertEquals(ScheduleStatus.PLANNED, model.weekDays.first { it.date == today }.status)
        assertEquals(
            ScheduleStatus.COMPLETED,
            model.weekDays.first { it.date == today.minusDays(1) }.status,
        )
        assertEquals(1, model.weekTally.completed)
        assertEquals(1, model.weekTally.planned)
        assertEquals(1, model.weekTally.skipped)
    }

    @Test
    fun `a workout scheduled for tomorrow is named in the subtitle`() {
        val dayA = routine("routine-a", "Upper A")
        val model = ScreenModels.home(
            state(listOf(dayA), schedule = listOf(slot("s1", today.plusDays(1), dayA))),
            ExerciseLibrary,
            now,
            zone,
        )

        assertEquals(NextActionType.START_WORKOUT, model.nextAction.type)
        assertTrue(model.nextAction.subtitle.contains("tomorrow"))
        assertTrue(model.nextAction.subtitle.contains("Upper A"))
    }

    @Test
    fun `day labels stay short and relative`() {
        assertEquals("today", ScreenModels.dayLabel(today, today))
        assertEquals("tomorrow", ScreenModels.dayLabel(today.plusDays(1), today))
        assertEquals("Friday", ScreenModels.dayLabel(today.plusDays(3), today))
    }

    @Test
    fun `muscle ranking ignores groups that are not on the body map`() {
        val dayA = routine("routine-a", "Full body A")
        val session = completedSession(now, dayA.id, dayA.name)
        val window = ScreenModels.muscleWindow(
            state(listOf(dayA), sessions = listOf(session)),
            ExerciseLibrary,
            days = 14,
            nowMs = now,
            zone = zone,
        )

        val ranking = ScreenModels.muscleRanking(window)
        assertTrue(ranking.isNotEmpty())
        assertTrue(ranking.all { it.first.appearsOnBodyMap })
        assertEquals(ranking.sortedByDescending { it.second }, ranking)
        assertNull(ranking.firstOrNull { it.first.displayName == "Conditioning" })
    }

    // --- Coaches --------------------------------------------------------------

    private fun sleepNight(id: String, minutes: Int) = SleepSummary(
        id = id,
        startMs = now - 9 * 60 * 60 * 1000L,
        endMs = now - 9 * 60 * 60 * 1000L + minutes * 60_000L,
        durationMinutes = minutes,
    )

    private fun openSession(routine: Routine, at: Long) = WorkoutSession(
        id = "open",
        routineId = routine.id,
        routineName = routine.name,
        startedAtMs = at - 30 * 60_000L,
        sets = listOf(
            LoggedSet(
                id = "open-set-1",
                sessionId = "open",
                exerciseId = "goblet-squat",
                setIndex = 1,
                weightKg = 24.0,
                reps = 10,
                completedAtMs = at - 10 * 60_000L,
            ),
        ),
    )

    @Test
    fun `an in-progress session makes the coach encouraging and lists the plan as programs`() {
        val dayA = routine("routine-a", "Full body A")
        val open = openSession(dayA, now)
        val model = ScreenModels.coach(
            state(listOf(dayA), sessions = listOf(open), activeSessionId = open.id),
            ExerciseLibrary,
            emptyList(),
            now,
            WeightUnit.KG,
            topicOpen = false,
            zone = zone,
        )

        assertEquals(CoachExpression.ENCOURAGING, model.presence.expression)
        assertEquals(1, model.context.workingSetsThisSession)
        assertEquals(listOf(dayA.id), model.programs.map { it.id })
        assertTrue(model.topics.any { it.id == "consistency" })
    }

    @Test
    fun `a session finished today counts as work logged`() {
        val dayA = routine("routine-a", "Full body A")
        val model = ScreenModels.coach(
            state(
                listOf(dayA),
                sessions = listOf(completedSession(now - 60 * 60_000L, dayA.id, dayA.name)),
            ),
            ExerciseLibrary,
            emptyList(),
            now,
            WeightUnit.KG,
            topicOpen = false,
            zone = zone,
        )

        assertEquals(CoachExpression.ENCOURAGING, model.presence.expression)
        assertEquals(1, model.context.sessionsCompletedToday)
        assertEquals("Work logged", model.presence.headline)
    }

    @Test
    fun `reading a topic switches the portrait to explaining`() {
        val model = ScreenModels.coach(
            state(emptyList()),
            ExerciseLibrary,
            emptyList(),
            now,
            WeightUnit.KG,
            topicOpen = true,
            zone = zone,
        )

        assertEquals(CoachExpression.EXPLAINING, model.presence.expression)
        assertEquals("David Laid, explaining", model.presence.photoDescription)
    }

    @Test
    fun `sleep context is only claimed when the caller actually passes nights`() {
        val withNights = ScreenModels.coach(
            state(emptyList()),
            ExerciseLibrary,
            listOf(sleepNight("n1", 420), sleepNight("n2", 390)),
            now,
            WeightUnit.KG,
            topicOpen = false,
            zone = zone,
        )
        assertEquals(2, withNights.context.sleepNightsAvailable)
        assertTrue(withNights.topics.any { it.id == "recovery" })

        val optedOut = ScreenModels.coach(
            state(emptyList()),
            ExerciseLibrary,
            emptyList(),
            now,
            WeightUnit.KG,
            topicOpen = false,
            zone = zone,
        )
        assertEquals(0, optedOut.context.sleepNightsAvailable)
        assertTrue(optedOut.topics.none { it.id == "recovery" })
    }

    @Test
    fun `an unknown coach id falls back to the ambassador rather than a blank page`() {
        val model = ScreenModels.coach(
            state(emptyList()),
            ExerciseLibrary,
            emptyList(),
            now,
            WeightUnit.KG,
            topicOpen = false,
            coachId = "nobody",
            zone = zone,
        )

        assertEquals("david-laid", model.coach.id)
        assertTrue(model.coach.pendingFacts.isNotEmpty())
    }
}
