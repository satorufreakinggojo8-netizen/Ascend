package com.cadence.fit.domain.progression

import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.math.ExerciseSessionEntry
import com.cadence.fit.domain.math.WorkoutMath
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.domain.units.Weights
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class SuggestionKind(val displayName: String) {
    STARTING_POINT("Set a starting point"),
    INCREASE_WEIGHT("Add a little weight"),
    ADD_REPS("Add reps"),
    ADD_SET("Add a set"),
    HOLD("Repeat what worked"),
    REDUCE("Take a step back"),
    DELOAD("Lighter week"),
}

enum class Confidence { LOW, MEDIUM, HIGH }

/**
 * One explainable suggestion for a single exercise. Nothing is ever applied
 * automatically: the caller shows [headline], [detail] and [reason], and the user
 * accepts, edits or ignores it.
 */
data class ProgressionRecommendation(
    val exerciseId: String,
    val kind: SuggestionKind,
    val headline: String,
    val detail: String,
    val reason: String,
    val suggestedWeightKg: Double? = null,
    val suggestedMinReps: Int? = null,
    val suggestedMaxReps: Int? = null,
    val suggestedSets: Int? = null,
    val confidence: Confidence = Confidence.MEDIUM,
    val basedOnSessions: Int = 0,
    val currentRepRange: IntRange = IntRange.EMPTY,
    val lastWeightKg: Double? = null,
) {
    /** Weight jump as a fraction of the previous load, used to warn about big steps. */
    val relativeJump: Double?
        get() {
            val previous = lastWeightKg ?: return null
            val next = suggestedWeightKg ?: return null
            if (previous <= 0.0) return null
            return (next - previous) / previous
        }
}

/** Plateau / deload guidance for one exercise or the whole plan. */
data class PlateauInsight(
    val exerciseId: String?,
    val sessionsWithoutProgress: Int,
    val headline: String,
    val detail: String,
    val suggestions: List<String>,
    val isDeloadRecommended: Boolean,
)

/**
 * Turns previous performance into gradual, explainable suggestions.
 *
 * Design rules baked in on purpose:
 *  * weight only moves *up* after the top of the rep range is reached;
 *  * a single step is capped at ~5% of the working weight;
 *  * nothing is ever written to the plan automatically;
 *  * bodyweight movements progress by reps, then by sets.
 */
class ProgressionEngine(private val catalog: ExerciseCatalog) {

    /** Largest single jump the engine will ever propose, as a share of the load. */
    private val maxRelativeJump = 0.05

    /** Never suggest growing an exercise beyond this many working sets. */
    private val maxWorkingSets = 6

