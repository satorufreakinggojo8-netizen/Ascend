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
import com.cadence.fit.domain.model.BodyProfile
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.SleepConnectionStatus
import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.plan.PlanBuilder
import com.cadence.fit.domain.plan.SplitTemplate
import com.cadence.fit.domain.sleep.SleepRepository
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.domain.workout.WorkoutLogger
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        launchIo {
            repository.load()
            // The calendar is filled lazily so existing installs (and imported
            // backups without a plan) get a week without a migration prompt.
            repository.ensureWeek()
            loadSleep()
        }
    }

    fun nowMs(): Long = SystemClock.nowMs()

    /**
     * Database reads, writes and backup file I/O run off the main thread. Screens
     * still collect the state flow, so every write appears immediately; scrolling
     * and typing simply no longer wait on SQLite.
     */
    private fun launchIo(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch(Dispatchers.IO, block = block)

    fun today(): LocalDate = repository.today()

    fun currentWeek(): LocalDate = repository.currentWeek()

    /** Stable id for a new routine or routine slot. */
    fun newId(): String = repository.newId()

    // --- Profile, plan and routines ------------------------------------------

    fun saveProfile(profile: TrainingProfile) = launchIo {
        repository.saveProfile(profile)
    }

    fun saveSettings(settings: AppSettings) = launchIo {
        repository.saveSettings(settings)
    }

    /** Saves optional age, height or a weight reading. Nothing here is required. */
    fun saveBodyProfile(body: BodyProfile) = launchIo {
        repository.saveBodyProfile(body)
    }

    fun recordWeight(weightKg: Double) = launchIo {
        val next = state.value.bodyProfile.withWeight(weightKg, nowMs())
        repository.saveBodyProfile(next)
        announce("Weight saved for today")
    }

    /**
     * Applies a split after the preview edits. Routines are added, never replaced,
     * and the calendar is laid out on the split's exact weekdays.
     */
    fun applySplit(template: SplitTemplate, edited: SplitTemplate = template) = launchIo {
        val builder = PlanBuilder(catalog, UuidGenerator)
        val application = repository.applySplit(
            template = edited,
            profile = state.value.profile,
            builder = builder,
            nowMs = nowMs(),
        )
        announce(
            "${edited.name} applied · ${application.routines.size} routines and " +
                "${application.plannedSlots.size} planned days",
        )
    }

    /** Called at the end of onboarding: saves the profile, unit and a new plan. */
    fun finishOnboarding(profile: TrainingProfile, unit: WeightUnit? = null) = launchIo {
        val saved = profile.copy(onboardingComplete = true)
            .withPlan("Starter plan")
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

    fun regeneratePlan() = launchIo {
        val profile = state.value.profile.withPlan("Starter plan")
        val builder = PlanBuilder(catalog, UuidGenerator)
        val plan = builder.buildStarterPlan(profile, nowMs())
        repository.saveRoutines(plan.routines)
        repository.saveProfile(profile)
        repository.ensureWeek()
        announce("Plan rebuilt from your current settings")
    }

    fun buildPlanFrom(profile: TrainingProfile) = launchIo {
        val next = profile.withPlan("Starter plan")
        val builder = PlanBuilder(catalog, UuidGenerator)
        val plan = builder.buildStarterPlan(next, nowMs())
        repository.saveRoutines(plan.routines)
        repository.saveProfile(next)
        repository.ensureWeek()
    }

    // --- Training calendar ----------------------------------------------------

    fun ensureScheduleFor(weekStart: LocalDate) = launchIo {
        repository.ensureWeek(weekStart)
    }

    fun moveScheduled(slotId: String, date: LocalDate) = launchIo {
        if (repository.moveSlot(slotId, date)) {
            announce("Moved to ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}")
        }
    }

    fun skipScheduled(slotId: String) = launchIo {
        if (repository.skipSlot(slotId)) announce("Marked as skipped — nothing is removed from history")
    }

    fun reopenScheduled(slotId: String) = launchIo {
        if (repository.reopenSlot(slotId)) announce("Back on the plan")
    }

    fun deleteScheduled(slotId: String) = launchIo {
        if (repository.deleteScheduleSlot(slotId)) announce("Removed from the calendar")
    }

    /** Links a planned day to a workout that is already saved in history. */
    fun completeScheduled(slotId: String) = launchIo {
        val slot = state.value.schedule.firstOrNull { it.id == slotId } ?: return@launchIo
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
            return@launchIo
        }
        if (repository.markSlotComplete(slotId, session.id, session.completedAtMs)) {
            announce("Linked to ${session.routineName}")
        }
    }

    fun clearPlannedSchedule() = launchIo {
        if (repository.clearPlannedSchedule()) announce("Planned days cleared")
    }

    fun saveRoutine(routine: Routine) = launchIo {
        repository.saveRoutine(routine)
    }

    fun deleteRoutine(routineId: String) = launchIo {
        repository.deleteRoutine(routineId)
        announce("Routine deleted")
    }

    // --- Session logging ------------------------------------------------------

    fun startSession(routineId: String?) = launchIo {
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
    ) = launchIo {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return@launchIo
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
    ) = launchIo {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return@launchIo
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

    fun undoLastSet(sessionId: String, exerciseId: String) = launchIo {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return@launchIo
        val target = session.sets.filter { it.exerciseId == exerciseId }.maxByOrNull { it.completedAtMs }
        if (target == null) {
            announce("Nothing to undo for this exercise")
            return@launchIo
        }
        repository.removeSet(session, target.id)
        announce("Last set removed")
    }

    fun deleteSet(sessionId: String, setId: String) = launchIo {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return@launchIo
        repository.removeSet(session, setId)
        announce("Set deleted")
    }

    fun finishSession(sessionId: String, note: String = "") = launchIo {
        val session = state.value.sessions.firstOrNull { it.id == sessionId }
        if (session == null || session.sets.isEmpty()) {
            warn("Log at least one set before finishing")
            return@launchIo
        }
        val finished = session.copy(endedAtMs = nowMs(), note = note.ifBlank { session.note })
        repository.saveSession(finished)
        repository.clearActiveSession()
        announce("Workout saved")
    }

    fun discardSession(sessionId: String) = launchIo {
        repository.deleteSession(sessionId)
        repository.clearActiveSession()
        warn("Workout discarded and not saved")
    }

    fun deleteSession(sessionId: String) = launchIo {
        repository.deleteSession(sessionId)
        announce("Session deleted")
    }

    // --- Sleep and recovery ---------------------------------------------------

    private suspend fun loadSleep() {
        mutableSleepStatus.value = sleepRepository.status()
        if (mutableSleepStatus.value.isConnected) refreshSleepInternal()
    }

    fun enableSleep() = launchIo { updateSleepStatus(sleepRepository.enable()) }

    /**
     * The sleep switch on the settings page and the sleep page both drive the same
     * stored opt-in, so the two screens can never disagree about whether reading is
     * on. Turning it off also clears the last-read time.
     */
    fun setSleepSyncEnabled(enabled: Boolean) = launchIo {
        if (enabled) {
            updateSleepStatus(sleepRepository.enable())
        } else {
            updateSleepStatus(sleepRepository.disable())
            mutableSleepSessions.value = emptyList()
            announce("Sleep reading turned off. Nothing is read from Health Connect.")
        }
    }

    fun recordSleepPermission(granted: Boolean) = launchIo {
        updateSleepStatus(sleepRepository.recordPermission(granted))
        if (granted) {
            refreshSleepInternal()
        } else {
            mutableSleepSessions.value = emptyList()
            warn("Sleep access was declined. Cadence works fine without it.")
        }
    }

    fun disconnectSleep() = launchIo {
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
    fun refreshSleep() = launchIo {
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

    fun exportBackup(uri: Uri) = launchIo {
        runCatching {
            val json = repository.exportBackup()
            backupService.writeToUri(uri, json)
        }.onSuccess {
            announce("Backup saved")
        }.onFailure { failure ->
            warn("Export failed: ${failure.message ?: "unknown error"}")
        }
    }

    fun importBackup(uri: Uri) = launchIo {
        runCatching {
            val text = backupService.readFromUri(uri)
            repository.importBackup(text)
        }.onSuccess {
            announce("Backup imported")
        }.onFailure { failure ->
            warn("Import failed: ${failure.message ?: "unknown error"}")
        }
    }

    fun deleteAllData(onDeleted: () -> Unit = {}) = launchIo {
        repository.deleteAllData()
        mutableSleepSessions.value = emptyList()
        mutableSleepStatus.value = sleepRepository.disable()
        announce("All local data deleted")
        // The callback navigates, which must happen on the main thread.
        withContext(Dispatchers.Main.immediate) { onDeleted() }
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
