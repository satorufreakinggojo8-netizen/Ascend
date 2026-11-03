package com.cadence.fit.domain.rank

import com.cadence.fit.domain.library.ExerciseLibrary
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthRankerTest {

    private val now = 1_700_000_000_000L

    private fun session(vararg lifts: Pair<String, Double>): WorkoutSession = WorkoutSession(
        id = "session-rank",
        routineId = null,
        routineName = "Rank test",
        startedAtMs = now,
        endedAtMs = now + 3_600_000L,
        sets = lifts.mapIndexed { index, (exerciseId, weightKg) ->
            LoggedSet(
                id = "set-$index",
                sessionId = "session-rank",
                exerciseId = exerciseId,
                setIndex = index + 1,
                weightKg = weightKg,
                reps = 1,
                completedAtMs = now + index,
            )
        },
    )

    @Test
    fun `rank averages the four barbell anchors against bodyweight`() {
        val assessment = StrengthRanker.assess(
            listOf(
                session(
                    "barbell-back-squat" to 160.0,
                    "barbell-bench-press" to 110.0,
                    "barbell-deadlift" to 210.0,
                    "barbell-overhead-press" to 65.0,
                ),
            ),
            ExerciseLibrary,
            bodyweightKg = 80.0,
        )

        assertEquals(StrengthRank.PRO, assessment.rank)
        assertEquals(4, assessment.lifts.size)
        assertEquals(0.25, assessment.progressToNext.toDouble(), 1e-6)
        assertEquals(
            2.0,
            assessment.lifts.first { it.anchor == RankAnchor.SQUAT }.ratio!!,
            1e-6,
        )
        assertEquals(
            StrengthRank.CHAMPION,
            assessment.lifts.first { it.anchor == RankAnchor.HINGE }.rank,
        )
        assertTrue(assessment.missingAnchors.isEmpty())
    }

    @Test
    fun `bodyweight is optional and lifts are still listed without a rank`() {
        val assessment = StrengthRanker.assess(
            listOf(session("barbell-bench-press" to 100.0)),
            ExerciseLibrary,
            bodyweightKg = null,
        )

        assertNull(assessment.rank)
        assertNull(assessment.lifts.single().ratio)
        assertEquals(RankAnchor.BENCH, assessment.lifts.single().anchor)
        assertEquals(3, assessment.missingAnchors.size)
    }

    @Test
    fun `machine work does not stand in for a barbell anchor`() {
        val assessment = StrengthRanker.assess(
            listOf(
                session(
                    "leg-press" to 300.0,
                    "machine-chest-press" to 120.0,
                    "barbell-bench-press" to 80.0,
                ),
            ),
            ExerciseLibrary,
            bodyweightKg = 80.0,
        )

        assertEquals(1, assessment.lifts.size)
        assertEquals(RankAnchor.BENCH, assessment.lifts.single().anchor)
        assertEquals(1.0, assessment.lifts.single().ratio!!, 1e-6)
    }
}