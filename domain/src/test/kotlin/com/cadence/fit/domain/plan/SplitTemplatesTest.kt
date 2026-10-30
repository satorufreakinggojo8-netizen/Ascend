package com.cadence.fit.domain.plan

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.core.SequentialIdGenerator
import com.cadence.fit.domain.library.ExerciseLibrary
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import com.cadence.fit.domain.model.TrainingLocation
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTemplatesTest {

    private val builder = PlanBuilder(ExerciseLibrary, SequentialIdGenerator("split"))
    private val ids = SequentialIdGenerator("slot")
    private val weekStart = LocalDate.of(2023, 11, 13) // a Monday

    private fun apply(
        template: SplitTemplate = SplitTemplates.personalSixDay,
        profile: com.cadence.fit.domain.model.TrainingProfile = Fixtures.profile(daysPerWeek = 6),
        existing: List<ScheduledWorkout> = emptyList(),
        weeks: Int = 2,
        notBefore: LocalDate? = null,
    ) = SplitApplier.apply(
        template = template,
        profile = profile,
        builder = builder,
        ids = ids,
        nowMs = NOW,
        weekStart = weekStart,
        existingSchedule = existing,
        weeks = weeks,
        notBefore = notBefore,
    )

    @Test
    fun `the featured split is six training days with Sunday off`() {
        val split = SplitTemplates.personalSixDay

        assertTrue(split.featured)
        assertEquals(6, split.daysPerWeek)
        assertEquals(listOf(DayOfWeek.SUNDAY), split.restDays.map { it.dayOfWeek })
        assertTrue(split.days.first().dayOfWeek == DayOfWeek.MONDAY)
    }

    @Test
    fun `the requested week maps each day to its focus`() {
        val split = SplitTemplates.personalSixDay
        val byDay = split.days.associate { it.dayOfWeek to it }

        assertEquals("Shoulders & arms", byDay.getValue(DayOfWeek.MONDAY).title)
        assertEquals("Legs", byDay.getValue(DayOfWeek.TUESDAY).title)
        assertEquals("Chest & back", byDay.getValue(DayOfWeek.WEDNESDAY).title)
        assertEquals("Shoulders & arms", byDay.getValue(DayOfWeek.THURSDAY).title)
        assertEquals("Legs", byDay.getValue(DayOfWeek.FRIDAY).title)
        assertEquals("Chest & back", byDay.getValue(DayOfWeek.SATURDAY).title)
        assertTrue(byDay.getValue(DayOfWeek.SUNDAY).isRest)
    }

    @Test
    fun `chest is never scheduled on Monday`() {
        val monday = SplitTemplates.personalSixDay.day(DayOfWeek.MONDAY)!!
        assertFalse(monday.title.contains("chest", ignoreCase = true))
        assertFalse(monday.focus.contains("chest", ignoreCase = true))
        assertTrue(monday.focus.contains("biceps", ignoreCase = true))
        assertTrue(monday.focus.contains("triceps", ignoreCase = true))
    }

    @Test
    fun `applying the split creates one routine for every training day`() {
        val result = apply()

        assertEquals(6, result.routines.size)
        assertEquals(6, result.routines.map { it.id }.distinct().size)
        assertEquals(
            listOf(
                "Shoulders & arms",
                "Legs",
                "Chest & back",
                "Shoulders & arms",
                "Legs",
                "Chest & back",
            ),
            result.routines.map { it.name },
        )
        assertTrue(result.routines.all { it.exercises.isNotEmpty() })
        assertTrue(result.routines.all { it.isStarterPlan })
    }

    @Test
    fun `the split schedules exact weekdays and skips Sunday`() {
        val result = apply(weeks = 1)
        val dates = result.plannedSlots.map { it.date }

        assertEquals(
            listOf(
                weekStart,
                weekStart.plusDays(1),
                weekStart.plusDays(2),
                weekStart.plusDays(3),
                weekStart.plusDays(4),
                weekStart.plusDays(5),
            ),
            dates,
        )
        assertFalse(dates.contains(weekStart.plusDays(6)))
        assertEquals(ScheduleStatus.PLANNED, result.plannedSlots.first().status)
        assertEquals("Shoulders & arms", result.plannedSlots.first().routineName)
        assertEquals("Chest & back", result.plannedSlots[2].routineName)
    }

    @Test
    fun `two weeks are planned from the same split`() {
        val result = apply(weeks = 2)
        assertEquals(12, result.plannedSlots.size)
        assertEquals(weekStart.plusWeeks(1), result.plannedSlots[6].date)
        assertEquals("Shoulders & arms", result.plannedSlots[6].routineName)
    }

    @Test
    fun `editing a day to rest removes it from the schedule`() {
        val edited = SplitTemplates.personalSixDay.withRest(DayOfWeek.MONDAY, true)
        val result = apply(template = edited, weeks = 1)

        assertEquals(5, edited.daysPerWeek)
        assertEquals(5, result.routines.size)
        assertFalse(result.plannedSlots.any { it.date == weekStart })
        assertEquals(5, result.plannedSlots.size)
    }

    @Test
    fun `a rest day can be turned back into training`() {
        val edited = SplitTemplates.personalSixDay.withRest(DayOfWeek.SUNDAY, false)
        val result = apply(template = edited, weeks = 1)

        assertEquals(7, edited.daysPerWeek)
        assertEquals(7, result.plannedSlots.size)
        assertEquals("Full body A", result.plannedSlots.last().routineName)
        assertEquals(weekStart.plusDays(6), result.plannedSlots.last().date)
    }

    @Test
    fun `renaming a day changes the routine and the calendar entry`() {
        val edited = SplitTemplates.personalSixDay.renamed(DayOfWeek.WEDNESDAY, "Upper power")
        val result = apply(template = edited, weeks = 1)

        val routine = result.routines.first { it.name == "Upper power" }
        assertNotNull(routine)
        val slot = result.plannedSlots.first { it.date == weekStart.plusDays(2) }
        assertEquals("Upper power", slot.routineName)
        assertEquals(routine.id, slot.routineId)
    }

    @Test
    fun `a blank rename keeps the original title`() {
        val edited = SplitTemplates.personalSixDay.renamed(DayOfWeek.TUESDAY, "   ")
        assertEquals("Legs", edited.day(DayOfWeek.TUESDAY)!!.title)
    }

    @Test
    fun `completed days survive applying a split`() {
        val completed = ScheduledWorkout(
            id = "done-monday",
            date = weekStart,
            routineId = "old-routine",
            routineName = "Logged shoulders",
            status = ScheduleStatus.COMPLETED,
            sessionId = "session-1",
            completedAtMs = NOW,
        )
        val result = apply(existing = listOf(completed), weeks = 1)

        val kept = result.schedule.first { it.id == "done-monday" }
        assertEquals(ScheduleStatus.COMPLETED, kept.status)
        assertEquals("session-1", kept.sessionId)
        // No second Monday slot is added on top of the completed day.
        assertEquals(1, result.schedule.count { it.date == weekStart })
        assertEquals(5, result.plannedSlots.size)
    }

    @Test
    fun `a skipped day survives applying a split without a duplicate`() {
        val skipped = ScheduledWorkout(
            id = "skipped-tuesday",
            date = weekStart.plusDays(1),
            routineId = "old-routine",
            routineName = "Old legs",
            status = ScheduleStatus.SKIPPED,
        )
        val result = apply(existing = listOf(skipped), weeks = 1)

        val kept = result.schedule.single { it.date == weekStart.plusDays(1) }
        assertEquals("skipped-tuesday", kept.id)
        assertEquals(ScheduleStatus.SKIPPED, kept.status)
        assertEquals(5, result.plannedSlots.size)
    }

    @Test
    fun `an old planned week is replaced but other weeks stay`() {
        val previousWeek = ScheduledWorkout(
            id = "previous",
            date = weekStart.minusWeeks(1),
            routineId = "old",
            routineName = "Previous plan",
        )
        val sameWeek = ScheduledWorkout(
            id = "old-sunday",
            date = weekStart.plusDays(6),
            routineId = "old",
            routineName = "Old Sunday session",
        )
        val result = apply(existing = listOf(previousWeek, sameWeek), weeks = 1)

        assertTrue(result.schedule.any { it.id == "previous" })
        assertFalse(result.schedule.any { it.id == "old-sunday" })
        assertEquals(7, result.schedule.size)
    }

    @Test
    fun `past days are not back-filled`() {
        val result = apply(weeks = 1, notBefore = weekStart.plusDays(3))
        assertEquals(
            listOf(weekStart.plusDays(3), weekStart.plusDays(4), weekStart.plusDays(5)),
            result.plannedSlots.map { it.date },
        )
    }

    @Test
    fun `an empty split plans nothing and creates no routines`() {
        var empty = SplitTemplates.fullBodyThree
        DayOfWeek.entries.forEach { day -> empty = empty.withRest(day, true) }

        val result = apply(template = empty, weeks = 2)
        assertTrue(result.routines.isEmpty())
        assertTrue(result.plannedSlots.isEmpty())
    }

    @Test
    fun `home splits only use equipment the user selected`() {
        val equipment = setOf(Equipment.BODYWEIGHT, Equipment.MAT)
        val result = apply(
            template = SplitTemplates.homeThreeDay,
            profile = Fixtures.profile(
                location = TrainingLocation.HOME,
                equipment = equipment,
                daysPerWeek = 3,
            ),
            weeks = 1,
        )

        val used = result.routines.flatMap { routine ->
            routine.exercises.mapNotNull { ExerciseLibrary.byId(it.exerciseId) }
        }
        assertTrue(used.isNotEmpty())
        assertTrue(used.all { it.equipment in equipment })
        assertTrue(used.all { it.alsoRequires.isEmpty() })
    }

    @Test
    fun `splits never carry a starting weight`() {
        // RoutineExercise has no weight field at all; this test documents the
        // invariant that applying a split cannot invent loads.
        val result = apply(weeks = 1)
        result.routines.flatMap { it.exercises }.forEach { slot ->
            assertTrue(slot.targetSets > 0)
            assertTrue(slot.repRange.first > 0)
        }
    }

    @Test
    fun `every template is a complete Monday to Sunday week`() {
        SplitTemplates.all.forEach { template ->
            assertEquals("${template.id} must cover a full week", 7, template.days.size)
            assertEquals(DayOfWeek.entries.toList(), template.days.map { it.dayOfWeek })
            assertNotNull(template.trainingDays.firstOrNull())
            assertEquals(template.daysPerWeek, template.trainingDays.size)
        }
    }

    @Test
    fun `a later week follows the same split`() {
        val first = apply(weeks = 1)
        val nextWeek = weekStart.plusWeeks(1)

        val later = SplitApplier.ensureWeek(
            template = SplitTemplates.personalSixDay,
            routines = first.routines,
            weekStart = nextWeek,
            existing = first.schedule,
            ids = ids,
        )

        val added = later.filter { it.date >= nextWeek && it.date <= nextWeek.plusDays(6) }
        assertEquals(6, added.size)
        assertEquals(nextWeek, added.first().date)
        assertEquals("Chest & back", added[2].routineName)
        assertFalse(added.any { it.date.dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test
    fun `a future week skips a day that already has a slot`() {
        val first = apply(weeks = 1)
        val nextWeek = weekStart.plusWeeks(1)
        val existing = listOf(
            ScheduledWorkout(
                id = "existing",
                date = nextWeek,
                routineId = "custom",
                routineName = "My own Monday",
            ),
        )

        val later = SplitApplier.ensureWeek(
            template = SplitTemplates.personalSixDay,
            routines = first.routines,
            weekStart = nextWeek,
            existing = existing,
            ids = ids,
        )

        assertEquals(1, later.count { it.date == nextWeek })
        assertEquals("My own Monday", later.first { it.date == nextWeek }.routineName)
        assertEquals(6, later.size)
    }

    @Test
    fun `a later week is left alone when the split's routines no longer exist`() {
        val nextWeek = weekStart.plusWeeks(1)
        val later = SplitApplier.ensureWeek(
            template = SplitTemplates.personalSixDay,
            routines = emptyList(),
            weekStart = nextWeek,
            existing = emptyList(),
            ids = ids,
        )

        assertTrue(later.isEmpty())
    }

    @Test
    fun `the featured split is first and findable by id`() {
        assertTrue(SplitTemplates.all.first().featured)
        assertEquals(SplitTemplates.personalSixDay, SplitTemplates.byId("personal-six-day"))
        assertNull(SplitTemplates.byId("missing"))
    }
}
