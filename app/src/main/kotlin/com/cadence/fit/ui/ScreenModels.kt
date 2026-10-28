package com.cadence.fit.ui

import com.cadence.fit.data.CadenceState
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
import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.progression.PlateauInsight
import com.cadence.fit.domain.progression.ProgressionEngine
import com.cadence.fit.domain.progression.ProgressionRecommendation
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.domain.workout.PreviousPerformance
import com.cadence.fit.domain.workout.WorkoutLogger
import java.time.ZoneId

enum class NextActionType { RESUME_WORKOUT, START_WORKOUT, BUILD_PLAN }

/** The one clear thing the home screen asks the user to do. */
data class NextAction(
    val type: NextActionType,
    val title: String,
    val subtitle: String,
    val routineId: String?,
)

data class HomeModel(
    val nextAction: NextAction,
    val todayRoutine: Routine?,
    val summary: ProgressSummary,
    val lastCompleted: WorkoutSession?,
    val activeSession: WorkoutSession?,
    val muscleVolume: Map<MuscleGroup, Double>,
    val volumeTrend: List<Float>,
    val recovery: RecoveryContext?,
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
    val targetSessionsPerWeek: Int,
    val recentRecords: List<PersonalRecord>,
    val plateau: PlateauInsight?,
    val level: LevelAssessment?,
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
        val upcoming = nextRoutineOf(state.routines, lastCompleted)
        val summary = ChartData.summary(
            sessions = state.sessions,
            nowMs = nowMs,
            targetSessionsPerWeek = state.profile.daysPerWeek,
            zone = zone,
        )
        val window = muscleWindow(state, catalog, days = 14, nowMs = nowMs)

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

            else -> NextAction(
                type = NextActionType.START_WORKOUT,
                title = "Start ${upcoming?.name ?: "workout"}",
                subtitle = upcoming?.let { routine ->
                    "${routine.exercises.size} exercises · about ${routine.estimatedMinutes()} min"
                } ?: "Freestyle session",
                routineId = upcoming?.id,
            )
        }

        return HomeModel(
            nextAction = nextAction,
            todayRoutine = if (active != null) state.routines.firstOrNull { it.id == active.routineId } else upcoming,
            summary = summary,
            lastCompleted = lastCompleted,
            activeSession = active,
            muscleVolume = window.volumeByMuscle,
            volumeTrend = ChartData.volumeByWeek(state.sessions, com.cadence.fit.domain.insight.ChartWindow.TWELVE_WEEKS, nowMs, zone)
                .map { it.volumeKg.toFloat() },
            recovery = null,
        )
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
