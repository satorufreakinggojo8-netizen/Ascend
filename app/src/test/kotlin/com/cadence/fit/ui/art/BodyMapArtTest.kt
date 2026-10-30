package com.cadence.fit.ui.art

import com.cadence.fit.domain.model.BodySide
import com.cadence.fit.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMapArtTest {

    @Test
    fun `every body-map muscle is drawn on at least one side`() {
        val drawn = bodyMapMusclesFor(BodySide.FRONT) + bodyMapMusclesFor(BodySide.BACK)

        MuscleGroup.bodyMapGroups.forEach { muscle ->
            assertTrue("${muscle.displayName} is not drawn", muscle in drawn)
        }
    }

    @Test
    fun `conditioning is never drawn as a muscle`() {
        val drawn = bodyMapMusclesFor(BodySide.FRONT) + bodyMapMusclesFor(BodySide.BACK)
        assertFalse(MuscleGroup.CONDITIONING in drawn)
    }

    @Test
    fun `sides match the anatomy labels`() {
        assertTrue(MuscleGroup.CHEST in bodyMapMusclesFor(BodySide.FRONT))
        assertFalse(MuscleGroup.CHEST in bodyMapMusclesFor(BodySide.BACK))
        assertTrue(MuscleGroup.TRAPS in bodyMapMusclesFor(BodySide.BACK))
        assertFalse(MuscleGroup.TRAPS in bodyMapMusclesFor(BodySide.FRONT))
        assertTrue(MuscleGroup.QUADS in bodyMapMusclesFor(BodySide.FRONT))
        assertTrue(MuscleGroup.HAMSTRINGS in bodyMapMusclesFor(BodySide.BACK))
    }

    @Test
    fun `an untrained window has no intensity`() {
        assertEquals(0, muscleBand(0.0, 100.0))
        assertEquals(0, muscleBand(10.0, 0.0))
    }

    @Test
    fun `bands sit on clear quarter boundaries`() {
        assertEquals(1, muscleBand(24.9, 100.0))
        assertEquals(2, muscleBand(25.0, 100.0))
        assertEquals(3, muscleBand(50.0, 100.0))
        assertEquals(4, muscleBand(75.0, 100.0))
        assertEquals(4, muscleBand(100.0, 100.0))
    }

    @Test
    fun `the most-trained muscle is always in the top band`() {
        val windows = listOf(
            mapOf(MuscleGroup.CHEST to 1200.0, MuscleGroup.QUADS to 300.0),
            mapOf(MuscleGroup.LATS to 42.5),
            mapOf(MuscleGroup.CALVES to 0.1, MuscleGroup.ABS to 99.9),
        )

        windows.forEach { window ->
            val max = window.values.max()
            val top = window.maxByOrNull { it.value }!!.key
            assertEquals(4, muscleBand(window.getValue(top), max))
        }
    }

    @Test
    fun `volume for a single muscle cannot produce a peak when it is the only value`() {
        // Nothing is trained, so nothing should be presented as "most".
        assertEquals(0, muscleBand(0.0, 0.0))
    }
}
