package com.cadence.fit.data

import com.cadence.fit.domain.model.BodyProfile
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.units.WeightUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any?>()

    override fun getString(key: String, defaultValue: String?): String? =
        values[key] as? String ?: defaultValue

    override fun putString(key: String, value: String?) {
        values[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue

    override fun putLong(key: String, value: Long) {
        values[key] = value
    }

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun clear() {
        values.clear()
    }
}

class PreferencesStoreTest {

    private val keyValues = InMemoryKeyValueStore()
    private val store = PreferencesStore(keyValues)

    @Test
    fun `a fresh install starts at sensible defaults`() {
        val profile = store.profile

        assertFalse(profile.onboardingComplete)
        assertEquals(TrainingLocation.GYM, profile.location)
        assertEquals(ExperienceLevel.BEGINNER, profile.experience)
        assertEquals(TrainingGoal.GENERAL_FITNESS, profile.goal)
        assertEquals(3, profile.daysPerWeek)
        assertEquals(45, profile.sessionMinutes)
        // Gym kit because the default location is the gym.
        assertTrue(profile.equipment.contains(Equipment.BARBELL))
    }

    @Test
    fun `a home default supplies home equipment instead of gym kit`() {
        keyValues.putString(PreferencesStore.KEY_LOCATION, TrainingLocation.HOME.name)
        val profile = store.profile

        assertEquals(TrainingLocation.HOME, profile.location)
        assertTrue(profile.equipment.contains(Equipment.DUMBBELL))
        assertFalse(profile.equipment.contains(Equipment.BARBELL))
    }

    @Test
    fun `the profile survives a round trip`() {
        val profile = store.profile.copy(
            onboardingComplete = true,
            location = TrainingLocation.HOME,
            equipment = setOf(Equipment.DUMBBELL, Equipment.MAT, Equipment.BODYWEIGHT),
            experience = ExperienceLevel.INTERMEDIATE,
            goal = TrainingGoal.MUSCLE_GAIN,
            daysPerWeek = 4,
            sessionMinutes = 60,
        )

        store.profile = profile

        assertEquals(profile, store.profile)
    }

    @Test
    fun `the current plan label survives a round trip`() {
        val profile = store.profile.withPlan("Personal 6-day", "personal-six-day")
        store.profile = profile

        assertEquals("Personal 6-day", store.profile.planName)
        assertEquals("personal-six-day", store.profile.splitTemplateId)
    }

    @Test
    fun `optional body details stay local and survive a round trip`() {
        val body = BodyProfile(ageYears = 28, heightCm = 175.0)
            .withWeight(74.2, 1_700_000_000_000L)
        store.bodyProfile = body

        val reloaded = PreferencesStore(keyValues).bodyProfile
        assertEquals(28, reloaded.ageYears)
        assertEquals(175.0, reloaded.heightCm!!, 1e-9)
        assertEquals(74.2, reloaded.latestWeightKg!!, 1e-9)
    }

    @Test
    fun `deleting all preferences clears body details and the plan name`() {
        store.profile = store.profile.withPlan("Full body", "full-body-three")
        store.bodyProfile = BodyProfile(ageYears = 30).withWeight(80.0, 1_700_000_000_000L)

        store.clearAll()

        assertEquals(BodyProfile(), store.bodyProfile)
        assertEquals("", store.profile.planName)
        assertNull(store.profile.splitTemplateId)
    }

    @Test
    fun `absurd values from a corrupted store are clamped`() {
        keyValues.putInt(PreferencesStore.KEY_DAYS, 99)
        keyValues.putInt(PreferencesStore.KEY_MINUTES, 5)

        val profile = store.profile

        assertEquals(PreferencesStore.MAX_DAYS, profile.daysPerWeek)
        assertEquals(PreferencesStore.MIN_MINUTES, profile.sessionMinutes)
    }

    @Test
    fun `an unknown enum value falls back instead of crashing`() {
        keyValues.putString(PreferencesStore.KEY_LOCATION, "MOON_BASE")
        keyValues.putString(PreferencesStore.KEY_EXPERIENCE, "WIZARD")
        keyValues.putString(PreferencesStore.KEY_GOAL, "TIME_TRAVEL")
        keyValues.putString(PreferencesStore.KEY_UNIT, "STONE")

        assertEquals(TrainingLocation.GYM, store.profile.location)
        assertEquals(ExperienceLevel.BEGINNER, store.profile.experience)
        assertEquals(TrainingGoal.GENERAL_FITNESS, store.profile.goal)
        assertEquals(WeightUnit.KG, store.settings.weightUnit)
    }

    @Test
    fun `unreadable equipment names are dropped`() {
        keyValues.putString(PreferencesStore.KEY_EQUIPMENT, "DUMBBELL,NONSENSE,MAT")

        val profile = store.profile

        assertEquals(setOf(Equipment.DUMBBELL, Equipment.MAT), profile.equipment)
    }

    @Test
    fun `the settings survive a round trip`() {
        val settings = store.settings.copy(
            weightUnit = WeightUnit.LB,
            showProgressLevel = false,
            sleepInsightsEnabled = true,
            restTimerSoundEnabled = false,
            keepScreenOnDuringWorkout = false,
            barWeightKg = 15.0,
            barWeightLb = 35.0,
        )

        store.settings = settings

        assertEquals(settings, store.settings)
    }

    @Test
    fun `bar weights are kept separate per unit`() {
        store.settings = store.settings
            .withBarWeight(WeightUnit.KG, 15.0)
            .withBarWeight(WeightUnit.LB, 35.0)

        val reloaded = PreferencesStore(keyValues).settings

        assertEquals(15.0, reloaded.barWeight(WeightUnit.KG), 1e-9)
        assertEquals(35.0, reloaded.barWeight(WeightUnit.LB), 1e-9)
    }

    @Test
    fun `bar weights default to a standard bar and never exceed the cap`() {
        val defaults = store.settings
        assertEquals(20.0, defaults.barWeightKg, 1e-9)
        assertEquals(45.0, defaults.barWeightLb, 1e-9)

        keyValues.putString(PreferencesStore.KEY_BAR_KG, "5000")
        assertEquals(PreferencesStore.MAX_BAR_WEIGHT, store.settings.barWeightKg, 1e-9)
    }

    @Test
    fun `a corrupt bar weight falls back instead of breaking the logger`() {
        keyValues.putString(PreferencesStore.KEY_BAR_KG, "heavy")
        keyValues.putString(PreferencesStore.KEY_BAR_LB, "")

        val settings = store.settings

        assertEquals(20.0, settings.barWeightKg, 1e-9)
        assertEquals(45.0, settings.barWeightLb, 1e-9)
    }

    @Test
    fun `settings start with levels visible and sleep off`() {
        val settings = store.settings

        assertTrue(settings.showProgressLevel)
        assertFalse(settings.sleepInsightsEnabled)
        assertEquals(WeightUnit.KG, settings.weightUnit)
    }

    @Test
    fun `an in-progress workout id can be stored and cleared`() {
        assertNull(store.activeSessionId)

        store.activeSessionId = "session-1"
        assertEquals("session-1", store.activeSessionId)

        store.activeSessionId = null
        assertNull(store.activeSessionId)
        assertFalse(keyValues.contains(PreferencesStore.KEY_ACTIVE_SESSION))
    }

    @Test
    fun `a blank active session id is treated as cleared`() {
        store.activeSessionId = "session-1"
        store.activeSessionId = "   "
        assertNull(store.activeSessionId)
    }

    @Test
    fun `sleep sync is off until the user turns it on`() = runTest {
        assertFalse(store.isEnabled())
        assertNull(store.lastSyncMs())

        store.setEnabled(true)
        store.setLastSyncMs(1_700_000_000_000L)

        assertTrue(store.isEnabled())
        assertEquals(1_700_000_000_000L, store.lastSyncMs())
    }

    @Test
    fun `disconnecting clears the stored sync time`() = runTest {
        store.setEnabled(true)
        store.setLastSyncMs(1_700_000_000_000L)

        store.setEnabled(false)
        store.setLastSyncMs(null)

        assertFalse(store.isEnabled())
        assertNull(store.lastSyncMs())
        assertFalse(keyValues.contains(PreferencesStore.KEY_SLEEP_LAST_SYNC))
    }

    @Test
    fun `clearing everything returns the defaults`() {
        store.profile = store.profile.copy(daysPerWeek = 6, onboardingComplete = true)
        store.settings = store.settings.copy(weightUnit = WeightUnit.LB)

        store.clearAll()

        assertFalse(store.profile.onboardingComplete)
        assertEquals(3, store.profile.daysPerWeek)
        assertEquals(WeightUnit.KG, store.settings.weightUnit)
    }
}
