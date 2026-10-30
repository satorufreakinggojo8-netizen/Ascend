package com.cadence.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExerciseFilter

/**
 * Shared search overlay for adding an exercise, used by the routine editor and
 * by the logger. Results are limited to equipment the user actually has.
 */
@Composable
fun ExercisePickerOverlay(
    catalog: ExerciseCatalog,
    availableEquipment: Set<Equipment>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Add an exercise",
    excludeIds: Set<String> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, availableEquipment, excludeIds) {
        catalog.search(
            ExerciseFilter(query = query, onlyAvailable = true),
            availableEquipment,
        ).filterNot { it.id in excludeIds }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            SectionHeader(
                title = title,
                subtitle = "Filtered to the equipment you have",
                trailing = { FilterChip(label = "Close", selected = false, onClick = onDismiss) },
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name or muscle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(12.dp))
            ThinDivider()
            if (results.isEmpty()) {
                Spacer(Modifier.size(16.dp))
                Text(
                    "Nothing matches that search with your equipment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.heightIn(min = 140.dp)) {
                    items(results, key = { it.id }) { exercise ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        exercise.primaryMuscles.joinToString(", ") { it.displayName } +
                                            " · " + exercise.equipment.displayName +
                                            " · " + exercise.difficulty.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilterChip(
                                    label = "Add",
                                    selected = false,
                                    onClick = { onPick(exercise.id) },
                                )
                            }
                            ThinDivider()
                        }
                    }
                }
            }
        }
    }
}
