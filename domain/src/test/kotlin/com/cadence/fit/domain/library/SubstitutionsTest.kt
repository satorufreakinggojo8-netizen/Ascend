package com.cadence.fit.domain.library

import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstitutionsTest {

    private fun exercise(id: String) = ExerciseLibrary.byId(id)
        ?: throw AssertionError("missing fixture exercise $id")

    private fun forBench(
        equipment: Set<Equipment> = emptySet(),
        experience: ExperienceLevel? = null,
        includeUnavailable: Boolean = false,
    ) = Substitutions.forExercise(
        target = exercise("barbell-bench-press"),
        catalog = ExerciseLibrary,
        availableEquipment = equipment,
        experience = experience,
        includeUnavailable = includeUnavailable,
    )

    @Test
    fun `the original exercise is never offered as its own alternative`() {
        val ids = Substitutions.forExercise(exercise("barbell-bench-press"), ExerciseLibrary)
            .map { it.exercise.id }

        assertFalse(ids.contains("barbell-bench-press"))
    }

    @Test
    fun `every alternative trains an overlapping muscle or the same pattern`() {
        val target = exercise("barbell-bench-press")
        Substitutions.forExercise(target, ExerciseLibrary, limit = 30).forEach { option ->
            val sharesMuscle = option.exercise.primaryMuscles.any { it in target.primaryMuscles }
            val sharesPattern = option.exercise.pattern == target.pattern
            assertTrue(
                "${option.exercise.id} does not belong with ${target.id}",
                sharesMuscle || sharesPattern,
            )
        }
    }

    @Test
    fun `a bench press can be replaced by pressing movements`() {
        val ids = forBench().map { it.exercise.id }

        assertTrue(ids.contains("dumbbell-bench-press"))
        assertTrue(ids.contains("push-up"))
        assertTrue(ids.any { it == "machine-chest-press" || it == "cable-chest-fly" })
    }

    @Test
    fun `alternatives explain the muscles and the equipment they need`() {
        val option = forBench().first { it.exercise.id == "dumbbell-bench-press" }

        assertTrue(option.reasons.any { it.contains("chest") })
        assertTrue(option.reasons.any { it.contains("dumbbell") })
        assertEquals(Equipment.DUMBBELL, option.exercise.equipment)
    }

    @Test
    fun `a bodyweight option is marked as needing no equipment`() {
        val option = forBench().first { it.exercise.id == "push-up" }

        assertTrue(option.reasons.any { it.contains("No equipment needed") })
        assertTrue(option.isAvailable)
    }

    @Test
    fun `only movements the user can actually train are offered by default`() {
        val equipment = setOf(Equipment.BODYWEIGHT, Equipment.MAT, Equipment.DUMBBELL)
        val options = forBench(equipment = equipment)

        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.isAvailable })
        assertTrue(options.all { it.missingEquipment.isEmpty() })
        options.forEach { option ->
            val needs = option.exercise.equipment
            assertTrue(
                "offered ${option.exercise.id} which needs $needs",
                needs == Equipment.BODYWEIGHT || needs == Equipment.MAT || needs in equipment,
            )
        }
    }

    @Test
    fun `an unavailable alternative is listed last and says which kit is missing`() {
        val equipment = setOf(Equipment.BODYWEIGHT, Equipment.MAT)
        val options = forBench(equipment = equipment, includeUnavailable = true)

        val unavailable = options.filterNot { it.isAvailable }
        assertTrue(unavailable.isNotEmpty())
        unavailable.forEach { option ->
            assertTrue(option.missingEquipment.isNotEmpty())
            assertTrue(option.reasons.any { it.startsWith("Needs ") })
        }
        val firstUnavailable = options.indexOfFirst { !it.isAvailable }
        assertTrue(options.drop(firstUnavailable).none { it.isAvailable })
    }

    @Test
    fun `an option above the user's experience is labelled and ranked lower`() {
        val options = forBench(experience = ExperienceLevel.BEGINNER, includeUnavailable = true)

        val hard = options.filter { option ->
            option.reasons.contains("Above your current experience level")
        }
        assertTrue(hard.isNotEmpty())
        assertFalse(options.first().reasons.contains("Above your current experience level"))
    }

    @Test
    fun `a simpler variation is called out`() {
        val bodyweightSquat = exercise("bodyweight-squat")
        val options = Substitutions.forExercise(
            target = exercise("barbell-back-squat"),
            catalog = ExerciseLibrary,
            availableEquipment = setOf(Equipment.BODYWEIGHT, Equipment.MAT),
        )

        val simpler = options.filter { option -> option.reasons.contains("Simpler variation") }
        assertTrue(simpler.isNotEmpty())
        assertTrue(options.any { it.exercise.id == bodyweightSquat.id })
    }

    @Test
    fun `the result count is capped`() {
        val options = Substitutions.forExercise(
            target = exercise("barbell-back-squat"),
            catalog = ExerciseLibrary,
            limit = 3,
        )

        assertTrue(options.size <= 3)
    }

    @Test
    fun `curls offer other curls rather than pressing movements`() {
        val ids = Substitutions.forExercise(
            target = exercise("dumbbell-curl"),
            catalog = ExerciseLibrary,
            availableEquipment = setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELL, Equipment.MAT),
        ).map { it.exercise.id }

        assertTrue(ids.contains("hammer-curl"))
        assertFalse(ids.contains("push-up"))
        assertFalse(ids.contains("leg-press"))
        assertFalse(ids.contains("leg-press"))
    }

    @Test
    fun `a swap message names both movements and says the prescription is unchanged`() {
        val message = Substitutions.describeSwap(
            exercise("barbell-bench-press"),
            exercise("push-up"),
        )

        assertTrue(message.contains("Barbell bench press"))
        assertTrue(message.contains("Push-up"))
        assertTrue(message.contains("unchanged"))
    }

    @Test
    fun `an alternative that shares a movement pattern but no primary muscle is still explained`() {
        val target = exercise("barbell-back-squat")
        val option = Substitutions.forExercise(target, ExerciseLibrary)
            .firstOrNull { option ->
                option.exercise.primaryMuscles.none { it in listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES) } &&
                    option.exercise.pattern == target.pattern
            }

        if (option != null) {
            assertTrue(option.reasons.any { it.startsWith("Same pattern") })
        }
    }
}
