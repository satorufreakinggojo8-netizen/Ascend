package com.cadence.fit.domain.workout

import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.units.WeightUnit

/** What the same exercise looked like in the previous session. */
data class PreviousPerformance(
    val sessionId: String,
    val completedAtMs: Long,
    val sets: List<LoggedSet>,
    val topWeightKg: Double,
    val totalVolumeKg: Double,
) {
    val setCount: Int get() = sets.size

    val reps: List<Int> get() = sets.map { it.reps }

    val bestSet: LoggedSet?
        get() = sets.maxWithOrNull(compareBy({ it.estimatedOneRepMaxKg }, { it.weightKg }))
}

/** Values pre-filled into the weight and rep controls for the next set. */
data class EntryDefaults(
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean,
    /** True when the values come from the set just logged in this session. */
    val repeatedFromCurrentSession: Boolean,
)

data class WarmupStep(val weightKg: Double, val reps: Int, val label: String)

/** How far through the routine the session is. */
data class SessionProgress(
    val exerciseIndex: Int,
    val exerciseCount: Int,
    val completedSets: Int,
    val plannedSets: Int,
) {
    val fraction: Float
        get() = if (plannedSets <= 0) 0f else (completedSets.toFloat() / plannedSets).coerceIn(0f, 1f)
}

/**
 * Set-by-set logging rules, kept out of the UI so that "what happens when I tap
 * the big + button" is a tested, pure function.
 */
object WorkoutLogger {

    /** Warm-up ladder as a fraction of the working weight, with target reps. */
    private val WARMUP_LADDER: List<Pair<Double, Int>> = listOf(
        0.50 to 8,
        0.70 to 5,
        0.85 to 3,
    )

    const val MAX_WARMUP_STEPS = 3

    fun previousPerformance(
        sessions: List<WorkoutSession>,
        exerciseId: String,
        beforeMs: Long,
    ): PreviousPerformance? {
        val session = sessions.asSequence()
            .filter { it.isComplete }
            .filter { it.completedAtMs < beforeMs }
            .filter { candidate -> candidate.workingSets.any { it.exerciseId == exerciseId } }
            .maxByOrNull { it.completedAtMs } ?: return null

        val sets = session.workingSets
            .filter { it.exerciseId == exerciseId }
            .sortedBy { it.setIndex }
        if (sets.isEmpty()) return null

        return PreviousPerformance(
            sessionId = session.id,
            completedAtMs = session.completedAtMs,
            sets = sets,
            topWeightKg = sets.maxOf { it.weightKg },
            totalVolumeKg = sets.sumOf { it.volumeKg },
        )
    }

    fun nextSetIndex(session: WorkoutSession, exerciseId: String): Int =
        (session.sets.filter { it.exerciseId == exerciseId }.maxOfOrNull { it.setIndex } ?: 0) + 1

    /**
     * Pre-fill values for the next set. Repeats the set just logged in this
     * session, otherwise falls back to the previous session's top weight and the
     * top of the routine's rep range.
     */
    fun entryDefaults(
        session: WorkoutSession,
        exerciseId: String,
        previous: PreviousPerformance?,
        routineExercise: RoutineExercise?,
        isWarmup: Boolean = false,
        unit: WeightUnit = WeightUnit.KG,
    ): EntryDefaults {
        val logged = session.sets.filter { it.exerciseId == exerciseId }
        val lastOfSameKind = logged.filter { it.isWarmup == isWarmup }.maxByOrNull { it.setIndex }
        if (lastOfSameKind != null) {
            return EntryDefaults(
                weightKg = lastOfSameKind.weightKg,
                reps = lastOfSameKind.reps,
                isWarmup = isWarmup,
                repeatedFromCurrentSession = true,
            )
        }

        val workingWeight = logged.filterNot { it.isWarmup }.maxOfOrNull { it.weightKg }
            ?: previous?.topWeightKg
            ?: 0.0

        // Adding a warm-up set after working sets: start at the first rung of the
        // ladder rather than reusing the working weight.
        if (isWarmup && workingWeight > 0.0) {
            val firstRung = warmupPlan(workingWeight, warmupSets = 1, unit = unit).firstOrNull()
            if (firstRung != null) {
                return EntryDefaults(
                    weightKg = firstRung.weightKg,
                    reps = firstRung.reps,
                    isWarmup = true,
                    repeatedFromCurrentSession = false,
                )
            }
        }

        val targetReps = routineExercise?.repRange?.last ?: previous?.bestSet?.reps ?: 8
        return EntryDefaults(
            weightKg = workingWeight,
            reps = targetReps,
            isWarmup = isWarmup,
            repeatedFromCurrentSession = false,
        )
    }

