package com.cadence.fit.domain.library

import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.domain.model.ExperienceLevel

/**
 * One suggested replacement for an exercise.
 *
 * [reasons] are shown next to the option verbatim, so the suggestion explains
 * itself instead of asking the user to trust a score.
 */
data class SubstitutionOption(
    val exercise: Exercise,
    val score: Int,
    val reasons: List<String>,
    /** Kit the user does not have; empty when the option is trainable right now. */
    val missingEquipment: List<Equipment> = emptyList(),
) {
    val isAvailable: Boolean get() = missingEquipment.isEmpty()
}

/**
 * Finds movements that can stand in for another.
 *
 * The ranking is deliberately conservative: an alternative must train at least one
 * of the same primary muscles or share the movement pattern, and anything that needs
 * equipment the user does not have is listed last and clearly labelled. Nothing is
 * swapped automatically; the caller decides.
 */
object Substitutions {

    fun forExercise(
        target: Exercise,
        catalog: ExerciseCatalog,
        availableEquipment: Set<Equipment> = emptySet(),
        experience: ExperienceLevel? = null,
        includeUnavailable: Boolean = false,
        limit: Int = 12,
    ): List<SubstitutionOption> {
        val knownKit = availableEquipment.isNotEmpty()

        return catalog.all.asSequence()
            .filter { it.id != target.id }
            .mapNotNull { candidate -> score(target, candidate, availableEquipment, knownKit, experience) }
            .filter { includeUnavailable || it.isAvailable }
            .sortedWith(
                compareByDescending<SubstitutionOption> { it.isAvailable }
                    .thenByDescending { it.score }
                    .thenBy { it.exercise.name },
            )
            .take(limit)
            .toList()
    }

    private fun score(
        target: Exercise,
        candidate: Exercise,
        availableEquipment: Set<Equipment>,
        knownKit: Boolean,
        experience: ExperienceLevel?,
    ): SubstitutionOption? {
        val sharedPrimary = target.primaryMuscles.intersect(candidate.primaryMuscles.toSet())
        val samePattern = target.pattern == candidate.pattern
        if (sharedPrimary.isEmpty() && !samePattern) return null

        val missing = if (knownKit) missingKit(candidate, availableEquipment) else emptyList()
        val reasons = mutableListOf<String>()
        var score = 0

        if (sharedPrimary.isNotEmpty()) {
            score += 6 + (sharedPrimary.size - 1) * 2
            reasons += "Trains ${sharedPrimary.joinToString(" and ") { it.displayName.lowercase() }}"
        }
        if (target.primaryMuscles.toSet() == candidate.primaryMuscles.toSet()) {
            score += 3
        }
        if (samePattern) {
            score += 4
            reasons += "Same pattern: ${candidate.pattern.displayName.lowercase()}"
        }
        val secondaryOverlap = target.primaryMuscles.intersect(candidate.secondaryMuscles.toSet())
        if (secondaryOverlap.isNotEmpty()) {
            reasons += "Also works ${secondaryOverlap.joinToString(" and ") { it.displayName.lowercase() }}"
        }

        when {
            candidate.equipment == target.equipment ->
                reasons += "Uses the same ${candidate.equipment.displayName.lowercase()}"
            candidate.equipment == Equipment.BODYWEIGHT || candidate.equipment == Equipment.MAT -> {
                score += 2
                reasons += "No equipment needed"
            }
            else -> reasons += "Uses ${candidate.equipment.displayName.lowercase()}"
        }
        if (candidate.equipment == target.equipment) score += 1

        if (candidate.equipment == Equipment.BODYWEIGHT || candidate.equipment == Equipment.MAT) score += 1

        when {
            candidate.difficulty.ordinal < target.difficulty.ordinal -> {
                score += 2
                reasons += "Simpler variation"
            }
            candidate.difficulty.ordinal > target.difficulty.ordinal -> {
                score -= 2
                reasons += "More demanding than the original"
            }
            else -> score += 1
        }

        if (candidate.isCompound == target.isCompound) score += 1
        if (candidate.isUnilateral == target.isUnilateral) score += 1

        if (experience != null && candidate.difficulty.ordinal > experience.ordinal) {
            score -= 4
            reasons += "Above your current experience level"
        }

        if (missing.isNotEmpty()) {
            reasons += "Needs ${missing.joinToString(", ") { it.displayName.lowercase() }}"
        }

        return SubstitutionOption(
            exercise = candidate,
            score = score,
            reasons = reasons.distinct(),
            missingEquipment = missing,
        )
    }

    private fun missingKit(exercise: Exercise, available: Set<Equipment>): List<Equipment> {
        val required = buildList {
            if (exercise.equipment != Equipment.BODYWEIGHT && exercise.equipment != Equipment.MAT) {
                add(exercise.equipment)
            }
            addAll(exercise.alsoRequires)
        }
        return required.filterNot { kit -> kit == Equipment.BODYWEIGHT || kit == Equipment.MAT || kit in available }
            .distinct()
    }

    /** A short sentence explaining what a swap did, for the confirmation message. */
    fun describeSwap(target: Exercise, replacement: Exercise): String {
        val shared = target.primaryMuscles.intersect(replacement.primaryMuscles.toSet())
        val reason = when {
            shared.isNotEmpty() -> "same muscles: ${shared.joinToString(", ") { it.displayName.lowercase() }}"
            target.pattern == replacement.pattern -> "same movement pattern"
            else -> "an alternative for this slot"
        }
        return "Swapped ${target.name} for ${replacement.name} — $reason. Sets, reps and rest are unchanged."
    }
}
