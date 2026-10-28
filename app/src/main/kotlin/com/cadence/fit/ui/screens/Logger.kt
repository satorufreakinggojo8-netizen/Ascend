package com.cadence.fit.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.units.Durations
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.domain.workout.PreviousPerformance
import com.cadence.fit.domain.workout.WorkoutLogger
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceProgressBar
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.ExercisePickerOverlay
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.SegmentedOptions
import com.cadence.fit.ui.components.StepButton
import com.cadence.fit.ui.components.ThinDivider
import com.cadence.fit.ui.theme.CadenceTheme
import kotlinx.coroutines.delay

/** One exercise being logged, from the routine or added on the fly. */
private data class LogSlot(
    val exercise: Exercise,
    val routineExercise: RoutineExercise?,
) {
    val exerciseId: String get() = exercise.id
    val targetSets: Int get() = routineExercise?.targetSets ?: 3
    val repRange: IntRange get() = routineExercise?.repRange ?: 8..12
    val restSeconds: Int get() = routineExercise?.restSeconds ?: 90
    val warmupSets: Int get() = routineExercise?.warmupSets ?: 0
    val supersetLabel: String? get() = routineExercise?.supersetLabel
}

/**
 * The screen used mid-workout. Large +/- controls, quick increments, previous
 * values pre-filled, an optional RPE and a rest timer that starts on its own.
 */
@Composable
fun LoggerScreen(
    viewModel: CadenceViewModel,
    sessionId: String,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.sessions.firstOrNull { it.id == sessionId }
    val settings = state.settings
    val unit = settings.weightUnit

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOnDuringWorkout) {
        view.keepScreenOn = settings.keepScreenOnDuringWorkout
        onDispose { view.keepScreenOn = false }
    }

    if (session == null) {
        EmptyState(
            title = "This workout is no longer open",
            message = "It may have been finished, discarded or deleted on another screen.",
            icon = CadenceIcon.Warning,
            actionLabel = "Back to home",
            onAction = onBack,
        )
        return
    }

    val routine = session.routineId?.let { id -> state.routines.firstOrNull { it.id == id } }
    var extraExerciseIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val slots = remember(routine, session.exerciseIds, extraExerciseIds) {
        buildSlots(viewModel, routine, session, extraExerciseIds)
    }
    var activeIndex by remember { mutableIntStateOf(0) }
    val safeIndex = activeIndex.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    val current = slots.getOrNull(safeIndex)

    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }
    DisposableEffect(Unit) {
        onDispose { runCatching { tone?.release() } }
    }

    var showPicker by remember { mutableStateOf(false) }
    var pendingFinish by remember { mutableStateOf(false) }
    var pendingDiscard by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    // --- rest timer -----------------------------------------------------------

    var restTotal by remember { mutableIntStateOf(0) }
    var restRemaining by remember { mutableIntStateOf(0) }
    var restTicker by remember { mutableIntStateOf(0) }

    LaunchedEffect(restTicker) {
        if (restTotal <= 0) return@LaunchedEffect
        restRemaining = restTotal
        while (restRemaining > 0) {
            delay(1000)
            restRemaining -= 1
        }
        if (state.settings.restTimerSoundEnabled) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350) }
        }
    }

    val progress = routine?.let { WorkoutLogger.progress(session, it) }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = session.routineName,
            subtitle = buildString {
                append("${session.workingSets.size} working sets")
                progress?.let { append(" · ${it.completedSets}/${it.plannedSets} planned") }
            },
            onBack = onBack,
            actions = listOf(
                CadenceIcon.Plus to { showPicker = true },
                CadenceIcon.Trash to { pendingDiscard = true },
            ),
            actionDescriptions = listOf("Add an exercise", "Discard this workout"),
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            CadenceProgressBar(fraction = progress?.fraction ?: 0f)
        }

        if (restTotal > 0) {
            RestTimerBar(
                remaining = restRemaining,
                total = restTotal,
                onExtend = { restRemaining += 30; restTotal += 30 },
                onSkip = { restTotal = 0; restRemaining = 0; restTicker += 1 },
            )
        }

        if (slots.isNotEmpty()) {
            ExerciseStrip(
                slots = slots,
                session = session,
                activeIndex = safeIndex,
                onSelect = { index -> activeIndex = index },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (current == null) {
                EmptyState(
                    title = "Nothing to log yet",
                    message = "Add an exercise to start logging sets.",
                    icon = CadenceIcon.Dumbbell,
                    actionLabel = "Add an exercise",
                    onAction = { showPicker = true },
                )
            } else {
                ExerciseLogger(
                    viewModel = viewModel,
                    session = session,
                    slot = current,
                    unit = unit,
                    onUnitChange = { selected ->
                        viewModel.saveSettings(settings.copy(weightUnit = selected))
                    },
                    onOpenExercise = { onOpenExercise(current.exerciseId) },
                    onRestStarted = { seconds ->
                        restTotal = seconds
                        restTicker += 1
                    },
                    onToast = { message -> toast = message },
                    onRemoveExtra = {
                        extraExerciseIds = extraExerciseIds - current.exerciseId
                        activeIndex = 0
                    },
                    isExtra = current.exerciseId in extraExerciseIds,
                )
            }

            toast?.let { message ->
                InfoNote(message, emphasise = true)
            }

            Spacer(Modifier.size(8.dp))
            PrimaryButton(
                text = "Finish workout",
                icon = CadenceIcon.Check,
                enabled = session.sets.isNotEmpty(),
                onClick = { pendingFinish = true },
            )
            Spacer(Modifier.size(20.dp))
        }
    }

    if (showPicker) {
        ExercisePickerOverlay(
            catalog = viewModel.catalog,
            availableEquipment = state.profile.availableEquipment,
            excludeIds = slots.map { it.exerciseId }.toSet(),
            onPick = { exerciseId ->
                extraExerciseIds = extraExerciseIds + exerciseId
                showPicker = false
                activeIndex = slots.size
            },
            onDismiss = { showPicker = false },
        )
    }

    if (pendingFinish) {
        ConfirmDialog(
            title = "Finish workout?",
            message = "${session.sets.size} sets across ${session.exerciseIds.size} exercises will be saved " +
                "to your history.",
            confirmLabel = "Save workout",
            destructive = false,
            onConfirm = {
                pendingFinish = false
                viewModel.finishSession(sessionId)
                onFinished()
            },
            onDismiss = { pendingFinish = false },
        )
    }

    if (pendingDiscard) {
        ConfirmDialog(
            title = "Discard this workout?",
            message = "Nothing from this session will be kept. This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                pendingDiscard = false
                viewModel.discardSession(sessionId)
                onFinished()
            },
            onDismiss = { pendingDiscard = false },
        )
    }
}