    /** A small percentage ladder so warm-up sets are not guesswork. */
    fun warmupPlan(workingWeightKg: Double, warmupSets: Int, unit: WeightUnit): List<WarmupStep> {
        if (workingWeightKg <= 0.0 || warmupSets <= 0) return emptyList()
        return WARMUP_LADDER
            .take(warmupSets.coerceIn(0, MAX_WARMUP_STEPS))
            .map { (fraction, reps) ->
                val snap = unit.snap(unit.fromKilograms(workingWeightKg * fraction))
                WarmupStep(
                    weightKg = unit.toKilograms(snap),
                    reps = reps,
                    label = "${(fraction * 100).toInt()}% of working weight",
                )
            }
    }

    fun restSecondsFor(routineExercise: RoutineExercise?, fallbackSeconds: Int = 90): Int =
        routineExercise?.restSeconds?.coerceAtLeast(0) ?: fallbackSeconds

    /**
     * Rest recommendation for the set that was just finished. Heavier, lower-rep
     * work gets longer; high-rep work gets shorter.
     */
    fun recommendedRestSeconds(
        set: LoggedSet,
        routineExercise: RoutineExercise?,
        fallbackSeconds: Int = 90,
    ): Int {
        val base = restSecondsFor(routineExercise, fallbackSeconds)
        return when {
            set.reps <= 5 -> base + 30
            set.reps >= 15 -> (base - 15).coerceAtLeast(30)
            else -> base
        }
    }

    fun addSet(session: WorkoutSession, set: LoggedSet): WorkoutSession =
        session.copy(sets = session.sets + set)

    fun updateSet(
        session: WorkoutSession,
        setId: String,
        transform: (LoggedSet) -> LoggedSet,
    ): WorkoutSession = session.copy(
        sets = session.sets.map { if (it.id == setId) transform(it) else it },
    )

    fun removeSet(session: WorkoutSession, setId: String): WorkoutSession =
        session.copy(sets = session.sets.filterNot { it.id == setId })

    /** Undo the most recently logged set, optionally for one exercise only. */
    fun undoLast(session: WorkoutSession, exerciseId: String? = null): WorkoutSession {
        val candidate = session.sets
            .filter { exerciseId == null || it.exerciseId == exerciseId }
            .maxByOrNull { it.completedAtMs } ?: return session
        return removeSet(session, candidate.id)
    }

    fun completedSetsFor(session: WorkoutSession, exerciseId: String): Int =
        session.sets.count { it.exerciseId == exerciseId && !it.isWarmup }

    fun isFinished(session: WorkoutSession, routineExercise: RoutineExercise): Boolean =
        completedSetsFor(session, routineExercise.exerciseId) >= routineExercise.targetSets

    fun progress(session: WorkoutSession, routine: Routine): SessionProgress {
        val index = routine.exercises.indexOfFirst { routineExercise ->
            !isFinished(session, routineExercise)
        }
        return SessionProgress(
            exerciseIndex = if (index < 0) routine.exercises.size else index,
            exerciseCount = routine.exercises.size,
            completedSets = session.workingSets.size,
            plannedSets = routine.workingSets,
        )
    }

    /** True once at least one set has been logged, so "finish" can be offered. */
    fun hasLoggedAnything(session: WorkoutSession): Boolean = session.sets.isNotEmpty()
}
