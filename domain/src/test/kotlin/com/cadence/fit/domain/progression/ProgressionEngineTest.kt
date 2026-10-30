package com.cadence.fit.domain.progression

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.library.ExerciseLibrary
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.domain.units.Weights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {

    private val engine = ProgressionEngine(ExerciseLibrary)

    private fun history(
        exerciseId: String,
        sessions: Int,
        weightKg: Double,
        reps: Int,
        setCount: Int = 3,
        everyDays: Long = 7,
    ) = (0 until sessions).map { index ->
        val at = NOW - (sessions - 1 - index) * everyDays * DAY_MS
        Fixtures.session(
            id = "s$index",
            atMs = at,
            sets = Fixtures.sets(
                exerciseId = exerciseId,
                count = setCount,
                weightKg = weightKg,
                reps = reps,
                atMs = at,
                sessionId = "s$index",
            ),
        )
    }

    @Test
    fun `with no history it asks for a starting weight instead of guessing one`() {
        val recommendation = engine.recommend(
            Fixtures.routineExercise("barbell-bench-press", repRange = 8..12),
            emptyList(),
            TrainingGoal.MUSCLE_GAIN,
            WeightUnit.KG,
        )

        assertEquals(SuggestionKind.STARTING_POINT, recommendation.kind)
        assertNull(recommendation.suggestedWeightKg)
        assertEquals(Confidence.LOW, recommendation.confidence)
        assertTrue(recommendation.detail.isNotBlank())
    }

    @Test
    fun `topping out the rep range suggests one small weight increase`() {
        val sessions = history("barbell-bench-press", sessions = 1, weightKg = 60.0, reps = 12)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("barbell-bench-press", repRange = 8..12),
            sessions,
            TrainingGoal.MUSCLE_GAIN,
            WeightUnit.KG,
        )

        assertEquals(SuggestionKind.INCREASE_WEIGHT, recommendation.kind)
        assertEquals(62.5, recommendation.suggestedWeightKg!!, 1e-6)
        assertEquals(60.0, recommendation.lastWeightKg!!, 1e-9)
        assertEquals(8, recommendation.suggestedMinReps)
        assertTrue(recommendation.detail.contains("62.5"))
    }

    @Test
    fun `a single step never exceeds five percent of the working weight`() {
        // The deadlift uses a 5 kg increment, so the cap has to bind at 80 kg.
        val sessions = history("barbell-deadlift", sessions = 1, weightKg = 80.0, reps = 5)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("barbell-deadlift", repRange = 3..5),
            sessions,
            TrainingGoal.STRENGTH,
            WeightUnit.KG,
        )

        assertEquals(SuggestionKind.INCREASE_WEIGHT, recommendation.kind)
        assertEquals(84.0, recommendation.suggestedWeightKg!!, 1e-6)
        assertTrue(recommendation.relativeJump!! <= 0.05 + 1e-9)
    }

    @Test
    fun `a light movement still moves by the smallest usable increment`() {
        // 5% of 8 kg is 0.4 kg, which is below anything you can actually load.
        val sessions = history("dumbbell-curl", sessions = 1, weightKg = 8.0, reps = 15)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("dumbbell-curl", repRange = 12..15),
            sessions,
            TrainingGoal.GENERAL_FITNESS,
            WeightUnit.KG,
        )

        assertEquals(SuggestionKind.INCREASE_WEIGHT, recommendation.kind)
        assertEquals(9.25, recommendation.suggestedWeightKg!!, 1e-6)
    }

    @Test
    fun `suggestions land on the pound grid when pounds are selected`() {
        val sessions = history("barbell-bench-press", sessions = 1, weightKg = 60.0, reps = 12)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("barbell-bench-press", repRange = 8..12),
            sessions,
            TrainingGoal.MUSCLE_GAIN,
            WeightUnit.LB,
        )

        assertEquals("132.5", Weights.format(60.0, WeightUnit.LB))
        assertNotNull(recommendation.suggestedWeightKg)
        assertEquals("138", Weights.format(recommendation.suggestedWeightKg!!, WeightUnit.LB))
    }

    @Test
    fun `working inside the rep range suggests adding reps at the same weight`() {
        val at = NOW
        val session = Fixtures.session(
            id = "s0",
            atMs = at,
            sets = listOf(
                Fixtures.set("barbell-bench-press", 60.0, 8, atMs = at, setIndex = 1),
                Fixtures.set("barbell-bench-press", 60.0, 9, atMs = at, setIndex = 2),
                Fixtures.set("barbell-bench-press", 60.0, 10, atMs = at, setIndex = 3),
            ),
        )
        val recommendation = engine.recommend(
            Fixtures.routineExercise("barbell-bench-press", repRange = 8..12),
            listOf(session),
            TrainingGoal.GENERAL_FITNESS,
            WeightUnit.KG,
        )

        assertEquals(SuggestionKind.ADD_REPS, recommendation.kind)
        assertEquals(60.0, recommendation.suggestedWeightKg!!, 1e-9)
        assertEquals(9, recommendation.suggestedMinReps)
        assertEquals(12, recommendation.suggestedMaxReps)
    }

    @Test
    fun `missing the bottom of the range suggests stepping back rather than pushing on`() {
        val sessions = history("barbell-bench-press", sessions = 1, weightKg = 70.0, reps = 5)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("barbell-bench-press", repRange = 8..12),
            sessions,
            TrainingGoal.MUSCLE_GAIN,
            WeightUnit.KG,
        )

        assertEquals(SuggestionKind.REDUCE, recommendation.kind)
        assertEquals(67.5, recommendation.suggestedWeightKg!!, 1e-6)
    }

    @Test
    fun `bodyweight work progresses by sets once it has already been completed twice`() {
        val sessions = history("push-up", sessions = 2, weightKg = 0.0, reps = 12)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("push-up", targetSets = 3, repRange = 8..12),
            sessions,
            TrainingGoal.GENERAL_FITNESS,
            WeightUnit.KG,
        )

        assertEquals(SuggestionKind.ADD_SET, recommendation.kind)
        assertEquals(4, recommendation.suggestedSets)
    }

    @Test
    fun `bodyweight work suggests reps when there is no previous session to compare`() {
        val sessions = history("push-up", sessions = 1, weightKg = 0.0, reps = 12)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("push-up", targetSets = 3, repRange = 8..12),
            sessions,
            TrainingGoal.GENERAL_FITNESS,
            WeightUnit.KG,
        )

        assertEquals(SuggestionKind.ADD_REPS, recommendation.kind)
        assertNull(recommendation.suggestedWeightKg)
    }

    @Test
    fun `confidence grows with the number of logged sessions`() {
        // Sessions must actually improve, otherwise the engine deliberately drops
        // to low confidence because the movement looks stalled.
        fun improvingHistory(count: Int) = (0 until count).map { index ->
            val at = NOW - (count - 1 - index) * 7 * DAY_MS
            Fixtures.session(
                id = "c$index",
                atMs = at,
                sets = Fixtures.sets(
                    exerciseId = "barbell-bench-press",
                    count = 3,
                    weightKg = 60.0 + index * 2.5,
                    reps = 10,
                    atMs = at,
                    sessionId = "c$index",
                ),
            )
        }

        fun confidenceFor(count: Int) = engine.recommend(
            Fixtures.routineExercise("barbell-bench-press", repRange = 8..12),
            improvingHistory(count),
            TrainingGoal.MUSCLE_GAIN,
            WeightUnit.KG,
        ).confidence

        assertEquals(Confidence.LOW, confidenceFor(1))
        assertEquals(Confidence.MEDIUM, confidenceFor(2))
        assertEquals(Confidence.HIGH, confidenceFor(3))
    }

    @Test
    fun `a stalled movement is reported with low confidence`() {
        val flat = history("barbell-bench-press", sessions = 3, weightKg = 60.0, reps = 10)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("barbell-bench-press", repRange = 8..12),
            flat,
            TrainingGoal.MUSCLE_GAIN,
            WeightUnit.KG,
        )

        assertEquals(Confidence.LOW, recommendation.confidence)
    }

    @Test
    fun `every recommendation explains itself`() {
        val sessions = history("barbell-bench-press", sessions = 3, weightKg = 60.0, reps = 12)
        val recommendation = engine.recommend(
            Fixtures.routineExercise("barbell-bench-press", repRange = 8..12),
            sessions,
            TrainingGoal.STRENGTH,
            WeightUnit.KG,
        )

        assertTrue(recommendation.headline.isNotBlank())
        assertTrue(recommendation.detail.isNotBlank())
        assertTrue(recommendation.reason.isNotBlank())
        assertTrue(recommendation.basedOnSessions == 3)
    }

    @Test
    fun `recommendations never mutate the routine or the history`() {
        val routine = Fixtures.routine(
            exercises = listOf(Fixtures.routineExercise("barbell-bench-press")),
        )
        val sessions = history("barbell-bench-press", sessions = 2, weightKg = 60.0, reps = 12)
        val routineSnapshot = routine.copy()
        val sessionsSnapshot = sessions.toList()

        engine.recommendForRoutine(routine, sessions, WeightUnit.KG)

        assertEquals(routineSnapshot, routine)
        assertEquals(sessionsSnapshot, sessions)
    }

    @Test
    fun `plateau guidance appears after several sessions without a new best`() {
        val sessions = history("barbell-bench-press", sessions = 5, weightKg = 60.0, reps = 10)
        val insight = engine.plateauInsight("barbell-bench-press", sessions, NOW)

        assertNotNull(insight)
        assertEquals(4, insight!!.sessionsWithoutProgress)
        assertTrue(insight.headline.contains("Barbell bench press"))
        assertTrue(insight.suggestions.isNotEmpty())
    }

    @Test
    fun `plateau guidance stays silent while an exercise is still improving`() {
        val sessions = (0 until 5).map { index ->
            val at = NOW - (4 - index) * 7 * DAY_MS
            Fixtures.session(
                id = "s$index",
                atMs = at,
                sets = Fixtures.sets(
                    exerciseId = "barbell-bench-press",
                    count = 3,
                    weightKg = 60.0 + index * 2.5,
                    reps = 10,
                    atMs = at,
                    sessionId = "s$index",
                ),
            )
        }
        assertNull(engine.plateauInsight("barbell-bench-press", sessions, NOW))
    }

    @Test
    fun `plateau guidance needs enough history to be meaningful`() {
        val sessions = history("barbell-bench-press", sessions = 3, weightKg = 60.0, reps = 10)
        assertNull(engine.plateauInsight("barbell-bench-press", sessions, NOW))
    }

    @Test
    fun `a whole plan deload is recommended only after a long flat stretch`() {
        val exercises = listOf("barbell-bench-press", "goblet-squat", "barbell-row")
        val sessions = exercises.flatMap { exerciseId ->
            history(exerciseId, sessions = 8, weightKg = 50.0, reps = 10)
        }.sortedBy { it.completedAtMs }

        val insight = engine.deloadInsight(sessions, exercises, NOW)
        assertNotNull(insight)
        assertTrue(insight!!.isDeloadRecommended)
        assertNull(insight.exerciseId)
        assertTrue(insight.detail.contains("3 of 3"))
    }

    @Test
    fun `a deload is not recommended when only one lift has stalled`() {
        val improving = (0 until 6).map { index ->
            val at = NOW - (5 - index) * 7 * DAY_MS
            Fixtures.session(
                id = "g$index",
                atMs = at,
                sets = Fixtures.sets(
                    exerciseId = "goblet-squat",
                    count = 3,
                    weightKg = 20.0 + index * 2.5,
                    reps = 10,
                    atMs = at,
                    sessionId = "g$index",
                ),
            )
        }
        val flatBench = history("barbell-bench-press", sessions = 8, weightKg = 50.0, reps = 10)
        val flatRow = history("barbell-row", sessions = 8, weightKg = 50.0, reps = 5)

        val sessions = (improving + flatBench + flatRow + emptyList()).sortedBy { it.completedAtMs }
        val insight = engine.deloadInsight(sessions, listOf("goblet-squat", "barbell-bench-press", "barbell-row"), NOW)

        // Two of three tracked lifts have stalled, so guidance appears, but the
        // improving lift keeps the recommendation conservative.
        assertNotNull(insight)
        assertTrue(insight!!.detail.contains("2 of 3"))
    }
}
