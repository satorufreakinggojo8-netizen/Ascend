package com.cadence.fit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.ExercisePickerOverlay
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.StepButton
import com.cadence.fit.ui.components.SubstitutionOverlay
import com.cadence.fit.ui.components.ThinDivider

/**
 * Create or edit one routine: name, goal, and the exercise slots with their sets,
 * rep range, rest and warm-up sets. Everything is editable, including the routines
 * the plan builder generated.
 */
@Composable
fun RoutineEditorScreen(
    viewModel: CadenceViewModel,
    routineId: String?,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existing = remember(routineId, state.routines) {
        routineId?.let { id -> state.routines.firstOrNull { it.id == id } }
    }

    var name by remember(routineId) { mutableStateOf(existing?.name ?: "") }
    var focus by remember(routineId) { mutableStateOf(existing?.focus ?: "") }
    var goal by remember(routineId) { mutableStateOf(existing?.goal ?: state.profile.goal) }
    var location by remember(routineId) {
        mutableStateOf(existing?.location ?: state.profile.location)
    }
    val slots = remember(routineId) {
        mutableStateListOf<RoutineExercise>().also { list -> existing?.exercises?.let(list::addAll) }
    }
    var showPicker by remember { mutableStateOf(false) }
    var swapSlotIndex by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val isNew = existing == null

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = if (isNew) "New routine" else "Edit routine",
            subtitle = if (isNew) "Nothing is saved until you tap Save" else existing?.focus,
            onBack = onBack,
            actions = listOf(CadenceIcon.Plus to { showPicker = true }),
            actionDescriptions = listOf("Add an exercise"),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CadenceCard {
                SectionHeader(title = "Basics")
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = focus,
                    onValueChange = { focus = it },
                    label = { Text("Focus, e.g. Push, Legs, Full body") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(16.dp))
                Text("Goal", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrainingGoal.entries.forEach { option ->
                        FilterChip(
                            label = option.displayName,
                            selected = goal == option,
                            onClick = { goal = option },
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Text("Where you train it", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrainingLocation.entries.forEach { option ->
                        FilterChip(
                            label = option.displayName,
                            selected = location == option,
                            onClick = { location = option },
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                InfoNote(
                    "Rep range and rest default from the goal: " +
                        "${goal.displayName.lowercase()} uses ${goal.defaultRepRange.first}-" +
                        "${goal.defaultRepRange.last} reps with ${goal.defaultRestSeconds}s rest.",
                )
            }

            if (slots.isEmpty()) {
                CadenceCard {
                    SectionHeader(title = "Exercises", subtitle = "None yet")
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Add movements in the order you want to train them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    PrimaryButton(
                        text = "Add an exercise",
                        icon = CadenceIcon.Plus,
                        onClick = { showPicker = true },
                    )
                }
            } else {
                slots.forEachIndexed { index, slot ->
                    val exercise = viewModel.catalog.byId(slot.exerciseId)
                    SlotEditor(
                        index = index,
                        slot = slot,
                        exerciseName = exercise?.name ?: slot.exerciseId,
                        muscleLine = exercise?.let { item ->
                            item.primaryMuscles.joinToString(", ") { it.displayName } +
                                " · " + item.equipment.displayName
                        }.orEmpty(),
                        onOpenExercise = { onOpenExercise(slot.exerciseId) },
                        onSwap = { swapSlotIndex = index },
                        onChange = { updated -> slots[index] = updated },
                        onMoveUp = {
                            if (index > 0) {
                                val item = slots.removeAt(index)
                                slots.add(index - 1, item)
                            }
                        },
                        onMoveDown = {
                            if (index < slots.size - 1) {
                                val item = slots.removeAt(index)
                                slots.add(index + 1, item)
                            }
                        },
                        onRemove = { slots.removeAt(index) },
                    )
                }

                SecondaryButton(
                    text = "Add another exercise",
                    icon = CadenceIcon.Plus,
                    onClick = { showPicker = true },
                )
            }

            error?.let { message -> InfoNote(message, emphasise = true, icon = CadenceIcon.Warning) }

            Spacer(Modifier.size(8.dp))
            PrimaryButton(
                text = if (isNew) "Create routine" else "Save routine",
                icon = CadenceIcon.Check,
                onClick = {
                    if (name.isBlank()) {
                        error = "Give the routine a name so you can recognise it later."
                        return@PrimaryButton
                    }
                    if (slots.isEmpty()) {
                        error = "Add at least one exercise before saving."
                        return@PrimaryButton
                    }
                    val routine = Routine(
                        id = existing?.id ?: viewModel.newId(),
                        name = name.trim(),
                        focus = focus.trim().ifBlank { goal.displayName },
                        goal = goal,
                        location = location,
                        exercises = slots.toList(),
                        isStarterPlan = existing?.isStarterPlan ?: false,
                        createdAtMs = existing?.createdAtMs ?: viewModel.nowMs(),
                    )
                    viewModel.saveRoutine(routine)
                    onBack()
                },
            )
            if (!isNew) {
                Spacer(Modifier.size(8.dp))
                SecondaryButton(
                    text = "Delete routine",
                    icon = CadenceIcon.Trash,
                    destructive = true,
                    onClick = {
                        viewModel.deleteRoutine(existing.id)
                        onBack()
                    },
                )
            }
            Spacer(Modifier.size(20.dp))
        }
    }

    if (showPicker) {
        ExercisePickerOverlay(
            catalog = viewModel.catalog,
            availableEquipment = state.profile.availableEquipment,
            excludeIds = slots.map { it.exerciseId }.toSet(),
            onPick = { exerciseId ->
                slots.add(
                    RoutineExercise(
                        id = viewModel.newId(),
                        exerciseId = exerciseId,
                        targetSets = if (goal == TrainingGoal.STRENGTH) 4 else 3,
                        repRange = goal.defaultRepRange,
                        restSeconds = goal.defaultRestSeconds,
                        warmupSets = 0,
                    ),
                )
                showPicker = false
                error = null
            },
            onDismiss = { showPicker = false },
        )
    }

    val swapIndex = swapSlotIndex
    if (swapIndex != null) {
        val slot = slots.getOrNull(swapIndex)
        val targetExercise = slot?.let { viewModel.catalog.byId(it.exerciseId) }
        if (slot == null || targetExercise == null) {
            swapSlotIndex = null
        } else {
            SubstitutionOverlay(
                target = targetExercise,
                catalog = viewModel.catalog,
                availableEquipment = state.profile.availableEquipment,
                experience = state.profile.experience,
                excludeIds = slots.map { it.exerciseId }.toSet(),
                onPick = { replacement ->
                    // The slot keeps its id, sets, reps and rest, so anything already
                    // logged for the old movement stays in history.
                    slots[swapIndex] = slot.copy(exerciseId = replacement.id)
                    swapSlotIndex = null
                    error = null
                },
                onDismiss = { swapSlotIndex = null },
            )
        }
    }
}

@Composable
private fun SlotEditor(
    index: Int,
    slot: RoutineExercise,
    exerciseName: String,
    muscleLine: String,
    onOpenExercise: () -> Unit,
    onSwap: () -> Unit,
    onChange: (RoutineExercise) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    CadenceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(exerciseName, style = MaterialTheme.typography.titleSmall)
                if (muscleLine.isNotBlank()) {
                    Text(
                        muscleLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            slot.supersetLabel?.let { label -> Badge(label) }
        }

        Spacer(Modifier.size(12.dp))
        CounterRow(
            label = "Working sets",
            value = slot.targetSets.toString(),
            onMinus = { onChange(slot.copy(targetSets = (slot.targetSets - 1).coerceAtLeast(1))) },
            onPlus = { onChange(slot.copy(targetSets = (slot.targetSets + 1).coerceAtMost(10))) },
        )
        CounterRow(
            label = "Rep range",
            value = "${slot.repRange.first}-${slot.repRange.last}",
            onMinus = {
                val lower = (slot.repRange.first - 1).coerceAtLeast(1)
                onChange(slot.copy(repRange = lower..slot.repRange.last.coerceAtLeast(lower)))
            },
            onPlus = {
                val higher = (slot.repRange.last + 1).coerceAtMost(30)
                onChange(slot.copy(repRange = slot.repRange.first.coerceAtMost(higher)..higher))
            },
        )
        CounterRow(
            label = "Rest (seconds)",
            value = slot.restSeconds.toString(),
            onMinus = { onChange(slot.copy(restSeconds = (slot.restSeconds - 15).coerceAtLeast(0))) },
            onPlus = { onChange(slot.copy(restSeconds = (slot.restSeconds + 15).coerceAtMost(600))) },
        )
        CounterRow(
            label = "Warm-up sets",
            value = slot.warmupSets.toString(),
            onMinus = { onChange(slot.copy(warmupSets = (slot.warmupSets - 1).coerceAtLeast(0))) },
            onPlus = { onChange(slot.copy(warmupSets = (slot.warmupSets + 1).coerceAtMost(5))) },
        )

        Spacer(Modifier.size(10.dp))
        OutlinedTextField(
            value = slot.note,
            onValueChange = { onChange(slot.copy(note = it)) },
            label = { Text("Note for this slot (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.size(12.dp))
        ThinDivider()
        Spacer(Modifier.size(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                label = if (slot.supersetGroup == null) "Make superset" else "Plain sets",
                selected = slot.supersetGroup != null,
                onClick = {
                    onChange(
                        slot.copy(supersetGroup = if (slot.supersetGroup == null) 0 else null),
                    )
                },
            )
            FilterChip(
                label = "Move up",
                selected = false,
                onClick = onMoveUp,
            )
            FilterChip(label = "Move down", selected = false, onClick = onMoveDown)
            FilterChip(label = "Swap", selected = false, onClick = onSwap)
            FilterChip(label = "About", selected = false, onClick = onOpenExercise)
            FilterChip(label = "Remove", selected = false, onClick = onRemove)
        }
        if (slot.supersetGroup != null) {
            Spacer(Modifier.size(8.dp))
            InfoNote(
                "Supersets pair this slot with the exercise right after it, so you move " +
                    "straight from one to the other.",
            )
        }
    }
}

@Composable
private fun CounterRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        StepButton(
            icon = CadenceIcon.Minus,
            contentDescription = "Decrease $label",
            onClick = onMinus,
            size = 40.dp,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton(
            icon = CadenceIcon.Plus,
            contentDescription = "Increase $label",
            onClick = onPlus,
            size = 40.dp,
        )
    }
}
