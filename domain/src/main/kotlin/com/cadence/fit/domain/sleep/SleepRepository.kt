package com.cadence.fit.domain.sleep

import com.cadence.fit.domain.core.Clock
import com.cadence.fit.domain.model.SleepAvailability
import com.cadence.fit.domain.model.SleepConnectionStatus
import com.cadence.fit.domain.model.SleepPermissionState
import com.cadence.fit.domain.model.SleepRefreshResult
import com.cadence.fit.domain.model.SleepSummary

/**
 * Platform contract for reading sleep sessions.
 *
 * Implemented by `:integrations` with Android Health Connect. The interface is
 * deliberately tiny so the repository logic below can be unit-tested without a
 * device: `SleepSummary` is the *only* thing that crosses this boundary.
 */
interface SleepDataSource {
    /** Hardware/OS capability, independent of permission. */
    suspend fun availability(): SleepAvailability

    /** Whether the read-sleep permission is currently granted. */
    suspend fun hasPermission(): Boolean

    /**
     * Reads sessions overlapping the given window.
     *
     * Implementations may throw: [SleepRepository.refresh] turns a failure into a
     * `Failed` result so the UI can admit that reading did not work.
     */
    suspend fun readSessions(fromMs: Long, toMs: Long): List<SleepSummary>
}

/** Persistence for the opt-in flag and last-sync time (kept out of the domain). */
interface SleepStateStore {
    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)

    suspend fun lastSyncMs(): Long?

    /** `null` clears the stored time, which is what disconnecting should do. */
    suspend fun setLastSyncMs(ms: Long?)
}

/**
 * Decides whether Cadence may read sleep, reads the minimum it needs, and reports
 * every failure mode instead of pretending the connection works.
 */
class SleepRepository(
    private val source: SleepDataSource,
    private val stateStore: SleepStateStore,
    private val clock: Clock,
) {

    suspend fun status(): SleepConnectionStatus {
        val enabled = stateStore.isEnabled()
        val availability = source.availability()
        val permission = if (availability == SleepAvailability.AVAILABLE) {
            if (source.hasPermission()) SleepPermissionState.GRANTED else SleepPermissionState.UNKNOWN
        } else {
            SleepPermissionState.UNKNOWN
        }
        return SleepConnectionStatus(
            availability = availability,
            permission = permission,
            enabled = enabled,
            lastSyncMs = stateStore.lastSyncMs(),
        )
    }

    /** Called only after the user has read the explanation and turned sync on. */
    suspend fun enable(): SleepConnectionStatus {
        stateStore.setEnabled(true)
        return status()
    }

    suspend fun disable(): SleepConnectionStatus {
        stateStore.setEnabled(false)
        stateStore.setLastSyncMs(null)
        return status()
    }

    /** Records that the OS permission dialog came back granted / denied. */
    suspend fun recordPermission(granted: Boolean): SleepConnectionStatus {
        if (!granted) {
            // A denied prompt turns the opt-in off, so the app never keeps asking.
            // Sleep sessions are not stored at all; a later grant reads them again.
            stateStore.setEnabled(false)
        }
        return status()
    }

    suspend fun refresh(days: Int = DEFAULT_WINDOW_DAYS, nowMs: Long? = null): SleepRefreshResult {
        if (!stateStore.isEnabled()) return SleepRefreshResult.Disabled

        val availability = source.availability()
        if (availability != SleepAvailability.AVAILABLE) {
            return SleepRefreshResult.Unavailable(availability)
        }

        if (!source.hasPermission()) {
            return SleepRefreshResult.PermissionMissing
        }

        val now = nowMs ?: clock.nowMs()
        val from = now - days * MILLIS_PER_DAY
        return try {
            val sessions = source.readSessions(from, now).sortedByDescending { it.endMs }
            stateStore.setLastSyncMs(now)
            SleepRefreshResult.Success(sessions)
        } catch (throwable: Throwable) {
            SleepRefreshResult.Failed(
                throwable.message?.takeIf { it.isNotBlank() } ?: "Could not read sleep data",
            )
        }
    }

    companion object {
        /** Two weeks is enough for a trend without pulling in unnecessary history. */
        const val DEFAULT_WINDOW_DAYS = 14
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
