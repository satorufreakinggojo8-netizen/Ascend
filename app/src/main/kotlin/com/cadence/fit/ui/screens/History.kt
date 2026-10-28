package com.cadence.fit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.domain.units.Durations
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.components.ThinDivider

/** Every finished session, newest first, grouped by month. */
@Composable
fun HistoryScreen(
    viewModel: CadenceViewModel,
    onOpenSession: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val now = viewModel.nowMs()
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val completed = state.completedSessions.sortedByDescending { it.completedAtMs }
    val groups = remember(completed) {
        completed.groupBy { TimeRanges.formatMonth(it.completedAtMs) }
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "History",
            subtitle = "${completed.size} sessions · " +
                Weights.formatVolume(completed.sumOf { it.totalVolumeKg }, unit),
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (completed.isEmpty()) {
                EmptyState(
                    title = "No finished workouts",
                    message = "Finish a workout and it appears here with every set you logged.",
                    icon = CadenceIcon.Calendar,
                )
            } else {
                state.activeSession?.let { active ->
                    CadenceCard {
                        SectionHeader(
                            title = "In progress",
                            subtitle = "${active.workingSets.size} working sets so far",
                        )
                        Spacer(Modifier.size(10.dp))
                        SecondaryButton(
                            text = "Open ${active.routineName}",
                            icon = CadenceIcon.Plan,
                            onClick = { onOpenSession(active.id) },
                        )
                    }
                }

                groups.forEach { (month, sessions) ->
                    SectionHeader(title = month, subtitle = "${sessions.size} sessions")
                    sessions.forEach { session ->
                        CadenceCard(onClick = { onOpenSession(session.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        session.routineName,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "${TimeRanges.relativeDay(session.completedAtMs, now)} · " +
                                            "${session.exerciseIds.size} exercises · " +
                                            "${session.workingSets.size} sets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        Weights.formatVolume(session.totalVolumeKg, unit),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    session.durationMinutes?.let { minutes ->
                                        Text(
                                            Durations.hoursMinutes(minutes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (session.note.isNotBlank()) {
                                Spacer(Modifier.size(8.dp))
                                InfoNote(session.note)
                            }
                            Spacer(Modifier.size(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Badge(
                                    text = TimeRanges.formatDay(session.completedAtMs),
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FilterChip(
                                    label = "Delete",
                                    selected = false,
                                    onClick = { pendingDelete = session.id },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    pendingDelete?.let { sessionId ->
        ConfirmDialog(
            title = "Delete this workout?",
            message = "Its sets are removed from your history, charts and records. " +
                "This cannot be undone.",
            confirmLabel = "Delete workout",
            onConfirm = {
                viewModel.deleteSession(sessionId)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** One finished session, set by set, with per-exercise subtotals. */
@Composable
fun SessionDetailScreen(
    viewModel: CadenceViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val now = viewModel.nowMs()
    val session = state.sessions.firstOrNull { it.id == sessionId }

    if (session == null) {
        Column(Modifier.fillMaxSize()) {
            CadenceTopBar(title = "Workout", onBack = onBack)
            InfoNote("That workout is no longer stored.")
        }
        return
    }

    val byExercise = session.exerciseIds.map { exerciseId ->
        exerciseId to session.setsFor(exerciseId).sortedBy { it.setIndex }
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = session.routineName,
            subtitle = TimeRanges.formatDay(session.completedAtMs),
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CadenceCard {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatTile(
                        "Working sets",
                        session.workingSets.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        "Volume",
                        Weights.formatVolume(session.totalVolumeKg, unit),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        "Reps",
                        session.totalReps.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                session.durationMinutes?.let { minutes ->
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "Took ${Durations.hoursMinutes(minutes)} · " +
                            "${session.warmupSets.size} warm-up sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (session.note.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    InfoNote(session.note)
                }
            }

            byExercise.forEach { (exerciseId, sets) ->
                val exercise = viewModel.catalog.byId(exerciseId)
                CadenceCard(onClick = { onOpenExercise(exerciseId) }) {
                    SectionHeader(
                        title = exercise?.name ?: exerciseId,
                        subtitle = "${sets.count { !it.isWarmup }} working sets · " +
                            Weights.formatVolume(
                                sets.filterNot { it.isWarmup }.sumOf { it.volumeKg },
                                unit,
                            ),
                        trailing = {
                            CadenceIcon(
                                CadenceIcon.ChevronRight,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Spacer(Modifier.size(10.dp))
                    sets.forEach { set ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "#${set.setIndex}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(width = 34.dp, height = 20.dp),
                            )
                            Text(
                                "${Weights.format(set.weightKg, unit)} ${unit.abbreviation} × ${set.reps}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            set.rpe?.let { rpe ->
                                Badge(
                                    text = "RPE ${Weights.formatAmount(rpe)}",
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (set.isWarmup) {
                                Spacer(Modifier.size(6.dp))
                                Badge("Warm-up")
                            }
                        }
                        if (set.note.isNotBlank()) {
                            Text(
                                set.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            ThinDivider()
            Text(
                "Estimated 1RM values are a formula, not a measurement — they are there to show " +
                    "direction of travel, not your maximum.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(20.dp))
        }
    }
}
