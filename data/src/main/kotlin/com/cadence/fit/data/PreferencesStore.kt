package com.cadence.fit.data

import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.model.AppSettings
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.TrainingProfile
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
        }

    var settings: AppSettings
        get() = AppSettings(
            weightUnit = enumOr(store.getString(KEY_UNIT), WeightUnit.default),
            showProgressLevel = store.getBoolean(KEY_SHOW_LEVEL, true),
            sleepInsightsEnabled = store.getBoolean(KEY_SLEEP_INSIGHTS, false),
            restTimerSoundEnabled = store.getBoolean(KEY_REST_SOUND, true),
            keepScreenOnDuringWorkout = store.getBoolean(KEY_KEEP_SCREEN_ON, true),
        )
        set(value) {
            store.putString(KEY_UNIT, value.weightUnit.name)
            store.putBoolean(KEY_SHOW_LEVEL, value.showProgressLevel)
            store.putBoolean(KEY_SLEEP_INSIGHTS, value.sleepInsightsEnabled)
            store.putBoolean(KEY_REST_SOUND, value.restTimerSoundEnabled)
            store.putBoolean(KEY_KEEP_SCREEN_ON, value.keepScreenOnDuringWorkout)
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
        const val KEY_ACTIVE_SESSION = "active_session_id"
        const val KEY_SLEEP_ENABLED = "sleep_sync_enabled"
        const val KEY_SLEEP_LAST_SYNC = "sleep_last_sync"

        const val MIN_DAYS = 2
        const val MAX_DAYS = 6
        const val DEFAULT_DAYS = 3
        const val MIN_MINUTES = 15
        const val MAX_MINUTES = 120
        const val DEFAULT_MINUTES = 45
        private const val NOT_SET = -1L
    }
}
