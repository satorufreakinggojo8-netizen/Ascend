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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.art.MuscleBodyMap
import com.cadence.fit.ui.art.MuscleMapLegend
import com.cadence.fit.ui.art.muscleBand
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceProgressBar
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.components.ThinDivider
import com.cadence.fit.ui.theme.CadenceTheme

/**
 * Where working volume and estimated strength landed, by muscle group.
 *
 * Informational only: this shows what was logged, never what a body looks like,
 * how recovered a muscle is, or whether an injury is present.
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

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Body map",
            subtitle = "Where your work landed · not body composition",
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 28, 90).forEach { option ->
                    FilterChip(
                        label = "Last $option days",
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
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (window.sessionsCounted == 0) {
                InfoNote(
                    "No completed sessions in the last $days days. The map fills in as soon " +
                        "as you log a workout.",
                    icon = CadenceIcon.Info,
                )
            }

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
                        label = "Coverage",
                        value = "${window.trainedGroups.size}/${MuscleGroup.bodyMapGroups.size}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            CadenceCard {
                SectionHeader(
                    title = "Training map",
                    subtitle = ranking.firstOrNull()?.let { (muscle, _) ->
                        "Most work: ${muscle.displayName}"
                    } ?: "Tap a muscle group for its numbers",
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
                    title = if (shadeBySets) {
                        "Working sets by muscle"
                    } else {
                        "Share of logged volume"
                    },
                )
            }

            selected?.let { muscle ->
                val volume = window.volumeByMuscle[muscle] ?: 0.0
                val sets = window.setsByMuscle[muscle] ?: 0
                val sessions = window.sessionsByMuscle[muscle] ?: 0
                val totalVolume = window.volumeByMuscle.values.sum()
                val share = if (totalVolume > 0) volume / totalVolume else 0.0
                val best = window.bestLiftByMuscle[muscle]
                CadenceCard {
                    SectionHeader(
                        title = muscle.displayName,
                        subtitle = buildString {
                            append("${(share * 100).toInt()}% of volume")
                            append(" · last $days days")
                        },
                    )
                    Spacer(Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StatTile(
                            "Volume",
                            Weights.formatVolume(volume, unit),
                            modifier = Modifier.weight(1.25f),
                        )
                        StatTile(
                            "Sets",
                            sets.toString(),
                            modifier = Modifier.weight(0.8f),
                        )
                        StatTile(
                            "Sessions",
                            sessions.toString(),
                            modifier = Modifier.weight(0.8f),
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    ThinDivider()
                    Spacer(Modifier.size(10.dp))
                    if (best != null) {
                        Text(
                            "Strongest lift",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(3.dp))
                        Text(best.exerciseName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${Weights.formatWithUnit(best.weightKg, unit)} × ${best.reps} reps · " +
                                "${Weights.formatWithUnit(best.estimatedOneRepMaxKg, unit)} estimated 1RM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "No primary-mover sets for this group in this window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Volume counts working sets only: weight × reps, with secondary muscles " +
                            "counted at half. A 1RM is estimated from weight and reps, not tested.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val bestLifts = ranking.mapNotNull { (muscle, _) ->
                window.bestLiftByMuscle[muscle]?.let { muscle to it }
            }
            if (bestLifts.isNotEmpty()) {
                CadenceCard {
                    SectionHeader(
                        title = "Best lift by muscle",
                        subtitle = "Strongest estimated 1RM from a primary mover",
                    )
                    Spacer(Modifier.size(6.dp))
                    bestLifts.take(6).forEachIndexed { index, (muscle, lift) ->
                        if (index > 0) ThinDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    muscle.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    lift.exerciseName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Weights.formatWithUnit(lift.estimatedOneRepMaxKg, unit),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "est. 1RM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "One best set per muscle group. Estimates are not comparable between " +
                            "different lifts and are not tested maximums.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CadenceCard {
                SectionHeader(
                    title = "Volume by muscle group",
                    subtitle = "${window.sessionsCounted} sessions in the last $days days",
                )
                Spacer(Modifier.size(12.dp))
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
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    muscle.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    Weights.formatVolume(volume, unit),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    "${(share * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(38.dp),
                                )
                            }
                            Spacer(Modifier.size(6.dp))
                            CadenceProgressBar(
                                fraction = share.toFloat(),
                                height = 5.dp,
                                color = heatColor(volume, window.maxVolume),
                            )
                        }
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
                        "Muscles missing from a two-week window is normal, especially on a " +
                            "3-day plan. Use it to check that the plan matches what you intended.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            InfoNote(
                "This map is a record of your logged sets. It is not a body-composition " +
                    "measurement, it cannot tell how recovered a muscle is, and it does not " +
                    "diagnose injuries. No photos of any athlete are used here.",
                icon = CadenceIcon.Info,
            )
            Spacer(Modifier.size(16.dp))
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
