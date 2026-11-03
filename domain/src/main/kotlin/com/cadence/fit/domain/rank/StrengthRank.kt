package com.cadence.fit.domain.rank

import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.MovementPattern
import com.cadence.fit.domain.model.WorkoutSession

/** Five broad training tiers, from first logged lifts to a strong all-round base. */
enum class StrengthRank(val displayName: String, val order: Int) {
    NEWBIE("Newbie", 0),
    ROOKIE("Rookie", 1),
    INTERMEDIATE("Intermediate", 2),
    PRO("Pro", 3),
    CHAMPION("Champion", 4),
}

/**
 * The four barbell anchors used for the rank. Machines and accessory movements
 * are deliberately excluded: a leg-press number and a back-squat number are not
 * the same measurement.
 *
 * Ratios are best estimated 1RM divided by current body weight. The thresholds
 * are broad recreational benchmarks, not competition classes or medical advice.
 */
enum class RankAnchor(
    val displayName: String,
    internal val patterns: Set<MovementPattern>,
    internal val allowedEquipment: Set<Equipment>,
    private val minimumRatios: List<Double>,
) {
    SQUAT(
        "Squat",
        setOf(MovementPattern.SQUAT),
        setOf(Equipment.BARBELL, Equipment.TRAP_BAR),
        listOf(0.0, 0.75, 1.25, 1.75, 2.25),
    ),
    BENCH(
        "Bench press",
        setOf(MovementPattern.HORIZONTAL_PUSH),
        setOf(Equipment.BARBELL),
        listOf(0.0, 0.50, 0.80, 1.10, 1.40),
    ),
    HINGE(
        "Deadlift",
        setOf(MovementPattern.HINGE),
        setOf(Equipment.BARBELL, Equipment.TRAP_BAR),
        listOf(0.0, 1.00, 1.50, 2.00, 2.50),
    ),
    PRESS(
        "Overhead press",
        setOf(MovementPattern.VERTICAL_PUSH),
        setOf(Equipment.BARBELL),
        listOf(0.0, 0.35, 0.55, 0.75, 0.95),
    ),
    ;

    fun rankFor(ratio: Double): StrengthRank {
        var result = StrengthRank.NEWBIE
        for (index in 1 until minimumRatios.size) {
            if (ratio >= minimumRatios[index]) {
                result = StrengthRank.entries[index]
            }
        }
        return result
    }
}

/** Best logged lift for one anchor. */
data class RankedLift(
    val anchor: RankAnchor,
    val exerciseName: String,
    val bestE1rmKg: Double,
    val weightKg: Double,
    val reps: Int,
    val ratio: Double?,
    val rank: StrengthRank?,
)

data class StrengthRankAssessment(
    val bodyweightKg: Double?,
    val rank: StrengthRank?,
    val score: Double?,
    val progressToNext: Float,
    val lifts: List<RankedLift>,
    val missingAnchors: List<RankAnchor>,
) {
    val nextRank: StrengthRank? get() = rank?.let { StrengthRank.entries.getOrNull(it.order + 1) }
    val anchorCount: Int get() = lifts.size
    val totalAnchors: Int get() = RankAnchor.entries.size
}

object StrengthRanker {

    private data class Candidate(
        val exerciseName: String,
        val bestE1rmKg: Double,
        val weightKg: Double,
        val reps: Int,
    )

    /**
     * Uses complete sessions only and one best set per anchor. Body weight is
     * optional: without it the lifts are still listed, but no rank is awarded.
     */
    fun assess(
        sessions: List<WorkoutSession>,
        catalog: ExerciseCatalog,
        bodyweightKg: Double?,
    ): StrengthRankAssessment {
        val usableWeight = bodyweightKg?.takeIf { it.isFinite() && it > 0.0 }
        val best = mutableMapOf<RankAnchor, Candidate>()

        sessions.forEach { session ->
            if (!session.isComplete) return@forEach
            session.workingSets.forEach { set ->
                if (set.reps !in 1..12 || set.weightKg <= 0.0) return@forEach
                val exercise = catalog.byId(set.exerciseId) ?: return@forEach
                if (!exercise.isCompound) return@forEach
                val anchor = RankAnchor.entries.firstOrNull { candidate ->
                    exercise.pattern in candidate.patterns &&
                        exercise.equipment in candidate.allowedEquipment
                } ?: return@forEach
                val estimate = set.estimatedOneRepMaxKg
                if (estimate > (best[anchor]?.bestE1rmKg ?: 0.0)) {
                    best[anchor] = Candidate(
                        exerciseName = exercise.name,
                        bestE1rmKg = estimate,
                        weightKg = set.weightKg,
                        reps = set.reps,
                    )
                }
            }
        }

        val lifts = RankAnchor.entries.mapNotNull { anchor ->
            best[anchor]?.let { candidate ->
                val ratio = usableWeight?.let { candidate.bestE1rmKg / it }
                RankedLift(
                    anchor = anchor,
                    exerciseName = candidate.exerciseName,
                    bestE1rmKg = candidate.bestE1rmKg,
                    weightKg = candidate.weightKg,
                    reps = candidate.reps,
                    ratio = ratio,
                    rank = ratio?.let(anchor::rankFor),
                )
            }
        }
        val heldRanks = lifts.mapNotNull { it.rank }
        val score = heldRanks
            .takeIf { it.isNotEmpty() }
            ?.map { it.order.toDouble() }
            ?.average()
        val rank = score?.let { average ->
            StrengthRank.entries[average.toInt().coerceIn(0, StrengthRank.entries.lastIndex)]
        }
        val progress = when {
            rank == null || score == null -> 0f
            rank == StrengthRank.CHAMPION -> 1f
            else -> (score - rank.order).toFloat().coerceIn(0f, 1f)
        }

        return StrengthRankAssessment(
            bodyweightKg = usableWeight,
            rank = rank,
            score = score,
            progressToNext = progress,
            lifts = lifts,
            missingAnchors = RankAnchor.entries.filterNot { it in best.keys },
        )
    }
}