private fun buildSlots(
    viewModel: CadenceViewModel,
    routine: Routine?,
    session: WorkoutSession,
    extras: List<String>,
): List<LogSlot> {
    val catalog = viewModel.catalog
    val fromRoutine = routine?.exercises.orEmpty().mapNotNull { slot ->
        catalog.byId(slot.exerciseId)?.let { LogSlot(it, slot) }
    }
    val known = fromRoutine.map { it.exerciseId }.toSet()
    val logged = session.exerciseIds
        .filterNot { it in known }
        .mapNotNull { id -> catalog.byId(id)?.let { LogSlot(it, routineSlot(routine, id)) } }
    val added = extras
        .filterNot { it in known || it in logged.map { slot -> slot.exerciseId } }
        .mapNotNull { id -> catalog.byId(id)?.let { LogSlot(it, routineSlot(routine, id)) } }
    return fromRoutine + logged + added
}

private fun routineSlot(routine: Routine?, exerciseId: String): RoutineExercise? =
    routine?.exercises?.firstOrNull { it.exerciseId == exerciseId }

@Composable
private fun ExerciseStrip(
    slots: List<LogSlot>,
    session: WorkoutSession,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEachIndexed { index, slot ->
            val done = WorkoutLogger.completedSetsFor(session, slot.exerciseId)
            FilterChip(
                label = if (done >= slot.targetSets && slot.targetSets > 0) {
                    "${slot.exercise.name} ✓"
                } else {
                    slot.exercise.name
                },
                selected = index == activeIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RestTimerBar(
    remaining: Int,
    total: Int,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
) {
    val finished = remaining <= 0
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (finished) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    CadenceTheme.tones.rest.copy(alpha = 0.14f)
                },
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CadenceIcon(
                CadenceIcon.Timer,
                size = 18.dp,
                tint = if (finished) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    CadenceTheme.tones.rest
                },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (finished) "Rest complete" else "Rest",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    if (finished) "Ready for the next set" else Durations.clock(remaining),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            FilterChip(label = "+30s", selected = false, onClick = onExtend)
            Spacer(Modifier.width(8.dp))
            FilterChip(label = "Skip", selected = false, onClick = onSkip)
        }
        if (!finished && total > 0) {
            Spacer(Modifier.size(8.dp))
            CadenceProgressBar(
                fraction = (total - remaining).toFloat() / total.toFloat(),
                color = CadenceTheme.tones.rest,
            )
        }
    }
}

