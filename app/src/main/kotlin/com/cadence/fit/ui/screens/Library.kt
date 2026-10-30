package com.cadence.fit.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.library.MuscleFilterGroups
import com.cadence.fit.domain.model.Exercise
import com.cadence.fit.domain.model.ExerciseFilter
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.art.ExerciseArtwork
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.theme.CadenceTheme
import com.cadence.fit.ui.voice.VoiceInputController
import com.cadence.fit.ui.voice.VoiceSearchPolicy
import com.cadence.fit.ui.voice.VoiceSearchState

/**
 * Searchable exercise library. Filters match on what the user actually has, so a
 * home trainee is not shown machine-only movements by default.
 */
@Composable
fun LibraryScreen(
    viewModel: CadenceViewModel,
    onOpenExercise: (String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var muscleGroups by remember { mutableStateOf(setOf<MuscleGroup>()) }
    var onlyAvailable by remember { mutableStateOf(true) }
    var showFilters by remember { mutableStateOf(false) }
    var voiceState by remember { mutableStateOf(VoiceSearchState()) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onReady = { voiceState = VoiceSearchState(phase = com.cadence.fit.ui.voice.VoiceSearchPhase.LISTENING) },
            onFinished = { text ->
                val next = VoiceSearchPolicy.onResult(text, query)
                voiceState = next
                if (next.query.isNotEmpty()) query = next.query
            },
            onError = { permissionMissing -> voiceState = VoiceSearchPolicy.onError(permissionMissing) },
        )
    }
    val voiceAvailable = remember(voiceController) { voiceController.isAvailable() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        voiceState = VoiceSearchPolicy.onPermissionResult(granted, voiceAvailable)
        if (granted && voiceAvailable) voiceController.start()
    }

    // Audio is only captured while this screen is in front of the user.
    DisposableEffect(voiceController, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                voiceController.cancel()
                voiceState = VoiceSearchPolicy.onCancelled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceController.cancel()
            voiceController.destroy()
        }
    }

    val onVoiceClick: () -> Unit = {
        if (voiceState.isListening) {
            voiceController.cancel()
            voiceState = VoiceSearchPolicy.onCancelled()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            when {
                !voiceAvailable -> voiceState = VoiceSearchPolicy.onMicTapped(false, granted)
                !granted -> {
                    voiceState = VoiceSearchPolicy.onMicTapped(true, false)
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                else -> {
                    voiceState = VoiceSearchPolicy.onMicTapped(true, true)
                    voiceController.start()
                }
            }
        }
    }

    val filter = ExerciseFilter(
        query = query,
        muscleGroups = muscleGroups,
        onlyAvailable = onlyAvailable,
    )
    val results = remember(filter, state.profile.equipment) {
        viewModel.catalog.search(filter, state.profile.availableEquipment)
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Exercises",
            subtitle = "${results.size} of ${viewModel.catalog.all.size} movements",
            actions = listOf(CadenceIcon.Calendar to onOpenHistory),
            actionDescriptions = listOf("Workout history"),
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name or muscle") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                trailingIcon = {
                    IconButton(
                        onClick = onVoiceClick,
                        modifier = Modifier.size(44.dp),
                    ) {
                        CadenceIcon(
                            if (voiceState.isListening) CadenceIcon.Close else CadenceIcon.Mic,
                            size = 20.dp,
                            contentDescription = if (voiceState.isListening) {
                                "Stop listening"
                            } else {
                                "Search by voice"
                            },
                            tint = if (voiceState.isListening) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (voiceState.hasNotice) {
                Spacer(Modifier.size(8.dp))
                VoiceNotice(
                    state = voiceState,
                    onDismiss = {
                        if (voiceState.isListening) voiceController.cancel()
                        voiceState = VoiceSearchPolicy.onCancelled()
                    },
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    label = if (showFilters) "Hide filters" else "Filters",
                    selected = showFilters,
                    onClick = { showFilters = !showFilters },
                )
                FilterChip(
                    label = "My equipment",
                    selected = onlyAvailable,
                    onClick = { onlyAvailable = !onlyAvailable },
                )
                Spacer(Modifier.weight(1f))
                if (query.isNotBlank() || muscleGroups.isNotEmpty()) {
                    FilterChip(
                        label = "Clear",
                        selected = false,
                        onClick = {
                            query = ""
                            muscleGroups = emptySet()
                        },
                    )
                }
            }

            if (showFilters) {
                Spacer(Modifier.size(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MuscleFilterGroups.all.forEach { group ->
                        val selected = group.muscles.any { it in muscleGroups }
                        FilterChip(
                            label = group.label,
                            selected = selected,
                            onClick = {
                                val muscles = group.muscles.toSet()
                                muscleGroups = if (selected) {
                                    muscleGroups - muscles
                                } else {
                                    muscleGroups + muscles
                                }
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(12.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (results.isEmpty()) {
                EmptyState(
                    title = "Nothing matches",
                    message = if (onlyAvailable) {
                        "Try turning off the equipment filter, or clear the search."
                    } else {
                        "Try a different muscle group or search term."
                    },
                    icon = CadenceIcon.Search,
                    actionLabel = "Clear filters",
                    onAction = {
                        query = ""
                        muscleGroups = emptySet()
                        onlyAvailable = false
                    },
                )
            } else {
                results.forEach { exercise ->
                    ExerciseRow(exercise = exercise, onClick = { onOpenExercise(exercise.id) })
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun VoiceNotice(
    state: VoiceSearchState,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CadenceIcon(
            if (state.isListening) CadenceIcon.Mic else CadenceIcon.Info,
            size = 16.dp,
            tint = if (state.isListening) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = state.message ?: "Listening… tap the microphone to stop.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (!state.isListening) {
            TextButton(onClick = onDismiss) { Text("Type instead") }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    CadenceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseArtwork(
                pattern = exercise.pattern,
                muscleAccent = CadenceTheme.tones.heatHigh,
                modifier = Modifier.width(76.dp),
                height = 76.dp,
                contentDescription = null,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(2.dp))
                Text(
                    exercise.primaryMuscles.joinToString(", ") { it.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${exercise.equipment.displayName} · ${exercise.difficulty.displayName}",
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
