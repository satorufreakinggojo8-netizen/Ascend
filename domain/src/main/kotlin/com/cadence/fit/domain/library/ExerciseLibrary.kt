package com.cadence.fit.domain.library

import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.domain.model.ExerciseFilter
import com.cadence.fit.domain.model.TrainingLocation

/**
 * The built-in exercise library.
 *
 * Every illustration in the app is drawn from the movement pattern and primary
 * muscle of the exercise (see the `:app` illustration components); no third-party
 * images are bundled, so nothing here carries an external licence.
 */
object ExerciseLibrary : ExerciseCatalog {

    override val all: List<Exercise> =
        (pushExercises + pullExercises + legExercises + coreArmsConditioningExercises)
            .sortedBy { it.name }

    private val index: Map<String, Exercise> = all.associateBy { it.id }

    override fun byId(id: String): Exercise? = index[id]

    override fun search(filter: ExerciseFilter, availableEquipment: Set<Equipment>): List<Exercise> {
        val matches = all.filter { filter.matches(it, availableEquipment) }
        if (filter.query.isBlank()) return matches
        return matches.sortedWith(compareBy({ relevance(it, filter.query) }, { it.name }))
    }

    override fun suggestedEquipment(location: TrainingLocation): Set<Equipment> =
        ExerciseCatalog.defaultEquipment(location)

    private fun relevance(exercise: Exercise, query: String): Int {
        val needle = query.lowercase().trim()
        val name = exercise.name.lowercase()
        return when {
            name.startsWith(needle) -> 0
            name.contains(needle) -> 1
            else -> 2
        }
    }

    /** Self-check used by the test suite to keep the data set consistent. */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        all.forEach { exercise ->
            if (!seen.add(exercise.id)) problems += "duplicate id: ${exercise.id}"
            if (exercise.name.isBlank()) problems += "blank name: ${exercise.id}"
            if (exercise.primaryMuscles.isEmpty()) problems += "no primary muscle: ${exercise.id}"
            if (exercise.instructions.size < 2) problems += "too few instructions: ${exercise.id}"
            if (exercise.incrementKg <= 0.0) problems += "non-positive increment: ${exercise.id}"
            exercise.instructions.forEach { step ->
                if (step.isBlank()) problems += "blank instruction: ${exercise.id}"
            }
        }
        MuscleFilterGroupChecks.problems(catalog = this).let { problems += it }
        return problems
    }
}

private object MuscleFilterGroupChecks {
    fun problems(catalog: ExerciseCatalog): List<String> {
        val covered = MuscleFilterGroups.all.flatMap { it.muscles }.toSet()
        return catalog.all
            .flatMap { it.primaryMuscles }
            .distinct()
            .filterNot { it in covered }
            .map { "muscle group not reachable from the library filters: $it" }
    }
}
