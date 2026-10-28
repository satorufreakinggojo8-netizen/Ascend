package com.cadence.fit.integrations.healthconnect

import com.cadence.fit.domain.model.SleepAvailability
import com.cadence.fit.domain.model.SleepStage
import com.cadence.fit.domain.model.SleepSummary

/**
 * Pure conversion from Health Connect records into the app's minimal sleep model.
 *
 * Rules worth stating out loud:
 *  * "time asleep" excludes recorded awake time when stages are available, and
 *    falls back to the session window when they are not;
 *  * a record with an end before its start is treated as zero-length rather than
 *    being dropped silently or producing a negative duration.
 */
object SleepSummaryMapper {

    fun summaryOf(record: RawSleepRecord): SleepSummary {
        val windowMinutes = ((record.endMs - record.startMs) / 60_000L).toInt().coerceAtLeast(0)
        val awakeMinutes = record.stageMinutes[SleepStage.AWAKE] ?: 0
        val stageTotal = record.stageMinutes.values.sum()

        val duration = when {
            stageTotal > 0 -> (stageTotal - awakeMinutes).coerceIn(0, windowMinutes)
            else -> windowMinutes
        }

        return SleepSummary(
            id = record.id,
            startMs = record.startMs,
            endMs = record.endMs,
            durationMinutes = duration,
            stages = record.stageMinutes,
            sourcePackage = record.sourcePackage,
            sourceLabel = record.sourceLabel,
        )
    }

    fun summariesOf(records: List<RawSleepRecord>): List<SleepSummary> =
        records.map(::summaryOf).sortedByDescending { it.endMs }

    fun availabilityOf(status: HealthConnectSdkStatus): SleepAvailability = when (status) {
        HealthConnectSdkStatus.AVAILABLE -> SleepAvailability.AVAILABLE
        HealthConnectSdkStatus.UPDATE_REQUIRED -> SleepAvailability.PROVIDER_UPDATE_REQUIRED
        HealthConnectSdkStatus.UNAVAILABLE -> SleepAvailability.NOT_SUPPORTED
    }
}
