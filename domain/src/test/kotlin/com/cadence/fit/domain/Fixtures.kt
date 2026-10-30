package com.cadence.fit.domain

import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.model.WorkoutSession
import java.time.ZoneId

object Fixtures {

    const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Fixed reference instant: 2023-11-14T22:13:20Z, a Tuesday. */
    const val NOW = 1_700_000_000_000L

    val UTC: ZoneId = ZoneId.of("UTC")

    fun set(
        exerciseId: String,
        weightKg: Double,
        reps: Int,
        atMs: Long = NOW,
        sessionId: String = "session-1",
        setIndex: Int = 1,
        isWarmup: Boolean = false,
        rpe: Double? = null,
        supersetGroup: Int? = null,
    ): LoggedSet = LoggedSet(
        id = "$sessionId-$exerciseId-$setIndex",
        sessionId = sessionId,
        exerciseId = exerciseId,
        setIndex = setIndex,
        weightKg = weightKg,
        reps = reps,
        rpe = rpe,
        isWarmup = isWarmup,
        completedAtMs = atMs,
        supersetGroup = supersetGroup,
    )

    fun sets(
        exerciseId: String,
        count: Int,
        weightKg: Double,
        reps: Int,
        atMs: Long = NOW,
        sessionId: String = "session-1",
        startIndex: Int = 1,
        isWarmup: Boolean = false,
    ): List<LoggedSet> = (0 until count).map { offset ->
        set(
            exerciseId = exerciseId,
            weightKg = weightKg,
            reps = reps,
            atMs = atMs,
            sessionId = sessionId,
            setIndex = startIndex + offset,
            isWarmup = isWarmup,
        )
    }

    fun session(
        id: String = "session-1",
        atMs: Long = NOW,
        sets: List<LoggedSet> = emptyList(),
        routineName: String = "Test routine",
        routineId: String? = "routine-1",
        ended: Boolean = true,
        location: TrainingLocation = TrainingLocation.GYM,
    ): WorkoutSession = WorkoutSession(
        id = id,
        routineId = routineId,
        routineName = routineName,
        startedAtMs = atMs - 45L * 60L * 1000L,
        endedAtMs = if (ended) atMs else null,
        sets = sets.map { it.copy(sessionId = id) },
        location = location,
    )

    fun routineExercise(
        exerciseId: String,
        targetSets: Int = 3,
        repRange: IntRange = 8..12,
        restSeconds: Int = 90,
        warmupSets: Int = 0,
        supersetGroup: Int? = null,
    ): RoutineExercise = RoutineExercise(
        id = "re-$exerciseId",
        exerciseId = exerciseId,
        targetSets = targetSets,
        repRange = repRange,
        restSeconds = restSeconds,
        warmupSets = warmupSets,
        supersetGroup = supersetGroup,
    )

    fun routine(
        name: String = "Test routine",
        goal: TrainingGoal = TrainingGoal.GENERAL_FITNESS,
        location: TrainingLocation = TrainingLocation.GYM,
        exercises: List<RoutineExercise>,
    ): Routine = Routine(
        id = "routine-$name",
        name = name,
        focus = "Test",
        goal = goal,
        location = location,
        exercises = exercises,
        createdAtMs = NOW,
    )

    fun profile(
        location: TrainingLocation = TrainingLocation.GYM,
        equipment: Set<Equipment> = ExerciseCatalog.defaultEquipment(location),
        experience: ExperienceLevel = ExperienceLevel.BEGINNER,
        goal: TrainingGoal = TrainingGoal.GENERAL_FITNESS,
        daysPerWeek: Int = 3,
        sessionMinutes: Int = 45,
        onboardingComplete: Boolean = true,
    ): TrainingProfile = TrainingProfile(
        onboardingComplete = onboardingComplete,
        location = location,
        equipment = equipment,
        experience = experience,
        goal = goal,
        daysPerWeek = daysPerWeek,
        sessionMinutes = sessionMinutes,
    )

    fun sleep(
        endMs: Long,
        minutes: Int,
        id: String = "sleep-$endMs",
        sourcePackage: String? = "com.example.sleep",
    ): SleepSummary = SleepSummary(
        id = id,
        startMs = endMs - minutes * 60_000L,
        endMs = endMs,
        durationMinutes = minutes,
        sourcePackage = sourcePackage,
        sourceLabel = sourcePackage,
    )
}
