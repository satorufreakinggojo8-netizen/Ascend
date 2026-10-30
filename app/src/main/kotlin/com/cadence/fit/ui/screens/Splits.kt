package com.cadence.fit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.plan.SplitDay
import com.cadence.fit.domain.plan.SplitTemplate
import com.cadence.fit.domain.plan.SplitTemplates
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.ThinDivider
import com.cadence.fit.ui.theme.CadenceTheme
import java.time.DayOfWeek

/**
 * Split library with a preview that can be edited before anything is applied.
 *
 * Applying adds routines and lays out two weeks of planned days. It never deletes
 * an existing routine or rewrites a logged session.
 */
@Composable
fun SplitsScreen(
    viewModel: CadenceViewModel,
    onBack: () -> Unit,
    onApplied: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember(selectedId) { mutableStateOf(SplitTemplates.byId(selectedId)) }
    var renaming by remember { mutableStateOf<DayOfWeek?>(null) }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Training split",
            subtitle = if (selected == null) {
                "Choose a weekly structure"
            } else {
                "Preview and edit before applying"
            },
            onBack = {
                if (selected == null) onBack() else {
                    selected = null
                    selectedId = null
                }
            },
        )

        if (selected == null) {
            SplitLibrary(
                profileName = state.profile.planName,
                onPick = { template ->
                    selectedId = template.id
                    selected = template
                },
            )
        } else {
            val template = selected!!
            SplitPreview(
                template = template,
                currentPlanName = state.profile.planName,
                onToggleRest = { dayOfWeek, rest ->
                    selected = template.withRest(dayOfWeek, rest)
                },
                onRename = { dayOfWeek -> renaming = dayOfWeek },
                onBackToLibrary = {
                    selected = null
                    selectedId = null
                },
                onApply = {
                    viewModel.applySplit(template, template)
                    onApplied()
                },
            )
        }
    }

    renaming?.let { dayOfWeek ->
        val day = selected?.day(dayOfWeek)
        if (day != null) {
            RenameDayDialog(
                day = day,
                onConfirm = { title ->
                    selected = selected?.renamed(dayOfWeek, title)
                    renaming = null
                },
                onDismiss = { renaming = null },
            )
        }
    }
}

@Composable
private fun SplitLibrary(
    profileName: String,
    onPick: (SplitTemplate) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (profileName.isNotBlank()) {
            InfoNote("Current plan: $profileName. Choosing another split adds new routines; it does not delete this one.")
        }

        val featured = SplitTemplates.all.firstOrNull { it.featured } ?: SplitTemplates.all.first()
        FeaturedSplitCard(template = featured, onClick = { onPick(featured) })

        SectionHeader(title = "Other splits", subtitle = "Practical structures for the days you can train")
        SplitTemplates.all.filterNot { it.id == featured.id }.forEach { template ->
            SplitTemplateCard(template = template, onClick = { onPick(template) })
        }

        InfoNote(
            "A split only sets the weekly shape. Exercises are filled from the equipment, " +
                "experience and goal on your profile, with no starting weights.",
        )
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun FeaturedSplitCard(template: SplitTemplate, onClick: () -> Unit) {
    CadenceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Featured", style = MaterialTheme.typography.labelSmall, color = CadenceTheme.tones.heatHigh)
                Spacer(Modifier.size(4.dp))
                Text(template.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    template.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Badge("${template.daysPerWeek} days")
        }
        Spacer(Modifier.size(14.dp))
        SplitWeekStrip(days = template.days, emphasiseToday = false)
        Spacer(Modifier.size(12.dp))
        ThinDivider()
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Preview & edit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            CadenceIcon(
                CadenceIcon.ChevronRight,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SplitTemplateCard(template: SplitTemplate, onClick: () -> Unit) {
    CadenceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    template.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Badge(
                text = "${template.daysPerWeek} days",
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        SplitWeekStrip(days = template.days, emphasiseToday = false)
    }
}

/**
 * Seven small columns, one per weekday. Rest days are drawn as an outline so the
 * week's shape is readable at a glance and without relying on colour alone.
 */
@Composable
private fun SplitWeekStrip(
    days: List<SplitDay>,
    emphasiseToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            val training = !day.isRest
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (training) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(vertical = 8.dp, horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (training) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.size(3.dp))
                Text(
                    if (training) day.title.take(6) else "Rest",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (training) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SplitPreview(
    template: SplitTemplate,
    currentPlanName: String,
    onToggleRest: (DayOfWeek, Boolean) -> Unit,
    onRename: (DayOfWeek) -> Unit,
    onBackToLibrary: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${template.daysPerWeek} training days · ${template.restDays.size} rest",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (template.featured) Badge("Featured")
        }

        Text(
            template.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.size(2.dp))
        template.days.forEach { day ->
            DayEditRow(
                day = day,
                onToggleRest = { rest -> onToggleRest(day.dayOfWeek, rest) },
                onRename = { onRename(day.dayOfWeek) },
            )
        }

        InfoNote(
            "Applying replaces planned days in this week and next week with the days above. " +
                "Completed and skipped days are kept, and every existing routine stays in your plan. " +
                "No weights are filled in.",
        )
        if (currentPlanName.isNotBlank()) {
            Text(
                "This will be labelled as your current plan. Your old routines remain available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PrimaryButton(
            text = "Apply ${template.name}",
            icon = CadenceIcon.Check,
            enabled = template.daysPerWeek > 0,
            onClick = onApply,
        )
        SecondaryButton(
            text = "Choose a different split",
            icon = CadenceIcon.Back,
            onClick = onBackToLibrary,
        )
        Spacer(Modifier.size(20.dp))
    }
}

@Composable
private fun DayEditRow(
    day: SplitDay,
    onToggleRest: (Boolean) -> Unit,
    onRename: () -> Unit,
) {
    val training = !day.isRest
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (training) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day.dayOfWeek.name.take(3),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(42.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .clickable(enabled = training, role = Role.Button, onClick = onRename),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (training) day.title else "Rest",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (training) {
                    Spacer(Modifier.size(4.dp))
                    CadenceIcon(
                        CadenceIcon.Edit,
                        size = 13.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = "Rename ${day.title}",
                    )
                }
            }
            Text(
                if (training) day.focus else day.restNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        FilterChip(
            label = if (training) "Train" else "Rest",
            selected = training,
            onClick = { onToggleRest(training) },
        )
    }
}

@Composable
private fun RenameDayDialog(
    day: SplitDay,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(day.dayOfWeek) { mutableStateOf(day.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Session name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Only the plan label changes. Exercises are chosen when you apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
