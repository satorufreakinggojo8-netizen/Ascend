package com.cadence.fit.domain.coach

/** Where a guidance topic sends the user when they want the underlying detail. */
enum class CoachTopicDestination { EXERCISE, PROGRESS, SLEEP, NONE }

/**
 * One guidance topic on a coach page.
 *
 * [dataLine] is always a measured value (a count, a logged load, a rep range), so
 * the page can separate "what happened" from "what to consider". Topics that the
 * data does not support are simply absent from [CoachDirector.topics].
 */
data class CoachTopic(
    val id: String,
    val title: String,
    val summary: String,
    val detail: String,
    val dataLine: String?,
    val destination: CoachTopicDestination = CoachTopicDestination.NONE,
    val exerciseId: String? = null,
)

/**
 * What the coach page knows about the user's training right now.
 *
 * Everything here comes from stored sessions, the plan and the optional sleep
 * opt-in. Nothing is inferred about the body, and sleep is only ever a count of
 * nights that are actually available.
 */
data class CoachContext(
    val hasActiveSession: Boolean = false,
    val workingSetsThisSession: Int = 0,
    val sessionsCompletedToday: Int = 0,
    val recordsThisWeek: Int = 0,
    /** Headline of the first actionable suggestion, if the plan has one. */
    val suggestionHeadline: String? = null,
    val suggestionExerciseId: String? = null,
    val suggestionExerciseName: String? = null,
    val plateauHeadline: String? = null,
    val sessionsThisWeek: Int = 0,
    val targetSessionsPerWeek: Int = 3,
    val sleepNightsAvailable: Int = 0,
    /** True when the user has opened a topic on the coach page. */
    val topicOpen: Boolean = false,
) {
    val hasSuggestion: Boolean get() = suggestionHeadline != null

    val hasPlateau: Boolean get() = plateauHeadline != null
}

/**
 * The coach's current state: which photo to show, and the one line under it.
 *
 * The line is Cadence's own summary of measured data. It is never presented as a
 * quotation or as coaching advice attributed to the person in the photo.
 */
data class CoachPresence(
    val expression: CoachExpression,
    val headline: String,
    val detail: String,
    /** Accessibility description for the portrait, e.g. "David Laid, encouraging". */
    val photoDescription: String,
)

object CoachDirector {

    /**
     * Precedence is deliberate and tested:
     * 1. A topic the user just opened is what the page is doing -> EXPLAINING.
     * 2. A session in progress (or work already logged today) -> ENCOURAGING.
     * 3. A suggestion or plateau the app can explain -> SPEAKING.
     * 4. Otherwise IDLE.
     */
    fun presence(context: CoachContext, coach: CoachProfile = CoachRoster.davidLaid): CoachPresence {
        val expression = when {
            context.topicOpen -> CoachExpression.EXPLAINING
            context.hasActiveSession -> CoachExpression.ENCOURAGING
            context.sessionsCompletedToday > 0 || context.recordsThisWeek > 0 -> CoachExpression.ENCOURAGING
            context.hasSuggestion || context.hasPlateau -> CoachExpression.SPEAKING
            else -> CoachExpression.IDLE
        }

        val headline: String
        val detail: String
        when (expression) {
            CoachExpression.EXPLAINING -> {
                headline = "Explaining the numbers"
                detail = "You opened a topic. Everything in it is worked out from your own logged " +
                    "sets — none of it is a quote from the coach."
            }

            CoachExpression.ENCOURAGING -> {
                headline = if (context.hasActiveSession) "Mid-session" else "Work logged"
                detail = when {
                    context.hasActiveSession -> "${setsLabel(context.workingSetsThisSession)} logged. " +
                        "The logger, rest timer and plate maths stay within one tap."

                    context.recordsThisWeek > 0 -> "You set ${recordsLabel(context.recordsThisWeek)} " +
                        "this week. Add the next session when you are ready."

                    else -> "${sessionsLabel(context.sessionsCompletedToday)} finished today. " +
                        "Add the next session when you are ready."
                }
            }

            CoachExpression.SPEAKING -> {
                headline = context.suggestionHeadline ?: context.plateauHeadline ?: "A suggestion is ready"
                detail = "It is on the guidance list below. Nothing changes until you accept or edit it."
            }

            CoachExpression.IDLE -> {
                headline = "Nothing waiting"
                detail = "Log a workout and this page starts explaining your own numbers."
            }
        }

        return CoachPresence(
            expression = expression,
            headline = headline,
            detail = detail,
            photoDescription = "${coach.name}, ${expression.displayName.lowercase()}",
        )
    }

