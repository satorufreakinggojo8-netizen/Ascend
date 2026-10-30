package com.cadence.fit.domain.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {

    @Test
    fun `kilograms pass through unchanged`() {
        assertEquals(100.0, WeightUnit.KG.toKilograms(100.0), 1e-9)
        assertEquals(100.0, WeightUnit.KG.fromKilograms(100.0), 1e-9)
    }

    @Test
    fun `pounds convert to kilograms using the exact factor`() {
        assertEquals(20.41165665, WeightUnit.LB.toKilograms(45.0), 1e-6)
    }

    @Test
    fun `a weight typed in pounds reads back the same in pounds`() {
        val stored = WeightUnit.LB.toKilograms(45.0)
        assertEquals("45", Weights.format(stored, WeightUnit.LB))
    }

    @Test
    fun `display snaps onto the unit grid`() {
        // 100 kg is 220.462 lb, which should read as a clean 220.5 lb.
        assertEquals("220.5", Weights.format(100.0, WeightUnit.LB))
        assertEquals("100", Weights.format(100.0, WeightUnit.KG))
    }

    @Test
    fun `formatting drops trailing zeros and decimals`() {
        assertEquals("60", Weights.formatAmount(60.0))
        assertEquals("62.5", Weights.formatAmount(62.5))
        assertEquals("61.25", Weights.formatAmount(61.25))
        assertEquals("0", Weights.formatAmount(0.0))
    }

    @Test
    fun `volume formatting groups thousands and keeps the unit`() {
        assertEquals("12,450 kg", Weights.formatVolume(12_450.0, WeightUnit.KG))
        assertEquals("12,450", Weights.formatVolume(12_450.0, WeightUnit.KG, withUnit = false))
        assertEquals("1,000 kg", Weights.formatVolume(1_000.0, WeightUnit.KG))
    }

    @Test
    fun `digit grouping handles values below one thousand and negatives`() {
        assertEquals("999", Weights.groupDigits(999))
        assertEquals("1,234,567", Weights.groupDigits(1_234_567))
        assertEquals("-1,234,567", Weights.groupDigits(-1_234_567))
        assertEquals("0", Weights.groupDigits(0))
    }

    @Test
    fun `switching units converts the typed amount instead of reinterpreting it`() {
        // The bug this guards: 100 kg must not become 100 lb when the toggle moves.
        assertEquals(220.5, Weights.convertAmount(100.0, WeightUnit.KG, WeightUnit.LB), 1e-9)
        assertEquals("220.5", Weights.formatAmount(Weights.convertAmount(100.0, WeightUnit.KG, WeightUnit.LB)))
        assertEquals(102.0, Weights.convertAmount(225.0, WeightUnit.LB, WeightUnit.KG), 1e-9)
        assertEquals(100.0, Weights.convertAmount(220.5, WeightUnit.LB, WeightUnit.KG), 1e-9)
    }

    @Test
    fun `a converted amount still logs the same physical load`() {
        val inPounds = Weights.convertAmount(100.0, WeightUnit.KG, WeightUnit.LB)
        assertEquals(100.0, WeightUnit.LB.toKilograms(inPounds), 0.12)

        val inKilograms = Weights.convertAmount(225.0, WeightUnit.LB, WeightUnit.KG)
        assertEquals(225.0, WeightUnit.LB.fromKilograms(inKilograms), 0.26)
    }

    @Test
    fun `converting within the same unit is a no-op and zero stays zero`() {
        assertEquals(62.3, Weights.convertAmount(62.3, WeightUnit.KG, WeightUnit.KG), 1e-9)
        assertEquals(0.0, Weights.convertAmount(0.0, WeightUnit.KG, WeightUnit.LB), 1e-9)
        assertEquals(0.0, Weights.convertAmount(0.0, WeightUnit.LB, WeightUnit.KG), 1e-9)
    }

    @Test
    fun `parsing accepts a unit suffix and a comma decimal separator`() {
        assertEquals(45.0, Weights.parseToKilograms("45", WeightUnit.LB)!! / KG_PER_LB, 1e-6)
        assertEquals(60.0, Weights.parseToKilograms("60 kg", WeightUnit.KG)!!, 1e-9)
        assertEquals(60.5, Weights.parseToKilograms("60,5", WeightUnit.KG)!!, 1e-9)
    }

    @Test
    fun `parsing rejects nonsense and negative values`() {
        assertNull(Weights.parseToKilograms("", WeightUnit.KG))
        assertNull(Weights.parseToKilograms("heavy", WeightUnit.KG))
        assertNull(Weights.parseToKilograms("-10", WeightUnit.KG))
    }

    @Test
    fun `unit tags round trip and fall back to kilograms`() {
        assertEquals(WeightUnit.LB, WeightUnit.fromTag("lb"))
        assertEquals(WeightUnit.LB, WeightUnit.fromTag("LB"))
        assertEquals(WeightUnit.KG, WeightUnit.fromTag("kg"))
        assertEquals(WeightUnit.KG, WeightUnit.fromTag("stones"))
        assertEquals(WeightUnit.KG, WeightUnit.fromTag(null))
    }

    @Test
    fun `increments are larger in pounds than in kilograms`() {
        assertTrue(WeightUnit.KG.increment < WeightUnit.LB.increment)
        assertTrue(WeightUnit.KG.fineIncrement < WeightUnit.KG.increment)
        assertTrue(WeightUnit.LB.fineIncrement < WeightUnit.LB.increment)
    }

    @Test
    fun `body weight keeps its tenths instead of snapping to the plate grid`() {
        assertEquals("78.6 kg", Weights.formatBodyWeight(78.6, WeightUnit.KG))
        assertEquals("78.5 kg", Weights.formatWithUnit(78.6, WeightUnit.KG))
        assertEquals("173.28 lb", Weights.formatBodyWeight(78.6, WeightUnit.LB))
    }

    @Test
    fun `clock formatting pads seconds`() {
        assertEquals("0:00", Durations.clock(0))
        assertEquals("1:05", Durations.clock(65))
        assertEquals("12:30", Durations.clock(750))
        assertEquals("0:00", Durations.clock(-5))
    }

    @Test
    fun `hours and minutes formatting is compact`() {
        assertEquals("45m", Durations.hoursMinutes(45))
        assertEquals("1h", Durations.hoursMinutes(60))
        assertEquals("7h 40m", Durations.hoursMinutes(460))
        assertEquals("0m", Durations.hoursMinutes(0))
    }
}
