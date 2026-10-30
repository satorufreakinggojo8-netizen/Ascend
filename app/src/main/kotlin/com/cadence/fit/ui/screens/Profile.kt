package com.cadence.fit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.insight.ChartData
import com.cadence.fit.domain.insight.ChartWindow
import com.cadence.fit.domain.model.BodyProfile
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.LabeledRow
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.Sparkline
import com.cadence.fit.ui.components.ThinDivider
import com.cadence.fit.ui.theme.CadenceTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WEIGH_IN_DATE = DateTimeFormatter.ofPattern("d MMM")

/**
 * Optional details and the current plan in one place.
 *
 * Age, height and weight are never required: every card works with them empty and
 * explains what is stored. Nothing here is used to assess body composition.
 */
@Composable
fun ProfileScreen(
    viewModel: CadenceViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSplits: () -> Unit,
    onOpenNutrition: () -> Unit,
    onRestartOnboarding: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile
    val body = state.bodyProfile
    val unit = state.settings.weightUnit
    val now = viewModel.nowMs()
    val summary = remember(state.sessions, now, profile.daysPerWeek) {
        ChartData.summary(state.sessions, now, profile.daysPerWeek)
    }
    val volumeTrend = remember(state.sessions, now) {
        ChartData.volumeByWeek(state.sessions, ChartWindow.TWELVE_WEEKS, now)
            .map { it.volumeKg.toFloat() }
    }
    var editingDetails by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Profile",
            subtitle = "Local to this device",
            onBack = onBack,
            actions = listOf(CadenceIcon.Settings to onOpenSettings),
            actionDescriptions = listOf("Privacy and settings"),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlanCard(
                profile = profile,
                onOpenSplits = onOpenSplits,
                onRestartOnboarding = onRestartOnboarding,
            )

            CadenceCard {
                SectionHeader(
                    title = "Personal details",
                    subtitle = "Optional · stored only on this device",
                    trailing = {
                        Badge(
                            text = if (body.hasAnyDetail) "Added" else "Optional",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                Spacer(Modifier.size(8.dp))
                LabeledRow("Age", body.ageYears?.let { "$it years" } ?: "Not set")
                ThinDivider()
                LabeledRow("Height", body.heightCm?.let { "${Weights.formatAmount(it)} cm" } ?: "Not set")
                ThinDivider()
                LabeledRow(
                    label = "Latest weight",
                    value = body.latestWeightKg?.let { Weights.formatBodyWeight(it, unit) } ?: "Not set",
                )
                Spacer(Modifier.size(12.dp))
                SecondaryButton(
                    text = if (body.hasAnyDetail) "Edit details" else "Add details",
                    icon = CadenceIcon.Edit,
                    onClick = { editingDetails = true },
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Age and height are never used to decide what you may train. " +
                        "Cadence does not estimate body fat or diagnose anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            WeightTrendCard(
                body = body,
                unit = unit,
                nowMs = now,
                onAddWeight = { editingDetails = true },
            )

            CadenceCard {
                SectionHeader(
                    title = "Training so far",
                    subtitle = "From your own logged sessions",
                )
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    MiniStat("${state.completedSessions.size}", "workouts")
                    MiniStat("${summary.sessionsThisWeek}/${profile.daysPerWeek}", "this week")
                    MiniStat("${summary.streakWeeks}", "week streak")
                }
                if (volumeTrend.any { it > 0f }) {
                    Spacer(Modifier.size(14.dp))
                    Sparkline(values = volumeTrend, height = 40.dp)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Working volume per week, last 12 weeks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    "Detailed charts live on the Progress tab. This page keeps only the summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CadenceCard(onClick = onOpenNutrition) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nutrition app", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.size(8.dp))
                            Badge(
                                text = "Not connected yet",
                                container = MaterialTheme.colorScheme.surfaceVariant,
                                content = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "A separate nutrition app is planned. Nothing is shared today.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CadenceIcon(
                        CadenceIcon.ChevronRight,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CadenceCard(onClick = onOpenSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Privacy, export and delete", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sleep is opt-in. Exports include the details above because they are your data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CadenceIcon(
                        CadenceIcon.ChevronRight,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            InfoNote(
                "No account is needed and none exists. Cadence has no network permission, so " +
                    "nothing on this page can be uploaded.",
            )
            Spacer(Modifier.size(20.dp))
        }
    }

    if (editingDetails) {
        DetailsDialog(
            body = body,
            unit = unit,
            onSave = { age, height, weight ->
                var next = body.copy(ageYears = age, heightCm = height)
                if (weight != null) next = next.withWeight(weight, now)
                viewModel.saveBodyProfile(next)
                editingDetails = false
            },
            onDismiss = { editingDetails = false },
        )
    }
}

@Composable
private fun PlanCard(
    profile: TrainingProfile,
    onOpenSplits: () -> Unit,
    onRestartOnboarding: () -> Unit,
) {
    CadenceCard {
        SectionHeader(
            title = profile.planName.ifBlank { "Your plan" },
            subtitle = "${profile.daysPerWeek} days a week · ${profile.goal.displayName}",
        )
        Spacer(Modifier.size(8.dp))
        LabeledRow("Experience", profile.experience.displayName)
        ThinDivider()
        LabeledRow("Where you train", profile.location.displayName)
        ThinDivider()
        LabeledRow("Session target", "${profile.sessionMinutes} minutes")
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Training split",
                icon = CadenceIcon.Plan,
                onClick = onOpenSplits,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Edit answers",
                icon = CadenceIcon.Edit,
                onClick = onRestartOnboarding,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeightTrendCard(
    body: BodyProfile,
    unit: com.cadence.fit.domain.units.WeightUnit,
    nowMs: Long,
    onAddWeight: () -> Unit,
) {
    val change = body.changeKg(30, nowMs)
    CadenceCard {
        SectionHeader(
            title = "Weight",
            subtitle = "One reading per day · ${body.weightEntries.size} recorded",
        )
        if (body.weightEntries.isEmpty()) {
            Spacer(Modifier.size(10.dp))
            Text(
                "No weigh-ins yet. Add one when you want a trend; it is never required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            SecondaryButton(text = "Add a weight", icon = CadenceIcon.Plus, onClick = onAddWeight)
        } else {
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    body.latestWeightKg?.let { Weights.formatBodyWeight(it, unit) } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.size(10.dp))
                if (change != null) {
                    Text(
                        text = "${if (change >= 0) "+" else "−"}" +
                            "${Weights.formatAmount(unit.fromKilograms(kotlin.math.abs(change)))} " +
                            unit.abbreviation + " in 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (body.weightEntries.size >= 2) {
                Spacer(Modifier.size(10.dp))
                Sparkline(
                    values = body.trendKg().map { it.toFloat() },
                    accent = CadenceTheme.tones.heatHigh,
                    height = 46.dp,
                )
            }
            Spacer(Modifier.size(10.dp))
            val zone = ZoneId.systemDefault()
            body.weightEntries.sortedByDescending { it.atMs }.take(3).forEach { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Instant.ofEpochMilli(entry.atMs).atZone(zone).toLocalDate().format(WEIGH_IN_DATE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Weights.formatBodyWeight(entry.weightKg, unit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            SecondaryButton(text = "Add today's weight", icon = CadenceIcon.Plus, onClick = onAddWeight)
        }
    }
}

@Composable
private fun DetailsDialog(
    body: BodyProfile,
    unit: com.cadence.fit.domain.units.WeightUnit,
    onSave: (age: Int?, heightCm: Double?, weightKg: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var age by remember { mutableStateOf(body.ageYears?.toString().orEmpty()) }
    var height by remember { mutableStateOf(body.heightCm?.let { Weights.formatAmount(it) }.orEmpty()) }
    var weight by remember {
        mutableStateOf(body.latestWeightKg?.let { Weights.formatAmount(unit.fromKilograms(it)) }.orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personal details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Everything is optional. Leave age or height empty to remove it; a new " +
                        "weight is saved as today's reading and keeps older readings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter(Char::isDigit).take(3) },
                    label = { Text("Age") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter { character -> character.isDigit() || character == '.' } },
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { character -> character.isDigit() || character == '.' } },
                    label = { Text("Weight (${unit.abbreviation})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Saving a weight records today's date. It does not change any workout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedAge = age.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    val parsedHeight = height.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                    val parsedWeight = weight.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                    val invalidAge = age.isNotBlank() && (parsedAge == null || parsedAge !in BodyProfile.MIN_AGE..BodyProfile.MAX_AGE)
                    val invalidHeight = height.isNotBlank() &&
                        (parsedHeight == null || parsedHeight !in BodyProfile.MIN_HEIGHT_CM..BodyProfile.MAX_HEIGHT_CM)
                    val weightKg = parsedWeight?.let { unit.toKilograms(it) }
                    val invalidWeight = weight.isNotBlank() &&
                        (weightKg == null || weightKg !in BodyProfile.MIN_WEIGHT_KG..BodyProfile.MAX_WEIGHT_KG)
                    if (invalidAge || invalidHeight || invalidWeight) {
                        error = "Check the values: age ${BodyProfile.MIN_AGE}-${BodyProfile.MAX_AGE}, " +
                            "height ${BodyProfile.MIN_HEIGHT_CM.toInt()}-${BodyProfile.MAX_HEIGHT_CM.toInt()} cm, " +
                            "weight ${BodyProfile.MIN_WEIGHT_KG.toInt()}-${BodyProfile.MAX_WEIGHT_KG.toInt()} kg."
                    } else {
                        onSave(parsedAge, parsedHeight, weightKg)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
