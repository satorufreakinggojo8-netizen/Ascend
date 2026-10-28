package com.cadence.fit.domain.math

import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.model.WorkoutSession

enum class PersonalRecordType(val displayName: String) {
    HEAVIEST_WEIGHT("Heaviest weight"),
    BEST_ESTIMATED_1RM("Best estimated 1RM"),
    MOST_REPS("Most reps in a set"),
    MOST_VOLUME("Biggest session volume"),
}

data class PersonalRecord(
    val exerciseId: String,
    val type: PersonalRecordType,
    val achievedAtMs: Long,
    val sessionId: String,
    val weightKg: Double,
    val reps: Int,
    /** The recorded metric: kilograms, reps or kilograms of volume. */
    val value: Double,
)

/** Aggregated view of one exercise inside one session. */
data class ExerciseSessionEntry(
    val sessionId: String,
    val completedAtMs: Long,
    val sets: List<LoggedSet>,
    val topWeightKg: Double,
    val bestE1rmKg: Double,
    val totalVolumeKg: Double,
    val totalReps: Int,
)

/**
 * Pure calculations over logged data. Nothing here is a medical measure: volume,
 * estimated one-rep max and records are training bookkeeping only.
 */
object WorkoutMath {

    /** Secondary muscles are counted at half of the primary contribution. */
    const val SECONDARY_MUSCLE_WEIGHT = 0.5

    fun sessionVolumeKg(session: WorkoutSession, includeWarmups: Boolean = false): Double =
        if (includeWarmups) session.sets.sumOf { it.volumeKg } else session.totalVolumeKg

    fun totalVolumeKg(sessions: List<WorkoutSession>): Double =
        sessions.filter { it.isComplete }.sumOf { it.totalVolumeKg }

    /** Volume per muscle group over a set of sessions, blended by muscle role. */
    fun volumeByMuscle(
        sessions: List<WorkoutSession>,
        catalog: ExerciseCatalog,
        includeWarmups: Boolean = false,
    ): Map<MuscleGroup, Double> {
        val totals = mutableMapOf<MuscleGroup, Double>()
        sessions.filter { it.isComplete }.forEach { session ->
            val sets = if (includeWarmups) session.sets else session.workingSets
            sets.forEach { set ->
                val exercise = catalog.byId(set.exerciseId) ?: return@forEach
                val volume = set.volumeKg
                if (volume <= 0.0) return@forEach
                val primaries = exercise.primaryMuscles.ifEmpty { exercise.allMuscles }
                primaries.forEach { muscle ->
                    totals[muscle] = (totals[muscle] ?: 0.0) + volume / primaries.size
                }
                exercise.secondaryMuscles
                    .filterNot { it in primaries }
                    .forEach { muscle ->
                        totals[muscle] = (totals[muscle] ?: 0.0) +
                            volume * SECONDARY_MUSCLE_WEIGHT / exercise.secondaryMuscles.size
                    }
            }
        }
        return totals.toMap()
    }

