package com.cadence.fit.domain.model

/**
 * A logged set. [weightKg] is always kilograms; the active display unit is applied
 * when the value is shown or edited.
 */
data class LoggedSet(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    /** 1-based position of this set inside the session, in logging order. */
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val completedAtMs: Long,
    val supersetGroup: Int? = null,
    val note: String = "",
) {
    val volumeKg: Double get() = weightKg * reps.coerceAtLeast(0)

    /** Epley estimate. Only meaningful for reps <= 10, and always an estimate. */
    val estimatedOneRepMaxKg: Double get() = estimateOneRepMax(this)

    companion object {
        const val MAX_RPE = 10.0
        const val MIN_RPE = 5.0

        fun estimateOneRepMax(set: LoggedSet): Double {
            val reps = set.reps
            if (reps <= 0 || set.weightKg <= 0) return 0.0
            if (reps == 1) return set.weightKg
            if (reps > 12) return set.weightKg * (1 + reps / 30.0)
            return set.weightKg * (1 + reps / 30.0)
        }
    }
}

data class WorkoutSession(
    val id: String,
    val routineId: String?,
    val routineName: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val sets: List<LoggedSet> = emptyList(),
    val note: String = "",
    val location: TrainingLocation = TrainingLocation.GYM,
) {
    val isComplete: Boolean get() = endedAtMs != null

    /** The moment this session should be bucketed by on charts and history. */
    val completedAtMs: Long
        get() = endedAtMs ?: sets.maxOfOrNull { it.completedAtMs } ?: startedAtMs

    val durationMinutes: Int?
        get() = endedAtMs?.let { ((it - startedAtMs) / 60_000L).toInt().coerceAtLeast(0) }

    val workingSets: List<LoggedSet> get() = sets.filterNot { it.isWarmup }

    val warmupSets: List<LoggedSet> get() = sets.filter { it.isWarmup }

    val totalVolumeKg: Double get() = workingSets.sumOf { it.volumeKg }

    val totalReps: Int get() = workingSets.sumOf { it.reps }

    /** Distinct exercise ids in the order they were first logged. */
    val exerciseIds: List<String>
        get() = sets.sortedBy { it.completedAtMs }.map { it.exerciseId }.distinct()

    fun setsFor(exerciseId: String): List<LoggedSet> = sets.filter { it.exerciseId == exerciseId }

    fun bestSetFor(exerciseId: String): LoggedSet? =
        workingSets.filter { it.exerciseId == exerciseId }
            .maxWithOrNull(compareBy({ it.estimatedOneRepMaxKg }, { it.weightKg }))
}
