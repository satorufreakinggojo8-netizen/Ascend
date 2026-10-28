package com.cadence.fit.domain.plan

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.core.SequentialIdGenerator
import com.cadence.fit.domain.library.ExerciseLibrary
import com.cadence.fit.domain.model.Difficulty
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanBuilderTest {

    private val builder = PlanBuilder(ExerciseLibrary, SequentialIdGenerator("plan"))

    private fun exercisesOf(routine: Routine): List<Exercise> =
        routine.exercises.mapNotNull { ExerciseLibrary.byId(it.exerciseId) }

    private fun plan(profile: com.cadence.fit.domain.model.TrainingProfile) =
        builder.buildStarterPlan(profile, NOW)

    @Test
    fun `the number of days drives the number of routines`() {
        assertEquals(2, plan(Fixtures.profile(daysPerWeek = 2)).routines.size)
        assertEquals(3, plan(Fixtures.profile(daysPerWeek = 3)).routines.size)
        assertEquals(4, plan(Fixtures.profile(daysPerWeek = 4)).routines.size)
        assertEquals(5, plan(Fixtures.profile(daysPerWeek = 5)).routines.size)
        assertEquals(6, plan(Fixtures.profile(daysPerWeek = 6)).routines.size)
    }

    @Test
    fun `a three day plan is full body and named predictably`() {
        val result = plan(Fixtures.profile(daysPerWeek = 3, experience = ExperienceLevel.INTERMEDIATE))
        assertEquals(listOf("Full body A", "Full body B", "Full body C"), result.routines.map { it.name })
        assertTrue(result.routines.all { it.exercises.isNotEmpty() })
        assertTrue(result.routines.all { it.isStarterPlan })
    }

    @Test
    fun `a four day plan splits upper and lower body`() {
        val result = plan(Fixtures.profile(daysPerWeek = 4, experience = ExperienceLevel.INTERMEDIATE))
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), result.routines.map { it.name })
        assertTrue(result.routines[0].focus.contains("Upper"))
        assertTrue(result.routines[1].focus.contains("Lower"))
    }

    @Test
    fun `a home plan only uses equipment the user actually has`() {
        val equipment = setOf(Equipment.BODYWEIGHT, Equipment.RESISTANCE_BAND, Equipment.MAT)
        val result = plan(
            Fixtures.profile(
                location = TrainingLocation.HOME,
                equipment = equipment,
                daysPerWeek = 3,
            ),
        )

        val used = result.routines.flatMap { exercisesOf(it) }
        assertTrue(used.isNotEmpty())
        assertTrue(used.all { it.equipment in equipment })
        assertTrue(used.all { it.alsoRequires.isEmpty() })
    }

    @Test
    fun `a home plan with dumbbells uses them`() {
        val result = plan(
            Fixtures.profile(
                location = TrainingLocation.HOME,
                equipment = setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELL, Equipment.MAT),
                daysPerWeek = 3,
            ),
        )
        val used = result.routines.flatMap { exercisesOf(it) }
        assertTrue(used.any { it.equipment == Equipment.DUMBBELL })
    }

    @Test
    fun `a capable gym plan prefers loaded implements`() {
        val result = plan(
            Fixtures.profile(
                location = TrainingLocation.GYM,
                experience = ExperienceLevel.ADVANCED,
                daysPerWeek = 3,
            ),
        )
        val first = exercisesOf(result.routines.first())
        assertEquals(Equipment.BARBELL, first.first().equipment)
        assertTrue(first.first().name.contains("squat", ignoreCase = true))
    }

    @Test
    fun `a beginner plan only contains beginner movements`() {
        val result = plan(Fixtures.profile(experience = ExperienceLevel.BEGINNER, daysPerWeek = 3))
        val used = result.routines.flatMap { exercisesOf(it) }
        assertTrue(used.isNotEmpty())
        assertTrue(used.all { it.difficulty == Difficulty.BEGINNER })
    }

    @Test
    fun `an advanced plan may include harder movements`() {
        val result = plan(Fixtures.profile(experience = ExperienceLevel.ADVANCED, daysPerWeek = 3))
        val used = result.routines.flatMap { exercisesOf(it) }
        assertTrue(used.any { it.difficulty != Difficulty.BEGINNER })
    }

    @Test
    fun `no routine repeats the same movement`() {
        listOf(2, 3, 4, 5, 6).forEach { days ->
            val result = plan(Fixtures.profile(daysPerWeek = days, sessionMinutes = 90))
            result.routines.forEach { routine ->
                val ids = routine.exercises.map { it.exerciseId }
                assertEquals("duplicate movement in ${routine.name}", ids.distinct().size, ids.size)
            }
        }
    }

    @Test
    fun `a strength goal uses low reps and long rests on the main lifts`() {
        val result = plan(Fixtures.profile(goal = TrainingGoal.STRENGTH, daysPerWeek = 3))
        val main = result.routines.first().exercises.first()
        assertEquals(3..5, main.repRange)
        assertEquals(150, main.restSeconds)
    }

    @Test
    fun `a general fitness goal uses higher reps and shorter rests`() {
        val result = plan(Fixtures.profile(goal = TrainingGoal.GENERAL_FITNESS, daysPerWeek = 3))
        val main = result.routines.first().exercises.first()
        assertEquals(10..15, main.repRange)
        assertEquals(60, main.restSeconds)
    }

    @Test
    fun `accessory work uses a shorter rest than the main lifts`() {
        val result = plan(Fixtures.profile(goal = TrainingGoal.MUSCLE_GAIN, daysPerWeek = 3))
        val routine = result.routines.first()
        val accessory = routine.exercises.last()
        assertTrue(accessory.restSeconds < 90)
    }

    @Test
    fun `a longer session gets more exercises`() {
        val short = plan(Fixtures.profile(daysPerWeek = 3, sessionMinutes = 20, experience = ExperienceLevel.INTERMEDIATE))
        val long = plan(Fixtures.profile(daysPerWeek = 3, sessionMinutes = 90, experience = ExperienceLevel.INTERMEDIATE))

        assertTrue(long.routines.first().exercises.size > short.routines.first().exercises.size)
    }

    @Test
    fun `beginners get a slightly shorter session than advanced lifters`() {
        val beginner = plan(Fixtures.profile(daysPerWeek = 3, sessionMinutes = 60, experience = ExperienceLevel.BEGINNER))
        val advanced = plan(Fixtures.profile(daysPerWeek = 3, sessionMinutes = 60, experience = ExperienceLevel.ADVANCED))

        assertTrue(advanced.routines.first().exercises.size >= beginner.routines.first().exercises.size)
    }

    @Test
    fun `an unrealistic starting frequency is called out`() {
        val result = plan(Fixtures.profile(experience = ExperienceLevel.BEGINNER, daysPerWeek = 5))
        assertTrue(result.warnings.any { it.contains("Three days is easier to keep up") })
    }

    @Test
    fun `a home profile with no equipment selected is flagged`() {
        val result = plan(
            Fixtures.profile(
                location = TrainingLocation.HOME,
                equipment = emptySet(),
                daysPerWeek = 3,
            ),
        )
        assertTrue(result.warnings.any { it.contains("only bodyweight movements") })
    }

    @Test
    fun `limited equipment shortens sessions instead of duplicating movements`() {
        val result = plan(
            Fixtures.profile(
                location = TrainingLocation.HOME,
                equipment = setOf(Equipment.BODYWEIGHT, Equipment.MAT),
                daysPerWeek = 6,
                sessionMinutes = 90,
                experience = ExperienceLevel.ADVANCED,
            ),
        )
        result.routines.forEach { routine ->
            assertTrue(routine.exercises.size < 8)
        }
        assertTrue(result.warnings.any { it.contains("shorter than your target length") })
    }

    @Test
    fun `the explanation describes the split, goal and location`() {
        val result = plan(Fixtures.profile(location = TrainingLocation.HOME, goal = TrainingGoal.MUSCLE_GAIN, daysPerWeek = 3))
        assertTrue(result.explanation.contains("full body"))
        assertTrue(result.explanation.contains("moderate range"))
        assertTrue(result.explanation.contains("at home"))
    }

    @Test
    fun `a custom routine starts empty and grows through the builder`() {
        val profile = Fixtures.profile(goal = TrainingGoal.GENERAL_FITNESS, experience = ExperienceLevel.BEGINNER)
        val empty = builder.emptyRoutine(profile, NOW)
        assertTrue(empty.exercises.isEmpty())
        assertFalse(empty.isStarterPlan)

        val exercise = ExerciseLibrary.byId("goblet-squat")!!
        val filled = builder.addExerciseTo(empty, exercise, profile)

        assertEquals(1, filled.exercises.size)
        val added = filled.exercises.first()
        assertEquals("goblet-squat", added.exerciseId)
        assertEquals(2, added.targetSets)
        assertEquals(10..15, added.repRange)
        assertEquals(45, added.restSeconds)
        assertNotNull(added.id)
    }

    @Test
    fun `custom accessory additions keep isolation rest short`() {
        val profile = Fixtures.profile()
        val routine = builder.emptyRoutine(profile, NOW)
        val curl = ExerciseLibrary.byId("dumbbell-curl")!!
        val filled = builder.addExerciseTo(routine, curl, profile)
        assertEquals(60, filled.exercises.first().restSeconds)
    }

    @Test
    fun `an estimated session length is produced for every routine`() {
        val result = plan(Fixtures.profile(goal = TrainingGoal.STRENGTH, daysPerWeek = 3, sessionMinutes = 45))
        result.routines.forEach { routine ->
            assertTrue(routine.estimatedMinutes() > 0)
        }
    }
}
