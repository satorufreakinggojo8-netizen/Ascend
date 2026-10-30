package com.cadence.fit.domain.model

import kotlin.math.roundToInt

/** One exercise slot inside a routine. */
data class RoutineExercise(
    val id: String,
    val exerciseId: String,
    val targetSets: Int,
    val repRange: IntRange,
    val restSeconds: Int,
    val warmupSets: Int = 0,
    /** Non-null groups this slot with the following slot as a superset. */
    val supersetGroup: Int? = null,
    val note: String = "",
) {
    val repRangeText: String
        get() = if (repRange.first == repRange.last) "${repRange.first}" else "${repRange.first}-${repRange.last}"

    val supersetLabel: String?
        get() = supersetGroup?.let { group -> "Superset ${('A' + group)}" }
}

data class Routine(
    val id: String,
    val name: String,
    val focus: String,
    val goal: TrainingGoal,
    val location: TrainingLocation,
    val exercises: List<RoutineExercise>,
    val isStarterPlan: Boolean = false,
    val createdAtMs: Long = 0L,
) {
    val workingSets: Int get() = exercises.sumOf { it.targetSets.coerceAtLeast(0) }

    val warmupSets: Int get() = exercises.sumOf { it.warmupSets.coerceAtLeast(0) }

    /** Rough length estimate used by the planner and the home screen. */
    fun estimatedMinutes(secondsPerSet: Int = 35): Int {
        val work = exercises.sumOf { (it.targetSets + it.warmupSets) * secondsPerSet }
        val rest = exercises.sumOf { it.targetSets * it.restSeconds }
        val transitions = exercises.size * 45
        return ((work + rest + transitions) / 60.0).roundToInt().coerceAtLeast(5)
    }
}

/** Difficulty/experience-aware suggestion produced by the plan builder. */
data class PlanSummary(
    val routines: List<Routine>,
    val daysPerWeek: Int,
    val explanation: String,
    val warnings: List<String> = emptyList(),
)
