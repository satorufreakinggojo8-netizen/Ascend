package com.cadence.fit.domain.model

import com.cadence.fit.domain.time.TimeRanges
import java.time.ZoneId

/** One self-reported body-weight reading. */
data class WeightEntry(
    val atMs: Long,
    val weightKg: Double,
)

/**
 * Optional personal details.
 *
 * Nothing here is required to use Cadence, and nothing here is derived from the
 * body map or used to assess body composition. Age and height are stored only so
 * the profile page can show what the user chose to enter; they are never sent
 * anywhere and are included in a local export only because the export is the
 * user's own data.
 */
data class BodyProfile(
    val ageYears: Int? = null,
    val heightCm: Double? = null,
    val weightEntries: List<WeightEntry> = emptyList(),
) {
    val latestWeightKg: Double? get() = weightEntries.maxByOrNull { it.atMs }?.weightKg

    val hasAnyDetail: Boolean
        get() = ageYears != null || heightCm != null || weightEntries.isNotEmpty()

    /**
     * Records a weight for the local day containing [atMs].
     *
     * A second entry on the same day replaces the first, so the trend is one
     * reading per day rather than a noisy list from repeated edits. Older
     * entries are kept, capped at [MAX_ENTRIES] newest readings.
     */
    fun withWeight(weightKg: Double, atMs: Long, zone: ZoneId = TimeRanges.zone()): BodyProfile {
        if (!weightKg.isFinite()) return this
        val sanitized = weightKg.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
        val day = TimeRanges.toLocalDate(atMs, zone)
        val kept = weightEntries.filterNot { entry -> TimeRanges.toLocalDate(entry.atMs, zone) == day }
        val next = (kept + WeightEntry(atMs = atMs, weightKg = sanitized))
            .sortedBy { it.atMs }
            .takeLast(MAX_ENTRIES)
        return copy(weightEntries = next)
    }

    fun withoutWeight(atMs: Long): BodyProfile =
        copy(weightEntries = weightEntries.filterNot { it.atMs == atMs })

    /** Change since the first reading inside the window, if there are two. */
    fun changeKg(withinDays: Int, nowMs: Long, zone: ZoneId = TimeRanges.zone()): Double? {
        val cutoff = nowMs - withinDays.toLong() * DAY_MS
        val window = weightEntries.filter { it.atMs >= cutoff }.sortedBy { it.atMs }
        if (window.size < 2) return null
        return window.last().weightKg - window.first().weightKg
    }

    /** A weight trend sampled from the newest readings, oldest first. */
    fun trendKg(limit: Int = 12): List<Double> =
        weightEntries.sortedBy { it.atMs }.takeLast(limit.coerceAtLeast(2)).map { it.weightKg }

    /**
     * Clamps hand-entered values to a plausible range. A value outside the range
     * is treated as not entered rather than surfaced as a fact.
     */
    fun sanitized(): BodyProfile = copy(
        ageYears = ageYears?.takeIf { it in MIN_AGE..MAX_AGE },
        heightCm = heightCm?.takeIf { it.isFinite() }?.coerceIn(MIN_HEIGHT_CM, MAX_HEIGHT_CM),
        weightEntries = weightEntries
            .filter { it.weightKg.isFinite() && it.weightKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG }
            .sortedBy { it.atMs }
            .takeLast(MAX_ENTRIES),
    )

    companion object {
        const val MIN_AGE = 13
        const val MAX_AGE = 120
        const val MIN_HEIGHT_CM = 90.0
        const val MAX_HEIGHT_CM = 250.0
        const val MIN_WEIGHT_KG = 25.0
        const val MAX_WEIGHT_KG = 350.0
        const val MAX_ENTRIES = 400

        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