    fun recommend(
        routineExercise: RoutineExercise,
        sessions: List<WorkoutSession>,
        goal: TrainingGoal,
        unit: WeightUnit,
    ): ProgressionRecommendation {
        val exerciseId = routineExercise.exerciseId
        val exercise = catalog.byId(exerciseId)
        val repRange = routineExercise.repRange
        val history = WorkoutMath.exerciseHistory(sessions, exerciseId)
        val targetSets = routineExercise.targetSets.coerceAtLeast(1)

        if (history.isEmpty()) {
            return ProgressionRecommendation(
                exerciseId = exerciseId,
                kind = SuggestionKind.STARTING_POINT,
                headline = "Set a starting weight",
                detail = "No sets logged yet. Pick a load you could stop 2-3 reps short of failure with, " +
                    "then aim for ${repRange.first}-${repRange.last} reps across $targetSets sets.",
                reason = "The first session establishes the baseline every later suggestion compares against.",
                suggestedMinReps = repRange.first,
                suggestedMaxReps = repRange.last,
                suggestedSets = targetSets,
                confidence = Confidence.LOW,
                currentRepRange = repRange,
            )
        }

        val latest = history.last()
        val previous = history.dropLast(1).lastOrNull()
        val isBodyweight = exercise?.equipment?.let { it.name == "BODYWEIGHT" } == true
        val setsAtTopOfRange = latest.sets.count { it.reps >= repRange.last }
        val completedAllSets = latest.sets.size >= targetSets
        val hitTopOfRange = completedAllSets && setsAtTopOfRange >= targetSets
        val missedBottom = completedAllSets && latest.sets.take(targetSets).all { it.reps < repRange.first }
        val confidence = when {
            history.size >= 3 -> Confidence.HIGH
            history.size == 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        // 1. Topped out the range -> progress the load (or add work when loading is not possible).
        if (hitTopOfRange) {
            if (isBodyweight || latest.topWeightKg <= 0.0) {
                if (targetSets < maxWorkingSets && previous != null && previous.sets.size >= targetSets) {
                    return ProgressionRecommendation(
                        exerciseId = exerciseId,
                        kind = SuggestionKind.ADD_SET,
                        headline = "Add a ${targetSets + 1}th set",
                        detail = "You reached ${repRange.last} reps on all $targetSets sets. Since this movement " +
                            "has no external load, add one more set at the same rep target.",
                        reason = "For bodyweight work the way to add stimulus is more reps or more sets, not more load.",
                        suggestedSets = targetSets + 1,
                        suggestedMinReps = repRange.first,
                        suggestedMaxReps = repRange.last,
                        confidence = confidence,
                        basedOnSessions = history.size,
                        currentRepRange = repRange,
                        lastWeightKg = latest.topWeightKg,
                    )
                }
                return ProgressionRecommendation(
                    exerciseId = exerciseId,
                    kind = SuggestionKind.ADD_REPS,
                    headline = "Push the top set further",
                    detail = "All $targetSets sets hit ${repRange.last} reps. Next time, aim for " +
                        "${repRange.last + 1}-${repRange.last + 3} reps on the last set only, or move to a harder variation.",
                    reason = "Bodyweight progress comes from reps, tempo and harder leverage — not from adding plates.",
                    suggestedMinReps = repRange.last,
                    suggestedMaxReps = repRange.last + 3,
                    suggestedSets = targetSets,
                    confidence = confidence,
                    basedOnSessions = history.size,
                    currentRepRange = repRange,
                    lastWeightKg = latest.topWeightKg,
                )
            }

            val step = stepFor(latest.topWeightKg, exercise?.incrementKg ?: 2.5, unit)
            val nextWeight = latest.topWeightKg + step
            return ProgressionRecommendation(
                exerciseId = exerciseId,
                kind = SuggestionKind.INCREASE_WEIGHT,
                headline = "Next: ${Weights.formatWithUnit(nextWeight, unit)} for ${repRange.first} reps",
                detail = "Last session you completed " +
                    "${setsAtTopOfRange.coerceAtMost(latest.sets.size)} sets at ${Weights.formatWithUnit(latest.topWeightKg, unit)}, " +
                    "which is the top of your ${repRange.first}-${repRange.last} range. " +
                    "Move up to ${Weights.formatWithUnit(nextWeight, unit)} and rebuild from ${repRange.first} reps.",
                reason = "A single small step of ${Weights.formatWithUnit(step, unit)} after topping out the rep " +
                    "range is the standard way to keep progressing without a jump that changes the movement.",
                suggestedWeightKg = nextWeight,
                suggestedMinReps = repRange.first,
                suggestedMaxReps = repRange.last,
                suggestedSets = targetSets,
                confidence = confidence,
                basedOnSessions = history.size,
                currentRepRange = repRange,
                lastWeightKg = latest.topWeightKg,
            )
        }

        // 2. Missed the bottom of the range -> suggest a step back instead of pushing on.
        if (missedBottom) {
            val step = stepFor(latest.topWeightKg, exercise?.incrementKg ?: 2.5, unit)
            val nextWeight = max(latest.topWeightKg - step, 0.0)
            return ProgressionRecommendation(
                exerciseId = exerciseId,
                kind = SuggestionKind.REDUCE,
                headline = "Repeat ${Weights.formatWithUnit(nextWeight, unit)} and finish every set",
                detail = "You logged sets below ${repRange.first} reps at ${Weights.formatWithUnit(latest.topWeightKg, unit)}. " +
                    "Take ${Weights.formatWithUnit(step, unit)} off and complete all $targetSets sets in the " +
                    "${repRange.first}-${repRange.last} range before adding load again.",
                reason = "Rebuilding at a weight you can complete keeps the movement quality and lets you progress next week.",
                suggestedWeightKg = nextWeight,
                suggestedMinReps = repRange.first,
                suggestedMaxReps = repRange.last,
                suggestedSets = targetSets,
                confidence = confidence,
                basedOnSessions = history.size,
                currentRepRange = repRange,
                lastWeightKg = latest.topWeightKg,
            )
        }

        // 3. Within range -> add reps at the same load before adding weight.
        val lowestReps = latest.sets.take(targetSets).minOfOrNull { it.reps } ?: repRange.first
        val improving = previous == null || WorkoutMath.isImprovement(previous, latest)
        if (lowestReps < repRange.last) {
            val target = min(lowestReps + 1, repRange.last)
            val goalNote = when (goal) {
                TrainingGoal.STRENGTH ->
                    "With a strength goal, staying at this weight until every set reaches " +
                        "${repRange.last} reps is a reasonable next step."
                TrainingGoal.MUSCLE_GAIN ->
                    "For muscle gain, adding reps at the same weight keeps the stimulus rising with less risk."
                TrainingGoal.GENERAL_FITNESS ->
                    "Adding a rep or two at the same weight is the simplest way to keep moving forward."
            }
            return ProgressionRecommendation(
                exerciseId = exerciseId,
                kind = SuggestionKind.ADD_REPS,
                headline = "Next: ${Weights.formatWithUnit(latest.topWeightKg, unit)} for $target+ reps",
                detail = "Your lowest working set last time was $lowestReps reps at " +
                    "${Weights.formatWithUnit(latest.topWeightKg, unit)}. Aim for at least $target reps on every set.",
                reason = goalNote,
                suggestedWeightKg = latest.topWeightKg,
                suggestedMinReps = target,
                suggestedMaxReps = repRange.last,
                suggestedSets = targetSets,
                confidence = if (improving) confidence else Confidence.LOW,
                basedOnSessions = history.size,
                currentRepRange = repRange,
                lastWeightKg = latest.topWeightKg,
            )
        }

        // 4. Same performance twice -> hold, unless it is genuinely stalled.
        val stalled = previous != null && !WorkoutMath.isImprovement(previous, latest) &&
            abs(latest.bestE1rmKg - previous.bestE1rmKg) < 0.5
        return ProgressionRecommendation(
            exerciseId = exerciseId,
            kind = if (stalled) SuggestionKind.HOLD else SuggestionKind.ADD_REPS,
            headline = "Repeat ${Weights.formatWithUnit(latest.topWeightKg, unit)}, aim for clean reps",
            detail = "Last time: ${latest.sets.size} sets, top set " +
                "${Weights.formatWithUnit(latest.topWeightKg, unit)} x ${latest.sets.maxOf { it.reps }} reps. " +
                "Match or beat it before changing anything.",
            reason = if (stalled) {
                "Two similar sessions in a row usually mean it is time to repeat the weight and " +
                    "tighten technique rather than add load."
            } else {
                "Repeating a session you can complete is how a new weight becomes your baseline."
            },
            suggestedWeightKg = latest.topWeightKg,
            suggestedMinReps = repRange.first,
            suggestedMaxReps = repRange.last,
            suggestedSets = targetSets,
            confidence = confidence,
            basedOnSessions = history.size,
            currentRepRange = repRange,
            lastWeightKg = latest.topWeightKg,
        )
    }

    /** Suggestions for every exercise in a routine, in plan order. */
    fun recommendForRoutine(
        routine: Routine,
        sessions: List<WorkoutSession>,
        unit: WeightUnit,
    ): List<ProgressionRecommendation> =
        routine.exercises.map { recommend(it, sessions, routine.goal, unit) }

    /**
     * How long since this exercise last improved, based on estimated one-rep max.
     * A change smaller than 0.5% is treated as noise rather than progress.
     */
    fun plateauInsight(
        exerciseId: String,
        sessions: List<WorkoutSession>,
        nowMs: Long,
    ): PlateauInsight? {
        val history = WorkoutMath.exerciseHistory(sessions, exerciseId)
        if (history.size < 4) return null

        // Walk forward tracking the running best so a completely flat series still
        // counts as "no progress since the first session".
        var runningBest = Double.NEGATIVE_INFINITY
        var lastImprovementIndex = 0
        history.forEachIndexed { index, entry ->
            if (entry.bestE1rmKg > runningBest + 0.001) {
                runningBest = entry.bestE1rmKg
                lastImprovementIndex = index
            }
        }
        val sessionsWithoutProgress = history.size - 1 - lastImprovementIndex
        val lastImprovedAt = history[lastImprovementIndex].completedAtMs
        val weeksWithoutProgress = ((nowMs - lastImprovedAt) / (7 * 24L * 60L * 60L * 1000L)).toInt()

        if (sessionsWithoutProgress < 3 && weeksWithoutProgress < 3) return null

        val name = catalog.byId(exerciseId)?.name ?: exerciseId
        val isDeloadRecommended = sessionsWithoutProgress >= 6 || weeksWithoutProgress >= 6
        val suggestions = buildList {
            add("Change the rep range for 2-3 weeks (for example 5 reps instead of 10) and keep 2-3 reps in reserve.")
            add("Add one set to the movement, or swap to a close variation of it.")
            add("Check the basics outside the gym: sleep, food and how many hard sessions you did this week.")
            if (isDeloadRecommended) {
                add("Consider a lighter week, then return to this weight.")
            }
        }

        return PlateauInsight(
            exerciseId = exerciseId,
            sessionsWithoutProgress = sessionsWithoutProgress,
            headline = "$name has not moved in $sessionsWithoutProgress sessions",
            detail = "Your best estimated one-rep max for $name is about the same as it was " +
                "$sessionsWithoutProgress sessions ago. That is normal, and it usually means the " +
                "plan needs a small change rather than more effort.",
            suggestions = suggestions,
            isDeloadRecommended = isDeloadRecommended,
        )
    }

    /**
     * Whole-plan deload guidance. Only fires when the *majority* of tracked
     * movements have stalled and the user has been training hard and often.
     */
    fun deloadInsight(
        sessions: List<WorkoutSession>,
        exerciseIds: List<String>,
        nowMs: Long,
    ): PlateauInsight? {
        val tracked = exerciseIds.distinct()
        if (tracked.size < 3) return null
        val stalled = tracked.mapNotNull { plateauInsight(it, sessions, nowMs) }
        if (stalled.size < 2 || stalled.size < (tracked.size + 1) / 2) return null

        val recent = com.cadence.fit.domain.time.TimeRanges.sessionsWithin(sessions, 21, nowMs)
        val hardRpe = recent.flatMap { it.workingSets }
            .mapNotNull { it.rpe }
            .takeIf { it.isNotEmpty() }
            ?.average()

        val recommended = stalled.count { it.isDeloadRecommended } >= 2 ||
            (hardRpe != null && hardRpe >= 9.0 && recent.size >= 6)

        return PlateauInsight(
            exerciseId = null,
            sessionsWithoutProgress = stalled.maxOf { it.sessionsWithoutProgress },
            headline = if (recommended) {
                "Several lifts have flattened out — a lighter week may help"
            } else {
                "Progress has slowed across several lifts"
            },
            detail = buildString {
                append("${stalled.size} of ${tracked.size} tracked exercises have not improved recently")
                if (hardRpe != null) {
                    append(", and your recorded RPE has been around ${String.format(java.util.Locale.ROOT, "%.1f", hardRpe)}")
                }
                append(". This is a normal part of training, not a failure.")
            },
            suggestions = listOf(
                "Keep the same movements but use about 60-70% of your usual sets for one week.",
                "Stay 3-4 reps away from failure during that week; do not chase personal records.",
                "Return to your normal sets the week after and re-test one main lift.",
            ),
            isDeloadRecommended = recommended,
        )
    }

    /**
     * One step that never exceeds ~5% of the current load, expressed on the
     * user's unit grid so it matches the +/- controls on the logging screen.
     */
    private fun stepFor(currentKg: Double, exerciseIncrementKg: Double, unit: WeightUnit): Double {
        val unitFineStep = unit.toKilograms(unit.fineIncrement)
        val relativeCap = if (currentKg > 0) currentKg * maxRelativeJump else unitFineStep
        val cap = max(relativeCap, unitFineStep)
        val raw = min(exerciseIncrementKg, cap)
        return unit.toKilograms(unit.snap(unit.fromKilograms(raw)))
    }
}
