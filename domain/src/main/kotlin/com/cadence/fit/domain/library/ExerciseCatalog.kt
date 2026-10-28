package com.cadence.fit.domain.library

import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.domain.model.ExerciseFilter
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.model.TrainingLocation

/**
 * Read-only access to the exercise library. Implemented by `ExerciseLibrary`;
 * the interface keeps the analysis code independent of the data set.
 */
interface ExerciseCatalog {
    val all: List<Exercise>

    fun byId(id: String): Exercise?

    fun search(filter: ExerciseFilter, availableEquipment: Set<Equipment>): List<Exercise>

    /** Equipment the user should tick by default for a training location. */
    fun suggestedEquipment(location: TrainingLocation): Set<Equipment>

    companion object {
        /** Sensible default kit so a brand-new profile can plan immediately. */
        fun defaultEquipment(location: TrainingLocation): Set<Equipment> = when (location) {
            TrainingLocation.HOME -> Equipment.homeStarterKit
            TrainingLocation.GYM -> setOf(
                Equipment.BODYWEIGHT,
                Equipment.BARBELL,
                Equipment.DUMBBELL,
                Equipment.MACHINE,
                Equipment.CABLE,
                Equipment.BENCH,
                Equipment.MAT,
            )
        }
    }
}

/** A named group of muscle groups, used by the library filter chips. */
data class MuscleFilterGroup(val label: String, val muscles: List<MuscleGroup>)

object MuscleFilterGroups {
    val all: List<MuscleFilterGroup> = listOf(
        MuscleFilterGroup("Chest", listOf(MuscleGroup.CHEST)),
        MuscleFilterGroup("Back", listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK, MuscleGroup.LOWER_BACK)),
        MuscleFilterGroup("Shoulders", listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRAPS)),
        MuscleFilterGroup("Arms", listOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS)),
        MuscleFilterGroup("Core", listOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
        MuscleFilterGroup(
            "Legs",
            listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES),
        ),
        MuscleFilterGroup("Conditioning", listOf(MuscleGroup.CONDITIONING)),
    )
}
