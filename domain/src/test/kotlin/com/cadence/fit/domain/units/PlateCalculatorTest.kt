package com.cadence.fit.domain.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorTest {

    private fun solve(
        target: Double,
        bar: Double = 20.0,
        unit: WeightUnit = WeightUnit.KG,
        plates: List<Double> = PlateCalculator.platesFor(unit),
        maxPerSide: Int = PlateCalculator.DEFAULT_MAX_PER_SIDE,
    ) = PlateCalculator.solve(target, bar, unit, plates, maxPerSide)

    @Test
    fun `a round kilogram total loads exact plates per side`() {
        val result = solve(100.0)

        assertEquals(40.0, result.perSide, 1e-9)
        assertEquals(listOf(PlateCount(25.0, 1), PlateCount(15.0, 1)), result.platesPerSide)
        assertEquals(100.0, result.achievable, 1e-9)
        assertTrue(result.exact)
        assertEquals(0.0, result.difference, 1e-9)
        assertNull(result.nextAchievable)
    }

    @Test
    fun `fractional plates are used when they are needed`() {
        val result = solve(102.5)

        assertEquals(102.5, result.achievable, 1e-9)
        assertTrue(result.exact)
        assertTrue(result.platesPerSide.any { it.plate == 1.25 && it.countPerSide == 1 })
    }

    @Test
    fun `small plates handle a tiny jump above the bar`() {
        val result = solve(21.5, bar = 20.0)

        assertEquals(0.75, result.perSide, 1e-9)
        assertEquals(21.5, result.achievable, 1e-9)
        assertTrue(result.exact)
        assertEquals(listOf(PlateCount(0.5, 1), PlateCount(0.25, 1)), result.platesPerSide)
    }

    @Test
    fun `an unreachable kilogram target reports the shortfall and the next weight`() {
        val result = solve(59.7)

        assertFalse(result.exact)
        assertEquals(59.5, result.achievable, 1e-9)
        assertEquals(-0.2, result.difference, 1e-6)
        assertEquals(60.0, result.nextAchievable!!, 1e-9)
        assertTrue(result.achievable < 59.7)
    }

    @Test
    fun `pound plates load a standard pound total`() {
        val result = solve(target = 225.0, bar = 45.0, unit = WeightUnit.LB)

        assertEquals(90.0, result.perSide, 1e-9)
        assertEquals(listOf(PlateCount(45.0, 2)), result.platesPerSide)
        assertEquals(225.0, result.achievable, 1e-9)
        assertTrue(result.exact)
    }

    @Test
    fun `pound and kilogram plate sets are different objects`() {
        assertTrue(PlateCalculator.platesFor(WeightUnit.KG).contains(25.0))
        assertTrue(PlateCalculator.platesFor(WeightUnit.LB).contains(45.0))
        assertFalse(PlateCalculator.platesFor(WeightUnit.KG).contains(45.0))
        assertEquals(20.0, PlateCalculator.defaultBar(WeightUnit.KG), 1e-9)
        assertEquals(45.0, PlateCalculator.defaultBar(WeightUnit.LB), 1e-9)
    }

    @Test
    fun `a pound total converts to the kilogram weight that would be logged`() {
        val result = solve(target = 135.0, bar = 45.0, unit = WeightUnit.LB)

        assertTrue(result.exact)
        // The logger stores kilograms, so the UI must convert before saving.
        assertEquals(61.23496995, WeightUnit.LB.toKilograms(result.achievable), 1e-9)
        assertEquals(135.0, WeightUnit.LB.fromKilograms(WeightUnit.LB.toKilograms(135.0)), 1e-9)
    }

    @Test
    fun `a target lighter than the bar is called out instead of pretending to load it`() {
        val result = solve(15.0, bar = 20.0)

        assertTrue(result.belowBar)
        assertEquals(20.0, result.achievable, 1e-9)
        assertEquals(5.0, result.difference, 1e-9)
        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(20.0, result.nextAchievable!!, 1e-9)
    }

    @Test
    fun `a target equal to the bar needs no plates`() {
        val result = solve(20.0, bar = 20.0)

        assertFalse(result.belowBar)
        assertTrue(result.exact)
        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(0.0, result.difference, 1e-9)
    }

    @Test
    fun `a partial plate set reports what it can actually make`() {
        val result = solve(90.0, bar = 20.0, plates = listOf(25.0))

        assertEquals(70.0, result.achievable, 1e-9)
        assertEquals(-20.0, result.difference, 1e-9)
        assertEquals(120.0, result.nextAchievable!!, 1e-9)
        assertFalse(result.exact)
    }

    @Test
    fun `an empty plate set leaves only the bar`() {
        val result = solve(100.0, bar = 20.0, plates = emptyList())

        assertEquals(20.0, result.achievable, 1e-9)
        assertTrue(result.platesPerSide.isEmpty())
        assertNull(result.nextAchievable)
        assertEquals(-80.0, result.difference, 1e-9)
    }

    @Test
    fun `the per-side plate cap is respected`() {
        val result = solve(100.0, bar = 0.0, plates = listOf(1.0), maxPerSide = 10)

        assertEquals(10, result.platesPerSide.sumOf { it.countPerSide })
        assertEquals(20.0, result.achievable, 1e-9)
        assertNull(result.nextAchievable)
    }

    @Test
    fun `both sides always match so the total is bar plus twice the load`() {
        listOf(40.0, 60.0, 87.5, 133.0, 200.0).forEach { target ->
            val result = solve(target)
            assertEquals(
                result.bar + result.perSide * 2.0,
                result.achievable,
                1e-9,
            )
            assertTrue(result.achievable <= target + 1e-6 || result.belowBar)
        }
    }

    @Test
    fun `plates are reported largest first with no duplicates`() {
        val result = solve(180.0)
        val sizes = result.platesPerSide.map { it.plate }

        assertEquals(sizes.sortedDescending(), sizes)
        assertEquals(sizes.distinct(), sizes)
        assertTrue(result.platesPerSide.all { it.countPerSide > 0 })
    }

    @Test
    fun `a nonsense target is treated as zero rather than crashing`() {
        val result = solve(Double.NaN, bar = 20.0)

        assertTrue(result.belowBar)
        assertEquals(20.0, result.achievable, 1e-9)
    }
}
