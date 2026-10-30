package com.cadence.fit.domain.plan

import com.cadence.fit.domain.core.IdGenerator
import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.model.Difficulty
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.MovementPattern
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.model.PlanSummary
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.TrainingProfile

/**
 * Turns a [TrainingProfile] into a starter plan the user can edit freely.
 *
 * The builder only proposes; it never invents loads. Rep ranges, rest periods and
 * set counts come from the chosen goal and experience level, and every routine is
 * a normal editable routine once it is created.
 */
class PlanBuilder(
    private val catalog: ExerciseCatalog,
    private val ids: IdGenerator,
) {

    private typealias Slot = MovementSlot
    private typealias DayTemplate = SessionTemplate

    fun buildStarterPlan(profile: TrainingProfile, nowMs: Long): PlanSummary {
        val templates = templatesFor(profile.daysPerWeek)
        val equipment = profile.availableEquipment
        val exerciseCount = exercisesPerSession(profile)
        val usedAcrossPlan = mutableSetOf<String>()

        val routines = templates.mapIndexed { index, template ->
            buildFromTemplate(
                template = template,
                profile = profile,
                equipment = equipment,
                exerciseCount = exerciseCount,
                variantOffset = index,
                usedAcrossPlan = usedAcrossPlan,
                nowMs = nowMs,
            )
        }

        return PlanSummary(
            routines = routines,
            daysPerWeek = profile.daysPerWeek,
            explanation = explanationFor(profile),
            warnings = warningsFor(profile, routines, exerciseCount),
        )
    }

    /**
     * Builds one routine from a public [SessionTemplate].
     *
     * Split templates use this so a split's sessions get exactly the same
     * equipment filtering, experience rules and goal-based rep ranges as the
     * starter plan. [name] and [focus] override the template labels when a split
     * names a day after its place in the week.
     */
    fun buildRoutine(
        template: SessionTemplate,
        profile: TrainingProfile,
        nowMs: Long,
        variantOffset: Int = 0,
        name: String = template.name,
        focus: String = template.focus,
    ): Routine {
        val routine = buildFromTemplate(
            template = template,
            profile = profile,
            equipment = profile.availableEquipment,
            exerciseCount = exercisesPerSession(profile),
            variantOffset = variantOffset,
            usedAcrossPlan = mutableSetOf(),
            nowMs = nowMs,
        )
        return routine.copy(name = name, focus = focus)
    }

    /** An empty routine the user can fill in from the exercise library. */
    fun emptyRoutine(profile: TrainingProfile, nowMs: Long): Routine = Routine(
        id = ids.next(),
        name = "My routine",
        focus = "Custom",
        goal = profile.goal,
        location = profile.location,
        exercises = emptyList(),
        isStarterPlan = false,
        createdAtMs = nowMs,
    )

    /** Adds an exercise to a routine with goal-appropriate defaults. */
    fun addExerciseTo(routine: Routine, exercise: Exercise, profile: TrainingProfile): Routine {
        val slot = Slot(label = "custom", patterns = setOf(exercise.pattern))
        val routineExercise = describe(routineExerciseId = ids.next(), exercise = exercise, slot = slot, profile = profile)
        return routine.copy(exercises = routine.exercises + routineExercise)
    }

    private fun buildFromTemplate(
        template: DayTemplate,
        profile: TrainingProfile,
        equipment: Set<Equipment>,
        exerciseCount: Int,
        variantOffset: Int,
        usedAcrossPlan: MutableSet<String>,
        nowMs: Long,
    ): Routine {
        val chosen = mutableListOf<RoutineExercise>()
        val usedHere = mutableSetOf<String>()

        template.slots.take(exerciseCount).forEach { slot ->
            val candidate = pick(slot, equipment, profile.experience, usedHere, usedAcrossPlan, variantOffset)
                ?: return@forEach
            usedHere += candidate.id
            usedAcrossPlan += candidate.id
            chosen += describe(ids.next(), candidate, slot, profile)
        }

        return Routine(
            id = ids.next(),
            name = template.name,
            focus = template.focus,
            goal = profile.goal,
            location = profile.location,
            exercises = chosen,
            isStarterPlan = true,
            createdAtMs = nowMs,
        )
    }

    private fun pick(
        slot: Slot,
        equipment: Set<Equipment>,
        experience: ExperienceLevel,
        usedHere: Set<String>,
        usedAcrossPlan: Set<String>,
        variantOffset: Int,
    ): Exercise? {
        val candidates = catalog.all.asSequence()
            .filter { it.pattern in slot.patterns }
            .filter { slot.muscles.isEmpty() || it.primaryMuscles.any { muscle -> muscle in slot.muscles } }
            .filter { kitAvailable(it, equipment) }
            .filter { difficultyAllowed(it.difficulty, experience) }
            .sortedWith(
                compareByDescending<Exercise> { it.isCompound }
                    .thenBy { equipmentRank(it.equipment) }
                    .thenBy { it.difficulty.ordinal }
                    .thenBy { it.name },
            )
            .toList()

        if (candidates.isEmpty()) return null

        // Prefer movements that are new to this routine, then new to the whole plan.
        // Returning null is deliberate: a shorter session beats a duplicated movement.
        val rotated = candidates.drop(variantOffset % candidates.size) + candidates.take(variantOffset % candidates.size)
        return rotated.firstOrNull { it.id !in usedHere && it.id !in usedAcrossPlan }
            ?: rotated.firstOrNull { it.id !in usedHere }
    }

    private fun describe(
        routineExerciseId: String,
        exercise: Exercise,
        slot: Slot,
        profile: TrainingProfile,
    ): RoutineExercise {
        val isIsolation = exercise.pattern in ISOLATION_PATTERNS
        val sets = when {
            slot.main -> when (profile.experience) {
                ExperienceLevel.BEGINNER -> 3
                ExperienceLevel.INTERMEDIATE -> 3
                ExperienceLevel.ADVANCED -> 4
            }
            profile.experience == ExperienceLevel.BEGINNER -> 2
            else -> 3
        }
        val repRange = repRangeFor(profile.goal, exercise, isIsolation)
        val rest = when {
            slot.main -> profile.goal.defaultRestSeconds
            isIsolation -> 60
            else -> (profile.goal.defaultRestSeconds - 30).coerceAtLeast(45)
        }
        val warmups = when {
            !slot.main -> 0
            profile.goal == TrainingGoal.STRENGTH -> 2
            profile.experience == ExperienceLevel.BEGINNER -> 0
            else -> 1
        }
        return RoutineExercise(
            id = routineExerciseId,
            exerciseId = exercise.id,
            targetSets = sets,
            repRange = repRange,
            restSeconds = rest,
            warmupSets = warmups,
            note = if (isIsolation && exercise.isUnilateral) "Each side" else "",
        )
    }

    private fun repRangeFor(goal: TrainingGoal, exercise: Exercise, isIsolation: Boolean): IntRange {
        if (isIsolation) {
            return when (goal) {
                TrainingGoal.STRENGTH -> 8..12
                TrainingGoal.MUSCLE_GAIN -> 10..15
                TrainingGoal.GENERAL_FITNESS -> 12..15
            }
        }
        if (exercise.pattern == MovementPattern.CONDITIONING) return 10..20
        return goal.defaultRepRange
    }

    private fun kitAvailable(exercise: Exercise, equipment: Set<Equipment>): Boolean {
        val freeAccess = exercise.equipment == Equipment.BODYWEIGHT || exercise.equipment == Equipment.MAT
        val primary = freeAccess || exercise.equipment in equipment
        val extras = exercise.alsoRequires.all { it in equipment }
        return primary && extras
    }

    /**
     * Preference order within a movement slot. Loading a bar or dumbbell is
     * preferred when the equipment list allows it, and bodyweight work is chosen
     * when it is genuinely all that is available.
     */
    private fun equipmentRank(equipment: Equipment): Int = when (equipment) {
        Equipment.BARBELL -> 0
        Equipment.TRAP_BAR -> 1
        Equipment.EZ_BAR -> 2
        Equipment.DUMBBELL -> 3
        Equipment.KETTLEBELL -> 4
        Equipment.SMITH_MACHINE -> 5
        Equipment.MACHINE -> 6
        Equipment.CABLE -> 7
        Equipment.SUSPENSION -> 8
        Equipment.RESISTANCE_BAND -> 9
        Equipment.MEDICINE_BALL -> 10
        Equipment.PULL_UP_BAR -> 11
        Equipment.BENCH -> 12
        Equipment.BODYWEIGHT -> 13
        Equipment.MAT -> 14
        Equipment.CARDIO_MACHINE -> 15
    }

    private fun difficultyAllowed(difficulty: Difficulty, experience: ExperienceLevel): Boolean =
        when (experience) {
            ExperienceLevel.BEGINNER -> difficulty == Difficulty.BEGINNER
            ExperienceLevel.INTERMEDIATE -> difficulty != Difficulty.ADVANCED
            ExperienceLevel.ADVANCED -> true
        }

    private fun exercisesPerSession(profile: TrainingProfile): Int {
        val byTime = when {
            profile.sessionMinutes <= 25 -> 4
            profile.sessionMinutes <= 35 -> 5
            profile.sessionMinutes <= 55 -> 6
            profile.sessionMinutes <= 75 -> 7
            else -> 8
        }
        return if (profile.experience == ExperienceLevel.BEGINNER) byTime - 1 else byTime
    }

    private fun templatesFor(daysPerWeek: Int): List<DayTemplate> = when (daysPerWeek.coerceIn(2, 6)) {
        2 -> listOf(FULL_BODY_A, FULL_BODY_B)
        3 -> listOf(FULL_BODY_A, FULL_BODY_B, FULL_BODY_C)
        4 -> listOf(UPPER_A, LOWER_A, UPPER_B, LOWER_B)
        5 -> listOf(UPPER_A, LOWER_A, PUSH, PULL, LEGS)
        else -> listOf(PUSH, PULL, LEGS, PUSH, PULL, LEGS)
    }

    private fun explanationFor(profile: TrainingProfile): String {
        val structure = when (profile.daysPerWeek.coerceIn(2, 6)) {
            2, 3 -> "Each session is full body, so every muscle group is trained 2-3 times a week."
            4 -> "Two upper and two lower sessions. This is the most common four-day structure."
            5 -> "Push, pull and legs days, plus an upper and a lower session."
            else -> "Push, pull and legs run twice, which suits people who recover quickly."
        }
        val time = "Sessions are built for about ${profile.sessionMinutes} minutes at a " +
            "${profile.experience.displayName.lowercase()} level."
        val goal = when (profile.goal) {
            TrainingGoal.STRENGTH -> "Reps are kept low with longer rests so the weight can stay heavy."
            TrainingGoal.MUSCLE_GAIN -> "Reps sit in a moderate range with enough volume to grow."
            TrainingGoal.GENERAL_FITNESS -> "Reps are moderate to high so sessions stay sustainable."
        }
        val place = when (profile.location) {
            TrainingLocation.GYM -> "Exercises were chosen from the equipment you have at the gym."
            TrainingLocation.HOME -> "Exercises were chosen from the kit you have at home."
        }
        return listOf(structure, time, goal, place).joinToString(" ")
    }

    private fun warningsFor(
        profile: TrainingProfile,
        routines: List<Routine>,
        expectedExercises: Int,
    ): List<String> = buildList {
        if (profile.location == TrainingLocation.HOME && profile.equipment.isEmpty()) {
            add("No equipment selected, so only bodyweight movements are available.")
        }
        if (profile.experience == ExperienceLevel.BEGINNER && profile.daysPerWeek >= 5) {
            add("Five or more days a week is a lot to start with. Three days is easier to keep up, and you can add more later.")
        }
        if (profile.experience == ExperienceLevel.BEGINNER && profile.sessionMinutes >= 75) {
            add("Long sessions are hard to recover from early on. 45-60 minutes is a good starting point.")
        }
        if (routines.any { it.exercises.isEmpty() }) {
            add("Some sessions could not be filled from your equipment list. Add a piece of kit or edit the routine.")
        } else if (routines.any { it.exercises.size < expectedExercises }) {
            add(
                "Some sessions are shorter than your target length because your equipment list limits " +
                    "the movements available for them.",
            )
        }
        if (profile.goal == TrainingGoal.STRENGTH && profile.daysPerWeek >= 5) {
            add("Heavy strength work five or more days a week leaves little room to recover.")
        }
    }

    private companion object {
        val ISOLATION_PATTERNS = setOf(
            MovementPattern.CURL,
            MovementPattern.EXTENSION,
            MovementPattern.CALF,
            MovementPattern.CORE,
        )

        val FULL_BODY_A = DayTemplate(
            name = "Full body A",
            focus = "Full body",
            slots = listOf(
                Slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
                Slot("Push", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
                Slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
                Slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
                Slot("Arms", setOf(MovementPattern.CURL, MovementPattern.EXTENSION)),
                Slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
            ),
        )

        val FULL_BODY_B = DayTemplate(
            name = "Full body B",
            focus = "Full body",
            slots = listOf(
                Slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES), main = true),
                Slot("Push", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
                Slot("Pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
                Slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
                Slot("Arms", setOf(MovementPattern.CURL, MovementPattern.EXTENSION)),
                Slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
            ),
        )

        val FULL_BODY_C = DayTemplate(
            name = "Full body C",
            focus = "Full body",
            slots = listOf(
                Slot("Squat", setOf(MovementPattern.SQUAT, MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
                Slot("Push", setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS), main = true),
                Slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
                Slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
                Slot("Arms", setOf(MovementPattern.CURL, MovementPattern.EXTENSION)),
                Slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
            ),
        )

        val UPPER_A = DayTemplate(
            name = "Upper A",
            focus = "Upper body",
            slots = listOf(
                Slot("Push", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
                Slot("Pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
                Slot("Shoulders", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
                Slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS)),
                Slot("Triceps", setOf(MovementPattern.EXTENSION)),
                Slot("Biceps", setOf(MovementPattern.CURL)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
            ),
        )

        val UPPER_B = DayTemplate(
            name = "Upper B",
            focus = "Upper body",
            slots = listOf(
                Slot("Shoulders", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
                Slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS), main = true),
                Slot("Push", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
                Slot("Pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK)),
                Slot("Biceps", setOf(MovementPattern.CURL)),
                Slot("Triceps", setOf(MovementPattern.EXTENSION)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
            ),
        )

        val LOWER_A = DayTemplate(
            name = "Lower A",
            focus = "Lower body",
            slots = listOf(
                Slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
                Slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES), main = true),
                Slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)),
                Slot("Calves", setOf(MovementPattern.CALF)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
                Slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
            ),
        )

        val LOWER_B = DayTemplate(
            name = "Lower B",
            focus = "Lower body",
            slots = listOf(
                Slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS), main = true),
                Slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
                Slot("Calves", setOf(MovementPattern.CALF)),
                Slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.GLUTES, MuscleGroup.QUADS)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
                Slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
            ),
        )

        val PUSH = DayTemplate(
            name = "Push",
            focus = "Chest, shoulders, triceps",
            slots = listOf(
                Slot("Chest", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST), main = true),
                Slot("Shoulders", setOf(MovementPattern.VERTICAL_PUSH), setOf(MuscleGroup.SHOULDERS), main = true),
                Slot("Chest", setOf(MovementPattern.HORIZONTAL_PUSH), setOf(MuscleGroup.CHEST)),
                Slot("Triceps", setOf(MovementPattern.EXTENSION)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
                Slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
            ),
        )

        val PULL = DayTemplate(
            name = "Pull",
            focus = "Back, biceps",
            slots = listOf(
                Slot("Pull", setOf(MovementPattern.VERTICAL_PULL), setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK), main = true),
                Slot("Pull", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS), main = true),
                Slot("Biceps", setOf(MovementPattern.CURL)),
                Slot("Rear delts", setOf(MovementPattern.HORIZONTAL_PULL), setOf(MuscleGroup.SHOULDERS)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
                Slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
            ),
        )

        val LEGS = DayTemplate(
            name = "Legs",
            focus = "Quads, hamstrings, glutes",
            slots = listOf(
                Slot("Squat", setOf(MovementPattern.SQUAT), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), main = true),
                Slot("Hinge", setOf(MovementPattern.HINGE), setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES), main = true),
                Slot("Single leg", setOf(MovementPattern.LUNGE), setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)),
                Slot("Calves", setOf(MovementPattern.CALF)),
                Slot("Core", setOf(MovementPattern.CORE), setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES)),
                Slot("Conditioning", setOf(MovementPattern.CONDITIONING)),
            ),
        )
    }
}
