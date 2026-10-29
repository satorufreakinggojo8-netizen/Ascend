package com.cadence.fit.domain.insight

import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.math.PersonalRecordType
import com.cadence.fit.domain.math.WorkoutMath
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.model.WorkoutSession

/** A record that was beaten for the first time in this session. */
data class SessionRecord(
    val exerciseId: String,
    val type: PersonalRecordType,
    val value: Double,
    val previousValue: Double?,
)

/**
 * What one finished workout contained.
 *
 * Volume, sets and reps are measured from what was logged. Only the estimated
 * one-rep max is a formula, and the screen says so.
 */
data class SessionSummary(
    val sessionId: String,
    val routineName: String,
    val volumeKg: Double,
    val workingSets: Int,
    val warmupSets: Int,
    val totalReps: Int,
    val exerciseCount: Int,
    val durationMinutes: Int?,
    val records: List<SessionRecord>,
    val muscleVolume: Map<MuscleGroup, Double>,
    val previousVolumeKg: Double?,
    val comparisonRoutineName: String?,
    val comparisonNote: String,
) {
    val volumeChangeFraction: Double?
        get() = previousVolumeKg?.takeIf { it > 0.0 }?.let { (volumeKg - it) / it }

    val hasEstimatedValues: Boolean
        get() = records.any { it.type == PersonalRecordType.BEST_ESTIMATED_1RM }

    /** Muscle groups sorted by the share of this session's volume, highest first. */
    fun muscleShares(): List<Pair<MuscleGroup, Double>> {
        val total = muscleVolume.values.sum()
        if (total <= 0.0) return emptyList()
        return muscleVolume.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .map { it.key to it.value / total }
    }
}

object SessionSummaries {

    /**
     * Builds a summary only when there is something real to summarise: the session
     * must be finished and contain at least one working set.
     */
    fun build(
        session: WorkoutSession,
        allSessions: List<WorkoutSession>,
        catalog: ExerciseCatalog,
    ): SessionSummary? {
        if (!session.isComplete || session.workingSets.isEmpty()) return null
        val prior = allSessions
            .filter { it.id != session.id && it.isComplete }
            .filter { it.completedAtMs <= session.startedAtMs }

        val records = mutableListOf<SessionRecord>()
        session.exerciseIds.forEach { exerciseId ->
            val current = WorkoutMath.exerciseHistory(listOf(session), exerciseId).firstOrNull() ?: return@forEach
            val previous = WorkoutMath.exerciseHistory(prior, exerciseId).lastOrNull()
            val candidates = listOf(
                PersonalRecordType.BEST_ESTIMATED_1RM to (current.bestE1rmKg to previous?.bestE1rmKg),
                PersonalRecordType.HEAVIEST_WEIGHT to (current.topWeightKg to previous?.topWeightKg),
                PersonalRecordType.MOST_REPS to (
                    (current.sets.maxOfOrNull { it.reps }?.toDouble() ?: 0.0) to
                        previous?.sets?.maxOfOrNull { it.reps }?.toDouble()
                    ),
                PersonalRecordType.MOST_VOLUME to (current.totalVolumeKg to previous?.totalVolumeKg),
            )
            candidates.forEach { (type, values) ->
                val (now, before) = values
                // A first-ever value is a baseline, not a record: only a value that
                // beats something already logged is announced as an achievement.
                if (now > 0.0 && before != null && now > before + 0.01) {
                    records += SessionRecord(
                        exerciseId = exerciseId,
                        type = type,
                        value = now,
                        previousValue = before,
                    )
                }
            }
        }

        val comparison = prior
            .filter { it.routineId != null && it.routineId == session.routineId }
            .maxByOrNull { it.completedAtMs }
            ?: prior.maxByOrNull { it.completedAtMs }

        val note = when {
            comparison == null -> "This is your first logged session, so it sets the baseline."
            comparison.routineName != session.routineName ->
                "Compared with your last session (${comparison.routineName})."
            else -> "Compared with your last ${comparison.routineName}."
        }

        return SessionSummary(
            sessionId = session.id,
            routineName = session.routineName,
            volumeKg = session.totalVolumeKg,
            workingSets = session.workingSets.size,
            warmupSets = session.warmupSets.size,
            totalReps = session.totalReps,
            exerciseCount = session.exerciseIds.size,
            durationMinutes = session.durationMinutes,
            records = records.sortedBy { it.type.ordinal },
            muscleVolume = WorkoutMath.volumeByMuscle(listOf(session), catalog),
            previousVolumeKg = comparison?.totalVolumeKg,
            comparisonRoutineName = comparison?.routineName,
            comparisonNote = note,
        )
    }

    /** Plain sentence for how this session's volume compares with the previous one. */
    fun volumeComparisonText(summary: SessionSummary): String {
        val change = summary.volumeChangeFraction ?: return summary.comparisonNote
        val percent = kotlin.math.abs((change * 100).toInt())
        return when {
            percent == 0 -> "About the same volume as last time."
            change > 0 -> "About $percent% more volume than last time."
            else -> "About $percent% less volume than last time. Lighter days are a normal part of training."
        }
    }

    const val MEASUREMENT_NOTE: String =
        "Volume, sets and reps are measured from the sets you logged. Estimated 1RM is a " +
            "formula based on weight and reps, not a tested maximum."
}
