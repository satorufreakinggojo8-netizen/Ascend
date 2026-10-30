package com.cadence.fit.integrations.healthconnect

import com.cadence.fit.domain.model.SleepAvailability
import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.sleep.SleepDataSource

/**
 * Adapts Health Connect to the app's sleep contract.
 *
 * Only `SleepSummary` crosses back into the domain: the rest of the app has no
 * idea Health Connect exists, which keeps the integration replaceable.
 */
class HealthConnectSleepDataSource(
    private val gateway: HealthConnectGateway,
) : SleepDataSource {

    override suspend fun availability(): SleepAvailability =
        try {
            SleepSummaryMapper.availabilityOf(gateway.sdkStatus())
        } catch (error: Throwable) {
            // A broken provider is not the same as a supported device, but it is
            // definitely not usable — report it as unsupported rather than crashing.
            SleepAvailability.NOT_SUPPORTED
        }

    override suspend fun hasPermission(): Boolean =
        try {
            gateway.hasSleepPermission()
        } catch (error: Throwable) {
            false
        }

    override suspend fun readSessions(fromMs: Long, toMs: Long): List<SleepSummary> =
        SleepSummaryMapper.summariesOf(gateway.readSleepRecords(fromMs, toMs))
}
