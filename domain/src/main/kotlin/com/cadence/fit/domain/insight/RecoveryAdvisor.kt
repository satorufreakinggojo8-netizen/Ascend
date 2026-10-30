package com.cadence.fit.domain.insight

import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.time.TimeRanges
import java.time.ZoneId

enum class RecoveryLevel(val displayName: String) {
    UNKNOWN("No sleep data"),
    BELOW_USUAL("Shorter than usual"),
    ABOUT_USUAL("Close to your usual"),
    WELL_RESTED("Longer than usual"),
}

data class SleepPoint(
    val epochMs: Long,
    val label: String,
    val minutes: Int,
)

data class RecoveryContext(
    val level: RecoveryLevel,
    val headline: String,
    val detail: String,
    val lastNightMinutes: Int?,
    val recentAverageMinutes: Int?,
    val lastSessionStartMs: Long?,
    val lastSessionEndMs: Long?,
    val sourceLabel: String?,
    val trend: List<SleepPoint>,
    val isSuggestionOnly: Boolean = true,
) {
    /** Always shown wherever recovery context appears. */
    val disclaimer: String =
        "Sleep context is shown for information only. It is not medical advice, and Cadence never " +
            "claims a workout result was caused by how you slept."
}

/**
 * Turns raw sleep durations into a short, calm piece of context.
 *
 * Rules:
 *  * only duration and session timing are used — never notes or ratings;
 *  * a suggestion is always phrased as an *option*, never as a diagnosis;
 *  * results are phrased as observations about sleep, not about training outcomes.
 */
object RecoveryAdvisor {

    /** Differences smaller than this are treated as "the same as usual". */
    const val NOISE_MINUTES = 45

    fun assess(
        sessions: List<SleepSummary>,
        nowMs: Long,
        enabled: Boolean,
        zone: ZoneId = TimeRanges.zone(),
    ): RecoveryContext? {
        if (!enabled || sessions.isEmpty()) return null

        val ordered = sessions.sortedBy { it.endMs }
        val lastNight = ordered.last()
        val earlier = ordered.dropLast(1)
        val average = earlier.takeIf { it.isNotEmpty() }
            ?.map { it.durationMinutes }
            ?.average()
            ?.toInt()

        val trend = ordered.takeLast(14).map { session ->
            SleepPoint(
                epochMs = session.endMs,
                label = TimeRanges.formatShortDay(session.endMs, zone),
                minutes = session.durationMinutes,
            )
        }

        val level = when {
            average == null -> RecoveryLevel.ABOUT_USUAL
            lastNight.durationMinutes <= average - NOISE_MINUTES -> RecoveryLevel.BELOW_USUAL
            lastNight.durationMinutes >= average + NOISE_MINUTES -> RecoveryLevel.WELL_RESTED
            else -> RecoveryLevel.ABOUT_USUAL
        }

        val durationText = formatMinutes(lastNight.durationMinutes)
        val headline = when (level) {
            RecoveryLevel.BELOW_USUAL -> "Last night: $durationText"
            RecoveryLevel.ABOUT_USUAL -> "Last night: $durationText"
            RecoveryLevel.WELL_RESTED -> "Last night: $durationText"
            RecoveryLevel.UNKNOWN -> "No sleep data yet"
        }

        val detail = when (level) {
            RecoveryLevel.BELOW_USUAL -> buildString {
                append("That is about ")
                append(formatMinutes((average ?: lastNight.durationMinutes) - lastNight.durationMinutes))
                append(" less than your recent average of ")
                append(formatMinutes(average ?: lastNight.durationMinutes))
                append(". If today feels heavier than usual, repeating last session's weights instead of ")
                append("adding load is a reasonable option. It is also fine to train as planned.")
            }

            RecoveryLevel.ABOUT_USUAL -> buildString {
                append("That is close to your recent average")
                if (average != null) append(" of ${formatMinutes(average)}")
                append(". Nothing here suggests changing your plan.")
            }

            RecoveryLevel.WELL_RESTED -> buildString {
                append("That is about ")
                append(formatMinutes(lastNight.durationMinutes - (average ?: lastNight.durationMinutes)))
                append(" more than your recent average")
                if (average != null) append(" of ${formatMinutes(average)}")
                append(". How you feel during the warm-up is still the better guide.")
            }

            RecoveryLevel.UNKNOWN -> "Connect a sleep source to see recovery context here."
        }

        return RecoveryContext(
            level = level,
            headline = headline,
            detail = detail,
            lastNightMinutes = lastNight.durationMinutes,
            recentAverageMinutes = average,
            lastSessionStartMs = lastNight.startMs,
            lastSessionEndMs = lastNight.endMs,
            sourceLabel = lastNight.sourceLabel ?: lastNight.sourcePackage,
            trend = trend,
        )
    }

    fun formatMinutes(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val remainder = safe % 60
        return when {
            hours == 0 -> "${remainder}m"
            remainder == 0 -> "${hours}h"
            else -> "${hours}h ${remainder}m"
        }
    }
}
