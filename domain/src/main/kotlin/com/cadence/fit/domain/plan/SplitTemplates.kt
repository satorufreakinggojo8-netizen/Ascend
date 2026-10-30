package com.cadence.fit.domain.plan

import com.cadence.fit.domain.core.IdGenerator
import com.cadence.fit.domain.model.MovementPattern
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.schedule.SchedulePlanner
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One day in a weekly split.
 *
 * [isRest] keeps the day in the seven-day skeleton even when it is not trained, so
 * the calendar can show an intentional rest day instead of a gap. The underlying
 * [template] is retained while resting, which lets the user switch the day back on
 * without losing its session shape.
 */
data class SplitDay(
    val dayOfWeek: DayOfWeek,
    val title: String,
    val focus: String,
    val template: SessionTemplate,
    val isRest: Boolean = false,
    val restNote: String = "Recovery",
) {
    val displayTitle: String get() = if (isRest) "Rest" else title

    val displayFocus: String get() = if (isRest) restNote else focus

    fun asRest(): SplitDay = if (isRest) this else copy(isRest = true)

    fun asTraining(): SplitDay =
        if (isRest) copy(isRest = false, title = template.name, focus = template.focus) else this

    fun renamed(newTitle: String): SplitDay =
        copy(title = newTitle.trim().ifBlank { title }, isRest = false)
}

/**
 * A selectable weekly training split.
 *
 * [days] always contains exactly seven entries, Monday to Sunday, so the preview
 * and the calendar can be generated from one ordered skeleton. Applying a split
 * only proposes routines and plan days; it never writes weights or history.
 */
data class SplitTemplate(
    val id: String,
    val name: String,
    val tagline: String,
    val summary: String,
    val days: List<SplitDay>,
    val featured: Boolean = false,
) {
    val trainingDays: List<SplitDay> get() = days.filterNot { it.isRest }

    val restDays: List<SplitDay> get() = days.filter { it.isRest }

    val daysPerWeek: Int get() = trainingDays.size

    fun day(dayOfWeek: DayOfWeek): SplitDay? = days.firstOrNull { it.dayOfWeek == dayOfWeek }

    /** Turns one weekday into a rest day or back into its session. */
    fun withRest(dayOfWeek: DayOfWeek, rest: Boolean): SplitTemplate = copy(
        days = days.map { day ->
            when {
                day.dayOfWeek != dayOfWeek -> day
                rest -> day.asRest()
                else -> day.asTraining()
            }
        },
    )

    /** Renames one training day. A renamed day stops being a rest day. */
    fun renamed(dayOfWeek: DayOfWeek, title: String): SplitTemplate = copy(
        days = days.map { day -> if (day.dayOfWeek == dayOfWeek) day.renamed(title) else day },
    )
}

/** Routines and calendar days produced by applying a split. */
data class SplitApplication(
    val template: SplitTemplate,
    val routines: List<Routine>,
    val schedule: List<ScheduledWorkout>,
    val plannedSlots: List<ScheduledWorkout>,
)

/**
 * The built-in split library.
 *
 * The featured plan is the exact six-day split that was requested: shoulders and
 * arms on Monday and Thursday, legs on Tuesday and Friday, chest and back on
 * Wednesday and Saturday, and rest on Sunday. Chest is never placed on Monday.
 */
object SplitTemplates {

    private fun slot(
        label: String,
        patterns: Set<MovementPattern>,
        muscles: Set<MuscleGroup> = emptySet(),
        main: Boolean = false,
    ) = MovementSlot(label = label, patterns = patterns, muscles = muscles, main = main)

