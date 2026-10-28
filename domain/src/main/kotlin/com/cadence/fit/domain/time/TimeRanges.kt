package com.cadence.fit.domain.time

import com.cadence.fit.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class DayBucket(
    val startMs: Long,
    val day: LocalDate,
    val sessions: Int,
    val volumeKg: Double,
)

data class WeekBucket(
    val startMs: Long,
    val weekOf: LocalDate,
    val sessions: Int,
    val volumeKg: Double,
)

/**
 * Calendar helpers. Every function takes an explicit [ZoneId] (defaulting to the
 * device zone) so tests are not sensitive to the machine's timezone.
 */
object TimeRanges {

    val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val SHORT_DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun toLocalDate(ms: Long, zone: ZoneId = zone()): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun startOfDayMs(date: LocalDate, zone: ZoneId = zone()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfDayMs(ms: Long, zone: ZoneId = zone()): Long = startOfDayMs(toLocalDate(ms, zone), zone)

    fun startOfWeek(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    fun startOfWeekMs(ms: Long, zone: ZoneId = zone(), firstDay: DayOfWeek = DayOfWeek.MONDAY): Long =
        startOfDayMs(startOfWeek(toLocalDate(ms, zone), firstDay), zone)

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    fun formatDay(ms: Long, zone: ZoneId = zone()): String = toLocalDate(ms, zone).format(DAY_LABEL)

    fun formatShortDay(ms: Long, zone: ZoneId = zone()): String = toLocalDate(ms, zone).format(SHORT_DAY_LABEL)

    fun formatMonth(ms: Long, zone: ZoneId = zone()): String = toLocalDate(ms, zone).format(MONTH_LABEL)

    /** Today / Yesterday / a short date, for history lists. */
    fun relativeDay(ms: Long, nowMs: Long, zone: ZoneId = zone()): String {
        val date = toLocalDate(ms, zone)
        val today = toLocalDate(nowMs, zone)
        return when (daysBetween(date, today)) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> date.format(DAY_LABEL)
        }
    }

    /** One bucket per day, oldest first; days without sessions are included. */
    fun dailyBuckets(
        sessions: List<WorkoutSession>,
        days: Int,
        nowMs: Long,
        zone: ZoneId = zone(),
    ): List<DayBucket> {
        val today = toLocalDate(nowMs, zone)
        val completed = sessions.filter { it.isComplete }
        return (days - 1 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            val startMs = startOfDayMs(day, zone)
            val endMs = startOfDayMs(day.plusDays(1), zone)
            val inDay = completed.filter { it.completedAtMs >= startMs && it.completedAtMs < endMs }
            DayBucket(
                startMs = startMs,
                day = day,
                sessions = inDay.size,
                volumeKg = inDay.sumOf { it.totalVolumeKg },
            )
        }
    }

    /** One bucket per week, oldest first. */
    fun weeklyBuckets(
        sessions: List<WorkoutSession>,
        weeks: Int,
        nowMs: Long,
        zone: ZoneId = zone(),
        firstDay: DayOfWeek = DayOfWeek.MONDAY,
    ): List<WeekBucket> {
        val currentWeekStart = startOfWeek(toLocalDate(nowMs, zone), firstDay)
        val completed = sessions.filter { it.isComplete }
        return (weeks - 1 downTo 0).map { offset ->
            val weekStart = currentWeekStart.minusWeeks(offset.toLong())
            val startMs = startOfDayMs(weekStart, zone)
            val endMs = startOfDayMs(weekStart.plusWeeks(1), zone)
            val inWeek = completed.filter { it.completedAtMs >= startMs && it.completedAtMs < endMs }
            WeekBucket(
                startMs = startMs,
                weekOf = weekStart,
                sessions = inWeek.size,
                volumeKg = inWeek.sumOf { it.totalVolumeKg },
            )
        }
    }

    /** Sessions completed within the last [days] days, newest first. */
    fun sessionsWithin(sessions: List<WorkoutSession>, days: Int, nowMs: Long): List<WorkoutSession> {
        val cutoff = nowMs - days * 24L * 60L * 60L * 1000L
        return sessions.filter { it.isComplete && it.completedAtMs >= cutoff }
            .sortedByDescending { it.completedAtMs }
    }

    /** Consecutive weeks (ending this week) that contain at least one session. */
    fun currentStreakWeeks(sessions: List<WorkoutSession>, nowMs: Long, zone: ZoneId = zone()): Int {
        val completed = sessions.filter { it.isComplete }
        if (completed.isEmpty()) return 0
        val weeksWithSessions = completed
            .map { startOfWeek(toLocalDate(it.completedAtMs, zone)) }
            .toSet()
        val thisWeek = startOfWeek(toLocalDate(nowMs, zone))
        var streak = 0
        var cursor = thisWeek
        // A week that has not been trained yet does not break a streak until it ends.
        if (cursor !in weeksWithSessions) cursor = cursor.minusWeeks(1)
        while (cursor in weeksWithSessions) {
            streak += 1
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    /** Total number of distinct weeks that contain at least one completed session. */
    fun activeWeekCount(sessions: List<WorkoutSession>, zone: ZoneId = zone()): Int =
        sessions.filter { it.isComplete }
            .map { startOfWeek(toLocalDate(it.completedAtMs, zone)) }
            .toSet()
            .size
}
