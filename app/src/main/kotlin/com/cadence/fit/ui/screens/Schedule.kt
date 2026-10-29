package com.cadence.fit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.schedule.SchedulePlanner
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.theme.CadenceTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val WEEK_RANGE = DateTimeFormatter.ofPattern("d MMM")

/**
 * The training week: what is planned, what was done, and what was skipped.
 *
 * Moving or skipping a day only changes the plan. Logged sessions are read from
 * history and are never edited here.
 */
@Composable
fun ScheduleScreen(
    viewModel: CadenceViewModel,
    onBack: () -> Unit,
    onStartAndOpen: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onEditRoutine: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = viewModel.today()
    var weekStart by remember { mutableStateOf(SchedulePlanner.weekStart(today)) }
    var pendingMove by remember { mutableStateOf<ScheduledWorkout?>(null) }
    var awaitingStart by remember { mutableStateOf(false) }
    val activeId = state.activeSession?.id

    LaunchedEffect(weekStart) {
        viewModel.ensureScheduleFor(weekStart)
    }
    LaunchedEffect(activeId, awaitingStart) {
        if (awaitingStart && activeId != null) {
            awaitingStart = false
            onStartAndOpen(activeId)
        }
    }

    val slots = state.schedule
        .filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekStart.plusDays(6)) }
        .sortedWith(compareBy({ it.date }, { it.id }))
    val sessions = state.completedSessions
        .filter {
            val date = TimeRanges.toLocalDate(it.completedAtMs)
            !date.isBefore(weekStart) && !date.isAfter(weekStart.plusDays(6))
        }
        .sortedBy { it.completedAtMs }
    val linkedSessionIds = slots.mapNotNull { it.sessionId }.toSet()
    val linkableSessions = state.completedSessions.filter { it.id !in linkedSessionIds }
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Schedule",
            subtitle = "${weekStart.format(WEEK_RANGE)} – ${weekStart.plusDays(6).format(WEEK_RANGE)}",
            onBack = onBack,
        )

        Row(
            Modifier
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                label = "Prev",
                selected = false,
                onClick = { weekStart = weekStart.minusWeeks(1) },
            )
            FilterChip(
                label = "This week",
                selected = weekStart == SchedulePlanner.weekStart(today),
                onClick = { weekStart = SchedulePlanner.weekStart(today) },
            )
            FilterChip(
                label = "Next",
                selected = false,
                onClick = { weekStart = weekStart.plusWeeks(1) },
            )
            Spacer(Modifier.weight(1f))
            val planned = slots.count { it.status == ScheduleStatus.PLANNED }
            val done = slots.count { it.status == ScheduleStatus.COMPLETED }
            if (slots.isNotEmpty()) {
                Badge("$done done")
                Badge(
                    text = "$planned left",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (slots.isEmpty() && sessions.isEmpty()) {
                EmptyState(
                    title = if (state.routines.isEmpty()) "No routines to plan yet" else "This week is open",
                    message = if (state.routines.isEmpty()) {
                        "Build a plan first, then its days appear here."
                    } else {
                        "Lay out this week from your routines. You can move or skip any day afterwards."
                    },
                    icon = CadenceIcon.Calendar,
                    actionLabel = if (state.routines.isEmpty()) null else "Plan this week",
                    onAction = if (state.routines.isEmpty()) null else {
                        { viewModel.ensureScheduleFor(weekStart) }
                    },
                )
            } else {
                InfoNote(
                    "Moving or skipping a day only changes the plan. Anything you have already " +
                        "logged stays in history untouched.",
                )
            }

            days.forEach { date ->
                val daySlots = slots.filter { it.date == date }
                val daySessions = sessions.filter {
                    TimeRanges.toLocalDate(it.completedAtMs) == date &&
                        daySlots.none { slot -> slot.sessionId == it.id }
                }
                DaySection(
                    date = date,
                    today = today,
                    slots = daySlots,
                    sessions = daySessions,
                    routinesExist = state.routines.isNotEmpty(),
                    linkableSessions = linkableSessions,
                    onStart = { routineId ->
                        awaitingStart = true
                        viewModel.startSession(routineId)
                    },
                    onOpenSession = onOpenSession,
                    onMove = { pendingMove = it },
                    onSkip = { viewModel.skipScheduled(it.id) },
                    onReopen = { viewModel.reopenScheduled(it.id) },
                    onComplete = { viewModel.completeScheduled(it.id) },
                    onRemove = { viewModel.deleteScheduled(it.id) },
                    onEditRoutine = onEditRoutine,
                )
            }

            if (slots.isNotEmpty()) {
                SecondaryButton(
                    text = "Clear planned days",
                    icon = CadenceIcon.Trash,
                    destructive = true,
                    onClick = { viewModel.clearPlannedSchedule() },
                )
            }
            Spacer(Modifier.size(12.dp))
        }
    }

    pendingMove?.let { slot ->
        MoveDialog(
            slot = slot,
            today = today,
            onPick = { date ->
                viewModel.moveScheduled(slot.id, date)
                pendingMove = null
            },
            onDismiss = { pendingMove = null },
        )
    }
}

