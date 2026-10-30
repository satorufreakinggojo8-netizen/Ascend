package com.cadence.fit.domain.model

import com.cadence.fit.domain.units.WeightUnit

/** Everything the planner needs to build a sensible starter plan. */
data class TrainingProfile(
    val onboardingComplete: Boolean = false,
    val location: TrainingLocation = TrainingLocation.GYM,
    val equipment: Set<Equipment> = Equipment.homeStarterKit,
    val experience: ExperienceLevel = ExperienceLevel.BEGINNER,
    val goal: TrainingGoal = TrainingGoal.GENERAL_FITNESS,
    val daysPerWeek: Int = 3,
    val sessionMinutes: Int = 45,
) {
    val availableEquipment: Set<Equipment>
        get() = equipment + Equipment.BODYWEIGHT + Equipment.MAT

    companion object {
        val DAY_OPTIONS = listOf(2, 3, 4, 5, 6)
        val SESSION_LENGTHS = listOf(20, 30, 45, 60, 75, 90)
    }
}

/**
 * Local-only preferences. Nothing here is transmitted anywhere; see
 * `PrivacySettings` and the in-app privacy page.
 */
data class AppSettings(
    val weightUnit: WeightUnit = WeightUnit.default,
    val showProgressLevel: Boolean = true,
    val sleepInsightsEnabled: Boolean = false,
    val restTimerSoundEnabled: Boolean = true,
    val keepScreenOnDuringWorkout: Boolean = true,
    /**
     * Bar weights are stored per unit rather than converted, because a 20 kg bar
     * and a 45 lb bar are different objects. The plate calculator reads whichever
     * one matches the active unit.
     */
    val barWeightKg: Double = 20.0,
    val barWeightLb: Double = 45.0,
) {
    fun barWeight(unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> barWeightKg
        WeightUnit.LB -> barWeightLb
    }

    fun withBarWeight(unit: WeightUnit, weight: Double): AppSettings = when (unit) {
        WeightUnit.KG -> copy(barWeightKg = weight)
        WeightUnit.LB -> copy(barWeightLb = weight)
    }
}

/** Explicitly opt-in switches, surfaced on the privacy page. */
data class PrivacySettings(
    /**
     * Reading sleep from Health Connect is opt-in and can be turned off again at
     * any time. Off means the app never queries the health store.
     */
    val sleepSyncEnabled: Boolean = false,
    /** Session/body-map context is only used inside the app. */
    val shareContextWithSleepInsights: Boolean = false,
)
