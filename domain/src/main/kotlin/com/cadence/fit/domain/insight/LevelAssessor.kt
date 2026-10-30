package com.cadence.fit.domain.insight

import com.cadence.fit.domain.model.ProgressLevel
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.time.TimeRanges
import java.time.ZoneId

/**
 * An optional level derived only from the user's own logged training.
 *
 * The benchmark is deliberately about *consistency*, which Cadence can actually
 * observe, rather than about strength standards or body measurements, which it
 * cannot see and should not guess.
 */
data class LevelAssessment(
    val level: ProgressLevel,
    val headline: String,
    val benchmark: String,
    val detail: String,
    val consistencyWeeks: Int,
    val sessionsLast28Days: Int,
    val sessionsPerWeek: Double,
    val nextStepHint: String,
) {
    val disclaimer: String =
        "This level summarises your own training consistency. It is not a medical assessment, it does " +
            "not describe your body, and it does not compare you with other people. You can hide it in Settings."
}

object LevelAssessor {

    const val MIN_SESSIONS_FOR_LEVEL = 4
    const val WEEKLY_RATE_WINDOW_DAYS = 56

    /** Returns null when the user has hidden levels in settings. */
    fun assess(
        sessions: List<WorkoutSession>,
        nowMs: Long,
        zone: ZoneId = TimeRanges.zone(),
        enabled: Boolean = true,
    ): LevelAssessment? {
        if (!enabled) return null

        val completed = sessions.filter { it.isComplete }
        val totalSessions = completed.size
        val activeWeeks = TimeRanges.activeWeekCount(completed, zone)
        val last28 = TimeRanges.sessionsWithin(completed, 28, nowMs).size
        val rateWindowSessions = TimeRanges.sessionsWithin(
            completed,
            WEEKLY_RATE_WINDOW_DAYS,
            nowMs,
        ).size
        val perWeek = rateWindowSessions / (WEEKLY_RATE_WINDOW_DAYS / 7.0)

        val level = when {
            totalSessions < MIN_SESSIONS_FOR_LEVEL -> ProgressLevel.STARTING
            activeWeeks >= 24 && perWeek >= 3.0 -> ProgressLevel.ADVANCED
            activeWeeks >= 8 && perWeek >= 2.0 -> ProgressLevel.INTERMEDIATE
            else -> ProgressLevel.BEGINNER
        }

        val detail = "You have logged $totalSessions sessions across $activeWeeks active " +
            "${if (activeWeeks == 1) "week" else "weeks"}, averaging " +
            "${formatRate(perWeek)} sessions per week over the last 8 weeks."

        val nextStep = when (level) {
            ProgressLevel.STARTING ->
                "Log ${MIN_SESSIONS_FOR_LEVEL - totalSessions} more " +
                    "${if (MIN_SESSIONS_FOR_LEVEL - totalSessions == 1) "session" else "sessions"} to reach Beginner."
            ProgressLevel.BEGINNER ->
                "Training at least twice a week for 8 different weeks moves you to Intermediate."
            ProgressLevel.INTERMEDIATE ->
                "Averaging 3 sessions a week across 24 active weeks moves you to Advanced."
            ProgressLevel.ADVANCED ->
                "Advanced is the top tier here — keeping the habit is what maintains it."
        }

        return LevelAssessment(
            level = level,
            headline = level.displayName,
            benchmark = benchmarkText(),
            detail = detail,
            consistencyWeeks = activeWeeks,
            sessionsLast28Days = last28,
            sessionsPerWeek = perWeek,
            nextStepHint = nextStep,
        )
    }

    private fun benchmarkText(): String =
        "How the tiers are defined: Getting started = fewer than $MIN_SESSIONS_FOR_LEVEL logged sessions. " +
            "Beginner = training regularly for under 8 separate weeks. " +
            "Intermediate = 8 or more active weeks averaging 2 sessions per week. " +
            "Advanced = 24 or more active weeks averaging 3 sessions per week. " +
            "Only sessions you logged are counted."

    private fun formatRate(rate: Double): String =
        String.format(java.util.Locale.ROOT, "%.1f", rate)
}
