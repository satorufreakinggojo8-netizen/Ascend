package com.cadence.fit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.ThinDivider

/**
 * The plan page: the routines that make up the week, what each one contains, and
 * the controls to edit, rebuild or start one.
 */
@Composable
fun PlanScreen(
    viewModel: CadenceViewModel,
    onOpenRoutine: (String) -> Unit,
    onNewRoutine: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onStartAndOpen: (String) -> Unit,
    onOpenSchedule: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile
    var pendingDelete by remember { mutableStateOf<Routine?>(null) }
    var awaitingStart by remember { mutableStateOf(false) }
    val activeId = state.activeSession?.id

    androidx.compose.runtime.LaunchedEffect(activeId, awaitingStart) {
        if (awaitingStart && activeId != null) {
            awaitingStart = false
            onStartAndOpen(activeId)
        }
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Plan",
            subtitle = "${profile.daysPerWeek} days a week · ${profile.goal.displayName}",
            actions = listOf(
                CadenceIcon.Calendar to onOpenSchedule,
                CadenceIcon.Plus to onNewRoutine,
            ),
            actionDescriptions = listOf("This week's schedule", "New routine"),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.routines.isEmpty()) {
                EmptyState(
                    title = "No routines yet",
                    message = "Build a starter plan from your answers, or create a routine " +
                        "exercise by exercise.",
                    icon = CadenceIcon.Plan,
                    actionLabel = "Rebuild from my settings",
                    onAction = { viewModel.regeneratePlan() },
                )
                SecondaryButton(text = "Create a routine", onClick = onNewRoutine)
            } else {
                InfoNote(
                    "Built for ${profile.location.displayName.lowercase()} training with " +
                        "${profile.availableEquipment.size} kinds of equipment available. " +
                        "Rebuilding replaces the generated routines only.",
                )

                SecondaryButton(
                    text = "This week's schedule",
                    icon = CadenceIcon.Calendar,
                    onClick = onOpenSchedule,
                )

                state.routines.forEach { routine ->
                    RoutineCard(
                        routine = routine,
                        onEdit = { onOpenRoutine(routine.id) },
                        onStart = {
                            awaitingStart = true
                            onStartRoutine(routine.id)
                        },
                        onDelete = { pendingDelete = routine },
                    )
                }

                Spacer(Modifier.size(4.dp))
                SecondaryButton(text = "New routine", icon = CadenceIcon.Plus, onClick = onNewRoutine)
                SecondaryButton(
                    text = "Rebuild starter plan",
                    icon = CadenceIcon.Undo,
                    onClick = { viewModel.regeneratePlan() },
                )
                Spacer(Modifier.size(8.dp))
            }
        }
    }

    pendingDelete?.let { routine ->
        ConfirmDialog(
            title = "Delete ${routine.name}?",
            message = "The routine is removed. Workouts you already logged are kept in history.",
            confirmLabel = "Delete routine",
            onConfirm = {
                viewModel.deleteRoutine(routine.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
) {
    CadenceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    routine.focus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (routine.isStarterPlan) Badge("Generated")
        }

        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MiniStat("${routine.exercises.size}", "exercises")
            MiniStat("${routine.workingSets}", "working sets")
            MiniStat("${routine.estimatedMinutes()}", "minutes")
        }

        Spacer(Modifier.size(12.dp))
        ThinDivider()
        Spacer(Modifier.size(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            routine.exercises.take(6).forEach { slot ->
                Badge(
                    text = slot.supersetLabel ?: slot.repRangeText + " reps",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (routine.exercises.size > 6) {
                Badge(
                    text = "+${routine.exercises.size - 6} more",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.size(14.dp))
        PrimaryButton(text = "Start ${routine.name}", icon = CadenceIcon.Timer, onClick = onStart)
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Edit",
                icon = CadenceIcon.Edit,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Delete",
                icon = CadenceIcon.Trash,
                destructive = true,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun MiniStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
