package com.cadence.fit.domain.model

/**
 * A single movement in the exercise library.
 *
 * `id` is a stable slug (`barbell-back-squat`). Progress, personal records and
 * routine entries all reference exercises by this id, so renaming the display
 * name never breaks history.
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    val pattern: MovementPattern,
    val difficulty: Difficulty,
    val instructions: List<String>,
    val tips: List<String> = emptyList(),
    /** Extra kit on top of [equipment] — for example a bench for a bench press. */
    val alsoRequires: Set<Equipment> = emptySet(),
    val isUnilateral: Boolean = false,
    /** Smallest sensible load jump for this movement, in kilograms. */
    val incrementKg: Double = 2.5,
) {
    val allMuscles: List<MuscleGroup> = (primaryMuscles + secondaryMuscles).distinct()

    val isCompound: Boolean
        get() = primaryMuscles.size > 1 || secondaryMuscles.size >= 2

    /** Everything searchable for this exercise, lower-cased and cached. */
    val searchText: String = buildString {
        append(name.lowercase()).append(' ')
        primaryMuscles.forEach { append(it.displayName.lowercase()).append(' ') }
        secondaryMuscles.forEach { append(it.displayName.lowercase()).append(' ') }
        append(equipment.displayName.lowercase()).append(' ')
        append(pattern.displayName.lowercase()).append(' ')
        append(difficulty.displayName.lowercase())
    }
}

/** Filter state for the searchable exercise library. */
data class ExerciseFilter(
    val query: String = "",
    val muscleGroups: Set<MuscleGroup> = emptySet(),
    val equipment: Set<Equipment> = emptySet(),
    val difficulties: Set<Difficulty> = emptySet(),
    val patterns: Set<MovementPattern> = emptySet(),
    val onlyAvailable: Boolean = true,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || muscleGroups.isNotEmpty() || equipment.isNotEmpty() ||
            difficulties.isNotEmpty() || patterns.isNotEmpty()

    val activeCount: Int
        get() = muscleGroups.size + equipment.size + difficulties.size + patterns.size

    fun matches(exercise: Exercise, availableEquipment: Set<Equipment>): Boolean {
        if (onlyAvailable && availableEquipment.isNotEmpty()) {
            val freeAccess = exercise.equipment == Equipment.BODYWEIGHT || exercise.equipment == Equipment.MAT
            val hasMainKit = freeAccess || exercise.equipment in availableEquipment
            val hasExtraKit = exercise.alsoRequires.all { it in availableEquipment }
            if (!hasMainKit || !hasExtraKit) return false
        }
        if (muscleGroups.isNotEmpty() && exercise.allMuscles.none { it in muscleGroups }) return false
        if (equipment.isNotEmpty() && exercise.equipment !in equipment) return false
        if (difficulties.isNotEmpty() && exercise.difficulty !in difficulties) return false
        if (patterns.isNotEmpty() && exercise.pattern !in patterns) return false
        if (query.isNotBlank()) {
            val terms = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (terms.any { term -> !exercise.searchText.contains(term) }) return false
        }
        return true
    }
}
