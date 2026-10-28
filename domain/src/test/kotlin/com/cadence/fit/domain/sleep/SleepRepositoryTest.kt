package com.cadence.fit.domain.sleep

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.DAY_MS
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.core.FixedClock
import com.cadence.fit.domain.model.SleepAvailability
import com.cadence.fit.domain.model.SleepPermissionState
import com.cadence.fit.domain.model.SleepRefreshResult
import com.cadence.fit.domain.model.SleepSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stand-in for the Android Health Connect client. */
private class FakeSleepSource(
    var availability: SleepAvailability = SleepAvailability.AVAILABLE,
    var permission: Boolean = true,
    var sessions: List<SleepSummary> = emptyList(),
    var failure: Throwable? = null,
) : SleepDataSource {
    var readCount = 0
    var lastFromMs: Long = 0
    var lastToMs: Long = 0

    override suspend fun availability(): SleepAvailability = availability

    override suspend fun hasPermission(): Boolean = permission

    override suspend fun readSessions(fromMs: Long, toMs: Long): List<SleepSummary> {
        readCount += 1
        lastFromMs = fromMs
        lastToMs = toMs
        failure?.let { throw it }
        return sessions.filter { it.endMs in fromMs..toMs }
    }
}

private class InMemorySleepState : SleepStateStore {
    private var enabled = false
    private var lastSyncMs: Long? = null

    override suspend fun isEnabled(): Boolean = enabled

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override suspend fun lastSyncMs(): Long? = lastSyncMs

    override suspend fun setLastSyncMs(ms: Long?) {
        lastSyncMs = ms
    }
}

class SleepRepositoryTest {

    private val source = FakeSleepSource()
    private val state = InMemorySleepState()
    private val clock = FixedClock(NOW)
    private val repository = SleepRepository(source, state, clock)

    @Test
    fun `sleep is off until the user turns it on`() = runTest {
        val status = repository.status()
        assertFalse(status.enabled)
        assertFalse(status.isConnected)
    }

    @Test
    fun `nothing is read while the feature is disabled`() = runTest {
        source.sessions = listOf(Fixtures.sleep(endMs = NOW, minutes = 420))

        val result = repository.refresh()

        assertEquals(SleepRefreshResult.Disabled, result)
        assertEquals(0, source.readCount)
    }

    @Test
    fun `a device without Health Connect reports that clearly and never reads`() = runTest {
        source.availability = SleepAvailability.NOT_SUPPORTED
        repository.enable()

        val result = repository.refresh()

        assertTrue(result is SleepRefreshResult.Unavailable)
        assertEquals(SleepAvailability.NOT_SUPPORTED, (result as SleepRefreshResult.Unavailable).availability)
        assertEquals(0, source.readCount)

        val status = repository.status()
        assertEquals(SleepAvailability.NOT_SUPPORTED, status.availability)
        assertEquals(SleepPermissionState.UNKNOWN, status.permission)
        assertFalse(status.isActionable)
    }

    @Test
    fun `a Health Connect install that needs an update is reported separately`() = runTest {
        source.availability = SleepAvailability.PROVIDER_UPDATE_REQUIRED
        repository.enable()

        val result = repository.refresh()

        assertTrue(result is SleepRefreshResult.Unavailable)
        assertEquals(
            SleepAvailability.PROVIDER_UPDATE_REQUIRED,
            (result as SleepRefreshResult.Unavailable).availability,
        )
        assertEquals(SleepAvailability.PROVIDER_UPDATE_REQUIRED, repository.status().availability)
    }

    @Test
    fun `a denied permission stops the read and is reported as missing`() = runTest {
        source.permission = false
        repository.enable()

        val result = repository.refresh()

        assertEquals(SleepRefreshResult.PermissionMissing, result)
        assertEquals(0, source.readCount)
        assertEquals(SleepPermissionState.UNKNOWN, repository.status().permission)
    }

    @Test
    fun `granted permission on a capable device is reported as granted`() = runTest {
        repository.enable()
        val status = repository.status()
        assertEquals(SleepPermissionState.GRANTED, status.permission)
        assertTrue(status.isConnected)
    }

    @Test
    fun `a successful refresh returns sessions newest first and records the sync time`() = runTest {
        val yesterday = Fixtures.sleep(endMs = NOW - DAY_MS, minutes = 400)
        val lastNight = Fixtures.sleep(endMs = NOW - 3600_000L, minutes = 450, id = "last-night")
        source.sessions = listOf(yesterday, lastNight)
        repository.enable()

        val result = repository.refresh()

        assertTrue(result is SleepRefreshResult.Success)
        val sessions = (result as SleepRefreshResult.Success).sessions
        assertEquals(listOf("last-night", yesterday.id), sessions.map { it.id })
        assertEquals(NOW, state.lastSyncMs())
        assertEquals(NOW, repository.status().lastSyncMs)
    }

    @Test
    fun `the read window is bounded so old history is not pulled in`() = runTest {
        repository.enable()

        repository.refresh(days = 14)

        assertEquals(NOW - 14 * DAY_MS, source.lastFromMs)
        assertEquals(NOW, source.lastToMs)
    }

    @Test
    fun `sessions older than the window are ignored`() = runTest {
        source.sessions = listOf(
            Fixtures.sleep(endMs = NOW - 2 * DAY_MS, minutes = 420),
            Fixtures.sleep(endMs = NOW - 60 * DAY_MS, minutes = 420, id = "ancient"),
        )
        repository.enable()

        val result = repository.refresh() as SleepRefreshResult.Success

        assertEquals(1, result.sessions.size)
        assertEquals(2 * DAY_MS, NOW - result.sessions.first().endMs)
    }

    @Test
    fun `a read failure is surfaced instead of pretending the connection works`() = runTest {
        source.failure = IllegalStateException("Health Connect is busy")
        repository.enable()

        val result = repository.refresh()

        assertTrue(result is SleepRefreshResult.Failed)
        assertEquals("Health Connect is busy", (result as SleepRefreshResult.Failed).message)
        assertNull(state.lastSyncMs())
    }

    @Test
    fun `a failure without a message still reports something readable`() = runTest {
        source.failure = IllegalStateException()
        repository.enable()

        val result = repository.refresh() as SleepRefreshResult.Failed
        assertEquals("Could not read sleep data", result.message)
    }

    @Test
    fun `revoking permission switches the feature back off`() = runTest {
        repository.enable()
        assertTrue(repository.status().enabled)

        val status = repository.recordPermission(granted = false)

        assertFalse(status.enabled)
        assertEquals(SleepRefreshResult.Disabled, repository.refresh())
    }

    @Test
    fun `the user can disconnect at any time and the stored sync time is cleared`() = runTest {
        source.sessions = listOf(Fixtures.sleep(endMs = NOW, minutes = 420))
        repository.enable()
        repository.refresh()

        val status = repository.disable()

        assertFalse(status.enabled)
        assertNull(status.lastSyncMs)
        assertEquals(SleepRefreshResult.Disabled, repository.refresh())
    }

    @Test
    fun `re-granting permission does not read anything until the user enables it again`() = runTest {
        repository.recordPermission(granted = false)
        repository.recordPermission(granted = true)

        assertEquals(SleepRefreshResult.Disabled, repository.refresh())
        assertEquals(0, source.readCount)
    }
}
