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
import com.cadence.fit.domain.insight.ChartWindow
import com.cadence.fit.domain.insight.LevelAssessment
import com.cadence.fit.domain.math.PersonalRecordType
import com.cadence.fit.domain.progression.PlateauInsight
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.components.BarChart
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.LineChart
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.theme.CadenceTheme

/** Charts, records and level context. Filters change the window for every chart. */
@Composable
fun ProgressScreen(
    viewModel: CadenceViewModel,
    onOpenExercise: (String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val now = viewModel.nowMs()
    var window by remember { mutableStateOf(ChartWindow.default) }
    var selectedWeek by remember { mutableStateOf<Int?>(null) }
    var selectedConsistency by remember { mutableStateOf<Int?>(null) }

    val model = remember(state, window, now) {
        ScreenModels.progress(state, viewModel.catalog, window, unit, now)
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Progress",
            subtitle = "${model.summary.totalSessions} sessions logged",
            actions = listOf(CadenceIcon.Calendar to onOpenHistory),
            actionDescriptions = listOf("Workout history"),
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartWindow.entries.forEach { option ->
                    FilterChip(
                        label = option.displayName,
                        selected = option == window,
                        onClick = {
                            window = option
                            selectedWeek = null
                            selectedConsistency = null
                        },
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (model.summary.totalSessions == 0) {
                EmptyState(
                    title = "Charts need a workout or two",
                    message = "Log a session and volume, consistency and records start filling in.",
                    icon = CadenceIcon.Progress,
                )
            } else {
                CadenceCard {
                    SectionHeader(
                        title = "Training volume",
                        subtitle = "Total working volume per week",
                        trailing = {
                            CadenceIcon(
                                CadenceIcon.TrendUp,
                                size = 18.dp,
                                tint = CadenceTheme.tones.success,
                            )
                        },
                    )
                    Spacer(Modifier.size(14.dp))
                    BarChart(
                        values = model.volumeByWeek,
                        labels = model.weekLabels,
                        highlightIndex = selectedWeek,
                        onSelect = { index ->
                            selectedWeek = if (selectedWeek == index) null else index
                        },
                    )
                    Spacer(Modifier.size(10.dp))
                    val index = selectedWeek
                    if (index != null && index in model.volumeByWeek.indices) {
                        InfoNote(
                            "Week of ${model.weekLabels[index]}: " +
                                Weights.formatVolume(model.volumeByWeek[index].toDouble(), unit) +
                                " of working volume.",
                        )
                    } else {
                        Text(
                            "Tap a bar for that week's total. Volume is weight × reps for working sets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                CadenceCard {
                    SectionHeader(
                        title = "Consistency",
                        subtitle = "Sessions per week against your ${model.targetSessionsPerWeek}-day target",
                    )
                    Spacer(Modifier.size(14.dp))
                    LineChart(
                        values = model.consistencyByWeek,
                        labels = model.weekLabels,
                        targetLine = model.targetSessionsPerWeek.toFloat(),
                        highlightIndex = selectedConsistency,
                        onSelect = { index ->
                            selectedConsistency = if (selectedConsistency == index) null else index
                        },
                    )
                    Spacer(Modifier.size(10.dp))
                    val index = selectedConsistency
                    if (index != null && index in model.consistencyByWeek.indices) {
                        InfoNote(
                            "Week of ${model.weekLabels[index]}: " +
                                "${model.consistencyByWeek[index].toInt()} of " +
                                "${model.targetSessionsPerWeek} planned sessions.",
                        )
                    } else {
                        Text(
                            "Tap a point to see that week. The line shows how many sessions you finished.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                CadenceCard {
                    SectionHeader(title = "Summary", subtitle = "Last 28 days")
                    Spacer(Modifier.size(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        StatTile(
                            "Sessions",
                            model.summary.sessionsLast28Days.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Records",
                            model.summary.recordsLast28Days.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Streak",
                            "${model.summary.streakWeeks}w",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    model.summary.volumeChangeFraction?.let { change ->
                        Spacer(Modifier.size(12.dp))
                        val percent = (change * 100).toInt()
                        Text(
                            if (change >= 0) {
                                "Volume is up about $percent% on last week."
                            } else {
                                "Volume is down about ${-percent}% on last week. " +
                                    "A lighter week is a normal part of training."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    model.summary.averageSessionMinutes?.let { minutes ->
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Typical session length: about $minutes minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (model.recentRecords.isNotEmpty()) {
                    CadenceCard {
                        SectionHeader(
                            title = "Recent records",
                            subtitle = "Set in the last 28 days",
                        )
                        Spacer(Modifier.size(10.dp))
                        model.recentRecords.take(8).forEach { record ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CadenceIcon(
                                    CadenceIcon.Trophy,
                                    size = 18.dp,
                                    tint = CadenceTheme.tones.warning,
                                )
                                Spacer(Modifier.size(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        viewModel.catalog.byId(record.exerciseId)?.name
                                            ?: record.exerciseId,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "${record.type.displayName} · " +
                                            when (record.type) {
                                                PersonalRecordType.MOST_REPS -> "${record.reps} reps"
                                                PersonalRecordType.MOST_VOLUME ->
                                                    Weights.formatVolume(record.value, unit)
                                                else -> Weights.formatWithUnit(record.value, unit)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilterChip(
                                    label = "Open",
                                    selected = false,
                                    onClick = { onOpenExercise(record.exerciseId) },
                                )
                            }
                        }
                    }
                }

                model.plateau?.let { plateau -> PlateauCard(plateau) }

                model.level?.let { level -> LevelCard(level, onHide = {
                    viewModel.saveSettings(state.settings.copy(showProgressLevel = false))
                }) }

                if (model.level == null) {
                    SecondaryButton(
                        text = "Show my progress level",
                        onClick = {
                            viewModel.saveSettings(state.settings.copy(showProgressLevel = true))
                        },
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PlateauCard(insight: PlateauInsight) {
    CadenceCard {
        SectionHeader(
            title = if (insight.isDeloadRecommended) "Consider a lighter week" else "Progress check",
            subtitle = insight.headline,
        )
        Spacer(Modifier.size(10.dp))
        Text(insight.detail, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(10.dp))
        insight.suggestions.forEach { suggestion ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                CadenceIcon(CadenceIcon.Info, size = 16.dp, tint = CadenceTheme.tones.rest)
                Spacer(Modifier.size(8.dp))
                Text(suggestion, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.size(10.dp))
        InfoNote(
            "These are options, not instructions. Plenty of people keep the same loads for weeks " +
                "and still make progress.",
        )
    }
}

@Composable
private fun LevelCard(level: LevelAssessment, onHide: () -> Unit) {
    CadenceCard {
        SectionHeader(
            title = "Progress level",
            subtitle = "Optional, and only about your own logged training",
        )
        Spacer(Modifier.size(12.dp))
        Text(level.headline, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(6.dp))
        Text(level.detail, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(10.dp))
        Text(level.nextStepHint, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile("Active weeks", level.consistencyWeeks.toString(), modifier = Modifier.weight(1f))
            StatTile(
                "Per week",
                Weights.formatAmount(level.sessionsPerWeek),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                "Last 28 days",
                level.sessionsLast28Days.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(12.dp))
        InfoNote(level.disclaimer, icon = CadenceIcon.Info)
        Spacer(Modifier.size(8.dp))
        Text(
            level.benchmark,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        SecondaryButton(text = "Hide this", onClick = onHide)
    }
}
