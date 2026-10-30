package com.cadence.fit.domain.backup

import com.cadence.fit.domain.Fixtures
import com.cadence.fit.domain.Fixtures.NOW
import com.cadence.fit.domain.model.AppSettings
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.units.WeightUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun document(): BackupDocument {
        val settings = AppSettings(
            weightUnit = WeightUnit.LB,
            showProgressLevel = false,
            sleepInsightsEnabled = true,
            restTimerSoundEnabled = false,
            keepScreenOnDuringWorkout = false,
            barWeightKg = 15.0,
            barWeightLb = 35.0,
        )
        val routine = Fixtures.routine(
            name = "Upper A",
            goal = TrainingGoal.MUSCLE_GAIN,
            exercises = listOf(
                Fixtures.routineExercise("barbell-bench-press", targetSets = 4, repRange = 6..8, restSeconds = 120),
                Fixtures.routineExercise("one-arm-dumbbell-row", targetSets = 3, supersetGroup = 0),
            ),
        )
        val session = Fixtures.session(
            id = "session-1",
            atMs = NOW,
            sets = listOf(
                Fixtures.set("barbell-bench-press", 60.0, 10, setIndex = 1, rpe = 8.5),
                Fixtures.set("barbell-bench-press", 60.0, 10, setIndex = 2),
                Fixtures.set("barbell-bench-press", 40.0, 10, setIndex = 3, isWarmup = true),
            ),
        ).copy(note = "Felt strong", routineName = "Upper A")

        return BackupDocument(
            exportedAtMs = NOW,
            appVersion = "0.1.0",
            profile = Fixtures.profile(
                location = TrainingLocation.HOME,
                equipment = setOf(Equipment.DUMBBELL, Equipment.BENCH, Equipment.BODYWEIGHT),
                experience = ExperienceLevel.INTERMEDIATE,
                goal = TrainingGoal.MUSCLE_GAIN,
                daysPerWeek = 4,
                sessionMinutes = 60,
            ),
            settings = settings,
            routines = listOf(routine),
            sessions = listOf(session),
            schedule = listOf(
                ScheduledWorkout(
                    id = "slot-1",
                    date = LocalDate.of(2023, 11, 13),
                    routineId = routine.id,
                    routineName = routine.name,
                    status = ScheduleStatus.COMPLETED,
                    sessionId = session.id,
                    completedAtMs = NOW,
                ),
                ScheduledWorkout(
                    id = "slot-2",
                    date = LocalDate.of(2023, 11, 15),
                    routineId = routine.id,
                    routineName = routine.name,
                    status = ScheduleStatus.SKIPPED,
                ),
            ),
        )
    }

    @Test
    fun `a document survives an export and import unchanged`() {
        val original = document()
        val restored = BackupCodec.decode(BackupCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun `the encoded file declares its format and version`() {
        val text = BackupCodec.encode(document())
        assertTrue(text.contains("\"format\": \"cadence-backup\""))
        assertTrue(text.contains("\"formatVersion\": 2"))
    }

    @Test
    fun `the encoded file states that no sleep data is included`() {
        val text = BackupCodec.encode(document())
        assertTrue(text.contains("no sleep data"))
        assertTrue(text.contains("no account details"))
    }

    @Test
    fun `the export never contains sleep records or notes fields in the payload`() {
        val text = BackupCodec.encode(document())
        assertTrue(!text.contains("sleepSession"))
        assertTrue(!text.contains("\"stages\""))
        assertTrue(!text.contains("journal"))
    }

    @Test
    fun `an unknown format tag is rejected with a clear message`() {
        val error = runCatching { BackupCodec.decode("""{"format":"something-else","formatVersion":1}""") }
            .exceptionOrNull()

        assertTrue(error is BackupException)
        assertTrue(error!!.message!!.contains("not a Cadence backup"))
    }

    @Test
    fun `a backup from a newer version is refused rather than silently downgraded`() {
        val error = runCatching {
            BackupCodec.decode("""{"format":"cadence-backup","formatVersion":99}""")
        }.exceptionOrNull()

        assertTrue(error is BackupException)
        assertTrue(error!!.message!!.contains("newer version"))
    }

    @Test
    fun `malformed json is reported as a backup problem`() {
        val error = runCatching { BackupCodec.decode("{not json") }.exceptionOrNull()
        assertTrue(error is BackupException)
    }

    @Test
    fun `a json document that is not an object is rejected`() {
        assertTrue(runCatching { BackupCodec.decode("[1,2,3]") }.exceptionOrNull() is BackupException)
    }

    @Test
    fun `decodeOrNull returns null instead of throwing`() {
        assertNull(BackupCodec.decodeOrNull("{}"))
        assertNull(BackupCodec.decodeOrNull("garbage"))
    }

    @Test
    fun `missing sections fall back to sensible defaults`() {
        val restored = BackupCodec.decode("""{"format":"cadence-backup","formatVersion":1}""")

        assertTrue(restored.routines.isEmpty())
        assertTrue(restored.sessions.isEmpty())
        assertEquals(WeightUnit.KG, restored.settings.weightUnit)
        assertEquals(3, restored.profile.daysPerWeek)
        assertEquals(TrainingLocation.GYM, restored.profile.location)
        assertTrue(restored.settings.showProgressLevel)
        assertEquals(20.0, restored.settings.barWeightKg, 1e-9)
        assertEquals(45.0, restored.settings.barWeightLb, 1e-9)
    }

    @Test
    fun `bar weights survive an export and import`() {
        val restored = BackupCodec.decode(BackupCodec.encode(document()))

        assertEquals(15.0, restored.settings.barWeightKg, 1e-9)
        assertEquals(35.0, restored.settings.barWeightLb, 1e-9)
    }

    @Test
    fun `unknown enum values fall back instead of failing the whole import`() {
        val text = """
            {
              "format": "cadence-backup",
              "formatVersion": 1,
              "profile": {"location": "MOON", "goal": "TIME_TRAVEL", "experience": "WIZARD"},
              "settings": {"weightUnit": "STONE"}
            }
        """.trimIndent()

        val restored = BackupCodec.decode(text)

        assertEquals(TrainingLocation.GYM, restored.profile.location)
        assertEquals(TrainingGoal.GENERAL_FITNESS, restored.profile.goal)
        assertEquals(ExperienceLevel.BEGINNER, restored.profile.experience)
        assertEquals(WeightUnit.KG, restored.settings.weightUnit)
    }

    @Test
    fun `warmup flags and rpe survive the round trip`() {
        val restored = BackupCodec.decode(BackupCodec.encode(document()))
        val sets = restored.sessions.single().sets

        assertEquals(3, sets.size)
        assertEquals(8.5, sets.first().rpe!!, 1e-9)
        assertTrue(sets.last().isWarmup)
        assertEquals("Felt strong", restored.sessions.single().note)
    }

    @Test
    fun `a suggested file name is derived from the export time`() {
        assertEquals("cadence-backup-1700000000000.json", BackupCodec.suggestedFileName(NOW))
    }

    @Test
    fun `planned days survive an export and import`() {
        val restored = BackupCodec.decode(BackupCodec.encode(document()))

        assertEquals(2, restored.schedule.size)
        val completed = restored.schedule.first { it.id == "slot-1" }
        assertEquals(LocalDate.of(2023, 11, 13), completed.date)
        assertEquals(ScheduleStatus.COMPLETED, completed.status)
        assertEquals("session-1", completed.sessionId)
        assertEquals(NOW, completed.completedAtMs)
        assertEquals(ScheduleStatus.SKIPPED, restored.schedule.first { it.id == "slot-2" }.status)
    }

    @Test
    fun `a version one backup imports with an empty calendar`() {
        val restored = BackupCodec.decode(
            """{"format":"cadence-backup","formatVersion":1,"routines":[],"sessions":[]}""",
        )

        assertTrue(restored.schedule.isEmpty())
        assertEquals(1, restored.formatVersion)
    }

    @Test
    fun `a scheduled day with an unreadable date is skipped`() {
        val text = """
            {
              "format": "cadence-backup",
              "formatVersion": 2,
              "schedule": [
                {"id": "bad", "routineName": "Upper"},
                {"id": "good", "dateEpochDay": 19674, "routineName": "Lower"}
              ]
            }
        """.trimIndent()

        val restored = BackupCodec.decode(text)

        assertEquals(1, restored.schedule.size)
        assertEquals("good", restored.schedule.single().id)
        assertEquals(LocalDate.ofEpochDay(19674), restored.schedule.single().date)
    }

    @Test
    fun `an empty history still exports and imports`() {
        val empty = BackupDocument(
            exportedAtMs = NOW,
            appVersion = "0.1.0",
            profile = Fixtures.profile(),
            settings = AppSettings(),
            routines = emptyList(),
            sessions = emptyList(),
        )
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }
}