    private val shouldersAndArms = SessionTemplate(
        name = "Shoulders & arms",
        focus = "Shoulders, biceps, triceps",
        slots = listOf(
            slot("Press", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
            slot("Rear delts", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.SHOULDERS)),
            slot("Biceps", setOf(MovementPattern.CURL)),
            slot("Triceps", setOf(MovementPattern.EXTENSION)),
            slot("Biceps", setOf(MovementPattern.CURL)),
            slot("Triceps", setOf(MovementPattern.EXTENSION)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
        ),
    )

    private val legs = SessionTemplate(
        name = "Legs",
        focus = "Quads, hamstrings, glutes",
        slots = listOf(
            slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
            slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES), main = true),
            slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)),
            slot("Calves", setOf(MovementPattern.CALF)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
            slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
        ),
    )

    private val chestAndBack = SessionTemplate(
        name = "Chest & back",
        focus = "Chest, back, rear delts",
        slots = listOf(
            slot("Chest", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
            slot("Row", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS), main = true),
            slot("Lat pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK)),
            slot("Chest", setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.CHEST)),
            slot("Rear delts", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.SHOULDERS)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
        ),
    )

    private val upperA = SessionTemplate(
        name = "Upper A",
        focus = "Chest, back, shoulders",
        slots = listOf(
            slot("Chest", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
            slot("Row", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS), main = true),
            slot("Press", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
            slot("Lat pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK)),
            slot("Biceps", setOf(MovementPattern.CURL)),
            slot("Triceps", setOf(MovementPattern.EXTENSION)),
        ),
    )

    private val upperB = SessionTemplate(
        name = "Upper B",
        focus = "Shoulders, back, chest",
        slots = listOf(
            slot("Press", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
            slot("Lat pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
            slot("Chest", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
            slot("Row", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS)),
            slot("Triceps", setOf(MovementPattern.EXTENSION)),
            slot("Biceps", setOf(MovementPattern.CURL)),
        ),
    )

    private val lowerA = SessionTemplate(
        name = "Lower A",
        focus = "Quads, hamstrings, glutes",
        slots = listOf(
            slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
            slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES), main = true),
            slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)),
            slot("Calves", setOf(MovementPattern.CALF)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
        ),
    )

    private val lowerB = SessionTemplate(
        name = "Lower B",
        focus = "Glutes, hamstrings, quads",
        slots = listOf(
            slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS), main = true),
            slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
            slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.GLUTES, MuscleGroup.QUADS)),
            slot("Calves", setOf(MovementPattern.CALF)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
        ),
    )

    private val push = SessionTemplate(
        name = "Push",
        focus = "Chest, shoulders, triceps",
        slots = listOf(
            slot("Chest", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
            slot("Shoulders", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
            slot("Chest", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST)),
            slot("Triceps", setOf(MovementPattern.EXTENSION)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
        ),
    )

    private val pull = SessionTemplate(
        name = "Pull",
        focus = "Back, biceps, rear delts",
        slots = listOf(
            slot("Lat pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
            slot("Row", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS), main = true),
            slot("Biceps", setOf(MovementPattern.CURL)),
            slot("Rear delts", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.SHOULDERS)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
        ),
    )

    private val fullBodyA = SessionTemplate(
        name = "Full body A",
        focus = "Full body",
        slots = listOf(
            slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
            slot("Push", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
            slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
            slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
            slot("Arms", setOf(MovementPattern.CURL, MovementPattern.EXTENSION)),
        ),
    )

    private val fullBodyB = SessionTemplate(
        name = "Full body B",
        focus = "Full body",
        slots = listOf(
            slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES), main = true),
            slot("Press", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
            slot("Lat pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
            slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
            slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
        ),
    )

    private val fullBodyC = SessionTemplate(
        name = "Full body C",
        focus = "Full body",
        slots = listOf(
            slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
            slot("Push", setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS), main = true),
            slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
            slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
            slot("Arms", setOf(MovementPattern.CURL, MovementPattern.EXTENSION)),
        ),
    )

    private val homePush = SessionTemplate(
        name = "Home push & core",
        focus = "Chest, shoulders, triceps",
        slots = listOf(
            slot("Push", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
            slot("Press", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
            slot("Triceps", setOf(MovementPattern.EXTENSION)),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
            slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
        ),
    )

    private val homePull = SessionTemplate(
        name = "Home pull & legs",
        focus = "Back, biceps, legs",
        slots = listOf(
            slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS), main = true),
            slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
            slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)),
            slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)),
            slot("Biceps", setOf(MovementPattern.CURL)),
        ),
    )

    private val homeFull = SessionTemplate(
        name = "Home full body",
        focus = "Full body",
        slots = listOf(
            slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
            slot("Push", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
            slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS), main = true),
            slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
            slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
        ),
    )

    private fun day(
        dayOfWeek: DayOfWeek,
        title: String,
        template: SessionTemplate,
        isRest: Boolean = false,
    ) = SplitDay(
        dayOfWeek = dayOfWeek,
        title = title,
        focus = template.focus,
        template = template,
        isRest = isRest,
    )

    /**
     * The requested personal split, featured in the picker.
     *
     * Six training days, one rest day, chest never on Monday.
     */
    val personalSixDay: SplitTemplate = SplitTemplate(
        id = "personal-six-day",
        name = "Personal 6-day",
        tagline = "Shoulders & arms · legs · chest & back, twice",
        summary = "Six training days with Sunday off. Shoulders and arms open the week, " +
            "and chest work is placed on Wednesday and Saturday so Monday stays upper-arm focused.",
        featured = true,
        days = listOf(
            day(DayOfWeek.MONDAY, "Shoulders & arms", shouldersAndArms),
            day(DayOfWeek.TUESDAY, "Legs", legs),
            day(DayOfWeek.WEDNESDAY, "Chest & back", chestAndBack),
            day(DayOfWeek.THURSDAY, "Shoulders & arms", shouldersAndArms),
            day(DayOfWeek.FRIDAY, "Legs", legs),
            day(DayOfWeek.SATURDAY, "Chest & back", chestAndBack),
            day(DayOfWeek.SUNDAY, "Rest", fullBodyA, isRest = true),
        ),
    )

    val upperLowerFour: SplitTemplate = SplitTemplate(
        id = "upper-lower-four",
        name = "Upper / lower · 4 days",
        tagline = "Two upper and two lower sessions",
        summary = "A balanced four-day structure with Wednesday, Saturday and Sunday off. " +
            "Good when you can train four times a week and want each muscle group twice.",
        days = listOf(
            day(DayOfWeek.MONDAY, "Upper A", upperA),
            day(DayOfWeek.TUESDAY, "Lower A", lowerA),
            day(DayOfWeek.WEDNESDAY, "Rest", fullBodyB, isRest = true),
            day(DayOfWeek.THURSDAY, "Upper B", upperB),
            day(DayOfWeek.FRIDAY, "Lower B", lowerB),
            day(DayOfWeek.SATURDAY, "Rest", fullBodyC, isRest = true),
            day(DayOfWeek.SUNDAY, "Rest", fullBodyA, isRest = true),
        ),
    )

    val pushPullLegsThree: SplitTemplate = SplitTemplate(
        id = "push-pull-legs-three",
        name = "Push / pull / legs · 3 days",
        tagline = "Three focused sessions with rest between",
        summary = "Push on Monday, pull on Wednesday and legs on Friday. The space between " +
            "sessions keeps the week sustainable when three days is your realistic limit.",
        days = listOf(
            day(DayOfWeek.MONDAY, "Push", push),
            day(DayOfWeek.TUESDAY, "Rest", fullBodyA, isRest = true),
            day(DayOfWeek.WEDNESDAY, "Pull", pull),
            day(DayOfWeek.THURSDAY, "Rest", fullBodyB, isRest = true),
            day(DayOfWeek.FRIDAY, "Legs", legs),
            day(DayOfWeek.SATURDAY, "Rest", fullBodyC, isRest = true),
            day(DayOfWeek.SUNDAY, "Rest", homeFull, isRest = true),
        ),
    )

    val fullBodyThree: SplitTemplate = SplitTemplate(
        id = "full-body-three",
        name = "Full body · 3 days",
        tagline = "Every muscle group, three times a week",
        summary = "Full-body sessions on Monday, Wednesday and Friday. The simplest structure " +
            "to keep up, and the easiest to recover from while you build the habit.",
        days = listOf(
            day(DayOfWeek.MONDAY, "Full body A", fullBodyA),
            day(DayOfWeek.TUESDAY, "Rest", legs, isRest = true),
            day(DayOfWeek.WEDNESDAY, "Full body B", fullBodyB),
            day(DayOfWeek.THURSDAY, "Rest", upperA, isRest = true),
            day(DayOfWeek.FRIDAY, "Full body C", fullBodyC),
            day(DayOfWeek.SATURDAY, "Rest", upperB, isRest = true),
            day(DayOfWeek.SUNDAY, "Rest", lowerA, isRest = true),
        ),
    )

    val homeThreeDay: SplitTemplate = SplitTemplate(
        id = "home-three-day",
        name = "Home · 3 days",
        tagline = "Bodyweight and bands, no gym needed",
        summary = "A home split that stays useful with limited kit. The builder fills each " +
            "session from the equipment you selected, so missing kit shortens a session " +
            "instead of inventing a movement.",
        days = listOf(
            day(DayOfWeek.MONDAY, "Home push & core", homePush),
            day(DayOfWeek.TUESDAY, "Rest", fullBodyA, isRest = true),
            day(DayOfWeek.WEDNESDAY, "Home pull & legs", homePull),
            day(DayOfWeek.THURSDAY, "Rest", fullBodyB, isRest = true),
            day(DayOfWeek.FRIDAY, "Home full body", homeFull),
            day(DayOfWeek.SATURDAY, "Rest", fullBodyC, isRest = true),
            day(DayOfWeek.SUNDAY, "Rest", lowerA, isRest = true),
        ),
    )

    /** Featured first, then the rest in increasing commitment. */
    val all: List<SplitTemplate> = listOf(
        personalSixDay,
        fullBodyThree,
        upperLowerFour,
        pushPullLegsThree,
        homeThreeDay,
    )

    fun byId(id: String?): SplitTemplate? = all.firstOrNull { it.id == id }
}

