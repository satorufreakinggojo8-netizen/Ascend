package com.cadence.fit.domain.math

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.library.ExerciseLibrary
import com.cadence.fit.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLoggingTest {

    @Test
    fun `a set records its volume as weight times reps`() {
        val set = Fixtures.set("barbell-bench-press", weightKg = 60.0, reps = 10)
        assertEquals(600.0, set.volumeKg, 1e-9)
    }

    @Test
    fun `estimated one rep max follows the standard formula`() {
        assertEquals(80.0, Fixtures.set("x", 60.0, 10).estimatedOneRepMaxKg, 1e-9)
        assertEquals(100.0, Fixtures.set("x", 100.0, 1).estimatedOneRepMaxKg, 1e-9)
        assertEquals(116.6667, Fixtures.set("x", 100.0, 5).estimatedOneRepMaxKg, 1e-4)
    }

    @Test
    fun `estimated one rep max is zero without weight or reps`() {
        assertEquals(0.0, Fixtures.set("x", 0.0, 10).estimatedOneRepMaxKg, 1e-9)
        assertEquals(0.0, Fixtures.set("x", 60.0, 0).estimatedOneRepMaxKg, 1e-9)
    }

    @Test
    fun `session volume counts working sets only`() {
        val warmup = Fixtures.sets("barbell-bench-press", 1, 40.0, 10, startIndex = 1, isWarmup = true)
        val working = Fixtures.sets("barbell-bench-press", 3, 60.0, 10, startIndex = 2)
        val session = Fixtures.session(sets = warmup + working)

        assertEquals(1800.0, session.totalVolumeKg, 1e-9)
        assertEquals(3, session.workingSets.size)
        assertEquals(1, session.warmupSets.size)
        assertEquals(30, session.totalReps)
    }

    @Test
    fun `an unfinished session is not marked complete and has no duration`() {
        val session = Fixtures.session(sets = Fixtures.sets("plank", 1, 0.0, 45), ended = false)
        assertFalse(session.isComplete)
        assertNull(session.durationMinutes)
    }

    @Test
    fun `completed timestamp falls back to the last logged set`() {
        val startedAt = NOW - 30L * 60L * 1000L
        val session = Fixtures.session(
            atMs = NOW,
            sets = Fixtures.sets("plank", 1, 0.0, 45, atMs = startedAt),
            ended = false,
        ).copy(startedAtMs = startedAt)
        assertEquals(startedAt, session.completedAtMs)
    }

    @Test
    fun `session reports distinct exercises in logging order`() {
        val session = Fixtures.session(
            sets = Fixtures.sets("goblet-squat", 3, 20.0, 10, startIndex = 1) +
                Fixtures.sets("push-up", 3, 0.0, 12, startIndex = 4),
        )
        assertEquals(listOf("goblet-squat", "push-up"), session.exerciseIds)
    }

    @Test
    fun `best set prefers the highest estimated one rep max`() {
        val heavyFewReps = Fixtures.set("barbell-bench-press", 80.0, 3, setIndex = 1)
        val lighterMoreReps = Fixtures.set("barbell-bench-press", 65.0, 12, setIndex = 2)
        val session = Fixtures.session(sets = listOf(heavyFewReps, lighterMoreReps))

        val best = session.bestSetFor("barbell-bench-press")
        assertEquals(65.0, best!!.weightKg, 1e-9)
    }

    @Test
    fun `muscle volume credits primary muscles fully and secondary muscles at half`() {
        val session = Fixtures.session(sets = Fixtures.sets("barbell-bench-press", 1, 60.0, 10))
        val volume = WorkoutMath.volumeByMuscle(listOf(session), ExerciseLibrary)

        assertEquals(600.0, volume[MuscleGroup.CHEST]!!, 1e-9)
        assertEquals(150.0, volume[MuscleGroup.SHOULDERS]!!, 1e-9)
        assertEquals(150.0, volume[MuscleGroup.TRICEPS]!!, 1e-9)
        assertNull(volume[MuscleGroup.QUADS])
    }

    @Test
    fun `muscle volume spreads evenly when an exercise has several primary muscles`() {
        val session = Fixtures.session(sets = Fixtures.sets("barbell-row", 1, 60.0, 10))
        val volume = WorkoutMath.volumeByMuscle(listOf(session), ExerciseLibrary)

        assertEquals(300.0, volume[MuscleGroup.LATS]!!, 1e-9)
        assertEquals(300.0, volume[MuscleGroup.UPPER_BACK]!!, 1e-9)
        // Three secondary muscles: 600 * 0.5 / 3 = 100 each.
        assertEquals(100.0, volume[MuscleGroup.BICEPS]!!, 1e-9)
    }

    @Test
    fun `muscle volume ignores warmup sets unless asked`() {
        val session = Fixtures.session(
            sets = Fixtures.sets("goblet-squat", 1, 10.0, 10, startIndex = 1, isWarmup = true) +
                Fixtures.sets("goblet-squat", 1, 20.0, 10, startIndex = 2),
        )
        val working = WorkoutMath.volumeByMuscle(listOf(session), ExerciseLibrary)
        val all = WorkoutMath.volumeByMuscle(listOf(session), ExerciseLibrary, includeWarmups = true)

        // A goblet squat credits two primary muscles, so each receives half.
        assertEquals(100.0, working[MuscleGroup.QUADS]!!, 1e-9)
        assertEquals(100.0, working[MuscleGroup.GLUTES]!!, 1e-9)
        assertEquals(150.0, all[MuscleGroup.QUADS]!!, 1e-9)
    }

    @Test
    fun `muscle volume ignores unfinished sessions and unknown exercises`() {
        val unfinished = Fixtures.session(
            sets = Fixtures.sets("goblet-squat", 3, 20.0, 10),
            ended = false,
        )
        val unknown = Fixtures.session(id = "s2", sets = Fixtures.sets("not-a-real-exercise", 3, 20.0, 10))

        val volume = WorkoutMath.volumeByMuscle(listOf(unfinished, unknown), ExerciseLibrary)
        assertTrue(volume.isEmpty())
    }

    @Test
    fun `session counts per muscle group count a session once per muscle`() {
        val session = Fixtures.session(
            sets = Fixtures.sets("goblet-squat", 3, 20.0, 10, startIndex = 1) +
                Fixtures.sets("bodyweight-squat", 3, 0.0, 15, startIndex = 4),
        )
        val counts = WorkoutMath.sessionCountByMuscle(listOf(session), ExerciseLibrary)
        assertEquals(1, counts[MuscleGroup.QUADS])
        assertEquals(1, counts[MuscleGroup.GLUTES])
    }

    @Test
    fun `exercise history is oldest first and aggregates each session`() {
        val week1 = Fixtures.session(
            id = "w1",
            atMs = NOW - 7 * DAY_MS,
            sets = Fixtures.sets("barbell-bench-press", 3, 60.0, 10, atMs = NOW - 7 * DAY_MS, sessionId = "w1"),
        )
        val week2 = Fixtures.session(
            id = "w2",
            atMs = NOW,
            sets = Fixtures.sets("barbell-bench-press", 3, 65.0, 8, atMs = NOW, sessionId = "w2"),
        )

        val history = WorkoutMath.exerciseHistory(listOf(week2, week1), "barbell-bench-press")
        assertEquals(listOf("w1", "w2"), history.map { it.sessionId })
        assertEquals(1800.0, history[0].totalVolumeKg, 1e-9)
        assertEquals(65.0, history[1].topWeightKg, 1e-9)
        assertEquals(24, history[1].totalReps)
        assertTrue(WorkoutMath.isImprovement(history[0], history[1]))
    }

    @Test
    fun `exercise history skips sessions that did not train the exercise`() {
        val session = Fixtures.session(sets = Fixtures.sets("goblet-squat", 3, 20.0, 10))
        assertTrue(WorkoutMath.exerciseHistory(listOf(session), "barbell-bench-press").isEmpty())
    }

    @Test
    fun `personal records pick the right set for each record type`() {
        val week1 = Fixtures.session(
            id = "w1",
            atMs = NOW - 7 * DAY_MS,
            sets = Fixtures.sets("barbell-bench-press", 3, 60.0, 10, atMs = NOW - 7 * DAY_MS, sessionId = "w1"),
        )
        val week2 = Fixtures.session(
            id = "w2",
            atMs = NOW,
            sets = Fixtures.sets("barbell-bench-press", 3, 65.0, 8, atMs = NOW, sessionId = "w2"),
        )

        val records = WorkoutMath.personalRecords(listOf(week1, week2), "barbell-bench-press")
            .associateBy { it.type }

        assertEquals(65.0, records.getValue(PersonalRecordType.HEAVIEST_WEIGHT).value, 1e-9)
        assertEquals(65.0 * (1 + 8 / 30.0), records.getValue(PersonalRecordType.BEST_ESTIMATED_1RM).value, 1e-4)
        assertEquals(10.0, records.getValue(PersonalRecordType.MOST_REPS).value, 1e-9)
        assertEquals(1800.0, records.getValue(PersonalRecordType.MOST_VOLUME).value, 1e-9)
    }

    @Test
    fun `personal records are empty for an untrained exercise`() {
        assertTrue(WorkoutMath.personalRecords(emptyList(), "barbell-bench-press").isEmpty())
    }

    @Test
    fun `recent personal records respect the window`() {
        val old = Fixtures.session(
            id = "old",
            atMs = NOW - 100 * DAY_MS,
            sets = Fixtures.sets(
                "barbell-bench-press",
                3,
                100.0,
                5,
                atMs = NOW - 100 * DAY_MS,
                sessionId = "old",
            ),
        )
        val recent = Fixtures.session(
            id = "recent",
            atMs = NOW,
            sets = Fixtures.sets("barbell-bench-press", 3, 60.0, 10, atMs = NOW, sessionId = "recent"),
        )

        val records = WorkoutMath.recentPersonalRecords(
            listOf(old, recent),
            listOf("barbell-bench-press"),
            NOW,
            windowDays = 28,
        )
        assertTrue(records.all { it.achievedAtMs >= NOW - 28 * DAY_MS })
        assertFalse(records.isEmpty())
    }

    @Test
    fun `trained exercise ids are most recent first`() {
        val first = Fixtures.session(
            id = "a",
            atMs = NOW - 7 * DAY_MS,
            sets = Fixtures.sets("goblet-squat", 3, 20.0, 10, atMs = NOW - 7 * DAY_MS, sessionId = "a"),
        )
        val second = Fixtures.session(
            id = "b",
            atMs = NOW,
            sets = Fixtures.sets("push-up", 3, 0.0, 12, atMs = NOW, sessionId = "b"),
        )
        assertEquals(
            listOf("push-up", "goblet-squat"),
            WorkoutMath.trainedExerciseIds(listOf(first, second)),
        )
    }
}