@Composable
private fun ExerciseLogger(
    viewModel: CadenceViewModel,
    session: WorkoutSession,
    slot: LogSlot,
    unit: WeightUnit,
    onUnitChange: (WeightUnit) -> Unit,
    onOpenExercise: () -> Unit,
    onRestStarted: (Int) -> Unit,
    onToast: (String) -> Unit,
    onRemoveExtra: () -> Unit,
    isExtra: Boolean,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = viewModel.nowMs()
    val previous = remember(session.id, slot.exerciseId, now) {
        WorkoutLogger.previousPerformance(state.sessions, slot.exerciseId, now)
    }

    var isWarmup by remember(slot.exerciseId) { mutableStateOf(false) }
    val defaults = remember(session.sets.size, slot.exerciseId, isWarmup, unit) {
        WorkoutLogger.entryDefaults(session, slot.exerciseId, previous, slot.routineExercise, isWarmup)
    }
    var weightDisplay by remember(slot.exerciseId, isWarmup) {
        mutableStateOf(unit.snap(unit.fromKilograms(defaults.weightKg)))
    }
    var reps by remember(slot.exerciseId, isWarmup) { mutableIntStateOf(defaults.reps) }
    var rpe by remember(slot.exerciseId) { mutableStateOf<Double?>(null) }
    var note by remember(slot.exerciseId) { mutableStateOf("") }
    var editingSetId by remember(slot.exerciseId) { mutableStateOf<String?>(null) }

    val logged = session.setsFor(slot.exerciseId).sortedBy { it.setIndex }

    CadenceCard {
        SectionHeader(
            title = slot.exercise.name,
            subtitle = "${slot.targetSets} × ${slot.repRange.first}-${slot.repRange.last} reps · " +
                "${slot.restSeconds}s rest",
            trailing = {
                CadenceIcon(
                    CadenceIcon.ChevronRight,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        slot.supersetLabel?.let { label ->
            Spacer(Modifier.size(8.dp))
            Badge(label)
        }
        Spacer(Modifier.size(10.dp))
        PreviousRow(previous, unit)
        Spacer(Modifier.size(6.dp))
        Text(
            slot.exercise.primaryMuscles.joinToString(", ") { it.displayName } +
                " · " + slot.exercise.equipment.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "About this exercise",
                onClick = onOpenExercise,
                modifier = Modifier.weight(1f),
            )
            if (isExtra) {
                SecondaryButton(
                    text = "Remove",
                    destructive = true,
                    onClick = onRemoveExtra,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (logged.isNotEmpty()) {
        CadenceCard {
            SectionHeader(title = "This session", subtitle = "${logged.size} sets logged")
            Spacer(Modifier.size(8.dp))
            logged.forEach { set ->
                LoggedSetRow(
                    set = set,
                    unit = unit,
                    onClick = {
                        editingSetId = set.id
                        weightDisplay = unit.snap(unit.fromKilograms(set.weightKg))
                        reps = set.reps
                        rpe = set.rpe
                        note = set.note
                        isWarmup = set.isWarmup
                    },
                    onDelete = { viewModel.deleteSet(session.id, set.id) },
                )
            }
            Spacer(Modifier.size(6.dp))
            SecondaryButton(
                text = "Undo last set",
                icon = CadenceIcon.Undo,
                onClick = {
                    viewModel.undoLastSet(session.id, slot.exerciseId)
                    editingSetId = null
                },
            )
        }
    }

    CadenceCard {
        SectionHeader(
            title = if (editingSetId != null) "Editing a set" else "Log a set",
            subtitle = if (defaults.repeatedFromCurrentSession && editingSetId == null) {
                "Pre-filled from your last set in this workout"
            } else {
                null
            },
        )
        Spacer(Modifier.size(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedOptions(
                options = listOf("Working", "Warm-up"),
                selectedIndex = if (isWarmup) 1 else 0,
                onSelect = { index ->
                    isWarmup = index == 1
                    editingSetId = null
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            SegmentedOptions(
                options = listOf("kg", "lb"),
                selectedIndex = if (unit == WeightUnit.KG) 0 else 1,
                onSelect = { index -> onUnitChange(if (index == 0) WeightUnit.KG else WeightUnit.LB) },
                modifier = Modifier.width(110.dp),
            )
        }

        Spacer(Modifier.size(18.dp))
        NumeralRow(
            label = "Weight (${unit.abbreviation})",
            value = Weights.formatAmount(weightDisplay),
            onMinus = {
                weightDisplay = (weightDisplay - unit.fineIncrement).coerceAtLeast(0.0)
                editingSetId = null
            },
            onPlus = {
                weightDisplay += unit.fineIncrement
                editingSetId = null
            },
        )
        Spacer(Modifier.size(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                label = "-${Weights.formatAmount(unit.increment)}",
                selected = false,
                onClick = { weightDisplay = (weightDisplay - unit.increment).coerceAtLeast(0.0) },
            )
            FilterChip(
                label = "+${Weights.formatAmount(unit.increment)}",
                selected = false,
                onClick = { weightDisplay += unit.increment },
            )
            FilterChip(
                label = previous?.topWeightKg
                    ?.takeIf { it > 0.0 }
                    ?.let { "Last: ${Weights.format(it, unit)}" }
                    ?: "Bodyweight",
                selected = false,
                onClick = {
                    previous?.let { previousSet -> weightDisplay = unit.display(previousSet.topWeightKg) }
                },
            )
        }

        Spacer(Modifier.size(20.dp))
        NumeralRow(
            label = "Reps",
            value = reps.toString(),
            onMinus = { reps = (reps - 1).coerceAtLeast(0); editingSetId = null },
            onPlus = { reps += 1; editingSetId = null },
        )
        Spacer(Modifier.size(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                label = "Target ${slot.repRange.last}",
                selected = false,
                onClick = { reps = slot.repRange.last },
            )
            listOf(1, 2, 5).forEach { amount ->
                FilterChip(
                    label = "+$amount",
                    selected = false,
                    onClick = { reps += amount },
                )
            }
        }

        Spacer(Modifier.size(20.dp))
        Text("Effort (optional)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6.0, 7.0, 8.0, 9.0, 10.0).forEach { value ->
                FilterChip(
                    label = "RPE ${value.toInt()}",
                    selected = rpe == value,
                    onClick = { rpe = if (rpe == value) null else value },
                )
            }
        }

        Spacer(Modifier.size(16.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Set note (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.size(16.dp))
        PrimaryButton(
            text = if (editingSetId != null) "Save changes" else "Log set",
            icon = CadenceIcon.Check,
            enabled = reps > 0,
            onClick = {
                val weightKg = unit.toKilograms(unit.snap(weightDisplay))
                val existing = editingSetId
                if (existing == null) {
                    val set = LoggedSet(
                        id = "",
                        sessionId = session.id,
                        exerciseId = slot.exerciseId,
                        setIndex = WorkoutLogger.nextSetIndex(session, slot.exerciseId),
                        weightKg = weightKg,
                        reps = reps,
                        rpe = rpe,
                        isWarmup = isWarmup,
                        completedAtMs = now,
                        note = note,
                    )
                    viewModel.logSet(
                        sessionId = session.id,
                        exerciseId = slot.exerciseId,
                        weightKg = weightKg,
                        reps = reps,
                        rpe = rpe,
                        isWarmup = isWarmup,
                        note = note,
                    )
                    onToast(
                        if (isRecord(previous, weightKg, reps)) {
                            "New best estimated 1RM for ${slot.exercise.name}."
                        } else {
                            "Set logged. Rest ${slot.restSeconds}s."
                        },
                    )
                    if (!isWarmup) {
                        onRestStarted(
                            WorkoutLogger.recommendedRestSeconds(set, slot.routineExercise),
                        )
                    }
                    note = ""
                } else {
                    viewModel.updateSet(
                        sessionId = session.id,
                        setId = existing,
                        weightKg = weightKg,
                        reps = reps,
                        rpe = rpe,
                        isWarmup = isWarmup,
                        note = note,
                    )
                    editingSetId = null
                }
                rpe = null
            },
        )
        if (editingSetId != null) {
            Spacer(Modifier.size(8.dp))
            SecondaryButton(
                text = "Cancel edit",
                onClick = {
                    editingSetId = null
                    note = ""
                    rpe = null
                },
            )
        }
    }

    if (slot.warmupSets > 0) {
        WarmupCard(
            workingWeightKg = unit.toKilograms(weightDisplay),
            slot = slot,
            unit = unit,
            onUse = { step ->
                weightDisplay = unit.display(step.weightKg)
                isWarmup = true
                reps = step.reps
                editingSetId = null
            },
        )
    }
}

private fun isRecord(previous: PreviousPerformance?, weightKg: Double, reps: Int): Boolean {
    if (weightKg <= 0.0 || reps <= 0) return false
    val best = previous?.bestSet?.estimatedOneRepMaxKg ?: 0.0
    val candidate = weightKg * (1 + reps / 30.0)
    return candidate > best + 0.01
}

@Composable
private fun PreviousRow(previous: PreviousPerformance?, unit: WeightUnit) {
    if (previous == null || previous.sets.isEmpty()) {
        InfoNote("No previous session for this exercise yet — the first set sets your baseline.")
        return
    }
    Text(
        "Last time (${Weights.formatVolume(previous.totalVolumeKg, unit)} total): " +
            previous.sets.joinToString(", ") { set ->
                "${Weights.format(set.weightKg, unit)}×${set.reps}"
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoggedSetRow(
    set: LoggedSet,
    unit: WeightUnit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${set.setIndex}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "${Weights.format(set.weightKg, unit)} ${unit.abbreviation} × ${set.reps}",
                style = MaterialTheme.typography.titleSmall,
            )
            val extras = buildList {
                if (set.isWarmup) add("Warm-up")
                set.rpe?.let { add("RPE ${Weights.formatAmount(it)}") }
                if (set.note.isNotBlank()) add(set.note)
                add("${Weights.formatVolume(set.volumeKg, unit)} volume")
            }
            Text(
                extras.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilterChip(label = "Edit", selected = false, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        FilterChip(label = "Delete", selected = false, onClick = onDelete)
    }
}

@Composable
private fun NumeralRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        StepButton(
            icon = CadenceIcon.Minus,
            contentDescription = "Decrease $label",
            onClick = onMinus,
            size = 60.dp,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(value, style = MaterialTheme.typography.displaySmall)
        }
        StepButton(
            icon = CadenceIcon.Plus,
            contentDescription = "Increase $label",
            onClick = onPlus,
            size = 60.dp,
        )
    }
}

@Composable
private fun WarmupCard(
    workingWeightKg: Double,
    slot: LogSlot,
    unit: WeightUnit,
    onUse: (com.cadence.fit.domain.workout.WarmupStep) -> Unit,
) {
    val steps = remember(workingWeightKg, slot.warmupSets, unit) {
        WorkoutLogger.warmupPlan(workingWeightKg, slot.warmupSets, unit)
    }
    if (steps.isEmpty()) return
    CadenceCard {
        SectionHeader(
            title = "Suggested warm-up",
            subtitle = "Percentages of today's working weight. Tap one to load it.",
        )
        Spacer(Modifier.size(10.dp))
        steps.forEach { step ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Weights.format(step.weightKg, unit)} ${unit.abbreviation} × ${step.reps}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        step.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(label = "Use", selected = false, onClick = { onUse(step) })
            }
        }
    }
}
