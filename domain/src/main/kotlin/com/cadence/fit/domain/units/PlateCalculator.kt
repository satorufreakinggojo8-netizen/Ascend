package com.cadence.fit.domain.units

import kotlin.math.abs
import kotlin.math.floor

/** How many plates of one size sit on **each** side of the bar. */
data class PlateCount(val plate: Double, val countPerSide: Int)

/**
 * What can actually be loaded onto a bar for a requested weight.
 *
 * All values are expressed in the unit the plates are measured in, because a
 * 20 kg plate and a 45 lb plate are different objects. Use [WeightUnit.toKilograms]
 * before writing anything into a logged set.
 */
data class PlateSolution(
    val unit: WeightUnit,
    val target: Double,
    val bar: Double,
    /** Heaviest weight at or below [target] that the available plates can make. */
    val achievable: Double,
    /** Per-side load, i.e. `(achievable - bar) / 2`. */
    val perSide: Double,
    val platesPerSide: List<PlateCount>,
    val exact: Boolean,
    /** `achievable - target`, so 0 when exact and negative when the target is short. */
    val difference: Double,
    /** The next weight above [target] the plates can make, if one exists. */
    val nextAchievable: Double?,
    /** True when the requested weight is lighter than the empty bar. */
    val belowBar: Boolean,
) {
    /** Total plates on the bar, both sides included. */
    val plateCount: Int get() = platesPerSide.sumOf { it.countPerSide * 2 }
}

/**
 * Greedy plate loading for a barbell.
 *
 * Standard kilogram and pound plate sets are canonical, so taking the largest
 * plate that still fits always gives the heaviest loadable weight without
 * exceeding the target. The result never invents a plate or a bar: if something
 * cannot be loaded, the shortfall and the next possible weight are reported
 * instead.
 */
object PlateCalculator {

    val KG_PLATES: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 2.0, 1.25, 0.5, 0.25)
    val LB_PLATES: List<Double> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)

    val KG_BARS: List<Double> = listOf(20.0, 15.0, 10.0, 5.0, 0.0)
    val LB_BARS: List<Double> = listOf(45.0, 35.0, 25.0, 15.0, 0.0)

    /** Most plates that physically fit on one side without becoming absurd. */
    const val DEFAULT_MAX_PER_SIDE = 10

    private const val EPSILON = 1e-9

    fun platesFor(unit: WeightUnit): List<Double> = when (unit) {
        WeightUnit.KG -> KG_PLATES
        WeightUnit.LB -> LB_PLATES
    }

    fun barsFor(unit: WeightUnit): List<Double> = when (unit) {
        WeightUnit.KG -> KG_BARS
        WeightUnit.LB -> LB_BARS
    }

    fun defaultBar(unit: WeightUnit): Double = barsFor(unit).first()

    /**
     * Loads [target] in [unit] onto a [bar] of the same unit.
     *
     * [availablePlates] exists for gyms with a partial set and for tests; it
     * defaults to the normal plate set for the unit.
     */
    fun solve(
        target: Double,
        bar: Double,
        unit: WeightUnit,
        availablePlates: List<Double> = platesFor(unit),
        maxPerSide: Int = DEFAULT_MAX_PER_SIDE,
    ): PlateSolution {
        val safeBar = bar.coerceAtLeast(0.0)
        val safeTarget = if (target.isFinite()) target.coerceAtLeast(0.0) else 0.0
        val plates = availablePlates.filter { it.isFinite() && it > 0.0 }
            .distinct()
            .sortedDescending()

        if (safeTarget < safeBar - EPSILON) {
            return PlateSolution(
                unit = unit,
                target = safeTarget,
                bar = safeBar,
                achievable = safeBar,
                perSide = 0.0,
                platesPerSide = emptyList(),
                exact = false,
                difference = safeBar - safeTarget,
                nextAchievable = safeBar,
                belowBar = true,
            )
        }

        var remaining = (safeTarget - safeBar) / 2.0
        val chosen = mutableListOf<PlateCount>()
        var usedPerSide = 0

        plates.forEach { plate ->
            if (remaining < plate - EPSILON) return@forEach
            val room = (maxPerSide - usedPerSide).coerceAtLeast(0)
            if (room == 0) return@forEach
            val count = minOf(floor((remaining + EPSILON) / plate).toInt(), room)
            if (count > 0) {
                chosen += PlateCount(plate, count)
                remaining -= plate * count
                usedPerSide += count
            }
        }

        val loadedPerSide = chosen.sumOf { it.plate * it.countPerSide }
        val achievable = safeBar + loadedPerSide * 2.0
        val difference = achievable - safeTarget
        val exact = abs(difference) < 1e-6
        val smallest = plates.minOrNull()
        val next = when {
            exact -> null
            smallest == null -> null
            usedPerSide >= maxPerSide && plates.isNotEmpty() -> null
            else -> achievable + smallest * 2.0
        }

        return PlateSolution(
            unit = unit,
            target = safeTarget,
            bar = safeBar,
            achievable = achievable,
            perSide = loadedPerSide,
            platesPerSide = chosen,
            exact = exact,
            difference = difference,
            nextAchievable = next,
            belowBar = false,
        )
    }
}
