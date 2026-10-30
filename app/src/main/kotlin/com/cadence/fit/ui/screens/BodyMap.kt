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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.art.MuscleBodyMap
import com.cadence.fit.ui.art.MuscleMapLegend
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.StatTile

/**
 * Front and back muscle map shaded by logged training volume.
 *
 * Informational only: it shows where your sets went, not what your body looks
 * like, and it makes no assessment of composition or injury.
 */
@Composable
fun BodyMapScreen(viewModel: CadenceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val now = viewModel.nowMs()
    var days by remember { mutableStateOf(14) }
    var selected by remember { mutableStateOf<MuscleGroup?>(null) }

    val window = remember(state, days, now) {
        ScreenModels.muscleWindow(state, viewModel.catalog, days, now)
    }
    val ranking = remember(window) { ScreenModels.muscleRanking(window) }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Body map",
            subtitle = "Where your training volume went",
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
                EmptyState(
                    title = "Nothing logged in this window",
                    message = "Finish a workout in the selected range and the map fills in.",
                    icon = CadenceIcon.Body,
                )
            }

            CadenceCard {
                MuscleBodyMap(
                    volumeByMuscle = window.volumeByMuscle,
                    maxVolume = window.maxVolume,
                    selected = selected,
                    onSelect = { muscle ->
                        selected = if (selected == muscle) null else muscle
                    },
                )
                Spacer(Modifier.size(14.dp))
                MuscleMapLegend()
            }

            selected?.let { muscle ->
                val volume = window.volumeByMuscle[muscle] ?: 0.0
                val sessions = window.sessionsByMuscle[muscle] ?: 0
                CadenceCard {
                    SectionHeader(
                        title = muscle.displayName,
                        subtitle = "In the last $days days",
                    )
                    Spacer(Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        StatTile(
                            "Volume",
                            Weights.formatVolume(volume, unit),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Sessions",
                            sessions.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Share",
                            if (window.maxVolume > 0) {
                                "${((volume / window.volumeByMuscle.values.sum()) * 100).toInt()}%"
                            } else {
                                "—"
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Volume counts working sets only: weight × reps, with secondary muscles " +
                            "counted at half.",
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
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
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
                                if (total > 0) "${((volume / total) * 100).toInt()}%" else "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    "diagnose injuries.",
                icon = CadenceIcon.Info,
            )
            Spacer(Modifier.size(16.dp))
        }
    }
}
