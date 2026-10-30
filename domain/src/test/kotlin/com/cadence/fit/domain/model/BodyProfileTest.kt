package com.cadence.fit.domain.model

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.profile.BodyProfileCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProfileTest {

    private val day = Fixtures.DAY_MS

    @Test
    fun `a fresh profile has no required details`() {
        val profile = BodyProfile()

        assertNull(profile.ageYears)
        assertNull(profile.heightCm)
        assertNull(profile.latestWeightKg)
        assertFalse(profile.hasAnyDetail)
    }

    @Test
    fun `age and height are optional and can be set independently`() {
        val onlyAge = BodyProfile(ageYears = 29)
        val onlyHeight = BodyProfile(heightCm = 172.5)

        assertEquals(29, onlyAge.ageYears)
        assertNull(onlyAge.heightCm)
        assertNull(onlyHeight.ageYears)
        assertEquals(172.5, onlyHeight.heightCm!!, 1e-9)
    }

    @Test
    fun `a second weight on the same day replaces the first`() {
        val first = BodyProfile().withWeight(80.0, NOW - 60_000, Fixtures.UTC)
        val second = first.withWeight(79.5, NOW, Fixtures.UTC)

        assertEquals(1, second.weightEntries.size)
        assertEquals(79.5, second.latestWeightKg!!, 1e-9)
    }

    @Test
    fun `weights on different days are both kept`() {
        val profile = BodyProfile()
            .withWeight(80.0, NOW - 2 * day, Fixtures.UTC)
            .withWeight(79.5, NOW - day, Fixtures.UTC)
            .withWeight(79.0, NOW, Fixtures.UTC)

        assertEquals(3, profile.weightEntries.size)
        assertEquals(79.0, profile.latestWeightKg!!, 1e-9)
        assertEquals(listOf(80.0, 79.5, 79.0), profile.trendKg())
    }

    @Test
    fun `out of range weights are clamped rather than rejected`() {
        val high = BodyProfile().withWeight(9_000.0, NOW, Fixtures.UTC)
        val low = BodyProfile().withWeight(1.0, NOW, Fixtures.UTC)

        assertEquals(BodyProfile.MAX_WEIGHT_KG, high.latestWeightKg!!, 1e-9)
        assertEquals(BodyProfile.MIN_WEIGHT_KG, low.latestWeightKg!!, 1e-9)
    }

    @Test
    fun `a non-finite weight is ignored`() {
        val profile = BodyProfile().withWeight(Double.NaN, NOW, Fixtures.UTC)
        assertTrue(profile.weightEntries.isEmpty())
    }

    @Test
    fun `the history is capped at the newest entries`() {
        var profile = BodyProfile()
        repeat(BodyProfile.MAX_ENTRIES + 5) { index ->
            profile = profile.withWeight(70.0 + index * 0.1, NOW + index * day, Fixtures.UTC)
        }

        assertEquals(BodyProfile.MAX_ENTRIES, profile.weightEntries.size)
        assertEquals(profile.weightEntries.maxByOrNull { it.atMs }, profile.weightEntries.last())
    }

    @Test
    fun `change is only reported when the window has two readings`() {
        val one = BodyProfile().withWeight(80.0, NOW - day, Fixtures.UTC)
        assertNull(one.changeKg(30, NOW, Fixtures.UTC))

        val two = one.withWeight(79.0, NOW, Fixtures.UTC)
        assertEquals(-1.0, two.changeKg(30, NOW, Fixtures.UTC)!!, 1e-9)
        assertNull(two.changeKg(0, NOW, Fixtures.UTC))
    }

    @Test
    fun `sanitizing drops implausible values instead of presenting them as facts`() {
        val profile = BodyProfile(
            ageYears = 5,
            heightCm = 400.0,
            weightEntries = listOf(
                WeightEntry(NOW, 80.0),
                WeightEntry(NOW - day, 0.0),
                WeightEntry(NOW - 2 * day, Double.NaN),
            ),
        ).sanitized()

        assertNull(profile.ageYears)
        assertEquals(BodyProfile.MAX_HEIGHT_CM, profile.heightCm!!, 1e-9)
        assertEquals(1, profile.weightEntries.size)
    }

    @Test
    fun `the local codec round trips every optional field`() {
        val profile = BodyProfile(
            ageYears = 34,
            heightCm = 181.0,
            weightEntries = listOf(
                WeightEntry(NOW - day, 84.25),
                WeightEntry(NOW, 83.9),
            ),
        )

        val restored = BodyProfileCodec.decode(BodyProfileCodec.encode(profile))
        assertEquals(profile.sanitized(), restored)
    }

    @Test
    fun `a corrupt or empty preference falls back to an empty profile`() {
        assertEquals(BodyProfile(), BodyProfileCodec.decode(null))
        assertEquals(BodyProfile(), BodyProfileCodec.decode(""))
        assertEquals(BodyProfile(), BodyProfileCodec.decode("{not json"))
        assertEquals(BodyProfile(), BodyProfileCodec.decode("[1,2,3]"))
    }

    @Test
    fun `malformed weight entries are dropped`() {
        val restored = BodyProfileCodec.decode(
            """{"ageYears":30,"weights":[{"atMs":1},{"weightKg":80},{"atMs":2,"weightKg":81}]}""",
        )

        assertEquals(30, restored.ageYears)
        assertEquals(1, restored.weightEntries.size)
        assertEquals(81.0, restored.latestWeightKg!!, 1e-9)
    }

    @Test
    fun `a weight entry can be removed without touching the profile`() {
        val profile = BodyProfile(ageYears = 40)
            .withWeight(90.0, NOW - day, Fixtures.UTC)
            .withWeight(89.5, NOW, Fixtures.UTC)

        val trimmed = profile.withoutWeight(profile.weightEntries.last().atMs)

        assertEquals(1, trimmed.weightEntries.size)
        assertEquals(40, trimmed.ageYears)
        assertEquals(90.0, trimmed.latestWeightKg!!, 1e-9)
    }
}
