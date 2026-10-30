package com.cadence.fit.domain.insight

import com.cadence.fit.domain.math.WorkoutMath
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.domain.units.WeightUnit
import java.time.ZoneId

/** Selectable ranges for the charts, each with a plain-language label. */
enum class ChartWindow(val days: Int, val displayName: String) {
    FOUR_WEEKS(28, "4 weeks"),
    TWELVE_WEEKS(84, "12 weeks"),
    SIX_MONTHS(182, "6 months"),
    ONE_YEAR(365, "1 year"),
    ;

    companion object {
        val default: ChartWindow = TWELVE_WEEKS
    }
}

/** `label` is pre-formatted for the axis so the UI does not do date maths. */
data class VolumePoint(
    val epochMs: Long,
    val label: String,
    val volumeKg: Double,
    val sessions: Int,
)

data class ConsistencyPoint(
    val epochMs: Long,
    val label: String,
    val sessions: Int,
    val targetSessions: Int,
)

data class StrengthPoint(
    val epochMs: Long,
    val label: String,
    val sessionId: String,
    val bestE1rmKg: Double,
    val topSetKg: Double,
    val topSetReps: Int,
    val totalVolumeKg: Double,
    /** Estimated 1RM already converted into the display unit. */
    val displayE1rm: Double,
    val displayTopSet: Double,
)

data class ExerciseStrengthSeries(
    val exerciseId: String,
    val exerciseName: String,
    val unit: WeightUnit,
    val points: List<StrengthPoint>,
    val firstE1rmKg: Double,
    val lastE1rmKg: Double,
) {
    val changeKg: Double get() = lastE1rmKg - firstE1rmKg

    val changeFraction: Double?
        get() = if (firstE1rmKg <= 0.0) null else changeKg / firstE1rmKg
}

/** The small numbers the home screen shows under today's workout. */
data class ProgressSummary(
    val sessionsThisWeek: Int,
    val targetSessionsPerWeek: Int,
    val volumeThisWeekKg: Double,
    val volumeLastWeekKg: Double,
    val streakWeeks: Int,
    val sessionsLast28Days: Int,
    val recordsLast28Days: Int,
    val topExerciseId: String?,
    val averageSessionMinutes: Int?,
    val totalSessions: Int,
) {
    val weeklyTargetMet: Boolean get() = sessionsThisWeek >= targetSessionsPerWeek

    /** Null when there is nothing to compare against yet. */
    val volumeChangeFraction: Double?
        get() = if (volumeLastWeekKg <= 0.0) null
        else (volumeThisWeekKg - volumeLastWeekKg) / volumeLastWeekKg
}

object ChartData {

    /** Total working volume per week, oldest first. */
    fun volumeByWeek(
        sessions: List<WorkoutSession>,
        window: ChartWindow,
        nowMs: Long,
        zone: ZoneId = TimeRanges.zone(),
    ): List<VolumePoint> {
        val weeks = (window.days / 7).coerceAtLeast(1)
        return TimeRanges.weeklyBuckets(sessions, weeks, nowMs, zone).map { bucket ->
            VolumePoint(
                epochMs = bucket.startMs,
                label = TimeRanges.formatShortDay(bucket.startMs, zone),
                volumeKg = bucket.volumeKg,
                sessions = bucket.sessions,
            )
        }
    }

    /** Sessions per week against the user's own weekly target. */
    fun consistencyByWeek(
        sessions: List<WorkoutSession>,
        window: ChartWindow,
        nowMs: Long,
        targetSessionsPerWeek: Int,
        zone: ZoneId = TimeRanges.zone(),
    ): List<ConsistencyPoint> {
        val weeks = (window.days / 7).coerceAtLeast(1)
        return TimeRanges.weeklyBuckets(sessions, weeks, nowMs, zone).map { bucket ->
            ConsistencyPoint(
                epochMs = bucket.startMs,
                label = TimeRanges.formatShortDay(bucket.startMs, zone),
                sessions = bucket.sessions,
                targetSessions = targetSessionsPerWeek,
            )
        }
    }

    /**
     * Strength trend for one exercise: one point per session that trained it.
     * The estimated one-rep max is the headline number; the heaviest set is shown
     * alongside so the estimate can be sanity-checked.
     */
    fun strengthSeries(
        sessions: List<WorkoutSession>,
        exerciseId: String,
        exerciseName: String,
        unit: WeightUnit,
        window: ChartWindow,
        nowMs: Long,
        zone: ZoneId = TimeRanges.zone(),
    ): ExerciseStrengthSeries {
        val cutoff = nowMs - window.days * 24L * 60L * 60L * 1000L
        val history = WorkoutMath.exerciseHistory(sessions, exerciseId)
            .filter { it.completedAtMs >= cutoff }
        val points = history.map { entry ->
            val topSet = entry.sets.maxWithOrNull(compareBy({ it.weightKg }, { it.reps }))
            StrengthPoint(
                epochMs = entry.completedAtMs,
                label = TimeRanges.formatShortDay(entry.completedAtMs, zone),
                sessionId = entry.sessionId,
                bestE1rmKg = entry.bestE1rmKg,
                topSetKg = entry.topWeightKg,
                topSetReps = topSet?.reps ?: 0,
                totalVolumeKg = entry.totalVolumeKg,
                displayE1rm = unit.fromKilograms(entry.bestE1rmKg),
                displayTopSet = unit.fromKilograms(entry.topWeightKg),
            )
        }
        return ExerciseStrengthSeries(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            unit = unit,
            points = points,
            firstE1rmKg = points.firstOrNull()?.bestE1rmKg ?: 0.0,
            lastE1rmKg = points.lastOrNull()?.bestE1rmKg ?: 0.0,
        )
    }

    fun summary(
        sessions: List<WorkoutSession>,
        nowMs: Long,
        targetSessionsPerWeek: Int,
        zone: ZoneId = TimeRanges.zone(),
    ): ProgressSummary {
        val completed = sessions.filter { it.isComplete }
        val buckets = TimeRanges.weeklyBuckets(completed, 2, nowMs, zone)
        val thisWeek = buckets.lastOrNull()
        val lastWeek = buckets.dropLast(1).lastOrNull()
        val recent = TimeRanges.sessionsWithin(completed, 28, nowMs)
        val trackedExerciseIds = WorkoutMath.trainedExerciseIds(completed)
        val recentRecords = WorkoutMath.recentPersonalRecords(completed, trackedExerciseIds, nowMs, 28)
        val durations = recent.mapNotNull { it.durationMinutes }
        val topExercise = WorkoutMath.exerciseHistory(completed, trackedExerciseIds.firstOrNull().orEmpty())
            .lastOrNull()?.let { trackedExerciseIds.firstOrNull() }

        return ProgressSummary(
            sessionsThisWeek = thisWeek?.sessions ?: 0,
            targetSessionsPerWeek = targetSessionsPerWeek.coerceAtLeast(1),
            volumeThisWeekKg = thisWeek?.volumeKg ?: 0.0,
            volumeLastWeekKg = lastWeek?.volumeKg ?: 0.0,
            streakWeeks = TimeRanges.currentStreakWeeks(completed, nowMs, zone),
            sessionsLast28Days = recent.size,
            recordsLast28Days = recentRecords.size,
            topExerciseId = topExercise,
            averageSessionMinutes = durations.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            totalSessions = completed.size,
        )
    }
}
