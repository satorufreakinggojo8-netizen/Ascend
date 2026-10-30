package com.cadence.fit.domain.library

import com.cadence.fit.domain.model.Difficulty
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExerciseFilter
import com.cadence.fit.domain.model.MovementPattern
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.model.TrainingLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryTest {

    private val allEquipment = Equipment.entries.toSet()

    private fun search(
        filter: ExerciseFilter = ExerciseFilter(),
        equipment: Set<Equipment> = allEquipment,
    ) = ExerciseLibrary.search(filter, equipment)

    @Test
    fun `the library data is internally consistent`() {
        val problems = ExerciseLibrary.validate()
        assertTrue("library problems: $problems", problems.isEmpty())
    }

    @Test
    fun `the library covers every movement pattern a plan can ask for`() {
        val patterns = ExerciseLibrary.all.map { it.pattern }.toSet()
        val planPatterns = setOf(
            MovementPattern.SQUAT,
            MovementPattern.HINGE,
            MovementPattern.LUNGE,
            MovementPattern.HORIZONTAL_PUSH,
            MovementPattern.VERTICAL_PUSH,
            MovementPattern.HORIZONTAL_PULL,
            MovementPattern.VERTICAL_PULL,
            MovementPattern.CORE,
            MovementPattern.CURL,
            MovementPattern.EXTENSION,
            MovementPattern.CALF,
            MovementPattern.CONDITIONING,
        )
        assertTrue("missing: ${planPatterns - patterns}", patterns.containsAll(planPatterns))
    }

    @Test
    fun `the library covers every muscle group used by the body map`() {
        val trained = ExerciseLibrary.all.flatMap { it.primaryMuscles }.toSet()
        assertTrue(trained.containsAll(MuscleGroup.bodyMapGroups))
    }

    @Test
    fun `there is at least one beginner option for every home friendly muscle group`() {
        val homeKit = Equipment.homeStarterKit
        MuscleGroup.bodyMapGroups.forEach { muscle ->
            val available = search(
                ExerciseFilter(muscleGroups = setOf(muscle), difficulties = setOf(Difficulty.BEGINNER)),
                homeKit,
            )
            assertTrue("no beginner home option for $muscle", available.isNotEmpty())
        }
    }

    @Test
    fun `lookup by id returns the exercise or null`() {
        val squat = ExerciseLibrary.byId("barbell-back-squat")
        assertNotNull(squat)
        assertEquals("Barbell back squat", squat!!.name)
        assertNull(ExerciseLibrary.byId("not-an-exercise"))
    }

    @Test
    fun `every exercise has instructions, a pattern and a positive increment`() {
        ExerciseLibrary.all.forEach { exercise ->
            assertTrue(exercise.id, exercise.instructions.size >= 2)
            assertTrue(exercise.id, exercise.primaryMuscles.isNotEmpty())
            assertTrue(exercise.id, exercise.incrementKg > 0.0)
        }
    }

    @Test
    fun `search matches a partial name regardless of case`() {
        val results = search(ExerciseFilter(query = "BENCH"))
        assertTrue(results.any { it.id == "barbell-bench-press" })
        assertTrue(results.all { it.searchText.contains("bench") })
    }

    @Test
    fun `search matches muscle group and equipment words`() {
        assertTrue(search(ExerciseFilter(query = "hamstrings")).any { it.id == "romanian-deadlift" })
        assertTrue(search(ExerciseFilter(query = "kettlebell")).isNotEmpty())
    }

    @Test
    fun `search puts prefix matches first`() {
        val results = search(ExerciseFilter(query = "row"))

        assertTrue(results.first().name.startsWith("Row", ignoreCase = true))
        assertTrue(results.any { it.name == "Barbell row" })
        // "Rowing machine intervals" is a prefix match, so it must precede "Barbell row".
        assertTrue(results.indexOfFirst { it.name == "Barbell row" } > 0)
    }

    @Test
    fun `an empty query returns the whole library without filters`() {
        assertEquals(ExerciseLibrary.all.size, search().size)
    }

    @Test
    fun `muscle group filters match primary and secondary muscles`() {
        val chest = search(ExerciseFilter(muscleGroups = setOf(MuscleGroup.CHEST)))
        assertTrue(chest.isNotEmpty())
        assertTrue(chest.all { exercise -> exercise.allMuscles.contains(MuscleGroup.CHEST) })
        assertTrue(chest.any { it.primaryMuscles.contains(MuscleGroup.CHEST) })
    }

    @Test
    fun `difficulty filters narrow the list`() {
        val advanced = search(ExerciseFilter(difficulties = setOf(Difficulty.ADVANCED)))
        assertTrue(advanced.isNotEmpty())
        assertTrue(advanced.all { it.difficulty == Difficulty.ADVANCED })
        assertTrue(advanced.size < ExerciseLibrary.all.size)
    }

    @Test
    fun `pattern filters narrow the list`() {
        val hinges = search(ExerciseFilter(patterns = setOf(MovementPattern.HINGE)))
        assertTrue(hinges.isNotEmpty())
        assertTrue(hinges.all { it.pattern == MovementPattern.HINGE })
    }

    @Test
    fun `equipment availability hides movements that need kit the user does not have`() {
        val bodyweightOnly = search(ExerciseFilter(), setOf(Equipment.BODYWEIGHT, Equipment.MAT))
        assertTrue(bodyweightOnly.none { it.equipment == Equipment.BARBELL })
        assertTrue(bodyweightOnly.any { it.id == "push-up" })
    }

    @Test
    fun `extra required kit such as a bench is respected`() {
        val noBench = search(ExerciseFilter(), setOf(Equipment.BARBELL, Equipment.BODYWEIGHT, Equipment.MAT))
        assertTrue(noBench.none { it.id == "barbell-bench-press" })

        val withBench = search(
            ExerciseFilter(),
            setOf(Equipment.BARBELL, Equipment.BENCH, Equipment.BODYWEIGHT, Equipment.MAT),
        )
        assertTrue(withBench.any { it.id == "barbell-bench-press" })
    }

    @Test
    fun `availability filtering can be turned off to browse the whole library`() {
        val everything = search(ExerciseFilter(onlyAvailable = false), setOf(Equipment.BODYWEIGHT))
        assertEquals(ExerciseLibrary.all.size, everything.size)
    }

    @Test
    fun `search terms combine with filters`() {
        val results = search(
            ExerciseFilter(query = "press", patterns = setOf(MovementPattern.VERTICAL_PUSH)),
            setOf(Equipment.DUMBBELL, Equipment.BODYWEIGHT, Equipment.MAT),
        )
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.pattern == MovementPattern.VERTICAL_PUSH })
    }

    @Test
    fun `filters report whether they are active`() {
        assertTrue(!ExerciseFilter().isActive)
        assertEquals(0, ExerciseFilter().activeCount)
        assertTrue(ExerciseFilter(query = "squat").isActive)
        assertEquals(1, ExerciseFilter(muscleGroups = setOf(MuscleGroup.CHEST)).activeCount)
    }

    @Test
    fun `suggested equipment differs between home and gym`() {
        val home = ExerciseLibrary.suggestedEquipment(TrainingLocation.HOME)
        val gym = ExerciseLibrary.suggestedEquipment(TrainingLocation.GYM)

        assertTrue(home.contains(Equipment.DUMBBELL))
        assertTrue(!home.contains(Equipment.BARBELL))
        assertTrue(gym.contains(Equipment.BARBELL))
        assertTrue(gym.contains(Equipment.BENCH))
    }

    @Test
    fun `muscle filter groups reach every body map muscle`() {
        val covered = MuscleFilterGroups.all.flatMap { it.muscles }.toSet()
        assertTrue(covered.containsAll(MuscleGroup.bodyMapGroups))
    }
}
