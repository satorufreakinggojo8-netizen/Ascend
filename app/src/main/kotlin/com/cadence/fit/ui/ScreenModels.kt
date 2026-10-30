package com.cadence.fit.ui

import com.cadence.fit.data.CadenceState
import com.cadence.fit.domain.coach.CoachContext
import com.cadence.fit.domain.coach.CoachDirector
import com.cadence.fit.domain.coach.CoachPresence
import com.cadence.fit.domain.coach.CoachProfile
import com.cadence.fit.domain.coach.CoachRoster
import com.cadence.fit.domain.coach.CoachTopic
import com.cadence.fit.domain.insight.ChartData
import com.cadence.fit.domain.insight.LevelAssessment
import com.cadence.fit.domain.insight.LevelAssessor
import com.cadence.fit.domain.insight.ProgressSummary
import com.cadence.fit.domain.insight.RecoveryAdvisor
import com.cadence.fit.domain.insight.RecoveryContext
import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.math.PersonalRecord
import com.cadence.fit.domain.math.WorkoutMath
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.model.WeekTally
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.schedule.SchedulePlanner
import com.cadence.fit.domain.progression.PlateauInsight
import com.cadence.fit.domain.progression.ProgressionEngine
import com.cadence.fit.domain.progression.ProgressionRecommendation
import com.cadence.fit.domain.progression.SuggestionKind
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.domain.workout.PreviousPerformance
import com.cadence.fit.domain.workout.WorkoutLogger
import java.time.LocalDate
import java.time.ZoneId

enum class NextActionType { RESUME_WORKOUT, START_WORKOUT, BUILD_PLAN, REST_DAY, VIEW_SESSION }

/** The one clear thing the home screen asks the user to do. */
data class NextAction(
    val type: NextActionType,
    val title: String,
    val subtitle: String,
    val routineId: String?,
    val sessionId: String? = null,
)

/** One square in the home screen's week strip. */
data class HomeDay(
    val date: LocalDate,
    val letter: String,
    val dayOfMonth: String,
    val isToday: Boolean,
    val status: ScheduleStatus?,
    val routineName: String?,
)

data class HomeModel(
    val nextAction: NextAction,
    val summary: ProgressSummary,
    val lastCompleted: WorkoutSession?,
    val activeSession: WorkoutSession?,
    val muscleVolume: Map<MuscleGroup, Double>,
    val volumeTrend: List<Float>,
    val todaySlot: ScheduledWorkout?,
    val weekDays: List<HomeDay>,
    val weekTally: WeekTally,
    val nextUpcoming: ScheduledWorkout?,
)

data class MuscleWindow(
    val days: Int,
    val volumeByMuscle: Map<MuscleGroup, Double>,
    val sessionsByMuscle: Map<MuscleGroup, Int>,
    val maxVolume: Double,
    val sessionsCounted: Int,
) {
    val untrainedGroups: List<MuscleGroup>
        get() = MuscleGroup.bodyMapGroups.filter { (volumeByMuscle[it] ?: 0.0) <= 0.0 }
}

data class ExerciseDetailModel(
    val exercise: Exercise,
    val previous: PreviousPerformance?,
    val records: List<PersonalRecord>,
    val history: List<WorkoutSession>,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val recommendation: ProgressionRecommendation?,
)

data class ProgressModel(
    val summary: ProgressSummary,
    val volumeByWeek: List<Float>,
    val consistencyByWeek: List<Float>,
    val weekLabels: List<String>,
    /** Epoch day of each week's Monday, so a selected bar can open its history. */
    val weekStartEpochDays: List<Long>,
    val targetSessionsPerWeek: Int,
    val recentRecords: List<PersonalRecord>,
    val plateau: PlateauInsight?,
    val level: LevelAssessment?,
    val muscleWindow: MuscleWindow,
)

/**
 * Everything a coach page shows, derived from stored state. [programs] are the
 * user's own routines: Cadence does not publish coach-authored programmes until
 * approved content exists.
 */
data class CoachModel(
    val coach: CoachProfile,
    val context: CoachContext,
    val presence: CoachPresence,
    val topics: List<CoachTopic>,
    val programs: List<Routine>,
)

/**
 * Screen-shaped data, derived purely from state. Keeping this out of the
 * composables means the numbers shown on each screen can be unit tested.
 */
object ScreenModels {