    /**
     * Guidance topics, highest-signal first. Each one carries the measured value it
     * is based on; a topic whose data is missing is not shown at all, so the page
     * never explains something the user has not done.
     */
    fun topics(context: CoachContext): List<CoachTopic> {
        val topics = mutableListOf<CoachTopic>()

        if (context.suggestionHeadline != null) {
            topics += CoachTopic(
                id = "progression",
                title = context.suggestionExerciseName?.let { "Next step · $it" } ?: "Next step",
                summary = context.suggestionHeadline,
                detail = "Cadence compares your last logged sets with the routine's rep range and " +
                    "your goal, then suggests the smallest sensible change. Load only moves in " +
                    "small steps, and you can accept, edit or ignore it in the logger.",
                dataLine = "Based on your last logged session for this exercise",
                destination = if (context.suggestionExerciseId != null) {
                    CoachTopicDestination.EXERCISE
                } else {
                    CoachTopicDestination.NONE
                },
                exerciseId = context.suggestionExerciseId,
            )
        }

        if (context.plateauHeadline != null) {
            topics += CoachTopic(
                id = "plateau",
                title = "Plateau check",
                summary = context.plateauHeadline,
                detail = "Several sessions at the same load and reps usually mean it is time to " +
                    "change one thing, not everything: hold the weight and add a rep, take a " +
                    "deload week, or check that rest and technique are unchanged. Cadence never " +
                    "raises a weight for you.",
                dataLine = "Several recent sessions with no increase in top set or reps",
                destination = CoachTopicDestination.PROGRESS,
            )
        }

        topics += CoachTopic(
            id = "consistency",
            title = "Consistency",
            summary = if (context.targetSessionsPerWeek <= 0) {
                "No weekly target set"
            } else {
                "${context.sessionsThisWeek} of ${context.targetSessionsPerWeek} sessions this week"
            },
            detail = "Only completed sessions count. A skipped day is never turned into a " +
                "completed one, and a scheduled day you have not done yet does not count either.",
            dataLine = "${sessionsLabel(context.sessionsThisWeek)} logged this week",
            destination = CoachTopicDestination.PROGRESS,
        )

        if (context.sleepNightsAvailable > 0) {
            topics += CoachTopic(
                id = "recovery",
                title = "Recovery context",
                summary = "${nightsLabel(context.sleepNightsAvailable)} available",
                detail = "Sleep is optional context you switched on, not a verdict. It never " +
                    "changes a planned weight or set count by itself, and Cadence does not read " +
                    "any journal notes. If it is not useful, turn it off and nothing else changes.",
                dataLine = "Read from Health Connect, only while the sleep switch is on",
                destination = CoachTopicDestination.SLEEP,
            )
        }

        return topics
    }

    private fun setsLabel(count: Int): String =
        if (count == 1) "1 working set" else "$count working sets"

    private fun sessionsLabel(count: Int): String =
        if (count == 1) "1 session" else "$count sessions"

    private fun recordsLabel(count: Int): String =
        if (count == 1) "1 best set" else "$count best sets"

    private fun nightsLabel(count: Int): String =
        if (count == 1) "1 recorded night" else "$count recorded nights"
}
