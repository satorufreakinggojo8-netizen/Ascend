package com.cadence.fit.data

import com.cadence.fit.domain.backup.BackupCodec
import com.cadence.fit.domain.backup.BackupDocument
import com.cadence.fit.domain.core.Clock
import com.cadence.fit.domain.core.IdGenerator
import com.cadence.fit.domain.core.SystemClock
import com.cadence.fit.domain.core.UuidGenerator
import com.cadence.fit.domain.model.AppSettings
import com.cadence.fit.domain.model.BodyProfile
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.ScheduledWorkout
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.plan.SplitApplier
import com.cadence.fit.domain.plan.SplitApplication
import com.cadence.fit.domain.plan.SplitTemplate
import com.cadence.fit.domain.plan.SplitTemplates
import com.cadence.fit.domain.plan.PlanBuilder
import com.cadence.fit.domain.schedule.SchedulePlanner
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.domain.workout.WorkoutLogger
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the UI renders, kept in memory and persisted as it changes. */
data class CadenceState(
    val loaded: Boolean = false,
    val profile: TrainingProfile = TrainingProfile(),
    val settings: AppSettings = AppSettings(),
    val bodyProfile: BodyProfile = BodyProfile(),
    val routines: List<Routine> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val schedule: List<ScheduledWorkout> = emptyList(),
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
    private val zone: ZoneId = TimeRanges.zone(),
) {

    private val routines = RoutineStore(database)
    private val workouts = WorkoutStore(database)
    private val calendar = ScheduleStore(database)
    private val planner = SchedulePlanner(ids)

    private val mutableState = MutableStateFlow(CadenceState())
    val state: StateFlow<CadenceState> = mutableState.asStateFlow()

    fun newId(): String = ids.next()

    suspend fun load() {
        mutableState.update {
            CadenceState(
                loaded = true,
                profile = preferences.profile,
                settings = preferences.settings,
                bodyProfile = preferences.bodyProfile,
                routines = routines.loadRoutines(),
                sessions = workouts.loadSessions(),
                schedule = calendar.loadSlots(),
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

    suspend fun saveBodyProfile(body: BodyProfile) {
        val sanitized = body.sanitized()
        preferences.bodyProfile = sanitized
        mutableState.update { it.copy(bodyProfile = sanitized) }
    }

    /**
     * Adds an edited split's routines and lays its days out on the calendar.
     *
     * Existing routines are never deleted. Planned days inside the applied weeks
     * are replaced so the week matches the preview; completed and skipped days are
     * preserved as history and as a record of the decision.
     */
    suspend fun applySplit(
        template: SplitTemplate,
        profile: TrainingProfile,
        builder: PlanBuilder,
        nowMs: Long,
        weeks: Int = 2,
    ): SplitApplication {
        val current = mutableState.value
        val application = SplitApplier.apply(
            template = template,
            profile = profile,
            builder = builder,
            ids = ids,
            nowMs = nowMs,
            weekStart = currentWeek(),
            existingSchedule = current.schedule,
            weeks = weeks,
            notBefore = today(),
        )

        val startPosition = current.routines.size
        application.routines.forEachIndexed { index, routine ->
            routines.upsertRoutine(routine, startPosition + index)
        }
        mutableState.update { state -> state.copy(routines = state.routines + application.routines) }

        saveSchedule(application.schedule)
        // The weekly target follows the split that is now in use; otherwise the home
        // card would compare six planned days against an old three-day target.
        saveProfile(
            profile
                .withPlan(template.name, template.id)
                .copy(daysPerWeek = template.daysPerWeek.coerceIn(2, 6)),
        )
        return application
    }

    suspend fun clearPlanLabel() {
        val current = mutableState.value.profile
        if (current.planName.isNotEmpty() || current.splitTemplateId != null) {
            saveProfile(current.copy(planName = "", splitTemplateId = null))
        }
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
        // Keep future planned days showing the routine's current name.
        syncPlannedNames(routine)
    }

    private suspend fun syncPlannedNames(routine: Routine) {
        val current = mutableState.value.schedule
        val renamed = current.map { slot ->
            if (slot.routineId == routine.id && slot.routineName != routine.name) {
                slot.copy(routineName = routine.name)
            } else {
                slot
            }
        }
        if (renamed != current) saveSchedule(renamed)
    }

    /**
     * Replaces the generated starter routines and keeps every routine the user made.
     *
     * The old starter rows are deleted from SQLite as well as from memory, so a
     * rebuild cannot leave ghost routines that reappear after a restart.
     */
    suspend fun saveRoutines(routineList: List<Routine>) {
        val existing = mutableState.value.routines
        val custom = existing.filterNot { it.isStarterPlan }
        existing.filter { it.isStarterPlan }.forEach { stale ->
            routines.deleteRoutine(stale.id)
        }
        routineList.forEachIndexed { index, routine ->
            routines.upsertRoutine(routine, custom.size + index)
        }
        // A rebuilt plan replaces every generated routine id, so stale planned days
        // are cleared rather than left pointing at routines that no longer exist.
        calendar.deletePlannedSlots()
        mutableState.update {
            it.copy(
                routines = custom + routineList,
                schedule = it.schedule.filter { slot -> slot.status == com.cadence.fit.domain.model.ScheduleStatus.COMPLETED },
            )
        }
    }

    suspend fun deleteRoutine(id: String) {
        routines.deleteRoutine(id)
        val nextSchedule = planner.dropSlotsForRoutine(mutableState.value.schedule, id)
        saveSchedule(nextSchedule)
        mutableState.update { state -> state.copy(routines = state.routines.filterNot { it.id == id }) }
    }

    suspend fun saveSession(session: WorkoutSession) {
        workouts.upsertSession(session)
        mutableState.update { state ->
            val others = state.sessions.filterNot { it.id == session.id }
            state.copy(sessions = (others + session).sortedByDescending { it.startedAtMs })
        }
        // A finished workout claims the planned day it belongs to. The plan never
        // invents a completion: if nothing matches, the calendar is left alone.
        if (session.isComplete) linkCompletedSession(session)
    }

    // --- Training calendar ----------------------------------------------------

    /**
     * Fills [weekStart] from the current routines if that week is still empty.
     *
     * The current week is never back-filled with days that have already passed, so
     * a new plan does not pretend the user trained on Monday when they did not.
     */
    suspend fun ensureWeek(weekStart: LocalDate = currentWeek()): Boolean {
        val current = mutableState.value
        if (current.routines.isEmpty()) return false
        if (planner.hasWeek(current.schedule, weekStart)) return false
        val notBefore = if (weekStart == currentWeek()) today() else null
        val split = SplitTemplates.byId(current.profile.splitTemplateId)
        val planned = if (split != null) {
            SplitApplier.ensureWeek(
                template = split,
                routines = current.routines,
                weekStart = weekStart,
                existing = current.schedule,
                ids = ids,
                notBefore = notBefore,
            )
        } else {
            planner.planWeek(
                routines = current.routines,
                weekStart = weekStart,
                existing = current.schedule,
                daysPerWeek = current.profile.daysPerWeek,
                notBefore = notBefore,
            )
        }
        return saveSchedule(planned)
    }

    suspend fun moveSlot(slotId: String, date: LocalDate): Boolean =
        saveSchedule(planner.move(mutableState.value.schedule, slotId, date))

    suspend fun skipSlot(slotId: String): Boolean =
        saveSchedule(planner.skip(mutableState.value.schedule, slotId))

    suspend fun reopenSlot(slotId: String): Boolean =
        saveSchedule(planner.reopen(mutableState.value.schedule, slotId))

    /** Marks a planned day done using a session that already exists in history. */
    suspend fun markSlotComplete(slotId: String, sessionId: String, completedAtMs: Long): Boolean =
        saveSchedule(planner.complete(mutableState.value.schedule, slotId, sessionId, completedAtMs))

    suspend fun deleteScheduleSlot(slotId: String): Boolean =
        saveSchedule(mutableState.value.schedule.filterNot { it.id == slotId })

    /** Clears future planned days while keeping completed ones linked to history. */
    suspend fun clearPlannedSchedule(): Boolean {
        calendar.deletePlannedSlots()
        val kept = mutableState.value.schedule.filter { it.status == com.cadence.fit.domain.model.ScheduleStatus.COMPLETED }
        mutableState.update { it.copy(schedule = kept) }
        return true
    }

    fun currentWeek(): LocalDate = SchedulePlanner.weekStart(today())

    fun today(): LocalDate = TimeRanges.toLocalDate(clock.nowMs(), zone)

    private suspend fun linkCompletedSession(session: WorkoutSession) {
        val current = mutableState.value.schedule
        val linked = planner.linkSession(current, session, zone)
        if (linked != current) saveSchedule(linked)
    }

    /** Persists exactly the difference between the in-memory plan and [next]. */
    private suspend fun saveSchedule(next: List<ScheduledWorkout>): Boolean {
        val previous = mutableState.value.schedule
        if (next == previous) return false
        val removed = previous.filter { old -> next.none { it.id == old.id } }
        val changed = next.filter { updated -> previous.none { it == updated } }
        if (changed.isNotEmpty()) calendar.upsertAll(changed)
        removed.forEach { calendar.deleteSlot(it.id) }
        mutableState.update { it.copy(schedule = next.sortedWith(compareBy({ slot -> slot.date }, { slot -> slot.id }))) }
        return true
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
            bodyProfile = current.bodyProfile,
            routines = current.routines,
            sessions = current.sessions,
            schedule = current.schedule,
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
        preferences.bodyProfile = document.bodyProfile
        preferences.activeSessionId = null

        routines.deleteAllRoutines()
        workouts.deleteAllSessions()
        calendar.deleteAllSlots()
        document.routines.forEachIndexed { index, routine -> routines.upsertRoutine(routine, index) }
        document.sessions.forEach { session -> workouts.upsertSession(session) }
        calendar.upsertAll(document.schedule)

        mutableState.update {
            CadenceState(
                loaded = true,
                profile = document.profile,
                settings = document.settings,
                bodyProfile = document.bodyProfile,
                routines = document.routines,
                sessions = document.sessions.sortedByDescending { session -> session.startedAtMs },
                schedule = document.schedule.sortedWith(compareBy({ slot -> slot.date }, { slot -> slot.id })),
                activeSessionId = null,
            )
        }
    }

    /** Removes every trace of local data, including preferences. */
    suspend fun deleteAllData() {
        routines.deleteAllRoutines()
        workouts.deleteAllSessions()
        calendar.deleteAllSlots()
        preferences.clearAll()
        mutableState.value = CadenceState(loaded = true)
    }
}
