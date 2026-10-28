package com.cadence.fit.domain.model

/** Which drawing of the body map a muscle group belongs to. */
enum class BodySide { FRONT, BACK }

/**
 * Muscle groups the workout data is summarised by.
 *
 * [sides] drives the body map only; it carries no medical meaning. `CONDITIONING`
 * is deliberately excluded from the body map because it is not a muscle.
 */
enum class MuscleGroup(
    val displayName: String,
    val sides: List<BodySide>,
) {
    CHEST("Chest", listOf(BodySide.FRONT)),
    SHOULDERS("Shoulders", listOf(BodySide.FRONT, BodySide.BACK)),
    BICEPS("Biceps", listOf(BodySide.FRONT)),
    TRICEPS("Triceps", listOf(BodySide.BACK)),
    FOREARMS("Forearms", listOf(BodySide.FRONT, BodySide.BACK)),
    ABS("Abs", listOf(BodySide.FRONT)),
    OBLIQUES("Obliques", listOf(BodySide.FRONT)),
    TRAPS("Traps", listOf(BodySide.BACK)),
    LATS("Lats", listOf(BodySide.BACK)),
    UPPER_BACK("Upper back", listOf(BodySide.BACK)),
    LOWER_BACK("Lower back", listOf(BodySide.BACK)),
    GLUTES("Glutes", listOf(BodySide.BACK)),
    QUADS("Quads", listOf(BodySide.FRONT)),
    HAMSTRINGS("Hamstrings", listOf(BodySide.BACK)),
    CALVES("Calves", listOf(BodySide.BACK)),
    CONDITIONING("Conditioning", emptyList()),
    ;

    val appearsOnBodyMap: Boolean get() = sides.isNotEmpty()

    companion object {
        val bodyMapGroups: List<MuscleGroup> = entries.filter { it.appearsOnBodyMap }
    }
}

enum class Equipment(val displayName: String, val homeFriendly: Boolean) {
    BODYWEIGHT("Bodyweight", true),
    DUMBBELL("Dumbbell", true),
    KETTLEBELL("Kettlebell", true),
    RESISTANCE_BAND("Resistance band", true),
    MAT("Mat / floor space", true),
    PULL_UP_BAR("Pull-up bar", true),
    BENCH("Bench", false),
    BARBELL("Barbell", false),
    EZ_BAR("EZ bar", false),
    TRAP_BAR("Trap bar", false),
    MACHINE("Machine", false),
    SMITH_MACHINE("Smith machine", false),
    CABLE("Cable station", false),
    SUSPENSION("Suspension trainer", true),
    MEDICINE_BALL("Medicine ball", true),
    CARDIO_MACHINE("Cardio machine", false),
    ;

    companion object {
        /** A practical starting set of kit for training at home. */
        val homeStarterKit: Set<Equipment> = setOf(
            BODYWEIGHT,
            DUMBBELL,
            RESISTANCE_BAND,
            MAT,
        )

        val homeOptions: List<Equipment> = entries.filter { it.homeFriendly }
        val gymOptions: List<Equipment> = entries
    }
}

enum class Difficulty(val displayName: String) { BEGINNER("Beginner"), INTERMEDIATE("Intermediate"), ADVANCED("Advanced") }

/** Movement families, used for plan balance and for the exercise illustrations. */
enum class MovementPattern(val displayName: String) {
    HORIZONTAL_PUSH("Horizontal push"),
    VERTICAL_PUSH("Vertical push"),
    HORIZONTAL_PULL("Horizontal pull"),
    VERTICAL_PULL("Vertical pull"),
    SQUAT("Squat"),
    HINGE("Hinge"),
    LUNGE("Single leg"),
    CARRY("Carry"),
    CORE("Core"),
    CURL("Curl"),
    EXTENSION("Extension"),
    CALF("Calf"),
    CONDITIONING("Conditioning"),
    MOBILITY("Mobility"),
}

enum class TrainingGoal(
    val displayName: String,
    val tagline: String,
    val defaultRepRange: IntRange,
    val defaultRestSeconds: Int,
) {
    STRENGTH("Strength", "Heavier sets with longer rests", 3..5, 150),
    MUSCLE_GAIN("Muscle gain", "Moderate reps and steady volume", 8..12, 90),
    GENERAL_FITNESS("General fitness", "Balanced, sustainable training", 10..15, 60),
}

enum class TrainingLocation(val displayName: String) { GYM("Gym"), HOME("Home") }

enum class ExperienceLevel(val displayName: String, val description: String) {
    BEGINNER("Beginner", "New to structured training, or coming back after a long break"),
    INTERMEDIATE("Intermediate", "Training consistently for several months"),
    ADVANCED("Advanced", "Training consistently for a year or more"),
}

/**
 * Self-reported training tier used to explain how long a routine should be.
 *
 * This is *not* a ranking of the person: see `insight.LevelAssessor` for the
 * separate, optional progress level derived from the user's own logged activity.
 */
enum class ProgressLevel(val displayName: String, val order: Int) {
    STARTING("Getting started", 0),
    BEGINNER("Beginner", 1),
    INTERMEDIATE("Intermediate", 2),
    ADVANCED("Advanced", 3),
}
