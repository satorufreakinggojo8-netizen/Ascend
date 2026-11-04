package com.cadence.fit.domain.plan

import com.cadence.fit.domain.model.MovementPattern
import com.cadence.fit.domain.model.MuscleGroup

/**
 * One movement slot in a session template.
 *
 * Slots are filled from the exercise library using the user's equipment,
 * experience and goal; a slot never hard-codes a weight or an exercise id.
 */
data class MovementSlot(
    val label: String,
    val patterns: Set<MovementPattern>,
    val muscles: Set<MuscleGroup> = emptySet(),
    /** Main lifts get more sets and longer rests than accessories. */
    val main: Boolean = false,
    /** Ordered, explicit choices for hand-built sessions; unavailable kit is skipped, never substituted silently. */
    val preferredExerciseIds: List<String> = emptyList(),
    val targetSets: Int? = null,
    val repRange: IntRange? = null,
    val restSeconds: Int? = null,
    val note: String = "",
)

/** A named, reusable session shape such as "Shoulders & arms" or "Legs". */
data class SessionTemplate(
    val name: String,
    val focus: String,
    val slots: List<MovementSlot>,
)
