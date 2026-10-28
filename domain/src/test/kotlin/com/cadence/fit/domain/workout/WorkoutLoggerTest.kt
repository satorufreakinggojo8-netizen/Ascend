package com.cadence.fit.domain.workout

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.units.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLoggerTest {

    private val exerciseId = "barbell-bench-press"

    private fun finishedSession(id: String, daysAgo: Int, weightKg: Double, reps: Int, setCount: Int = 3) =
        Fixtures.session(
            id = id,
            atMs = NOW - daysAgo * DAY_MS,
            sets = Fixtures.sets(
                exerciseId = exerciseId,
                count = setCount,
                weightKg = weightKg,
                reps = reps,
                atMs = NOW - daysAgo * DAY_MS,
                sessionId = id,
            ),
        )

    @Test
    fun `previous performance comes from the most recent completed session`() {
        val older = finishedSession("older", daysAgo = 14, weightKg = 55.0, reps = 10)
        val newer = finishedSession("newer", daysAgo = 7, weightKg = 60.0, reps = 10)
        val inProgress = Fixtures.session(
            id = "current",
            atMs = NOW,
            sets = Fixtures.sets(exerciseId, 1, 40.0, 10, atMs = NOW, sessionId = "current"),
            ended = false,
        )

        val previous = WorkoutLogger.previousPerformance(listOf(older, newer, inProgress), exerciseId, NOW)

        assertNotNull(previous)
        assertEquals("newer", previous!!.sessionId)
        assertEquals(60.0, previous.topWeightKg, 1e-9)
        assertEquals(listOf(10, 10, 10), previous.reps)
        assertEquals(1800.0, previous.totalVolumeKg, 1e-9)
    }

    @Test
    fun `previous performance ignores warm-up sets and unfinished sessions`() {
        val warmups = Fixtures.sets(exerciseId, 2, 100.0, 8, atMs = NOW - DAY_MS, sessionId = "s", isWarmup = true)
        val working = Fixtures.sets(
            exerciseId,
            3,
            60.0,
            10,
            atMs = NOW - DAY_MS,
            sessionId = "s",
            startIndex = 3,
        )
        val session = Fixtures.session(id = "s", atMs = NOW - DAY_MS, sets = warmups + working)

        val previous = WorkoutLogger.previousPerformance(listOf(session), exerciseId, NOW)!!

        assertEquals(60.0, previous.topWeightKg, 1e-9)
        assertEquals(3, previous.setCount)
    }

    @Test
    fun `previous performance is null without matching history`() {
        assertNull(WorkoutLogger.previousPerformance(emptyList(), exerciseId, NOW))
        val other = Fixtures.session(
            id = "s",
            atMs = NOW - DAY_MS,
            sets = Fixtures.sets("goblet-squat", 3, 20.0, 10),
        )
        assertNull(WorkoutLogger.previousPerformance(listOf(other), exerciseId, NOW))
    }

    @Test
    fun `previous performance never looks at the future`() {
        val future = finishedSession("future", daysAgo = 0, weightKg = 60.0, reps = 10)
        assertNull(
            WorkoutLogger.previousPerformance(
                listOf(future.copy(endedAtMs = NOW + DAY_MS)),
                exerciseId,
                NOW,
            ),
        )
    }

    @Test
    fun `set indexes keep increasing inside a session`() {
        var session = Fixtures.session(id = "current", sets = emptyList(), ended = false)
        assertEquals(1, WorkoutLogger.nextSetIndex(session, exerciseId))

        session = WorkoutLogger.addSet(
            session,
            Fixtures.set(exerciseId, 60.0, 10, sessionId = "current", setIndex = 1),
        )
        assertEquals(2, WorkoutLogger.nextSetIndex(session, exerciseId))
        assertEquals(1, WorkoutLogger.nextSetIndex(session, "goblet-squat"))
    }

    @Test
    fun `the first set is pre-filled from the previous session and the rep target`() {
        val previous = WorkoutLogger.previousPerformance(
            listOf(finishedSession("older", daysAgo = 7, weightKg = 60.0, reps = 10)),
            exerciseId,
            NOW,
        )

        val defaults = WorkoutLogger.entryDefaults(
            session = Fixtures.session(id = "current", sets = emptyList(), ended = false),
            exerciseId = exerciseId,
            previous = previous,
            routineExercise = Fixtures.routineExercise(exerciseId, repRange = 8..12),
        )

        assertEquals(60.0, defaults.weightKg, 1e-9)
        assertEquals(12, defaults.reps)
        assertFalse(defaults.repeatedFromCurrentSession)
    }

    @Test
    fun `later sets repeat the set that was just logged`() {
        val session = Fixtures.session(
            id = "current",
            sets = listOf(Fixtures.set(exerciseId, 62.5, 9, sessionId = "current", setIndex = 1)),
            ended = false,
        )

        val defaults = WorkoutLogger.entryDefaults(
            session = session,
            exerciseId = exerciseId,
            previous = null,
            routineExercise = Fixtures.routineExercise(exerciseId, repRange = 8..12),
        )

        assertEquals(62.5, defaults.weightKg, 1e-9)
        assertEquals(9, defaults.reps)
        assertTrue(defaults.repeatedFromCurrentSession)
    }

    @Test
    fun `adding a warm-up set after working sets drops to the ladder`() {
        val session = Fixtures.session(
            id = "current",
            sets = listOf(Fixtures.set(exerciseId, 60.0, 10, sessionId = "current", setIndex = 1)),
            ended = false,
        )

        val defaults = WorkoutLogger.entryDefaults(
            session = session,
            exerciseId = exerciseId,
            previous = null,
            routineExercise = Fixtures.routineExercise(exerciseId, repRange = 8..12),
            isWarmup = true,
        )

        // 50% of the 60 kg working weight, not the working weight itself.
        assertEquals(30.0, defaults.weightKg, 1e-9)
        assertEquals(8, defaults.reps)
        assertTrue(defaults.isWarmup)
        assertFalse(defaults.repeatedFromCurrentSession)
    }

    @Test
    fun `a second warm-up set repeats the first one`() {
        val session = Fixtures.session(
            id = "current",
            ended = false,
            sets = listOf(
                Fixtures.set(exerciseId, 30.0, 8, sessionId = "current", setIndex = 1, isWarmup = true),
                Fixtures.set(exerciseId, 60.0, 10, sessionId = "current", setIndex = 2),
            ),
        )

        val defaults = WorkoutLogger.entryDefaults(
            session = session,
            exerciseId = exerciseId,
            previous = null,
            routineExercise = Fixtures.routineExercise(exerciseId, repRange = 8..12),
            isWarmup = true,
        )

        assertEquals(30.0, defaults.weightKg, 1e-9)
        assertTrue(defaults.repeatedFromCurrentSession)
    }

    @Test
    fun `working sets ignore the warm-up weight when pre-filling`() {
        val session = Fixtures.session(
            id = "current",
            ended = false,
            sets = listOf(
                Fixtures.set(exerciseId, 100.0, 8, sessionId = "current", setIndex = 1, isWarmup = true),
            ),
        )

        val defaults = WorkoutLogger.entryDefaults(
            session = session,
            exerciseId = exerciseId,
            previous = null,
            routineExercise = Fixtures.routineExercise(exerciseId, repRange = 8..12),
            isWarmup = false,
        )

        assertEquals(0.0, defaults.weightKg, 1e-9)
        assertFalse(defaults.isWarmup)
    }

    @Test
    fun `warm-up sets climb as a percentage of the working weight`() {
        val plan = WorkoutLogger.warmupPlan(workingWeightKg = 100.0, warmupSets = 3, unit = WeightUnit.KG)

        assertEquals(3, plan.size)
        assertEquals(50.0, plan[0].weightKg, 1e-9)
        assertEquals(70.0, plan[1].weightKg, 1e-9)
        assertEquals(85.0, plan[2].weightKg, 1e-9)
        assertEquals(8, plan[0].reps)
        assertEquals(3, plan[2].reps)
        assertTrue(plan.zipWithNext().all { (a, b) -> b.weightKg > a.weightKg })
    }

    @Test
    fun `warm-up sets land on the pound grid too`() {
        val plan = WorkoutLogger.warmupPlan(workingWeightKg = 60.0, warmupSets = 1, unit = WeightUnit.LB)
        assertEquals(1, plan.size)
        // 30 kg is 66.14 lb, snapped to 66 lb and converted back.
        assertEquals(66 * 0.45359237, plan[0].weightKg, 1e-6)
    }

    @Test
    fun `warm-up plans are capped and empty without a working weight`() {
        assertEquals(WorkoutLogger.MAX_WARMUP_STEPS, WorkoutLogger.warmupPlan(100.0, 9, WeightUnit.KG).size)
        assertTrue(WorkoutLogger.warmupPlan(0.0, 3, WeightUnit.KG).isEmpty())
        assertTrue(WorkoutLogger.warmupPlan(100.0, 0, WeightUnit.KG).isEmpty())
    }

    @Test
    fun `rest follows the routine and adapts to the set that was logged`() {
        val routineExercise = Fixtures.routineExercise(exerciseId, repRange = 3..5, restSeconds = 150)

        assertEquals(180, WorkoutLogger.recommendedRestSeconds(Fixtures.set(exerciseId, 100.0, 3), routineExercise))
        assertEquals(150, WorkoutLogger.recommendedRestSeconds(Fixtures.set(exerciseId, 80.0, 8), routineExercise))
        assertEquals(135, WorkoutLogger.recommendedRestSeconds(Fixtures.set(exerciseId, 60.0, 15), routineExercise))
    }

    @Test
    fun `rest never drops below half a minute`() {
        val routineExercise = Fixtures.routineExercise(exerciseId, repRange = 12..15, restSeconds = 30)
        assertTrue(WorkoutLogger.recommendedRestSeconds(Fixtures.set(exerciseId, 20.0, 15), routineExercise) >= 30)
    }

    @Test
    fun `rest falls back when there is no routine entry`() {
        assertEquals(90, WorkoutLogger.restSecondsFor(null))
        assertEquals(45, WorkoutLogger.restSecondsFor(null, fallbackSeconds = 45))
    }

    @Test
    fun `a logged set can be edited`() {
        val set = Fixtures.set(exerciseId, 60.0, 10, sessionId = "current", setIndex = 1)
        var session = WorkoutLogger.addSet(
            Fixtures.session(id = "current", sets = emptyList(), ended = false),
            set,
        )

        session = WorkoutLogger.updateSet(session, set.id) { it.copy(weightKg = 62.5, reps = 8, rpe = 8.0) }

        val updated = session.sets.single()
        assertEquals(62.5, updated.weightKg, 1e-9)
        assertEquals(8, updated.reps)
        assertEquals(8.0, updated.rpe!!, 1e-9)
    }

    @Test
    fun `undo removes the most recent set, optionally for one exercise`() {
        val session = Fixtures.session(
            id = "current",
            ended = false,
            sets = listOf(
                Fixtures.set(exerciseId, 60.0, 10, atMs = NOW - 5_000L, sessionId = "current", setIndex = 1),
                Fixtures.set("goblet-squat", 20.0, 10, atMs = NOW - 3_000L, sessionId = "current", setIndex = 1),
                Fixtures.set(exerciseId, 60.0, 9, atMs = NOW - 1_000L, sessionId = "current", setIndex = 2),
            ),
        )

        val afterGeneralUndo = WorkoutLogger.undoLast(session)
        assertEquals(2, afterGeneralUndo.sets.size)
        assertFalse(afterGeneralUndo.sets.any { it.reps == 9 })

        val afterScopedUndo = WorkoutLogger.undoLast(session, exerciseId)
        assertEquals(2, afterScopedUndo.sets.size)
        // The scoped undo takes the newest set of that exercise only.
        assertFalse(afterScopedUndo.sets.any { it.exerciseId == exerciseId && it.setIndex == 2 })
        assertTrue(afterScopedUndo.sets.any { it.exerciseId == exerciseId && it.setIndex == 1 })
        assertTrue(afterScopedUndo.sets.any { it.exerciseId == "goblet-squat" })
    }

    @Test
    fun `undo on an empty session is a no-op`() {
        val session = Fixtures.session(id = "current", sets = emptyList(), ended = false)
        assertEquals(session, WorkoutLogger.undoLast(session))
        assertFalse(WorkoutLogger.hasLoggedAnything(session))
    }

    @Test
    fun `a free set can be deleted by id`() {
        val set = Fixtures.set(exerciseId, 60.0, 10, sessionId = "current", setIndex = 1)
        val session = WorkoutLogger.addSet(
            Fixtures.session(id = "current", sets = emptyList(), ended = false),
            set,
        )

        assertTrue(WorkoutLogger.removeSet(session, set.id).sets.isEmpty())
        assertEquals(1, WorkoutLogger.removeSet(session, "missing").sets.size)
    }

    @Test
    fun `an exercise is finished once its target sets are logged`() {
        val routineExercise = Fixtures.routineExercise(exerciseId, targetSets = 3)
        val partial = Fixtures.session(
            id = "current",
            ended = false,
            sets = Fixtures.sets(exerciseId, 2, 60.0, 10, sessionId = "current"),
        )
        val complete = Fixtures.session(
            id = "current",
            ended = false,
            sets = Fixtures.sets(exerciseId, 3, 60.0, 10, sessionId = "current"),
        )

        assertFalse(WorkoutLogger.isFinished(partial, routineExercise))
        assertTrue(WorkoutLogger.isFinished(complete, routineExercise))
        assertEquals(2, WorkoutLogger.completedSetsFor(partial, exerciseId))
    }

    @Test
    fun `warm-up sets do not count towards the target sets`() {
        val routineExercise = Fixtures.routineExercise(exerciseId, targetSets = 3)
        val session = Fixtures.session(
            id = "current",
            ended = false,
            sets = Fixtures.sets(exerciseId, 2, 40.0, 10, sessionId = "current", isWarmup = true) +
                Fixtures.sets(exerciseId, 2, 60.0, 10, sessionId = "current", startIndex = 3),
        )

        assertFalse(WorkoutLogger.isFinished(session, routineExercise))
        assertEquals(2, WorkoutLogger.completedSetsFor(session, exerciseId))
    }

    @Test
    fun `progress points at the first unfinished exercise`() {
        val routine = Fixtures.routine(
            exercises = listOf(
                Fixtures.routineExercise(exerciseId, targetSets = 3),
                Fixtures.routineExercise("goblet-squat", targetSets = 3),
            ),
        )
        val session = Fixtures.session(
            id = "current",
            ended = false,
            sets = Fixtures.sets(exerciseId, 3, 60.0, 10, sessionId = "current"),
        )

        val progress = WorkoutLogger.progress(session, routine)

        assertEquals(1, progress.exerciseIndex)
        assertEquals(2, progress.exerciseCount)
        assertEquals(3, progress.completedSets)
        assertEquals(6, progress.plannedSets)
        assertEquals(0.5f, progress.fraction, 1e-6f)
    }

    @Test
    fun `progress reports completion when everything is logged`() {
        val routine = Fixtures.routine(exercises = listOf(Fixtures.routineExercise(exerciseId, targetSets = 2)))
        val session = Fixtures.session(
            id = "current",
            ended = false,
            sets = Fixtures.sets(exerciseId, 2, 60.0, 10, sessionId = "current"),
        )

        val progress = WorkoutLogger.progress(session, routine)
        assertEquals(1, progress.exerciseIndex)
        assertEquals(1f, progress.fraction, 1e-6f)
    }
}
