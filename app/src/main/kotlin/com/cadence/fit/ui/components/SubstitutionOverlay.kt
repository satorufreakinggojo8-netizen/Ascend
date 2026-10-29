package com.cadence.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.library.Substitutions
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.ui.theme.CadenceTheme

/**
 * Full-screen list of stand-ins for one movement.
 *
 * Every row states the muscles it trains, the equipment it needs and why it was
 * suggested. Options that need kit the user does not have are hidden until asked
 * for, then clearly labelled.
 */
@Composable
fun SubstitutionOverlay(
    target: Exercise,
    catalog: ExerciseCatalog,
    availableEquipment: Set<Equipment>,
    experience: ExperienceLevel,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    excludeIds: Set<String> = emptySet(),
) {
    var showUnavailable by remember { mutableStateOf(false) }
    val options = remember(target.id, availableEquipment, experience, showUnavailable) {
        Substitutions.forExercise(
            target = target,
            catalog = catalog,
            availableEquipment = availableEquipment,
            experience = experience,
            includeUnavailable = showUnavailable,
        ).filterNot { it.exercise.id in excludeIds }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            SectionHeader(
                title = "Swap ${target.name}",
                subtitle = "Same slot, sets and rest. Your logged history is untouched.",
                trailing = { FilterChip(label = "Close", selected = false, onClick = onDismiss) },
            )
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Alternatives for ${target.primaryMuscles.joinToString(", ") { it.displayName.lowercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                FilterChip(
                    label = if (showUnavailable) "Only my kit" else "Show all kit",
                    selected = showUnavailable,
                    onClick = { showUnavailable = !showUnavailable },
                )
            }
            Spacer(Modifier.size(8.dp))
            ThinDivider()

            if (options.isEmpty()) {
                Spacer(Modifier.size(16.dp))
                Text(
                    "No close alternatives with your current equipment. You can still add a " +
                        "different exercise from the library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.heightIn(min = 140.dp)) {
                    items(options, key = { it.exercise.id }) { option ->
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.exercise.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        option.exercise.primaryMuscles.joinToString(", ") { it.displayName } +
                                            " · " + option.exercise.equipment.displayName +
                                            " · " + option.exercise.difficulty.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilterChip(
                                    label = if (option.isAvailable) "Swap" else "Needs kit",
                                    selected = false,
                                    onClick = {
                                        if (option.isAvailable) onPick(option.exercise)
                                    },
                                )
                            }
                            Spacer(Modifier.size(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                option.reasons.take(3).forEach { reason ->
                                    Badge(
                                        text = reason,
                                        container = if (option.isAvailable) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            CadenceTheme.tones.warning.copy(alpha = 0.16f)
                                        },
                                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        ThinDivider()
                    }
                }
            }
        }
    }
}
