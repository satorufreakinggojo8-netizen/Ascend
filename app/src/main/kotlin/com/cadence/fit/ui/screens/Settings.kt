package com.cadence.fit.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.units.PlateCalculator
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.LabeledRow
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.SegmentedOptions
import com.cadence.fit.ui.components.SettingSwitchRow
import com.cadence.fit.ui.components.ThinDivider
import com.cadence.fit.ui.theme.CadenceTheme

/**
 * Settings and privacy in one place: training preferences, what is stored, what
 * leaves the device (nothing), export, and delete.
 */
@Composable
fun SettingsScreen(
    viewModel: CadenceViewModel,
    onBack: () -> Unit,
    onOpenSleep: () -> Unit,
    onRestartOnboarding: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sleepStatus by viewModel.sleepStatus.collectAsStateWithLifecycle()
    val settings = state.settings
    val profile = state.profile
    val unit = settings.weightUnit

    var pendingDeleteAll by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    LaunchedEffect(messages.firstOrNull()?.id) {
        messages.firstOrNull()?.let { message -> report = message.text }
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(title = "Settings", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CadenceCard {
                SectionHeader(title = "Units", subtitle = "Weights are stored in kilograms either way")
                Spacer(Modifier.size(12.dp))
                SegmentedOptions(
                    options = listOf("Kilograms (kg)", "Pounds (lb)"),
                    selectedIndex = if (unit == WeightUnit.KG) 0 else 1,
                    onSelect = { index ->
                        viewModel.saveSettings(
                            settings.copy(weightUnit = if (index == 0) WeightUnit.KG else WeightUnit.LB),
                        )
                    },
                )
            }

            CadenceCard {
                SectionHeader(title = "Your training", subtitle = "Used when generating a plan")
                Spacer(Modifier.size(8.dp))
                LabeledRow("Where you train", profile.location.displayName)
                ThinDivider()
                LabeledRow("Goal", profile.goal.displayName)
                ThinDivider()
                LabeledRow("Experience", profile.experience.displayName)
                ThinDivider()
                LabeledRow("Days per week", profile.daysPerWeek.toString())
                ThinDivider()
                LabeledRow("Session length", "${profile.sessionMinutes} minutes")
                ThinDivider()
                LabeledRow("Equipment", "${profile.availableEquipment.size} kinds available")
                Spacer(Modifier.size(12.dp))
                SecondaryButton(
                    text = "Change these answers",
                    icon = CadenceIcon.Edit,
                    onClick = onRestartOnboarding,
                )
            }

            CadenceCard {
                SectionHeader(title = "Interface")
                Spacer(Modifier.size(6.dp))
                SettingSwitchRow(
                    title = "Show progress level",
                    subtitle = "An optional label based only on your logged sessions",
                    checked = settings.showProgressLevel,
                    onCheckedChange = { checked ->
                        viewModel.saveSettings(settings.copy(showProgressLevel = checked))
                    },
                )
                SettingSwitchRow(
                    title = "Rest timer sound",
                    subtitle = "A short tone when the rest timer finishes",
                    checked = settings.restTimerSoundEnabled,
                    onCheckedChange = { checked ->
                        viewModel.saveSettings(settings.copy(restTimerSoundEnabled = checked))
                    },
                )
                SettingSwitchRow(
                    title = "Keep the screen on while training",
                    subtitle = "Stops the display sleeping mid-session",
                    checked = settings.keepScreenOnDuringWorkout,
                    onCheckedChange = { checked ->
                        viewModel.saveSettings(settings.copy(keepScreenOnDuringWorkout = checked))
                    },
                )
                Spacer(Modifier.size(12.dp))
                ThinDivider()
                Spacer(Modifier.size(12.dp))
                Text(
                    "Barbell weight for the plate calculator (${unit.abbreviation})",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Used only to work out plates. It never changes a logged weight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlateCalculator.barsFor(unit).forEach { option ->
                        FilterChip(
                            label = if (option == 0.0) "No bar" else
                                "${Weights.formatAmount(option)} ${unit.abbreviation}",
                            selected = kotlin.math.abs(settings.barWeight(unit) - option) < 1e-6,
                            onClick = {
                                viewModel.saveSettings(settings.withBarWeight(unit, option))
                            },
                        )
                    }
                }
            }

            CadenceCard {
                SectionHeader(
                    title = "Sleep and recovery",
                    subtitle = "Off by default, and revocable at any time",
                )
                Spacer(Modifier.size(10.dp))
                LabeledRow(
                    label = "Sleep reading",
                    value = if (sleepStatus.enabled) "On" else "Off",
                    onClick = onOpenSleep,
                )
                Spacer(Modifier.size(6.dp))
                InfoNote(
                    "Cadence reads sleep only from Android Health Connect, only when you turn it " +
                        "on, and only duration and timing. Journal notes and ratings are never read.",
                )
                Spacer(Modifier.size(12.dp))
                SettingSwitchRow(
                    title = "Use sleep as recovery context",
                    subtitle = "Shows a gentle note on the home screen. Nothing is changed for you.",
                    checked = sleepStatus.enabled,
                    onCheckedChange = { checked ->
                        viewModel.setSleepSyncEnabled(checked)
                        if (checked) onOpenSleep()
                    },
                )
            }

            CadenceCard {
                SectionHeader(title = "Your data", subtitle = "Stored on this device only")
                Spacer(Modifier.size(10.dp))
                Text(
                    "${state.completedSessions.size} workouts · ${state.routines.size} routines · " +
                        "${state.completedSessions.sumOf { it.sets.size }} sets",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(10.dp))
                BulletLine("No account is needed and none exists.")
                BulletLine("Nothing is uploaded, shared or sold.")
                BulletLine("Exports contain your routines, history and preferences only.")
                BulletLine("Exports never contain sleep data or identifiers from other apps.")
                Spacer(Modifier.size(14.dp))
                SecondaryButton(
                    text = "Export a backup",
                    icon = CadenceIcon.Share,
                    onClick = { exportLauncher.launch("cadence-backup.json") },
                )
                Spacer(Modifier.size(8.dp))
                SecondaryButton(
                    text = "Import a backup",
                    icon = CadenceIcon.Undo,
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                )
                Spacer(Modifier.size(8.dp))
                SecondaryButton(
                    text = "Delete all local data",
                    icon = CadenceIcon.Trash,
                    destructive = true,
                    onClick = { pendingDeleteAll = true },
                )
            }

            CadenceCard {
                SectionHeader(title = "About")
                Spacer(Modifier.size(8.dp))
                Text(
                    "Cadence plans and tracks your training entirely on this device. Estimated " +
                        "one-rep max values are a formula, levels summarise your own consistency, " +
                        "and the body map records where your sets went — none of it is a medical " +
                        "measurement or a diagnosis.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CadenceIcon(CadenceIcon.Info, size = 16.dp, tint = CadenceTheme.tones.rest)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "All exercise illustrations and icons are drawn in-app. No third-party " +
                            "artwork is bundled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            report?.let { message -> InfoNote(message, emphasise = true) }

            Spacer(Modifier.size(20.dp))
        }
    }

    if (pendingDeleteAll) {
        ConfirmDialog(
            title = "Delete everything?",
            message = "Your routines, workout history, records and preferences are removed from " +
                "this device. Sleep reading is turned off. This cannot be undone.",
            confirmLabel = "Delete all data",
            onConfirm = {
                pendingDeleteAll = false
                viewModel.deleteAllData { onBack() }
            },
            onDismiss = { pendingDeleteAll = false },
        )
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        CadenceIcon(CadenceIcon.Check, size = 16.dp, tint = CadenceTheme.tones.success)
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
