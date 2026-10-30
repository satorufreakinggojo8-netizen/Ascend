package com.cadence.fit.data

import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.model.AppSettings
import com.cadence.fit.domain.model.BodyProfile
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.profile.BodyProfileCodec
import com.cadence.fit.domain.sleep.SleepStateStore
import com.cadence.fit.domain.units.WeightUnit

/**
 * Reads and writes the user's profile, preferences and sleep opt-in.
 *
 * Everything here is defensive: a corrupt or hand-edited preference falls back to
 * a sensible default instead of crashing the app on launch.
 */
class PreferencesStore(private val store: KeyValueStore) : SleepStateStore {

    var profile: TrainingProfile
        get() {
            val location = enumOr(store.getString(KEY_LOCATION), TrainingLocation.GYM)
            val storedEquipment = store.getString(KEY_EQUIPMENT)
                ?.split(',')
                ?.mapNotNull { name -> name.trim().takeIf { it.isNotEmpty() } }
                ?.mapNotNull { name -> enumOrNull<Equipment>(name) }
                ?.toSet()
            return TrainingProfile(
                onboardingComplete = store.getBoolean(KEY_ONBOARDING, false),
                location = location,
                equipment = storedEquipment ?: ExerciseCatalog.defaultEquipment(location),
                experience = enumOr(store.getString(KEY_EXPERIENCE), ExperienceLevel.BEGINNER),
                goal = enumOr(store.getString(KEY_GOAL), TrainingGoal.GENERAL_FITNESS),
                daysPerWeek = store.getInt(KEY_DAYS, DEFAULT_DAYS).coerceIn(MIN_DAYS, MAX_DAYS),
                sessionMinutes = store.getInt(KEY_MINUTES, DEFAULT_MINUTES)
                    .coerceIn(MIN_MINUTES, MAX_MINUTES),
                planName = store.getString(KEY_PLAN_NAME).orEmpty(),
                splitTemplateId = store.getString(KEY_SPLIT_ID)?.takeIf { it.isNotBlank() },
            )
        }
        set(value) {
            store.putBoolean(KEY_ONBOARDING, value.onboardingComplete)
            store.putString(KEY_LOCATION, value.location.name)
            store.putString(KEY_EQUIPMENT, value.equipment.joinToString(",") { it.name })
            store.putString(KEY_EXPERIENCE, value.experience.name)
            store.putString(KEY_GOAL, value.goal.name)
            store.putInt(KEY_DAYS, value.daysPerWeek.coerceIn(MIN_DAYS, MAX_DAYS))
            store.putInt(KEY_MINUTES, value.sessionMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES))
            store.putString(KEY_PLAN_NAME, value.planName.trim())
            val splitId = value.splitTemplateId?.trim().orEmpty()
            if (splitId.isEmpty()) store.remove(KEY_SPLIT_ID) else store.putString(KEY_SPLIT_ID, splitId)
        }

    /**
     * Optional age, height and weight history. Stored as one local preference so
     * deleting all data removes it together with everything else.
     */
    var bodyProfile: BodyProfile
        get() = BodyProfileCodec.decode(store.getString(KEY_BODY_PROFILE))
        set(value) {
            store.putString(KEY_BODY_PROFILE, BodyProfileCodec.encode(value))
        }

    var settings: AppSettings
        get() = AppSettings(
            weightUnit = enumOr(store.getString(KEY_UNIT), WeightUnit.default),
            showProgressLevel = store.getBoolean(KEY_SHOW_LEVEL, true),
            sleepInsightsEnabled = store.getBoolean(KEY_SLEEP_INSIGHTS, false),
            restTimerSoundEnabled = store.getBoolean(KEY_REST_SOUND, true),
            keepScreenOnDuringWorkout = store.getBoolean(KEY_KEEP_SCREEN_ON, true),
            barWeightKg = decimalOr(store.getString(KEY_BAR_KG), AppSettings().barWeightKg),
            barWeightLb = decimalOr(store.getString(KEY_BAR_LB), AppSettings().barWeightLb),
        )
        set(value) {
            store.putString(KEY_UNIT, value.weightUnit.name)
            store.putBoolean(KEY_SHOW_LEVEL, value.showProgressLevel)
            store.putBoolean(KEY_SLEEP_INSIGHTS, value.sleepInsightsEnabled)
            store.putBoolean(KEY_REST_SOUND, value.restTimerSoundEnabled)
            store.putBoolean(KEY_KEEP_SCREEN_ON, value.keepScreenOnDuringWorkout)
            store.putString(KEY_BAR_KG, value.barWeightKg.coerceIn(0.0, MAX_BAR_WEIGHT).toString())
            store.putString(KEY_BAR_LB, value.barWeightLb.coerceIn(0.0, MAX_BAR_WEIGHT).toString())
        }

    /** Id of the workout currently in progress, so it survives process death. */
    var activeSessionId: String?
        get() = store.getString(KEY_ACTIVE_SESSION)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) store.remove(KEY_ACTIVE_SESSION) else store.putString(KEY_ACTIVE_SESSION, value)
        }

    // --- SleepStateStore, used by the shared SleepRepository -------------------

    override suspend fun isEnabled(): Boolean = store.getBoolean(KEY_SLEEP_ENABLED, false)

    override suspend fun setEnabled(enabled: Boolean) {
        store.putBoolean(KEY_SLEEP_ENABLED, enabled)
    }

    override suspend fun lastSyncMs(): Long? =
        store.getLong(KEY_SLEEP_LAST_SYNC, NOT_SET).takeIf { it > 0L }

    override suspend fun setLastSyncMs(ms: Long?) {
        if (ms == null) {
            store.remove(KEY_SLEEP_LAST_SYNC)
        } else {
            store.putLong(KEY_SLEEP_LAST_SYNC, ms)
        }
    }

    /** Removes every preference. Used by "delete all data". */
    fun clearAll() {
        store.clear()
    }

    /** A corrupt or hand-edited bar weight falls back instead of crashing the logger. */
    private fun decimalOr(raw: String?, fallback: Double): Double =
        raw?.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.0, MAX_BAR_WEIGHT) ?: fallback

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumOrNull<T>(name) ?: fallback

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? {
        if (name == null) return null
        return enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    companion object {
        const val FILE_NAME = "cadence_preferences"

        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_LOCATION = "training_location"
        const val KEY_EQUIPMENT = "equipment"
        const val KEY_EXPERIENCE = "experience_level"
        const val KEY_GOAL = "training_goal"
        const val KEY_DAYS = "days_per_week"
        const val KEY_MINUTES = "session_minutes"
        const val KEY_UNIT = "weight_unit"
        const val KEY_SHOW_LEVEL = "show_progress_level"
        const val KEY_SLEEP_INSIGHTS = "sleep_insights_enabled"
        const val KEY_REST_SOUND = "rest_timer_sound"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_BAR_KG = "bar_weight_kg"
        const val KEY_BAR_LB = "bar_weight_lb"
        const val KEY_PLAN_NAME = "current_plan_name"
        const val KEY_SPLIT_ID = "current_split_id"
        const val KEY_BODY_PROFILE = "body_profile"
        const val KEY_ACTIVE_SESSION = "active_session_id"
        const val KEY_SLEEP_ENABLED = "sleep_sync_enabled"
        const val KEY_SLEEP_LAST_SYNC = "sleep_last_sync"

        const val MIN_DAYS = 2
        const val MAX_DAYS = 6
        const val DEFAULT_DAYS = 3
        const val MIN_MINUTES = 15
        const val MAX_MINUTES = 120
        const val DEFAULT_MINUTES = 45
        /** No real barbell is heavier than this; a corrupt value must not poison the UI. */
        const val MAX_BAR_WEIGHT = 100.0
        private const val NOT_SET = -1L
    }
}