@Composable
private fun DaySection(
    date: LocalDate,
    today: LocalDate,
    slots: List<ScheduledWorkout>,
    sessions: List<WorkoutSession>,
    routinesExist: Boolean,
    linkableSessions: List<WorkoutSession>,
    onStart: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onMove: (ScheduledWorkout) -> Unit,
    onSkip: (ScheduledWorkout) -> Unit,
    onReopen: (ScheduledWorkout) -> Unit,
    onComplete: (ScheduledWorkout) -> Unit,
    onRemove: (ScheduledWorkout) -> Unit,
    onEditRoutine: (String) -> Unit,
) {
    val isToday = date == today
    val isPast = date.isBefore(today)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isToday) "Today" else date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "${date.dayOfMonth} ${date.format(DateTimeFormatter.ofPattern("MMM"))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(6.dp))

        if (slots.isEmpty() && sessions.isEmpty()) {
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isPast) "Nothing planned" else "Rest day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            slots.forEach { slot ->
                SlotCard(
                    slot = slot,
                    isPast = isPast,
                    canLink = linkableSessions.isNotEmpty(),
                    onStart = onStart,
                    onOpenSession = onOpenSession,
                    onMove = onMove,
                    onSkip = onSkip,
                    onReopen = onReopen,
                    onComplete = onComplete,
                    onRemove = onRemove,
                    onEditRoutine = onEditRoutine,
                )
                Spacer(Modifier.size(8.dp))
            }
            sessions.forEach { session ->
                CadenceCard(onClick = { onOpenSession(session.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CadenceIcon(
                            CadenceIcon.Check,
                            size = 18.dp,
                            tint = CadenceTheme.tones.success,
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.routineName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Logged · ${session.workingSets.size} sets · " +
                                    "${session.durationMinutes ?: 0} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CadenceIcon(
                            CadenceIcon.ChevronRight,
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotCard(
    slot: ScheduledWorkout,
    isPast: Boolean,
    canLink: Boolean,
    onStart: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onMove: (ScheduledWorkout) -> Unit,
    onSkip: (ScheduledWorkout) -> Unit,
    onReopen: (ScheduledWorkout) -> Unit,
    onComplete: (ScheduledWorkout) -> Unit,
    onRemove: (ScheduledWorkout) -> Unit,
    onEditRoutine: (String) -> Unit,
) {
    val completed = slot.status == ScheduleStatus.COMPLETED
    CadenceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(slot.routineName, style = MaterialTheme.typography.titleSmall)
                Text(
                    when (slot.status) {
                        ScheduleStatus.PLANNED -> if (isPast) "Still open · past day" else "Planned"
                        ScheduleStatus.COMPLETED -> "Completed"
                        ScheduleStatus.SKIPPED -> "Skipped"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (slot.status) {
                ScheduleStatus.COMPLETED -> Badge(
                    text = "Done",
                    container = CadenceTheme.tones.success.copy(alpha = 0.16f),
                    content = CadenceTheme.tones.success,
                )
                ScheduleStatus.SKIPPED -> Badge(
                    text = "Skipped",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScheduleStatus.PLANNED -> Unit
            }
        }

        Spacer(Modifier.size(10.dp))
        if (completed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slot.sessionId?.let { sessionId ->
                    SecondaryButton(
                        text = "Open session",
                        onClick = { onOpenSession(sessionId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                slot.routineId?.let { routineId ->
                    SecondaryButton(
                        text = "Edit routine",
                        onClick = { onEditRoutine(routineId) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            if (slot.status == ScheduleStatus.PLANNED) {
                PrimaryButton(
                    text = "Start workout",
                    icon = CadenceIcon.Timer,
                    onClick = { onStart(slot.routineId) },
                )
                Spacer(Modifier.size(8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(label = "Move", selected = false, onClick = { onMove(slot) })
                if (slot.status == ScheduleStatus.PLANNED) {
                    FilterChip(label = "Skip", selected = false, onClick = { onSkip(slot) })
                    if (canLink) {
                        FilterChip(
                            label = "Link a session",
                            selected = false,
                            onClick = { onComplete(slot) },
                        )
                    }
                } else {
                    FilterChip(label = "Undo skip", selected = false, onClick = { onReopen(slot) })
                }
                FilterChip(label = "Remove", selected = false, onClick = { onRemove(slot) })
            }
        }
    }
}

@Composable
private fun MoveDialog(
    slot: ScheduledWorkout,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val days = (0..13).map { today.plusDays(it.toLong()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${slot.routineName}") },
        text = {
            Column {
                Text(
                    "Pick a new day. The session and anything you have logged stay as they are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    days.forEach { date ->
                        FilterChip(
                            label = "${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} " +
                                "${date.dayOfMonth} ${date.format(DateTimeFormatter.ofPattern("MMM"))}" +
                                if (date == slot.date) " · current" else "",
                            selected = date == slot.date,
                            onClick = { onPick(date) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
