package com.cadence.fit.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.coach.CoachFact
import com.cadence.fit.domain.coach.CoachPhotoSlot
import com.cadence.fit.domain.coach.CoachRoster
import com.cadence.fit.domain.coach.CoachTopic
import com.cadence.fit.domain.coach.CoachTopicDestination
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.CoachPortrait
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.ThinDivider

/**
 * The coaches list. One ambassador for now, with the honest state of his profile
 * visible before it is opened: what is confirmed, and what is still waiting on
 * approved copy and photos.
 */
@Composable
fun CoachesScreen(
    viewModel: CadenceViewModel,
    onOpenCoach: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sleepStatus by viewModel.sleepStatus.collectAsStateWithLifecycle()
    val sleepSessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    val now = viewModel.nowMs()
    val model = remember(state, sleepSessions, sleepStatus.enabled, now) {
        ScreenModels.coach(
            state = state,
            catalog = viewModel.catalog,
            sleepSessions = if (sleepStatus.enabled) sleepSessions else emptyList(),
            nowMs = now,
            unit = state.settings.weightUnit,
            topicOpen = false,
            coachId = CoachRoster.davidLaid.id,
        )
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Coaches",
            subtitle = "Guidance from your own training",
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InfoNote(
                "David Laid is Cadence's brand ambassador. Only details you approved are " +
                    "shown as facts; open the profile for the full picture.",
            )

            SectionHeader(
                title = "Brand ambassador",
                subtitle = "Featured in Cadence",
            )

            CadenceCard(onClick = { onOpenCoach(model.coach.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoachPortrait(
                        slot = model.coach.photoFor(model.presence.expression),
                        description = model.presence.photoDescription,
                        compact = true,
                        corner = 16.dp,
                        modifier = Modifier.width(78.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(model.coach.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(4.dp))
                        Badge(model.coach.role)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            model.presence.headline,
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

            CadenceCard(onClick = { onOpenCoach(model.coach.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Programs & guidance", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${model.programs.size} routine${if (model.programs.size == 1) "" else "s"} " +
                                "in your plan · ${model.topics.size} guidance " +
                                "topic${if (model.topics.size == 1) "" else "s"} from your data",
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

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MiniStat("${model.coach.confirmedFacts.size}", "confirmed facts")
                MiniStat("${model.coach.photos.count { it.approved }}", "approved photos")
                MiniStat("${model.coach.pendingFacts.size}", "awaiting approval")
            }

            InfoNote(
                text = "Only details you supplied are shown as facts. Biography, training " +
                    "background, programmes and official links are listed as pending until " +
                    "approved copy exists — none of them are invented.",
                icon = CadenceIcon.Warning,
            )

            InfoNote(CoachRoster.DISCLAIMER)
            Spacer(Modifier.size(8.dp))
        }
    }
}

/**
 * A single coach's page: portrait state, confirmed facts, what still needs
 * approval, the user's own programmes, and guidance topics built from logged data.
 */
@Composable
fun CoachProfileScreen(
    viewModel: CadenceViewModel,
    coachId: String,
    onBack: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenSleep: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sleepStatus by viewModel.sleepStatus.collectAsStateWithLifecycle()
    val sleepSessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    val now = viewModel.nowMs()
    val coach = CoachRoster.byId(coachId)

    if (coach == null) {
        Column(Modifier.fillMaxSize()) {
            CadenceTopBar(title = "Coaches", onBack = onBack)
            EmptyState(
                title = "Coach not found",
                message = "This coach is no longer part of the roster.",
                icon = CadenceIcon.Coach,
            )
        }
        return
    }

    var openTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    val model = remember(state, sleepSessions, sleepStatus.enabled, now, openTopicId, coachId) {
        ScreenModels.coach(
            state = state,
            catalog = viewModel.catalog,
            sleepSessions = if (sleepStatus.enabled) sleepSessions else emptyList(),
            nowMs = now,
            unit = state.settings.weightUnit,
            topicOpen = openTopicId != null,
            coachId = coachId,
        )
    }
    val presence = model.presence

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = coach.name,
            subtitle = coach.role,
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoachHero(
                coachName = coach.name,
                role = coach.role,
                presenceHeadline = presence.headline,
                presenceDetail = presence.detail,
                photo = coach.photoFor(presence.expression),
                photoDescription = presence.photoDescription,
                expressionLabel = presence.expression.displayName,
            )

            InfoNote(
                text = "Showing the ${presence.expression.displayName.lowercase()} portrait. " +
                    CoachRoster.PHOTO_NOTE,
            )

            SectionHeader(title = "Confirmed", subtitle = "Supplied by you")
            CadenceCard {
                coach.confirmedFacts.forEachIndexed { index, fact ->
                    if (index > 0) {
                        ThinDivider()
                        Spacer(Modifier.size(10.dp))
                    }
                    FactBlock(fact)
                }
            }

            SectionHeader(
                title = "Awaiting approval",
                subtitle = "Not shown as claims about the coach",
            )
            CadenceCard {
                coach.pendingFacts.forEachIndexed { index, fact ->
                    if (index > 0) {
                        ThinDivider()
                        Spacer(Modifier.size(10.dp))
                    }
                    FactBlock(fact)
                }
            }

            SectionHeader(
                title = "Portrait assets",
                subtitle = "${coach.photos.count { it.approved }} of ${coach.photos.size} approved",
            )
            CadenceCard {
                coach.photos.forEachIndexed { index, slot ->
                    if (index > 0) {
                        ThinDivider()
                        Spacer(Modifier.size(10.dp))
                    }
                    PhotoSlotRow(slot)
                }
            }
            InfoNote(
                text = if (coach.hasApprovedPhotos) {
                    "Approved portraits are in the project. Each expression shows its own photo."
                } else {
                    "The portrait is a marked placeholder because no approved photo is in the " +
                        "project yet. Add 4:5 photos of David Laid to " +
                        "app/src/main/res/drawable-nodpi using the resource names above. " +
                        "Cadence never substitutes a stock photo, a different person or a " +
                        "generated expression."
                },
                icon = CadenceIcon.Camera,
                emphasise = !coach.hasApprovedPhotos,
            )

            SectionHeader(title = "Programs", subtitle = "Your routines")
            InfoNote(
                text = "No coach-authored programme is published here yet — that needs approved " +
                    "content. Your own routines are listed so you can go straight to one.",
                icon = CadenceIcon.Plan,
            )
            if (model.programs.isEmpty()) {
                EmptyState(
                    title = "No routines yet",
                    message = "Build a plan from the Plan tab and your programmes appear here.",
                    icon = CadenceIcon.Plan,
                )
            } else {
                model.programs.forEach { routine ->
                    ProgramCard(routine = routine, onClick = { onOpenRoutine(routine.id) })
                }
            }

            SectionHeader(
                title = "Guidance",
                subtitle = "Worked out from your logged sets",
            )
            model.topics.forEach { topic ->
                GuidanceCard(
                    topic = topic,
                    expanded = openTopicId == topic.id,
                    onToggle = {
                        openTopicId = if (openTopicId == topic.id) null else topic.id
                    },
                    onOpenExercise = onOpenExercise,
                    onOpenSleep = onOpenSleep,
                    onOpenProgress = onOpenProgress,
                )
            }

            InfoNote(CoachRoster.DISCLAIMER)
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun CoachHero(
    coachName: String,
    role: String,
    presenceHeadline: String,
    presenceDetail: String,
    photo: CoachPhotoSlot,
    photoDescription: String,
    expressionLabel: String,
) {
    CadenceCard {
        Row {
            Crossfade(
                targetState = photo,
                label = "coachPortrait",
                modifier = Modifier.width(124.dp),
            ) { slot ->
                CoachPortrait(
                    slot = slot,
                    description = photoDescription,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(coachName, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(4.dp))
                Badge(role)
                Spacer(Modifier.size(10.dp))
                Text(
                    expressionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(presenceHeadline, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(4.dp))
                Text(
                    presenceDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FactBlock(fact: CoachFact) {
    Column {
        Text(
            fact.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(3.dp))
        Text(fact.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(2.dp))
        Text(
            fact.source,
            style = MaterialTheme.typography.labelSmall,
            color = if (fact.verified) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
        )
    }
}

@Composable
private fun PhotoSlotRow(slot: CoachPhotoSlot) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CadenceIcon(
            if (slot.approved) CadenceIcon.Check else CadenceIcon.Warning,
            size = 18.dp,
            tint = if (slot.approved) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(slot.expression.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                slot.scene,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                slot.drawableName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Badge(
            text = if (slot.approved) "Added" else "Missing",
            container = if (slot.approved) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            content = if (slot.approved) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ProgramCard(routine: Routine, onClick: () -> Unit) {
    CadenceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${routine.focus} · ${routine.exercises.size} exercises · " +
                        "${routine.workingSets} working sets",
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
}

@Composable
private fun GuidanceCard(
    topic: CoachTopic,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenSleep: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    CadenceCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(topic.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    topic.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            CadenceIcon(
                if (expanded) CadenceIcon.Minus else CadenceIcon.Plus,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = if (expanded) "Collapse topic" else "Expand topic",
            )
        }

        Column(Modifier.animateContentSize()) {
            if (expanded) {
                Spacer(Modifier.size(12.dp))
                ThinDivider()
                Spacer(Modifier.size(12.dp))
                Text(topic.detail, style = MaterialTheme.typography.bodyMedium)
                topic.dataLine?.let { line ->
                    Spacer(Modifier.size(8.dp))
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (topic.destination) {
                    CoachTopicDestination.EXERCISE -> topic.exerciseId?.let { exerciseId ->
                        Spacer(Modifier.size(12.dp))
                        SecondaryButton(
                            text = "Open exercise history",
                            icon = CadenceIcon.TrendUp,
                            onClick = { onOpenExercise(exerciseId) },
                        )
                    }

                    CoachTopicDestination.PROGRESS -> {
                        Spacer(Modifier.size(12.dp))
                        SecondaryButton(
                            text = "See progress",
                            icon = CadenceIcon.Progress,
                            onClick = onOpenProgress,
                        )
                    }

                    CoachTopicDestination.SLEEP -> {
                        Spacer(Modifier.size(12.dp))
                        SecondaryButton(
                            text = "Sleep settings",
                            icon = CadenceIcon.Sleep,
                            onClick = onOpenSleep,
                        )
                    }

                    CoachTopicDestination.NONE -> Unit
                }
            }
        }
    }
}
