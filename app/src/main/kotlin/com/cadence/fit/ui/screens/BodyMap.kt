package com.cadence.fit.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.MuscleExerciseHistory
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.art.MuscleBodyMap
import com.cadence.fit.ui.art.MuscleMapLegend
import com.cadence.fit.ui.art.muscleBand
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceProgressBar
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.ScreenPadding
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.Sparkline
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.components.ThinDivider
import com.cadence.fit.ui.theme.CadenceTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Strength and training volume by muscle group.
 *
 * Every number here comes from logged working sets: the best set for each
 * movement, the date it was set, and how the estimated top set has moved. The
 * only estimate is the one-rep-max formula, and it is labelled as one. Nothing
 * is inferred from body measurements or from a photograph.
 */
@Composable
fun BodyMapScreen(viewModel: CadenceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val today = viewModel.today()
    val now = remember(state, today) { viewModel.nowMs() }
    var days by remember { mutableStateOf(28) }
    var shadeBySets by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MuscleGroup?>(null) }

    val window = remember(state, days, now) {
        ScreenModels.muscleWindow(state, viewModel.catalog, days, now)
    }
    val ranking = remember(window) { ScreenModels.muscleRanking(window) }
    val shading = remember(window, shadeBySets) {
        if (shadeBySets) {
            window.setsByMuscle.mapValues { it.value.toDouble() }
        } else {
            window.volumeByMuscle
        }
    }
    val shadingMax = remember(shading) { shading.values.maxOrNull() ?: 0.0 }
    val ranges = listOf(28 to "4 weeks", 90 to "3 months", 182 to "6 months", 365 to "1 year")
    val rangeLabel = ranges.firstOrNull { it.first == days }?.second ?: "4 weeks"

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Strength by muscle",
            subtitle = "Measured from your logged sets",
        )

        Column(Modifier.padding(horizontal = ScreenPadding.horizontal)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEach { (option, label) ->
                    FilterChip(
                        label = label,
                        selected = days == option,
                        onClick = {
                            days = option
                            selected = null
                        },
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding.horizontal, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (window.sessionsCounted == 0) {
                EmptyState(
                    title = "Nothing logged in this window",
                    message = "Complete a session and your best sets, the dates you hit them " +
                        "and a session-by-session trend appear here.",
                    icon = CadenceIcon.Progress,
                )
            } else {
                CadenceCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StatTile(
                            label = "Sessions",
                            value = window.sessionsCounted.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Working sets",
                            value = window.workingSetsCounted.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Muscles",
                            value = "${window.trainedGroups.size}/${MuscleGroup.bodyMapGroups.size}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Last $rangeLabel · working sets only, no warm-ups.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CadenceCard {
                    SectionHeader(
                        title = "Training map",
                        subtitle = ranking.firstOrNull()?.let { (muscle, _) ->
                            "Most volume: ${muscle.displayName}"
                        } ?: "Tap a muscle group for its lifts",
                        icon = CadenceIcon.Body,
                    )
                    Spacer(Modifier.size(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Shade by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        FilterChip(
                            label = "Volume",
                            selected = !shadeBySets,
                            onClick = { shadeBySets = false },
                        )
                        Spacer(Modifier.size(6.dp))
                        FilterChip(
                            label = "Working sets",
                            selected = shadeBySets,
                            onClick = { shadeBySets = true },
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    MuscleBodyMap(
                        volumeByMuscle = shading,
                        maxVolume = shadingMax,
                        selected = selected,
                        onSelect = { muscle ->
                            selected = if (selected == muscle) null else muscle
                        },
                    )
                    Spacer(Modifier.size(14.dp))
                    MuscleMapLegend(
                        title = if (shadeBySets) "Working sets by muscle" else "Share of logged volume",
                    )
                }

                val muscle = selected
                if (muscle == null) {
                    InfoNote(
                        "Tap a muscle group on the map to see each movement that trained it, " +
                            "your best set and the date you hit it.",
                        icon = CadenceIcon.Info,
                    )
                } else {
                    SelectedMuscleCard(
                        muscle = muscle,
                        window = window,
                        unit = unit,
                        rangeLabel = rangeLabel,
                    )
                }

                CadenceCard {
                    SectionHeader(
                        title = "All muscle groups",
                        subtitle = "Best logged set in the last $rangeLabel",
                    )
                    Spacer(Modifier.size(8.dp))
                    if (ranking.isEmpty()) {
                        Text(
                            "No working sets in this window yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val total = window.volumeByMuscle.values.sum()
                        ranking.forEach { (muscle, volume) ->
                            val share = if (total > 0) volume / total else 0.0
                            val best = window.exercisesByMuscle[muscle]?.firstOrNull()
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription =
                                            "Show ${muscle.displayName} lifts and progress"
                                    }
                                    .clickable(role = Role.Button) {
                                        selected = if (selected == muscle) null else muscle
                                    }
                                    .padding(vertical = 9.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            muscle.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            best?.let { it.exerciseName } ?: "No primary lift logged",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            best?.let {
                                                "${Weights.format(it.bestWeightKg, unit)} " +
                                                    "${unit.abbreviation} × ${it.bestReps}"
                                            } ?: "—",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            best?.let { shortDate(it.achievedAtMs) }
                                                ?: "${window.setsByMuscle[muscle] ?: 0} sets",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.size(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CadenceProgressBar(
                                        fraction = share.toFloat(),
                                        height = 5.dp,
                                        modifier = Modifier.weight(1f),
                                        color = heatColor(volume, window.maxVolume),
                                    )
                                    Text(
                                        "${(share * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 10.dp),
                                    )
                                }
                            }
                            ThinDivider()
                        }
                    }
                }

                if (window.untrainedGroups.isNotEmpty()) {
                    CadenceCard {
                        SectionHeader(
                            title = "Not trained in this window",
                            subtitle = "Information, not a warning",
                        )
                        Spacer(Modifier.size(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            window.untrainedGroups.forEach { muscle ->
                                FilterChip(
                                    label = muscle.displayName,
                                    selected = false,
                                    onClick = { selected = muscle },
                                )
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "A muscle missing from one window is normal, especially on a three-day " +
                                "plan. Use it to check that the plan matches what you intended.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            InfoNote(
                "Weights, reps, sets and dates are measured from the sets you logged. An " +
                    "estimated 1RM is a formula applied to a set, not a tested maximum. This " +
                    "view cannot tell how recovered a muscle is and does not diagnose injuries.",
                icon = CadenceIcon.Info,
            )
            Spacer(Modifier.size(ScreenPadding.bottom))
        }
    }
}

/** One muscle group: its volume, then every movement that trained it. */
@Composable
private fun SelectedMuscleCard(
    muscle: MuscleGroup,
    window: com.cadence.fit.ui.MuscleWindow,
    unit: WeightUnit,
    rangeLabel: String,
) {
    val volume = window.volumeByMuscle[muscle] ?: 0.0
    val sets = window.setsByMuscle[muscle] ?: 0
    val sessions = window.sessionsByMuscle[muscle] ?: 0
    val totalVolume = window.volumeByMuscle.values.sum()
    val share = if (totalVolume > 0) volume / totalVolume else 0.0
    val histories = window.exercisesByMuscle[muscle].orEmpty()

    CadenceCard {
        SectionHeader(
            title = muscle.displayName,
            subtitle = "${(share * 100).roundToInt()}% of volume · $sets sets · " +
                "$sessions sessions · last $rangeLabel",
        )
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatTile(
                "Volume",
                Weights.formatVolume(volume, unit),
                modifier = Modifier.weight(1.25f),
            )
            StatTile("Sets", sets.toString(), modifier = Modifier.weight(0.8f))
            StatTile("Lifts", histories.size.toString(), modifier = Modifier.weight(0.8f))
        }
        Spacer(Modifier.size(10.dp))
        ThinDivider()
        Spacer(Modifier.size(4.dp))

        if (histories.isEmpty()) {
            Spacer(Modifier.size(6.dp))
            Text(
                "No sets where this muscle was a primary mover in this window. Secondary " +
                    "involvement still counts towards volume, but it is not listed as a lift here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            histories.forEachIndexed { index, history ->
                if (index > 0) ThinDivider()
                ExerciseStrengthRow(history, unit)
            }
        }
    }
}

/** Best set, date and estimated trend for one movement inside a muscle group. */
@Composable
private fun ExerciseStrengthRow(history: MuscleExerciseHistory, unit: WeightUnit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    history.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Best set ${Weights.format(history.bestWeightKg, unit)} " +
                        "${unit.abbreviation} × ${history.bestReps} · " +
                        shortDate(history.achievedAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Weights.formatWithUnit(history.bestE1rmKg, unit),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "est. 1RM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${history.sessionCount} sessions · ${history.totalSets} sets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            history.changeKg?.let { change ->
                Text(
                    when {
                        change > 0.05 -> "▲ ${Weights.formatAmount(change)} ${unit.abbreviation} est."
                        change < -0.05 -> "▼ ${Weights.formatAmount(-change)} ${unit.abbreviation} est."
                        else -> "Flat estimate"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (change > 0.05) {
                        CadenceTheme.tones.success
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (history.trendKg.size >= 2) {
            Spacer(Modifier.size(6.dp))
            Sparkline(
                values = history.trendKg.map { it.toFloat() },
                accent = CadenceTheme.tones.heatHigh,
                height = 28.dp,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                "Best estimated 1RM per session, oldest first",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun heatColor(volume: Double, maxVolume: Double) = when (muscleBand(volume, maxVolume)) {
    1 -> CadenceTheme.tones.heatLow
    2 -> CadenceTheme.tones.heatMid
    3 -> CadenceTheme.tones.heatHigh
    4 -> CadenceTheme.tones.heatPeak
    else -> CadenceTheme.tones.heatEmpty
}

private val STRENGTH_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun shortDate(atMs: Long): String =
    Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).toLocalDate().format(STRENGTH_DATE)
