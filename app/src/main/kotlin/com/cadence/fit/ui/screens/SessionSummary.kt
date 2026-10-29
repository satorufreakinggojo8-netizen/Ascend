package com.cadence.fit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.insight.SessionSummaries
import com.cadence.fit.domain.insight.SessionSummary
import com.cadence.fit.domain.math.PersonalRecordType
import com.cadence.fit.domain.units.Durations
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceProgressBar
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.theme.CadenceTheme

/**
 * Shown once a workout is saved: what was done, what changed, and what (if
 * anything) was actually beaten. Nothing here is a health assessment.
 */
@Composable
fun SessionSummaryScreen(
    viewModel: CadenceViewModel,
    sessionId: String,
    onDone: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.sessions.firstOrNull { it.id == sessionId }
    val summary = remember(session, state.sessions) {
        session?.let { SessionSummaries.build(it, state.sessions, viewModel.catalog) }
    }

    if (summary == null) {
        Column(Modifier.fillMaxSize()) {
            CadenceTopBar(title = "Session", onBack = onDone)
            EmptyState(
                title = "Nothing to summarise",
                message = "This workout has no finished working sets yet.",
                icon = CadenceIcon.Info,
                actionLabel = "Back to home",
                onAction = onDone,
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Session complete",
            subtitle = summary.routineName,
            onBack = onDone,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CadenceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("WORKOUT", style = MaterialTheme.typography.labelSmall, color = CadenceTheme.tones.success)
                    Spacer(Modifier.weight(1f))
                    Badge(
                        text = "Saved",
                        container = CadenceTheme.tones.success.copy(alpha = 0.16f),
                        content = CadenceTheme.tones.success,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    Weights.formatVolume(summary.volumeKg, state.settings.weightUnit),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    "total working volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatTile("Sets", summary.workingSets.toString(), modifier = Modifier.weight(1f))
                    StatTile("Reps", summary.totalReps.toString(), modifier = Modifier.weight(1f))
                    StatTile(
                        "Time",
                        summary.durationMinutes?.let { Durations.hoursMinutes(it) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    summary.comparisonNote + " " + SessionSummaries.volumeComparisonText(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (summary.records.isNotEmpty()) {
                CadenceCard {
                    SectionHeader(
                        title = "New records",
                        subtitle = "Only values that beat something you had already logged",
                    )
                    Spacer(Modifier.size(10.dp))
                    summary.records.forEach { record ->
                        Row(
                            Modifier.padding(vertical = 6.dp),
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
                                    viewModel.catalog.byId(record.exerciseId)?.name ?: record.exerciseId,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    record.type.displayName + " · previous " +
                                        formatRecord(record.previousValue, record.type, state.settings.weightUnit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                formatRecord(record.value, record.type, state.settings.weightUnit),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    InfoNote(
                        "Records compare with your own previous sessions for the same movement, " +
                            "never with anyone else.",
                    )
                }
            } else {
                CadenceCard {
                    SectionHeader(title = "No new records this time", subtitle = "That is normal")
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Training is not a new best every session. The work still counts toward " +
                            "your volume and consistency.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (summary.muscleShares().isNotEmpty()) {
                CadenceCard {
                    SectionHeader(
                        title = "Where the work went",
                        subtitle = "Share of this session's working volume",
                    )
                    Spacer(Modifier.size(12.dp))
                    summary.muscleShares().take(8).forEach { (muscle, share) ->
                        Row(
                            Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                muscle.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.34f),
                            )
                            CadenceProgressBar(
                                fraction = share.toFloat(),
                                modifier = Modifier.weight(0.5f),
                                height = 6.dp,
                                color = CadenceTheme.tones.heatHigh,
                            )
                            Text(
                                "${(share * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Secondary muscles count half. This is a training summary, not a " +
                            "body-composition measurement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            InfoNote(SessionSummaries.MEASUREMENT_NOTE)

            PrimaryButton(text = "Done", icon = CadenceIcon.Check, onClick = onDone)
            SecondaryButton(
                text = "Open the full session",
                onClick = { onOpenSession(summary.sessionId) },
            )
            if (summary.records.isNotEmpty()) {
                SecondaryButton(
                    text = "Open an exercise",
                    onClick = { onOpenExercise(summary.records.first().exerciseId) },
                )
            }
            Spacer(Modifier.size(20.dp))
        }
    }
}

private fun formatRecord(value: Double?, type: PersonalRecordType, unit: WeightUnit): String {
    if (value == null) return "—"
    return when (type) {
        PersonalRecordType.MOST_REPS -> "${value.toInt()} reps"
        PersonalRecordType.MOST_VOLUME -> Weights.formatVolume(value, unit)
        else -> Weights.formatWithUnit(value, unit)
    }
}
