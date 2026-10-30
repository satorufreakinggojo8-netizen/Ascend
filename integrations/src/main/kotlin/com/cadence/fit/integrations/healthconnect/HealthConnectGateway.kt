package com.cadence.fit.integrations.healthconnect

import com.cadence.fit.domain.model.SleepStage

/** Health Connect capability, reduced to the three cases the UI cares about. */
enum class HealthConnectSdkStatus { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/**
 * One sleep session, reduced to the minimum Cadence uses: timing, duration and
 * (optionally) coarse stage totals. Notes, ratings and free text are never read.
 */
data class RawSleepRecord(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val stageMinutes: Map<SleepStage, Int> = emptyMap(),
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
)

/**
 * The only Health Connect surface this app touches.
 *
 * Keeping it behind an interface means the sleep state machine can be tested
 * without a device or a real Health Connect installation.
 */
interface HealthConnectGateway {
    suspend fun sdkStatus(): HealthConnectSdkStatus

    /** Whether `android.permission.health.READ_SLEEP` is currently granted. */
    suspend fun hasSleepPermission(): Boolean

    /** Reads sleep sessions overlapping the window. Must never throw. */
    suspend fun readSleepRecords(fromMs: Long, toMs: Long): List<RawSleepRecord>
}

/**
 * Permissions and package names used by the integration.
 *
 * `READ_SLEEP` is the only permission Cadence ever requests. `WRITE_SLEEP` is
 * documented because it is the permission a *sleep app* needs in order to publish
 * sessions — it is never requested by Cadence.
 */
object HealthConnectPermissions {
    const val READ_SLEEP = "android.permission.health.READ_SLEEP"
    const val WRITE_SLEEP = "android.permission.health.WRITE_SLEEP"

    /** Exactly one permission, requested only after the benefit is explained. */
    val requested: Set<String> = setOf(READ_SLEEP)

    const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    const val RATIONALE_ACTION = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
}
