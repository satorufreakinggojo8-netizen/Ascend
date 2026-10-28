package com.cadence.fit.data

import com.cadence.fit.domain.backup.BackupCodec
import com.cadence.fit.domain.backup.BackupDocument
import com.cadence.fit.domain.core.Clock
import com.cadence.fit.domain.core.IdGenerator
import com.cadence.fit.domain.core.SystemClock
import com.cadence.fit.domain.core.UuidGenerator
import com.cadence.fit.domain.model.AppSettings
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.workout.WorkoutLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the UI renders, kept in memory and persisted as it changes. */
data class CadenceState(
    val loaded: Boolean = false,
    val profile: TrainingProfile = TrainingProfile(),
    val settings: AppSettings = AppSettings(),
    val routines: List<Routine> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val activeSessionId: String? = null,
) {
    val completedSessions: List<WorkoutSession> get() = sessions.filter { it.isComplete }

    val activeSession: WorkoutSession?
        get() = activeSessionId?.let { id -> sessions.firstOrNull { it.id == id && !it.isComplete } }

    val hasData: Boolean get() = routines.isNotEmpty() || sessions.isNotEmpty()
}

/**
 * The single writer for local data.
 *
 * History is small (a few thousand sets at most), so it is held in memory for
 * instant, offline-first reads while every change is written through to SQLite.
 */
class CadenceRepository(
    database: CadenceDatabase,
    private val preferences: PreferencesStore,
    private val clock: Clock = SystemClock,
    private val ids: IdGenerator = UuidGenerator,
    private val appVersion: String = "0.1.0",
) {

    private val routines = RoutineStore(database)
    private val workouts = WorkoutStore(database)

    private val mutableState = MutableStateFlow(CadenceState())
    val state: StateFlow<CadenceState> = mutableState.asStateFlow()

    fun newId(): String = ids.next()

    suspend fun load() {
        mutableState.update {
            CadenceState(
                loaded = true,
                profile = preferences.profile,
                settings = preferences.settings,
                routines = routines.loadRoutines(),
                sessions = workouts.loadSessions(),
                activeSessionId = preferences.activeSessionId,
            )
        }
    }

    suspend fun saveProfile(profile: TrainingProfile) {
        preferences.profile = profile
        mutableState.update { it.copy(profile = profile) }
    }

    suspend fun saveSettings(settings: AppSettings) {
        preferences.settings = settings
        mutableState.update { it.copy(settings = settings) }
    }

    suspend fun saveRoutine(routine: Routine) {
        // Explicit ordering keeps list position stable in SQLite.
        val existing = mutableState.value.routines
        val position = existing.indexOfFirst { it.id == routine.id }.takeIf { it >= 0 } ?: existing.size
        routines.upsertRoutine(routine, position)
        mutableState.update { state ->
            val others = state.routines.filterNot { it.id == routine.id }
            state.copy(routines = others + routine)
        }
    }

    suspend fun saveRoutines(routineList: List<Routine>) {
        routineList.forEachIndexed { index, routine -> routines.upsertRoutine(routine, index) }
        mutableState.update { it.copy(routines = routineList) }
    }

    suspend fun deleteRoutine(id: String) {
        routines.deleteRoutine(id)
        mutableState.update { state -> state.copy(routines = state.routines.filterNot { it.id == id }) }
    }

    suspend fun saveSession(session: WorkoutSession) {
        workouts.upsertSession(session)
        mutableState.update { state ->
            val others = state.sessions.filterNot { it.id == session.id }
            state.copy(sessions = (others + session).sortedByDescending { it.startedAtMs })
        }
    }

    suspend fun deleteSession(id: String) {
        workouts.deleteSession(id)
        mutableState.update { state ->
            val nextActive = if (state.activeSessionId == id) null else state.activeSessionId
            if (nextActive == null) preferences.activeSessionId = null
            state.copy(sessions = state.sessions.filterNot { it.id == id }, activeSessionId = nextActive)
        }
    }

    /** Starts and persists a new in-progress session. */
    suspend fun startSession(
        routine: Routine?,
        name: String = routine?.name ?: "Freestyle workout",
        location: TrainingLocation = routine?.location ?: mutableState.value.profile.location,
    ): WorkoutSession {
        val session = WorkoutSession(
            id = ids.next(),
            routineId = routine?.id,
            routineName = name,
            startedAtMs = clock.nowMs(),
            endedAtMs = null,
            sets = emptyList(),
            location = location,
        )
        preferences.activeSessionId = session.id
        saveSession(session)
        mutableState.update { it.copy(activeSessionId = session.id) }
        return session
    }

    /** Appends a set and returns the updated session. */
    suspend fun appendSet(session: WorkoutSession, set: LoggedSet): WorkoutSession {
        val updated = WorkoutLogger.addSet(session, set)
        saveSession(updated)
        return updated
    }

    suspend fun updateSet(session: WorkoutSession, setId: String, transform: (LoggedSet) -> LoggedSet): WorkoutSession {
        val updated = WorkoutLogger.updateSet(session, setId, transform)
        saveSession(updated)
        return updated
    }

    suspend fun removeSet(session: WorkoutSession, setId: String): WorkoutSession {
        val updated = WorkoutLogger.removeSet(session, setId)
        saveSession(updated)
        return updated
    }

    suspend fun finishActiveSession(note: String = "") {
        val session = mutableState.value.activeSession ?: return
        val finished = session.copy(endedAtMs = clock.nowMs(), note = note.ifBlank { session.note })
        preferences.activeSessionId = null
        saveSession(finished)
        mutableState.update { it.copy(activeSessionId = null) }
    }

    /** Forgets which session was in progress, without touching the stored data. */
    suspend fun clearActiveSession() {
        preferences.activeSessionId = null
        mutableState.update { it.copy(activeSessionId = null) }
    }

    /** Throws the in-progress workout away without keeping it in history. */
    suspend fun discardActiveSession() {
        val session = mutableState.value.activeSession ?: return
        preferences.activeSessionId = null
        deleteSession(session.id)
    }

    // --- Export, import and deletion -----------------------------------------

    suspend fun exportBackup(): String {
        val current = mutableState.value
        val document = BackupDocument(
            exportedAtMs = clock.nowMs(),
            appVersion = appVersion,
            profile = current.profile,
            settings = current.settings,
            routines = current.routines,
            sessions = current.sessions,
        )
        return BackupCodec.encode(document)
    }

    suspend fun suggestedBackupFileName(): String = BackupCodec.suggestedFileName(clock.nowMs())

    /**
     * Replaces local data with the contents of a backup. Sleep sessions are not
     * part of a backup, so the sleep opt-in flag is left exactly as it was.
     */
    suspend fun importBackup(text: String) {
        val document = BackupCodec.decode(text)
        preferences.profile = document.profile
        preferences.settings = document.settings
        preferences.activeSessionId = null

        routines.deleteAllRoutines()
        workouts.deleteAllSessions()
        document.routines.forEachIndexed { index, routine -> routines.upsertRoutine(routine, index) }
        document.sessions.forEach { session -> workouts.upsertSession(session) }

        mutableState.update {
            CadenceState(
                loaded = true,
                profile = document.profile,
                settings = document.settings,
                routines = document.routines,
                sessions = document.sessions.sortedByDescending { session -> session.startedAtMs },
                activeSessionId = null,
            )
        }
    }

    /** Removes every trace of local data, including preferences. */
    suspend fun deleteAllData() {
        routines.deleteAllRoutines()
        workouts.deleteAllSessions()
        preferences.clearAll()
        mutableState.value = CadenceState(loaded = true)
    }
}
