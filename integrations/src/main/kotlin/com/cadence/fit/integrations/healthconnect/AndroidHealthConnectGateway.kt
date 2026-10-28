package com.cadence.fit.integrations.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.cadence.fit.domain.model.SleepStage
import java.time.Duration
import java.time.Instant

/**
 * Thin binding onto the Jetpack Health Connect client.
 *
 * This is the only class in the project that talks to the SDK. Everything above
 * it works against [HealthConnectGateway], so the interesting behaviour is
 * testable off-device; what remains here is glue that needs a real device to
 * exercise (see the README for the verification steps).
 */
class AndroidHealthConnectGateway(private val context: Context) : HealthConnectGateway {

    override suspend fun sdkStatus(): HealthConnectSdkStatus =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectSdkStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectSdkStatus.UPDATE_REQUIRED

            else -> HealthConnectSdkStatus.UNAVAILABLE
        }

    override suspend fun hasSleepPermission(): Boolean {
        val client = clientOrNull() ?: return false
        return runCatching {
            client.permissionController
                .getGrantedPermissions()
                .contains(HealthPermission.getReadPermission(SleepSessionRecord::class))
        }.getOrDefault(false)
    }

    override suspend fun readSleepRecords(fromMs: Long, toMs: Long): List<RawSleepRecord> {
        val client = clientOrNull() ?: return emptyList()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(fromMs),
                    Instant.ofEpochMilli(toMs),
                ),
            ),
        )
        return response.records.map { record -> record.toRaw() }
    }

    private fun clientOrNull(): HealthConnectClient? =
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()

    private fun SleepSessionRecord.toRaw(): RawSleepRecord {
        val stageMinutes = mutableMapOf<SleepStage, Int>()
        stages.forEach { stage ->
            val minutes = Duration.between(stage.startTime, stage.endTime).toMinutes().toInt()
            if (minutes <= 0) return@forEach
            val mapped = stage.stage.toSleepStage()
            stageMinutes[mapped] = (stageMinutes[mapped] ?: 0) + minutes
        }
        return RawSleepRecord(
            id = metadata.id,
            startMs = startTime.toEpochMilli(),
            endMs = endTime.toEpochMilli(),
            stageMinutes = stageMinutes,
            sourcePackage = metadata.dataOrigin.packageName,
            sourceLabel = metadata.dataOrigin.packageName,
        )
    }
}

/**
 * Health Connect stage codes. `SLEEPING` and `LIGHT` are both reported as light
 * sleep because that is the only distinction the app uses; anything unknown is
 * kept as [SleepStage.UNKNOWN] rather than being guessed at.
 */
internal fun Int.toSleepStage(): SleepStage = when (this) {
    SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepStage.AWAKE
    SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.LIGHT
    SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
    SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
    else -> SleepStage.UNKNOWN
}