/**
 * Turns an edited split into routines and calendar days.
 *
 * Routine ids are new; an existing routine is never mutated. Planned days that
 * belong to the applied weeks are replaced so the calendar matches the split,
 * while completed and skipped days are preserved as history and decisions.
 */
object SplitApplier {

    fun apply(
        template: SplitTemplate,
        profile: TrainingProfile,
        builder: PlanBuilder,
        ids: IdGenerator,
        nowMs: Long,
        weekStart: LocalDate,
        existingSchedule: List<ScheduledWorkout>,
        weeks: Int = 2,
        notBefore: LocalDate? = null,
    ): SplitApplication {
        val trainingDays = template.trainingDays
        val routines = trainingDays.mapIndexed { index, day ->
            builder.buildRoutine(
                template = day.template,
                profile = profile,
                nowMs = nowMs,
                variantOffset = index,
                name = day.title,
                focus = day.focus,
            )
        }

        val firstWeek = SchedulePlanner.weekStart(weekStart)
        val plannedWeeks = (0 until weeks.coerceAtLeast(1)).map { offset ->
            firstWeek.plusWeeks(offset.toLong())
        }
        val plannedRanges = plannedWeeks.map { it..it.plusDays(6) }

        // Only plan days inside the applied weeks are replaced. Everything outside
        // them, and every completed/skipped day, is kept exactly as it was.
        val kept = existingSchedule.filterNot { slot ->
            slot.status == ScheduleStatus.PLANNED &&
                plannedRanges.any { range -> !slot.date.isBefore(range.start) && !slot.date.isAfter(range.endInclusive) }
        }

        // A completed or skipped day is already a decision about that date. Keeping it
        // and adding a second planned slot on the same day would show the day twice.
        val settledDates = kept
            .filter { it.status != ScheduleStatus.PLANNED }
            .map { it.date }
            .toSet()

        val added = mutableListOf<ScheduledWorkout>()
        for (week in plannedWeeks) {
            trainingDays.forEachIndexed { index, day ->
                val date = week.plusDays((day.dayOfWeek.value - 1).toLong())
                val alreadyDone = date in settledDates
                val tooEarly = notBefore != null && date.isBefore(notBefore)
                if (!alreadyDone && !tooEarly) {
                    added += ScheduledWorkout(
                        id = ids.next(),
                        date = date,
                        routineId = routines[index].id,
                        routineName = day.title,
                        status = ScheduleStatus.PLANNED,
                    )
                }
            }
        }

        val schedule = (kept + added).sortedWith(compareBy({ it.date }, { it.id }))
        return SplitApplication(
            template = template,
            routines = routines,
            schedule = schedule,
            plannedSlots = added.sortedWith(compareBy({ it.date }, { it.id })),
        )
    }

