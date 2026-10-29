package com.cadence.fit.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadence.fit.data.BackupService
import com.cadence.fit.data.CadenceRepository
import com.cadence.fit.data.CadenceState
import com.cadence.fit.domain.core.SystemClock
import com.cadence.fit.domain.core.UuidGenerator
import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.library.ExerciseLibrary
import com.cadence.fit.domain.model.AppSettings
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.SleepConnectionStatus
import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.plan.PlanBuilder
import com.cadence.fit.domain.sleep.SleepRepository
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.domain.workout.WorkoutLogger
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiMessage(val id: Long, val text: String, val isError: Boolean = false)

/**
 * Holds screen-independent state and forwards user intent to the repository.
 *
 * All workout arithmetic happens in `:domain` (tested there); this class only
 * sequences calls and surfaces results.
 */
class CadenceViewModel(
    private val repository: CadenceRepository,
    private val sleepRepository: SleepRepository,
    private val backupService: BackupService,
) : ViewModel() {

    val catalog: ExerciseCatalog = ExerciseLibrary
    val state: StateFlow<CadenceState> = repository.state

    private val mutableSleepStatus = MutableStateFlow(SleepConnectionStatus())
    val sleepStatus: StateFlow<SleepConnectionStatus> = mutableSleepStatus.asStateFlow()

    private val mutableSleepSessions = MutableStateFlow<List<SleepSummary>>(emptyList())
    val sleepSessions: StateFlow<List<SleepSummary>> = mutableSleepSessions.asStateFlow()

    private val mutableMessages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = mutableMessages.asStateFlow()

    private var messageCounter = 0L

    init {
        viewModelScope.launch {
            repository.load()
            // The calendar is filled lazily so existing installs (and imported
            // backups without a plan) get a week without a migration prompt.
            repository.ensureWeek()
            loadSleep()
        }
    }

    fun nowMs(): Long = SystemClock.nowMs()

    fun today(): LocalDate = repository.today()

    fun currentWeek(): LocalDate = repository.currentWeek()

    /** Stable id for a new routine or routine slot. */
    fun newId(): String = repository.newId()

    // --- Profile, plan and routines ------------------------------------------

    fun saveProfile(profile: TrainingProfile) = viewModelScope.launch {
        repository.saveProfile(profile)
    }

    fun saveSettings(settings: AppSettings) = viewModelScope.launch {
        repository.saveSettings(settings)
    }

    /** Called at the end of onboarding: saves the profile, unit and a new plan. */
    fun finishOnboarding(profile: TrainingProfile, unit: WeightUnit? = null) = viewModelScope.launch {
        val saved = profile.copy(onboardingComplete = true)
        repository.saveProfile(saved)
        if (unit != null && unit != state.value.settings.weightUnit) {
            repository.saveSettings(state.value.settings.copy(weightUnit = unit))
        }
        val builder = PlanBuilder(catalog, UuidGenerator)
        val plan = builder.buildStarterPlan(saved, nowMs())
        repository.saveRoutines(plan.routines)
        repository.ensureWeek()
        announce("Built ${plan.routines.size} routines for you")
    }

    fun regeneratePlan() = viewModelScope.launch {
        val profile = state.value.profile
        val builder = PlanBuilder(catalog, UuidGenerator)
        val plan = builder.buildStarterPlan(profile, nowMs())
        repository.saveRoutines(plan.routines)
        repository.ensureWeek()
        announce("Plan rebuilt from your current settings")
    }

    fun buildPlanFrom(profile: TrainingProfile) = viewModelScope.launch {
        val builder = PlanBuilder(catalog, UuidGenerator)
        val plan = builder.buildStarterPlan(profile, nowMs())
        repository.saveRoutines(plan.routines)
        repository.ensureWeek()
    }

    // --- Training calendar ----------------------------------------------------

    fun ensureScheduleFor(weekStart: LocalDate) = viewModelScope.launch {
        repository.ensureWeek(weekStart)
    }

    fun moveScheduled(slotId: String, date: LocalDate) = viewModelScope.launch {
        if (repository.moveSlot(slotId, date)) {
            announce("Moved to ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}")
        }
    }

    fun skipScheduled(slotId: String) = viewModelScope.launch {
        if (repository.skipSlot(slotId)) announce("Marked as skipped — nothing is removed from history")
    }

    fun reopenScheduled(slotId: String) = viewModelScope.launch {
        if (repository.reopenSlot(slotId)) announce("Back on the plan")
    }

    fun deleteScheduled(slotId: String) = viewModelScope.launch {
        if (repository.deleteScheduleSlot(slotId)) announce("Removed from the calendar")
    }

    /** Links a planned day to a workout that is already saved in history. */
    fun completeScheduled(slotId: String) = viewModelScope.launch {
        val slot = state.value.schedule.firstOrNull { it.id == slotId } ?: return@launch
        val linkedIds = state.value.schedule.mapNotNull { it.sessionId }.toSet()
        val session = state.value.completedSessions
            .filter { candidate -> candidate.id !in linkedIds }
            .filter { candidate -> slot.routineId == null || candidate.routineId == slot.routineId }
            .minWithOrNull(
                compareBy(
                    { candidate ->
                        val day = com.cadence.fit.domain.time.TimeRanges.toLocalDate(candidate.completedAtMs, com.cadence.fit.domain.time.TimeRanges.zone())
                        kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(day, slot.date))
                    },
                    { candidate -> -candidate.completedAtMs },
                ),
            )
        if (session == null) {
            warn("No saved workout to link yet. Log the session and it will attach by itself.")
            return@launch
        }
        if (repository.markSlotComplete(slotId, session.id, session.completedAtMs)) {
            announce("Linked to ${session.routineName}")
        }
    }

    fun clearPlannedSchedule() = viewModelScope.launch {
        if (repository.clearPlannedSchedule()) announce("Planned days cleared")
    }

    fun saveRoutine(routine: Routine) = viewModelScope.launch {
        repository.saveRoutine(routine)
    }

    fun deleteRoutine(routineId: String) = viewModelScope.launch {
        repository.deleteRoutine(routineId)
        announce("Routine deleted")
    }

    // --- Session logging ------------------------------------------------------

    fun startSession(routineId: String?) = viewModelScope.launch {
        val routine = routineId?.let { id -> state.value.routines.firstOrNull { it.id == id } }
        repository.startSession(routine)
    }

    fun logSet(
        sessionId: String,
        exerciseId: String,
        weightKg: Double,
        reps: Int,
        rpe: Double?,
        isWarmup: Boolean,
        note: String,
    ) = viewModelScope.launch {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return@launch
        val supersetGroup = session.routineId
            ?.let { id -> state.value.routines.firstOrNull { it.id == id } }
            ?.exercises
            ?.firstOrNull { it.exerciseId == exerciseId }
            ?.supersetGroup
        repository.appendSet(
            session,
            LoggedSet(
                id = repository.newId(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                setIndex = WorkoutLogger.nextSetIndex(session, exerciseId),
                weightKg = weightKg,
                reps = reps,
                rpe = rpe,
                isWarmup = isWarmup,
                completedAtMs = nowMs(),
                supersetGroup = supersetGroup,
                note = note,
            ),
        )
    }

    fun updateSet(
        sessionId: String,
        setId: String,
        weightKg: Double,
        reps: Int,
        rpe: Double?,
        isWarmup: Boolean,
        note: String,
    ) = viewModelScope.launch {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return@launch
        repository.updateSet(session, setId) { set ->
            set.copy(
                weightKg = weightKg,
                reps = reps,
                rpe = rpe,
                isWarmup = isWarmup,
                note = note,
            )
        }
        announce("Set updated")
    }

    fun undoLastSet(sessionId: String, exerciseId: String) = viewModelScope.launch {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return@launch
        val target = session.sets.filter { it.exerciseId == exerciseId }.maxByOrNull { it.completedAtMs }
        if (target == null) {
            announce("Nothing to undo for this exercise")
            return@launch
        }
        repository.removeSet(session, target.id)
        announce("Last set removed")
    }

    fun deleteSet(sessionId: String, setId: String) = viewModelScope.launch {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return@launch
        repository.removeSet(session, setId)
        announce("Set deleted")
    }

    fun finishSession(sessionId: String, note: String = "") = viewModelScope.launch {
        val session = state.value.sessions.firstOrNull { it.id == sessionId }
        if (session == null || session.sets.isEmpty()) {
            warn("Log at least one set before finishing")
            return@launch
        }
        val finished = session.copy(endedAtMs = nowMs(), note = note.ifBlank { session.note })
        repository.saveSession(finished)
        repository.clearActiveSession()
        announce("Workout saved")
    }

    fun discardSession(sessionId: String) = viewModelScope.launch {
        repository.deleteSession(sessionId)
        repository.clearActiveSession()
        warn("Workout discarded and not saved")
    }

    fun deleteSession(sessionId: String) = viewModelScope.launch {
        repository.deleteSession(sessionId)
        announce("Session deleted")
    }

    // --- Sleep and recovery ---------------------------------------------------

    private suspend fun loadSleep() {
        mutableSleepStatus.value = sleepRepository.status()
        if (mutableSleepStatus.value.isConnected) refreshSleepInternal()
    }

    fun enableSleep() = viewModelScope.launch { updateSleepStatus(sleepRepository.enable()) }

    /**
     * The sleep switch on the settings page and the sleep page both drive the same
     * stored opt-in, so the two screens can never disagree about whether reading is
     * on. Turning it off also clears the last-read time.
     */
    fun setSleepSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            updateSleepStatus(sleepRepository.enable())
        } else {
            updateSleepStatus(sleepRepository.disable())
            mutableSleepSessions.value = emptyList()
            announce("Sleep reading turned off. Nothing is read from Health Connect.")
        }
    }

    fun recordSleepPermission(granted: Boolean) = viewModelScope.launch {
        updateSleepStatus(sleepRepository.recordPermission(granted))
        if (granted) {
            refreshSleepInternal()
        } else {
            mutableSleepSessions.value = emptyList()
            warn("Sleep access was declined. Cadence works fine without it.")
        }
    }

    fun disconnectSleep() = viewModelScope.launch {
        updateSleepStatus(sleepRepository.disable())
        mutableSleepSessions.value = emptyList()
        announce("Sleep sync turned off. Already-synced sessions are no longer read.")
    }

    private suspend fun updateSleepStatus(status: SleepConnectionStatus) {
        mutableSleepStatus.value = status
        // Keep the mirrored preference in step with the real opt-in.
        val settings = state.value.settings
        if (settings.sleepInsightsEnabled != status.enabled) {
            saveSettings(settings.copy(sleepInsightsEnabled = status.enabled))
        }
    }

    /** Called when the screen appears or the app resumes, since permission can change. */
    fun refreshSleep() = viewModelScope.launch {
        val status = sleepRepository.status()
        mutableSleepStatus.value = status
        if (status.isConnected) refreshSleepInternal() else mutableSleepSessions.value = emptyList()
    }

    private suspend fun refreshSleepInternal() {
        when (val result = sleepRepository.refresh()) {
            is com.cadence.fit.domain.model.SleepRefreshResult.Success -> {
                mutableSleepSessions.value = result.sessions
                mutableSleepStatus.value = sleepRepository.status()
            }

            is com.cadence.fit.domain.model.SleepRefreshResult.Unavailable -> {
                mutableSleepSessions.value = emptyList()
                mutableSleepStatus.value = sleepRepository.status()
                write(result.availability.explanation, isError = false)
            }

            com.cadence.fit.domain.model.SleepRefreshResult.PermissionMissing -> {
                mutableSleepSessions.value = emptyList()
                mutableSleepStatus.value = sleepRepository.status()
                warn("Sleep permission is not granted, so nothing could be read.")
            }

            com.cadence.fit.domain.model.SleepRefreshResult.Disabled -> {
                mutableSleepSessions.value = emptyList()
            }

            is com.cadence.fit.domain.model.SleepRefreshResult.Failed -> {
                mutableSleepSessions.value = emptyList()
                warn("Could not read sleep data: ${result.message}")
            }
        }
    }

    // --- Export, import and deletion -----------------------------------------

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        runCatching {
            val json = repository.exportBackup()
            backupService.writeToUri(uri, json)
        }.onSuccess {
            announce("Backup saved")
        }.onFailure { failure ->
            warn("Export failed: ${failure.message ?: "unknown error"}")
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = backupService.readFromUri(uri)
            repository.importBackup(text)
        }.onSuccess {
            announce("Backup imported")
        }.onFailure { failure ->
            warn("Import failed: ${failure.message ?: "unknown error"}")
        }
    }

    fun deleteAllData(onDeleted: () -> Unit = {}) = viewModelScope.launch {
        repository.deleteAllData()
        mutableSleepSessions.value = emptyList()
        mutableSleepStatus.value = sleepRepository.disable()
        announce("All local data deleted")
        onDeleted()
    }

    // --- Transient messages ---------------------------------------------------

    private fun announce(text: String) = write(text, isError = false)

    private fun warn(text: String) = write(text, isError = true)

    private fun write(text: String, isError: Boolean) {
        messageCounter += 1
        mutableMessages.update { it + UiMessage(messageCounter, text, isError) }
    }

    fun dismissMessage(id: Long) {
        mutableMessages.update { messages -> messages.filterNot { it.id == id } }
    }
}
