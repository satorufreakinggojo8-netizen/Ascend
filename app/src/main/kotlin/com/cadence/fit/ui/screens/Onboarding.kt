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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.fit.domain.library.ExerciseCatalog
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SegmentedOptions
import com.cadence.fit.ui.components.StepIndicator

private const val TOTAL_STEPS = 6

/**
 * Six short questions. Each answer feeds the plan builder directly, and the last
 * step shows exactly what is about to be created before anything is saved.
 */
@Composable
fun OnboardingScreen(
    initialProfile: TrainingProfile,
    initialUnit: WeightUnit,
    onComplete: (TrainingProfile, WeightUnit) -> Unit,
) {
    var profile by remember { mutableStateOf(initialProfile) }
    var unit by remember { mutableStateOf(initialUnit) }
    var step by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Build your plan",
            subtitle = "Step ${step + 1} of $TOTAL_STEPS",
            onBack = if (step > 0) ({ step -= 1; Unit }) else null,
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            StepIndicator(stepCount = TOTAL_STEPS, currentStep = step)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            when (step) {
                0 -> LocationStep(profile) { location ->
                    profile = profile.copy(
                        location = location,
                        equipment = ExerciseCatalog.defaultEquipment(location),
                    )
                }

                1 -> EquipmentStep(profile) { equipment ->
                    profile = profile.copy(equipment = equipment)
                }

                2 -> ExperienceStep(profile) { experience ->
                    profile = profile.copy(experience = experience)
                }

                3 -> GoalStep(profile) { goal -> profile = profile.copy(goal = goal) }

                4 -> FrequencyStep(profile) { days, minutes ->
                    profile = profile.copy(daysPerWeek = days, sessionMinutes = minutes)
                }

                else -> UnitsStep(
                    profile = profile,
                    unit = unit,
                    onUnitChange = { selected -> unit = selected },
                )
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (step == TOTAL_STEPS - 1) {
                PrimaryButton(
                    text = "Create my plan",
                    icon = CadenceIcon.Check,
                    onClick = { onComplete(profile, unit) },
                )
            } else {
                PrimaryButton(
                    text = "Continue",
                    onClick = { step += 1 },
                )
            }
            if (step > 0) {
                Spacer(Modifier.size(8.dp))
                SecondaryButton(text = "Back", onClick = { step -= 1 })
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.size(6.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(20.dp))
}

@Composable
private fun SelectableCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (selected) 0.dp else 1.dp,
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.size(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun LocationStep(profile: TrainingProfile, onChange: (TrainingLocation) -> Unit) {
    StepHeading(
        "Where do you train?",
        "This decides which exercises the plan can choose from.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectableCard(
            title = "Gym",
            body = "Barbells, machines and cables available",
            selected = profile.location == TrainingLocation.GYM,
            onClick = { onChange(TrainingLocation.GYM) },
        )
        SelectableCard(
            title = "Home",
            body = "Training with the kit you have at home",
            selected = profile.location == TrainingLocation.HOME,
            onClick = { onChange(TrainingLocation.HOME) },
        )
    }
}

@Composable
private fun EquipmentStep(profile: TrainingProfile, onChange: (Set<Equipment>) -> Unit) {
    val options = if (profile.location == TrainingLocation.HOME) {
        Equipment.homeOptions
    } else {
        Equipment.gymOptions
    }
    StepHeading(
        "What do you have?",
        "Only exercises you can actually set up will appear in your plan.",
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { equipment ->
            FilterChip(
                label = equipment.displayName,
                selected = equipment in profile.equipment,
                onClick = {
                    val updated = profile.equipment.toMutableSet()
                    if (!updated.add(equipment)) updated.remove(equipment)
                    onChange(updated)
                },
            )
        }
    }
    Spacer(Modifier.size(16.dp))
    InfoNote("Bodyweight and floor space are always available, so they are not listed separately.")
}

@Composable
private fun ExperienceStep(profile: TrainingProfile, onChange: (ExperienceLevel) -> Unit) {
    StepHeading(
        "How much training experience do you have?",
        "This sets how many exercises and sets a session starts with.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExperienceLevel.entries.forEach { level ->
            SelectableCard(
                title = level.displayName,
                body = level.description,
                selected = profile.experience == level,
                onClick = { onChange(level) },
            )
        }
    }
}

@Composable
private fun GoalStep(profile: TrainingProfile, onChange: (TrainingGoal) -> Unit) {
    StepHeading(
        "What are you training for?",
        "The goal sets rep ranges and rest times, and shapes the progression suggestions.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TrainingGoal.entries.forEach { goal ->
            SelectableCard(
                title = goal.displayName,
                body = goal.tagline,
                selected = profile.goal == goal,
                onClick = { onChange(goal) },
            )
        }
    }
}

@Composable
private fun FrequencyStep(
    profile: TrainingProfile,
    onChange: (Int, Int) -> Unit,
) {
    StepHeading(
        "How often and how long?",
        "Be realistic — a plan you can keep is worth more than a perfect one.",
    )
    Text("Days per week", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.size(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrainingProfile.DAY_OPTIONS.forEach { days ->
            FilterChip(
                label = "$days days",
                selected = profile.daysPerWeek == days,
                onClick = { onChange(days, profile.sessionMinutes) },
            )
        }
    }
    Spacer(Modifier.size(20.dp))
    Text("Session length", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.size(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrainingProfile.SESSION_LENGTHS.forEach { minutes ->
            FilterChip(
                label = "$minutes min",
                selected = profile.sessionMinutes == minutes,
                onClick = { onChange(profile.daysPerWeek, minutes) },
            )
        }
    }
}

@Composable
private fun UnitsStep(
    profile: TrainingProfile,
    unit: WeightUnit,
    onUnitChange: (WeightUnit) -> Unit,
) {
    StepHeading(
        "Which units do you use?",
        "Weights are stored in kilograms either way, so switching later never changes your history.",
    )
    SegmentedOptions(
        options = listOf("kg", "lb"),
        selectedIndex = if (unit == WeightUnit.KG) 0 else 1,
        onSelect = { index -> onUnitChange(if (index == 0) WeightUnit.KG else WeightUnit.LB) },
    )
    Spacer(Modifier.size(24.dp))
    Text("Your plan", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.size(8.dp))
    InfoNote(
        buildString {
            append("${profile.daysPerWeek} sessions a week")
            append(", about ${profile.sessionMinutes} minutes each")
            append(", for ${profile.goal.displayName.lowercase()}")
            append(" at ${profile.location.displayName.lowercase()}.")
            append(" You can edit every routine afterwards.")
        },
    )
}