    /**
     * Fills one future week from the split already attached to the profile.
     *
     * Routines are matched by the day title used when the split was applied, so a
     * renamed routine keeps its place. Days that already have a slot are left
     * alone, and a split day with no matching routine is skipped instead of
     * guessing at a session.
     */
    fun ensureWeek(
        template: SplitTemplate,
        routines: List<Routine>,
        weekStart: LocalDate,
        existing: List<ScheduledWorkout>,
        ids: IdGenerator,
        notBefore: LocalDate? = null,
    ): List<ScheduledWorkout> {
        val firstWeek = SchedulePlanner.weekStart(weekStart)
        val weekEnd = firstWeek.plusDays(6)
        val occupied = existing.filter { !it.date.isBefore(firstWeek) && !it.date.isAfter(weekEnd) }
            .map { it.date }
            .toSet()
        val additions = mutableListOf<ScheduledWorkout>()

        template.trainingDays.forEach { day ->
            val date = firstWeek.plusDays((day.dayOfWeek.value - 1).toLong())
            val routine = routines.firstOrNull { it.name.equals(day.title, ignoreCase = true) }
            val tooEarly = notBefore != null && date.isBefore(notBefore)
            if (routine != null && date !in occupied && !tooEarly) {
                additions += ScheduledWorkout(
                    id = ids.next(),
                    date = date,
                    routineId = routine.id,
                    routineName = day.title,
                    status = ScheduleStatus.PLANNED,
                )
            }
        }

        return (existing + additions).sortedWith(compareBy({ it.date }, { it.id }))
    }
}
