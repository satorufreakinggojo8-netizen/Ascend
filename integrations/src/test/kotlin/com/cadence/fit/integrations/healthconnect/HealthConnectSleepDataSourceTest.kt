package com.cadence.fit.integrations.healthconnect

import com.cadence.fit.domain.model.SleepAvailability
import com.cadence.fit.domain.model.SleepStage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGateway(
    var status: HealthConnectSdkStatus = HealthConnectSdkStatus.AVAILABLE,
    var permission: Boolean = true,
    var records: List<RawSleepRecord> = emptyList(),
    var statusFailure: Throwable? = null,
    var permissionFailure: Throwable? = null,
    var readFailure: Throwable? = null,
) : HealthConnectGateway {
    var readCount = 0

    override suspend fun sdkStatus(): HealthConnectSdkStatus {
        statusFailure?.let { throw it }
        return status
    }

    override suspend fun hasSleepPermission(): Boolean {
        permissionFailure?.let { throw it }
        return permission
    }

    override suspend fun readSleepRecords(fromMs: Long, toMs: Long): List<RawSleepRecord> {
        readCount += 1
        readFailure?.let { throw it }
        return records
    }
}

class HealthConnectSleepDataSourceTest {

    private val hour = 60L * 60L * 1000L
    private val nightStart = 1_700_000_000_000L
    private val nightEnd = 1_700_000_000_000L + 8 * hour

    @Test
    fun `an available provider reports as available`() = runTest {
        val source = HealthConnectSleepDataSource(FakeGateway(status = HealthConnectSdkStatus.AVAILABLE))
        assertEquals(SleepAvailability.AVAILABLE, source.availability())
    }

    @Test
    fun `a provider that needs an update is reported separately from unsupported`() = runTest {
        val needsUpdate = HealthConnectSleepDataSource(
            FakeGateway(status = HealthConnectSdkStatus.UPDATE_REQUIRED),
        )
        val unsupported = HealthConnectSleepDataSource(
            FakeGateway(status = HealthConnectSdkStatus.UNAVAILABLE),
        )

        assertEquals(SleepAvailability.PROVIDER_UPDATE_REQUIRED, needsUpdate.availability())
        assertEquals(SleepAvailability.NOT_SUPPORTED, unsupported.availability())
    }

    @Test
    fun `a crashing provider is reported as unsupported instead of crashing the app`() = runTest {
        val source = HealthConnectSleepDataSource(
            FakeGateway(statusFailure = IllegalStateException("provider gone")),
        )
        assertEquals(SleepAvailability.NOT_SUPPORTED, source.availability())
    }

    @Test
    fun `permission is reported faithfully`() = runTest {
        assertTrue(HealthConnectSleepDataSource(FakeGateway(permission = true)).hasPermission())
        assertFalse(HealthConnectSleepDataSource(FakeGateway(permission = false)).hasPermission())
    }

    @Test
    fun `a permission check that throws is treated as not granted`() = runTest {
        val source = HealthConnectSleepDataSource(
            FakeGateway(permissionFailure = SecurityException("denied")),
        )
        assertFalse(source.hasPermission())
    }

    @Test
    fun `records are converted into sessions with duration and source`() = runTest {
        val gateway = FakeGateway(
            records = listOf(
                RawSleepRecord(
                    id = "night-1",
                    startMs = nightStart,
                    endMs = nightEnd,
                    stageMinutes = mapOf(
                        SleepStage.LIGHT to 240,
                        SleepStage.DEEP to 90,
                        SleepStage.REM to 60,
                        SleepStage.AWAKE to 30,
                    ),
                    sourcePackage = "com.example.nocta",
                ),
            ),
        )
        val source = HealthConnectSleepDataSource(gateway)

        val sessions = source.readSessions(nightStart - hour, nightEnd + hour)

        assertEquals(1, sessions.size)
        val session = sessions.single()
        assertEquals("night-1", session.id)
        assertEquals(nightStart, session.startMs)
        assertEquals(nightEnd, session.endMs)
        // 240 + 90 + 60 = 390 minutes asleep; the 30 awake minutes are not sleep.
        assertEquals(390, session.durationMinutes)
        assertEquals("com.example.nocta", session.sourcePackage)
    }

    @Test
    fun `a session without stages falls back to the recorded window`() = runTest {
        val gateway = FakeGateway(
            records = listOf(RawSleepRecord(id = "night-1", startMs = nightStart, endMs = nightEnd)),
        )

        val session = HealthConnectSleepDataSource(gateway).readSessions(0, Long.MAX_VALUE).single()

        assertEquals(480, session.durationMinutes)
        assertTrue(session.stages.isEmpty())
    }

    @Test
    fun `a malformed record produces zero duration rather than a negative one`() = runTest {
        val gateway = FakeGateway(
            records = listOf(
                RawSleepRecord(id = "broken", startMs = nightEnd, endMs = nightStart),
            ),
        )

        val session = HealthConnectSleepDataSource(gateway).readSessions(0, Long.MAX_VALUE).single()

        assertEquals(0, session.durationMinutes)
    }

    @Test
    fun `stages that exceed the window are clamped to it`() = runTest {
        val gateway = FakeGateway(
            records = listOf(
                RawSleepRecord(
                    id = "odd",
                    startMs = nightStart,
                    endMs = nightStart + 2 * hour,
                    stageMinutes = mapOf(SleepStage.DEEP to 300, SleepStage.LIGHT to 300),
                ),
            ),
        )

        val session = HealthConnectSleepDataSource(gateway).readSessions(0, Long.MAX_VALUE).single()

        assertEquals(120, session.durationMinutes)
    }

    @Test
    fun `sessions come back newest first`() = runTest {
        val gateway = FakeGateway(
            records = listOf(
                RawSleepRecord(id = "older", startMs = nightStart - 24 * hour, endMs = nightStart - 16 * hour),
                RawSleepRecord(id = "newer", startMs = nightStart, endMs = nightEnd),
            ),
        )

        val sessions = HealthConnectSleepDataSource(gateway).readSessions(0, Long.MAX_VALUE)

        assertEquals(listOf("newer", "older"), sessions.map { it.id })
    }

    @Test
    fun `no records means no sessions and not an error`() = runTest {
        val source = HealthConnectSleepDataSource(FakeGateway(records = emptyList()))
        assertTrue(source.readSessions(0, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun `only the sleep permission is requested`() {
        assertEquals(setOf(HealthConnectPermissions.READ_SLEEP), HealthConnectPermissions.requested)
        assertFalse(HealthConnectPermissions.requested.contains(HealthConnectPermissions.WRITE_SLEEP))
        assertEquals("android.permission.health.READ_SLEEP", HealthConnectPermissions.READ_SLEEP)
    }

    @Test
    fun `the rationale action targets Health Connect`() {
        assertEquals(
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
            HealthConnectPermissions.RATIONALE_ACTION,
        )
    }

    @Test
    fun `no session is produced from an empty record list`() {
        assertTrue(SleepSummaryMapper.summariesOf(emptyList()).isEmpty())
    }

    @Test
    fun `an unknown stage is kept rather than silently dropped`() {
        val record = RawSleepRecord(
            id = "x",
            startMs = nightStart,
            endMs = nightEnd,
            stageMinutes = mapOf(SleepStage.UNKNOWN to 60),
        )
        val summary = SleepSummaryMapper.summaryOf(record)

        assertEquals(SleepStage.UNKNOWN, summary.stages.keys.first())
        assertNull(summary.sourceLabel)
    }
}