    /** How many sessions trained each muscle group in the window. */
    fun sessionCountByMuscle(
        sessions: List<WorkoutSession>,
        catalog: ExerciseCatalog,
    ): Map<MuscleGroup, Int> {
        val counts = mutableMapOf<MuscleGroup, Int>()
        sessions.filter { it.isComplete }.forEach { session ->
            val muscles = session.workingSets
                .mapNotNull { catalog.byId(it.exerciseId) }
                .flatMap { it.allMuscles }
                .toSet()
            muscles.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.toMap()
    }

    fun setsFor(entry: List<LoggedSet>, exerciseId: String, includeWarmups: Boolean = false): List<LoggedSet> =
        entry.filter { it.exerciseId == exerciseId && (includeWarmups || !it.isWarmup) }

    fun topSet(session: WorkoutSession, exerciseId: String): LoggedSet? =
        session.workingSets.filter { it.exerciseId == exerciseId }
            .maxWithOrNull(compareBy({ it.weightKg }, { it.reps }))

    fun bestE1rmKg(session: WorkoutSession, exerciseId: String): Double =
        session.workingSets.filter { it.exerciseId == exerciseId }
            .maxOfOrNull { it.estimatedOneRepMaxKg } ?: 0.0

    /** Every completed session that logged the exercise, oldest first. */
    fun exerciseHistory(
        sessions: List<WorkoutSession>,
        exerciseId: String,
    ): List<ExerciseSessionEntry> =
        sessions.asSequence()
            .filter { it.isComplete }
            .mapNotNull { session ->
                val sets = session.workingSets.filter { it.exerciseId == exerciseId }
                if (sets.isEmpty()) return@mapNotNull null
                ExerciseSessionEntry(
                    sessionId = session.id,
                    completedAtMs = session.completedAtMs,
                    sets = sets.sortedBy { it.setIndex },
                    topWeightKg = sets.maxOf { it.weightKg },
                    bestE1rmKg = sets.maxOf { it.estimatedOneRepMaxKg },
                    totalVolumeKg = sets.sumOf { it.volumeKg },
                    totalReps = sets.sumOf { it.reps },
                )
            }
            .sortedBy { it.completedAtMs }
            .toList()

    /** Personal records for one exercise. At most one entry per record type. */
    fun personalRecords(sessions: List<WorkoutSession>, exerciseId: String): List<PersonalRecord> {
        val history = exerciseHistory(sessions, exerciseId)
        if (history.isEmpty()) return emptyList()
        val records = mutableListOf<PersonalRecord>()

        val heaviestSet = history.flatMap { entry -> entry.sets.map { entry to it } }
            .maxWithOrNull(compareBy({ it.second.weightKg }, { it.second.reps }))
        if (heaviestSet != null && heaviestSet.second.weightKg > 0) {
            records += PersonalRecord(
                exerciseId = exerciseId,
                type = PersonalRecordType.HEAVIEST_WEIGHT,
                achievedAtMs = heaviestSet.second.completedAtMs,
                sessionId = heaviestSet.first.sessionId,
                weightKg = heaviestSet.second.weightKg,
                reps = heaviestSet.second.reps,
                value = heaviestSet.second.weightKg,
            )
        }

        val bestE1rm = history.maxByOrNull { it.bestE1rmKg }
        val bestE1rmSet = bestE1rm?.sets?.maxByOrNull { it.estimatedOneRepMaxKg }
        if (bestE1rm != null && bestE1rmSet != null && bestE1rm.bestE1rmKg > 0) {
            records += PersonalRecord(
                exerciseId = exerciseId,
                type = PersonalRecordType.BEST_ESTIMATED_1RM,
                achievedAtMs = bestE1rmSet.completedAtMs,
                sessionId = bestE1rm.sessionId,
                weightKg = bestE1rmSet.weightKg,
                reps = bestE1rmSet.reps,
                value = bestE1rm.bestE1rmKg,
            )
        }

        val mostReps = history.flatMap { entry -> entry.sets.map { entry to it } }
            .maxWithOrNull(compareBy { it.second.reps })
        if (mostReps != null && mostReps.second.reps > 0) {
            records += PersonalRecord(
                exerciseId = exerciseId,
                type = PersonalRecordType.MOST_REPS,
                achievedAtMs = mostReps.second.completedAtMs,
                sessionId = mostReps.first.sessionId,
                weightKg = mostReps.second.weightKg,
                reps = mostReps.second.reps,
                value = mostReps.second.reps.toDouble(),
            )
        }

        val bestVolume = history.maxByOrNull { it.totalVolumeKg }
        if (bestVolume != null && bestVolume.totalVolumeKg > 0) {
            records += PersonalRecord(
                exerciseId = exerciseId,
                type = PersonalRecordType.MOST_VOLUME,
                achievedAtMs = bestVolume.completedAtMs,
                sessionId = bestVolume.sessionId,
                weightKg = bestVolume.topWeightKg,
                reps = bestVolume.totalReps,
                value = bestVolume.totalVolumeKg,
            )
        }

        return records
    }

    /**
     * Records set inside the window (default: the last 28 days), so the UI can
     * celebrate recent progress without digging through the whole history.
     */
    fun recentPersonalRecords(
        sessions: List<WorkoutSession>,
        exerciseIds: Collection<String>,
        nowMs: Long,
        windowDays: Int = 28,
    ): List<PersonalRecord> {
        val cutoff = nowMs - windowDays * 24L * 60L * 60L * 1000L
        return exerciseIds.distinct()
            .flatMap { personalRecords(sessions, it) }
            .filter { it.achievedAtMs >= cutoff }
            .sortedByDescending { it.achievedAtMs }
    }

    /** Exercises logged at least once, most recently trained first. */
    fun trainedExerciseIds(sessions: List<WorkoutSession>): List<String> =
        sessions.filter { it.isComplete }
            .sortedByDescending { it.completedAtMs }
            .flatMap { it.exerciseIds }
            .distinct()

    /** Higher volume on a heavier set is always "better" in this simplistic model. */
    fun isImprovement(previous: ExerciseSessionEntry?, current: ExerciseSessionEntry): Boolean {
        if (previous == null) return true
        return current.bestE1rmKg > previous.bestE1rmKg + 0.01
    }
}