    fun nextRoutineOf(routines: List<Routine>, lastCompleted: WorkoutSession?): Routine? {
        if (routines.isEmpty()) return null
        val lastRoutineId = lastCompleted?.routineId ?: return routines.first()
        val index = routines.indexOfFirst { it.id == lastRoutineId }
        if (index < 0) return routines.first()
        return routines[(index + 1) % routines.size]
    }

    fun home(
        state: CadenceState,
        catalog: ExerciseCatalog,
        nowMs: Long,
        zone: ZoneId = TimeRanges.zone(),
    ): HomeModel {
        val active = state.activeSession
        val completed = state.completedSessions.sortedByDescending { it.completedAtMs }
        val lastCompleted = completed.firstOrNull()
        val summary = ChartData.summary(
            sessions = state.sessions,
            nowMs = nowMs,
            targetSessionsPerWeek = state.profile.daysPerWeek,
            zone = zone,
        )
        val window = muscleWindow(state, catalog, days = 14, nowMs = nowMs)
        val today = com.cadence.fit.domain.time.TimeRanges.toLocalDate(nowMs, zone)
        val weekStart = SchedulePlanner.weekStart(today)
        val weekSlots = state.schedule.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekStart.plusDays(6)) }
        val todaySlot = state.schedule
            .filter { it.date == today }
            .minByOrNull { if (it.status == ScheduleStatus.COMPLETED) 1 else 0 }
        val nextUpcoming = state.schedule
            .filter { it.isOpen && it.date.isAfter(today) }
            .minWithOrNull(compareBy({ it.date }, { it.id }))
        val upcomingRoutine = todaySlot?.let { slot -> state.routines.firstOrNull { it.id == slot.routineId } }
            ?: nextRoutineOf(state.routines, lastCompleted)

        val nextAction = when {
            active != null -> NextAction(
                type = NextActionType.RESUME_WORKOUT,
                title = "Resume ${active.routineName}",
                subtitle = "${active.workingSets.size} sets logged so far",
                routineId = active.routineId,
            )

            state.routines.isEmpty() -> NextAction(
                type = NextActionType.BUILD_PLAN,
                title = "Build your plan",
                subtitle = "A few questions to get started",
                routineId = null,
            )

            todaySlot?.status == ScheduleStatus.COMPLETED && todaySlot.sessionId != null -> NextAction(
                type = NextActionType.VIEW_SESSION,
                title = "Today is done",
                subtitle = "${todaySlot.routineName} · open the session you logged",
                routineId = todaySlot.routineId,
                sessionId = todaySlot.sessionId,
            )

            todaySlot?.status == ScheduleStatus.SKIPPED -> NextAction(
                type = NextActionType.REST_DAY,
                title = "Rest day",
                subtitle = nextUpcoming?.let { "Next: ${dayLabel(it.date, today)} · ${it.routineName}" }
                    ?: "Nothing else planned this week",
                routineId = null,
            )

            todaySlot != null -> NextAction(
                type = NextActionType.START_WORKOUT,
                title = "Start ${todaySlot.routineName}",
                subtitle = upcomingRoutine?.let { routine ->
                    "Scheduled today · ${routine.exercises.size} exercises · about " +
                        "${routine.estimatedMinutes()} min"
                } ?: "Scheduled today",
                routineId = todaySlot.routineId,
            )

            else -> NextAction(
                type = NextActionType.START_WORKOUT,
                title = "Start ${upcomingRoutine?.name ?: "workout"}",
                subtitle = nextUpcoming?.let { slot ->
                    "Next planned: ${dayLabel(slot.date, today)} · ${slot.routineName}"
                } ?: upcomingRoutine?.let { routine ->
                    "${routine.exercises.size} exercises · about ${routine.estimatedMinutes()} min"
                } ?: "Freestyle session",
                routineId = upcomingRoutine?.id,
            )
        }

        val weekDays = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val slot = weekSlots.filter { it.date == date }
                .minByOrNull { if (it.status == ScheduleStatus.COMPLETED) 1 else 0 }
            HomeDay(
                date = date,
                letter = date.dayOfWeek.name.take(1),
                dayOfMonth = date.dayOfMonth.toString(),
                isToday = date == today,
                status = slot?.status,
                routineName = slot?.routineName,
            )
        }

        return HomeModel(
            nextAction = nextAction,
            summary = summary,
            lastCompleted = lastCompleted,
            activeSession = active,
            muscleVolume = window.volumeByMuscle,
            volumeTrend = ChartData.volumeByWeek(state.sessions, com.cadence.fit.domain.insight.ChartWindow.TWELVE_WEEKS, nowMs, zone)
                .map { it.volumeKg.toFloat() },
            todaySlot = todaySlot,
            weekDays = weekDays,
            weekTally = WeekTally(
                planned = weekSlots.count { it.status == ScheduleStatus.PLANNED },
                completed = weekSlots.count { it.status == ScheduleStatus.COMPLETED },
                skipped = weekSlots.count { it.status == ScheduleStatus.SKIPPED },
            ),
            nextUpcoming = nextUpcoming,
        )
    }

    /** "today", "tomorrow" or a weekday, relative to [today]. */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun muscleWindow(
        state: CadenceState,
        catalog: ExerciseCatalog,
        days: Int,
        nowMs: Long,
        zone: ZoneId = TimeRanges.zone(),
    ): MuscleWindow {
        val sessions = TimeRanges.sessionsWithin(state.sessions, days, nowMs)
        val volume = WorkoutMath.volumeByMuscle(sessions, catalog)
        return MuscleWindow(
            days = days,
            volumeByMuscle = volume,
            sessionsByMuscle = WorkoutMath.sessionCountByMuscle(sessions, catalog),
            maxVolume = volume.values.maxOrNull() ?: 0.0,
            sessionsCounted = sessions.size,
        )
    }

    fun recovery(
        sleepSessions: List<SleepSummary>,
        enabled: Boolean,
        nowMs: Long,
        zone: ZoneId = TimeRanges.zone(),
    ): RecoveryContext? = RecoveryAdvisor.assess(sleepSessions, nowMs, enabled, zone)

    fun coach(
        state: CadenceState,
        catalog: ExerciseCatalog,
        sleepSessions: List<SleepSummary>,
        nowMs: Long,
        unit: WeightUnit,
        topicOpen: Boolean,
        coachId: String = CoachRoster.davidLaid.id,
        zone: ZoneId = TimeRanges.zone(),
    ): CoachModel {
        val coach = CoachRoster.byId(coachId) ?: CoachRoster.davidLaid
        val context = coachContext(
            state = state,
            catalog = catalog,
            sleepSessions = sleepSessions,
            nowMs = nowMs,
            unit = unit,
            topicOpen = topicOpen,
            zone = zone,
        )
        return CoachModel(
            coach = coach,
            context = context,
            presence = CoachDirector.presence(context, coach),
            topics = CoachDirector.topics(context),
            programs = state.routines,
        )
    }

    /**
     * Reads the coach's cues from real state: the session in progress, completed
     * sessions today, best sets from the last seven days, the first actionable
     * progression suggestion, and how many sleep nights are actually available.
     */
    fun coachContext(
        state: CadenceState,
        catalog: ExerciseCatalog,
        sleepSessions: List<SleepSummary>,
        nowMs: Long,
        unit: WeightUnit,
        topicOpen: Boolean,
        zone: ZoneId = TimeRanges.zone(),
    ): CoachContext {
        val completed = state.completedSessions
        val today = TimeRanges.toLocalDate(nowMs, zone)
        val summary = ChartData.summary(state.sessions, nowMs, state.profile.daysPerWeek, zone)
        val records = WorkoutMath.recentPersonalRecords(
            sessions = completed,
            exerciseIds = WorkoutMath.trainedExerciseIds(completed),
            nowMs = nowMs,
            windowDays = 7,
        )
        val engine = ProgressionEngine(catalog)
        val suggestion = state.routines.firstNotNullOfOrNull { routine ->
            routine.exercises.firstNotNullOfOrNull { slot ->
                val recommendation = engine.recommend(slot, state.sessions, routine.goal, unit)
                if (recommendation.kind == SuggestionKind.HOLD) null else recommendation to slot.exerciseId
            }
        }
        val plateau = engine.deloadInsight(
            state.sessions,
            WorkoutMath.trainedExerciseIds(completed),
            nowMs,
        )

        return CoachContext(
            hasActiveSession = state.activeSession != null,
            workingSetsThisSession = state.activeSession?.workingSets?.size ?: 0,
            sessionsCompletedToday = completed.count {
                TimeRanges.toLocalDate(it.completedAtMs, zone) == today
            },
            recordsThisWeek = records.size,
            suggestionHeadline = suggestion?.first?.headline,
            suggestionExerciseId = suggestion?.second,
            suggestionExerciseName = suggestion?.second?.let { exerciseId -> catalog.byId(exerciseId)?.name },
            plateauHeadline = plateau?.headline,
            sessionsThisWeek = summary.sessionsThisWeek,
            targetSessionsPerWeek = state.profile.daysPerWeek,
            sleepNightsAvailable = sleepSessions.count { it.startMs >= nowMs - SEVEN_DAYS_MS },
            topicOpen = topicOpen,
        )
    }

    private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L

    fun exerciseDetail(
        state: CadenceState,
        catalog: ExerciseCatalog,
        exerciseId: String,
        unit: WeightUnit,
        nowMs: Long,
        routineExerciseId: String? = null,
    ): ExerciseDetailModel? {
        val exercise = catalog.byId(exerciseId) ?: return null
        val history = state.completedSessions
            .filter { session -> session.workingSets.any { it.exerciseId == exerciseId } }
            .sortedByDescending { it.completedAtMs }
        val previous = WorkoutLogger.previousPerformance(state.sessions, exerciseId, nowMs)
        val routineExercise = state.routines
            .flatMap { it.exercises }
            .firstOrNull { it.exerciseId == exerciseId && (routineExerciseId == null || it.id == routineExerciseId) }

        val recommendation = routineExercise?.let { slot ->
            ProgressionEngine(catalog).recommend(slot, state.sessions, state.goalFor(slot), unit)
        }

        return ExerciseDetailModel(
            exercise = exercise,
            previous = previous,
            records = WorkoutMath.personalRecords(state.completedSessions, exerciseId),
            history = history,
            totalSets = history.sumOf { session -> session.workingSets.count { it.exerciseId == exerciseId } },
            totalVolumeKg = history.sumOf { session ->
                session.workingSets.filter { it.exerciseId == exerciseId }.sumOf { set -> set.volumeKg }
            },
            recommendation = recommendation,
        )
    }

    fun progress(
        state: CadenceState,
        catalog: ExerciseCatalog,
        window: com.cadence.fit.domain.insight.ChartWindow,
        unit: WeightUnit,
        nowMs: Long,
        zone: ZoneId = TimeRanges.zone(),
    ): ProgressModel {
        val summary = ChartData.summary(state.sessions, nowMs, state.profile.daysPerWeek, zone)
        val volumeWeeks = ChartData.volumeByWeek(state.sessions, window, nowMs, zone)
        val consistency = ChartData.consistencyByWeek(
            state.sessions,
            window,
            nowMs,
            state.profile.daysPerWeek,
            zone,
        )
        val trained = WorkoutMath.trainedExerciseIds(state.completedSessions)
        val engine = ProgressionEngine(catalog)
        val level = LevelAssessor.assess(state.sessions, nowMs, zone, state.settings.showProgressLevel)

        return ProgressModel(
            summary = summary,
            volumeByWeek = volumeWeeks.map { it.volumeKg.toFloat() },
            consistencyByWeek = consistency.map { it.sessions.toFloat() },
            weekLabels = volumeWeeks.map { it.label },
            weekStartEpochDays = volumeWeeks.map {
                com.cadence.fit.domain.time.TimeRanges.toLocalDate(it.epochMs, zone).toEpochDay()
            },
            muscleWindow = muscleWindow(state, catalog, days = 28, nowMs = nowMs, zone = zone),
            targetSessionsPerWeek = state.profile.daysPerWeek,
            recentRecords = WorkoutMath.recentPersonalRecords(
                state.completedSessions,
                trained,
                nowMs,
                windowDays = 28,
            ),
            plateau = engine.deloadInsight(state.sessions, trained, nowMs),
            level = level,
        )
    }

    /** Every muscle group with its share of the window's volume, highest first. */
    fun muscleRanking(window: MuscleWindow): List<Pair<MuscleGroup, Double>> =
        window.volumeByMuscle.entries
            .filter { (muscle, _) -> muscle.appearsOnBodyMap }
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    private fun CadenceState.goalFor(slot: com.cadence.fit.domain.model.RoutineExercise) =
        routines.firstOrNull { routine -> routine.exercises.any { it.id == slot.id } }?.goal
            ?: profile.goal
}
