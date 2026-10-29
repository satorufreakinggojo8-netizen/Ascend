package com.cadence.fit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
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
import com.cadence.fit.domain.model.ScheduleStatus
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
    onOpenSchedule: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val now = viewModel.nowMs()
    val model = remember(state, now) { ScreenModels.home(state, viewModel.catalog, now) }

    val sleepStatus by viewModel.sleepStatus.collectAsStateWithLifecycle()
    val sleepSessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    // The repository is the single source of truth for the opt-in; the settings
    // screen mirrors it, so the home card cannot claim a connection that is off.
    val sleepEnabled = sleepStatus.enabled
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
                        NextActionType.REST_DAY -> onOpenSchedule()
                        NextActionType.VIEW_SESSION -> model.nextAction.sessionId?.let(onOpenSession)
                    }
                },
            )

            WeekStripCard(model, onOpenSchedule)

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
                NextActionType.REST_DAY -> Badge("Rest")
                NextActionType.VIEW_SESSION -> Badge("Logged")
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
                NextActionType.REST_DAY -> "See the week"
                NextActionType.VIEW_SESSION -> "Open session"
            },
            icon = when (action.type) {
                NextActionType.RESUME_WORKOUT -> CadenceIcon.Plan
                NextActionType.BUILD_PLAN -> CadenceIcon.Edit
                NextActionType.START_WORKOUT -> CadenceIcon.Timer
                NextActionType.REST_DAY -> CadenceIcon.Calendar
                NextActionType.VIEW_SESSION -> CadenceIcon.Check
            },
            onClick = onAct,
        )
    }
}

/** Seven quiet squares: the whole week at a glance, one tap from the calendar. */
@Composable
private fun WeekStripCard(model: HomeModel, onOpenSchedule: () -> Unit) {
    CadenceCard(onClick = onOpenSchedule) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(
                if (model.weekTally.isEmpty) "Not planned yet" else "${model.weekTally.completed} of ${model.weekTally.total} done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(6.dp))
            CadenceIcon(
                CadenceIcon.ChevronRight,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            model.weekDays.forEach { day ->
                val marker = when (day.status) {
                    ScheduleStatus.COMPLETED -> CadenceTheme.tones.success
                    ScheduleStatus.PLANNED -> MaterialTheme.colorScheme.primary
                    ScheduleStatus.SKIPPED -> MaterialTheme.colorScheme.outline
                    null -> MaterialTheme.colorScheme.surfaceVariant
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        day.letter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(marker.copy(alpha = if (day.status == null) 1f else 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day.status == ScheduleStatus.COMPLETED) {
                            CadenceIcon(CadenceIcon.Check, size = 15.dp, tint = marker)
                        } else {
                            Text(
                                day.dayOfMonth,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (day.isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.size(5.dp))
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (day.isToday) MaterialTheme.colorScheme.onSurface else Color.Transparent),
                    )
                }
            }
        }
        if (model.weekTally.isEmpty) {
            Spacer(Modifier.size(8.dp))
            Text(
                "No planned days yet. Open the week to lay out your sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
