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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.insight.RecoveryContext
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.domain.units.Durations
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.HomeModel
import com.cadence.fit.ui.NextAction
import com.cadence.fit.ui.NextActionType
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.Sparkline
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.theme.CadenceTheme

/**
 * Deliberately short: today's workout, a small progress summary and one clear
 * next action. Everything explanatory lives on its own page.
 */
@Composable
fun HomeScreen(
    viewModel: CadenceViewModel,
    onStartWorkout: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onBuildPlan: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val now = viewModel.nowMs()
    val model = remember(state, now) { ScreenModels.home(state, viewModel.catalog, now) }

    val sleepStatus by viewModel.sleepStatus.collectAsStateWithLifecycle()
    val sleepSessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    val sleepEnabled = state.settings.sleepInsightsEnabled
    val recovery = remember(sleepSessions, sleepEnabled, now) {
        ScreenModels.recovery(sleepSessions, sleepEnabled, now)
    }

    var awaitingStart by remember { mutableStateOf(false) }
    val activeId = state.activeSession?.id
    LaunchedEffect(activeId, awaitingStart) {
        if (awaitingStart && activeId != null) {
            awaitingStart = false
            onOpenSession(activeId)
        }
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Cadence",
            subtitle = TimeRanges.relativeDay(now, now),
            actions = listOf(
                CadenceIcon.Sleep to onOpenSleep,
                CadenceIcon.Settings to onOpenSettings,
            ),
            actionDescriptions = listOf("Sleep and recovery", "Settings and privacy"),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NextActionCard(
                action = model.nextAction,
                onAct = {
                    when (model.nextAction.type) {
                        NextActionType.BUILD_PLAN -> onBuildPlan()
                        NextActionType.RESUME_WORKOUT -> model.activeSession?.let { onOpenSession(it.id) }
                        NextActionType.START_WORKOUT -> {
                            awaitingStart = true
                            onStartWorkout(model.nextAction.routineId)
                        }
                    }
                },
            )

            if (model.todayRoutine != null && model.activeSession == null) {
                TodayFocusCard(model)
            }

            ProgressSummaryCard(model, unit, onOpenPlan)

            if (recovery != null) {
                RecoveryCard(recovery, onOpenSleep)
            } else if (!sleepEnabled && sleepStatus.isActionable) {
                SleepTeaserCard(onOpenSleep)
            }

            model.lastCompleted?.let { session ->
                SectionHeader(title = "Last session")
                CadenceCard(onClick = { onOpenSession(session.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.routineName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${TimeRanges.relativeDay(session.completedAtMs, now)} · " +
                                    "${session.workingSets.size} sets · " +
                                    Weights.formatVolume(session.totalVolumeKg, unit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            session.durationMinutes?.let { minutes ->
                                Text(
                                    Durations.hoursMinutes(minutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        CadenceIcon(
                            CadenceIcon.ChevronRight,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (model.summary.totalSessions == 0) {
                EmptyState(
                    title = "No sessions yet",
                    message = "Your first logged workout will start building your history, " +
                        "records and charts.",
                    icon = CadenceIcon.TrendUp,
                    actionLabel = "Browse exercises",
                    onAction = onOpenLibrary,
                )
            }

            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun NextActionCard(action: NextAction, onAct: () -> Unit) {
    val accent = CadenceTheme.tones
    CadenceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY", style = MaterialTheme.typography.labelSmall, color = accent.rest)
            Spacer(Modifier.size(8.dp))
            when (action.type) {
                NextActionType.RESUME_WORKOUT -> Badge("In progress")
                NextActionType.BUILD_PLAN -> Badge("Setup")
                NextActionType.START_WORKOUT -> Unit
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(action.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(4.dp))
        Text(
            action.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        PrimaryButton(
            text = when (action.type) {
                NextActionType.RESUME_WORKOUT -> "Continue workout"
                NextActionType.BUILD_PLAN -> "Answer a few questions"
                NextActionType.START_WORKOUT -> "Start workout"
            },
            icon = when (action.type) {
                NextActionType.RESUME_WORKOUT -> CadenceIcon.Plan
                NextActionType.BUILD_PLAN -> CadenceIcon.Edit
                NextActionType.START_WORKOUT -> CadenceIcon.Timer
            },
            onClick = onAct,
        )
    }
}

@Composable
private fun TodayFocusCard(model: HomeModel) {
    val routine = model.todayRoutine ?: return
    CadenceCard {
        Text("Up next", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        Text(routine.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${routine.focus} · ${routine.exercises.size} exercises · " +
                "about ${routine.estimatedMinutes()} min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            "${routine.workingSets} working sets · ${routine.warmupSets} warm-up sets",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProgressSummaryCard(
    model: HomeModel,
    unit: com.cadence.fit.domain.units.WeightUnit,
    onOpenPlan: () -> Unit,
) {
    val summary = model.summary
    CadenceCard {
        SectionHeader(title = "Progress", subtitle = "Last 4 weeks")
        Spacer(Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatTile(
                label = "This week",
                value = "${summary.sessionsThisWeek}/${summary.targetSessionsPerWeek}",
                hint = "sessions",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Volume",
                value = Weights.formatVolume(summary.volumeThisWeekKg, unit, withUnit = false),
                hint = unit.abbreviation,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Streak",
                value = "${summary.streakWeeks}",
                hint = if (summary.streakWeeks == 1) "week" else "weeks",
                modifier = Modifier.weight(1f),
            )
        }
        if (model.volumeTrend.any { it > 0f }) {
            Spacer(Modifier.size(16.dp))
            Sparkline(values = model.volumeTrend)
        }
        Spacer(Modifier.size(14.dp))
        SecondaryButton(text = "Open your plan", onClick = onOpenPlan)
    }
}

@Composable
private fun RecoveryCard(recovery: RecoveryContext, onOpenSleep: () -> Unit) {
    CadenceCard(onClick = onOpenSleep) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CadenceIcon(CadenceIcon.Sleep, size = 18.dp, tint = CadenceTheme.tones.rest)
            Spacer(Modifier.size(8.dp))
            Text("Recovery context", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            CadenceIcon(
                CadenceIcon.ChevronRight,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(recovery.headline, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        Text(
            recovery.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SleepTeaserCard(onOpenSleep: () -> Unit) {
    CadenceCard(onClick = onOpenSleep) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CadenceIcon(CadenceIcon.Sleep, size = 18.dp, tint = CadenceTheme.tones.rest)
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Add sleep context", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Optional, read-only, and off by default.",
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
