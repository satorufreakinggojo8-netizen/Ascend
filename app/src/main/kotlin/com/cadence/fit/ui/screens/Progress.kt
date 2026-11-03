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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.insight.ChartWindow
import com.cadence.fit.domain.insight.LevelAssessment
import com.cadence.fit.domain.math.PersonalRecordType
import com.cadence.fit.domain.progression.PlateauInsight
import com.cadence.fit.domain.rank.RankAnchor
import com.cadence.fit.domain.rank.StrengthRank
import com.cadence.fit.domain.rank.StrengthRankAssessment
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.components.BarChart
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.FilterChip
import com.cadence.fit.ui.components.HeroButton
import com.cadence.fit.ui.components.HeroCard
import com.cadence.fit.ui.components.HeroProgressBar
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.LineChart
import com.cadence.fit.ui.components.RankCrest
import com.cadence.fit.ui.components.RankPill
import com.cadence.fit.ui.components.CadenceProgressBar
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.components.ThinDivider
import com.cadence.fit.ui.art.muscleBand
import com.cadence.fit.ui.theme.CadenceTheme
import kotlin.math.roundToInt

/** Charts, records and level context. Filters change the window for every chart. */
@Composable
fun ProgressScreen(
    viewModel: CadenceViewModel,
    onOpenExercise: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBodyMap: () -> Unit,
    onOpenWeekHistory: (Long) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val today = viewModel.today()
    val now = remember(state, today) { viewModel.nowMs() }
    var window by remember { mutableStateOf(ChartWindow.default) }
    var selectedWeek by remember { mutableStateOf<Int?>(null) }
    var selectedConsistency by remember { mutableStateOf<Int?>(null) }

    val model = remember(state, window, now) {
        ScreenModels.progress(state, viewModel.catalog, window, unit, now)
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Progress",
            subtitle = "${model.summary.totalSessions} sessions logged",
            actions = listOf(CadenceIcon.Calendar to onOpenHistory),
            actionDescriptions = listOf("Workout history"),
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartWindow.entries.forEach { option ->
                    FilterChip(
                        label = option.displayName,
                        selected = option == window,
                        onClick = {
                            window = option
                            selectedWeek = null
                            selectedConsistency = null
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
            if (model.summary.totalSessions == 0) {
                EmptyState(
                    title = "Charts need a workout or two",
                    message = "Log a session and volume, consistency and records start filling in.",
                    icon = CadenceIcon.Progress,
                )
            } else {
                RankCard(
                    assessment = model.rank,
                    unit = unit,
                    onOpenProfile = onOpenProfile,
                    onOpenLibrary = onOpenLibrary,
                )

                CadenceCard {
                    SectionHeader(
                        title = "Training volume",
                        subtitle = "Total working volume per week",
                        trailing = {
                            CadenceIcon(
                                CadenceIcon.TrendUp,
                                size = 18.dp,
                                tint = CadenceTheme.tones.success,
                            )
                        },
                    )
                    Spacer(Modifier.size(14.dp))
                    val bestWeek = model.volumeByWeek.indices
                        .maxByOrNull { model.volumeByWeek[it] }
                    BarChart(
                        values = model.volumeByWeek,
                        labels = model.weekLabels,
                        highlightIndex = selectedWeek,
                        description = "Weekly training volume for ${window.displayName}. " +
                            (bestWeek?.takeIf { model.volumeByWeek[it] > 0f }?.let { index ->
                                "Best week is ${model.weekLabels[index]} with " +
                                    Weights.formatVolume(model.volumeByWeek[index].toDouble(), unit) + "."
                            } ?: "No volume logged yet."),
                        onSelect = { index ->
                            selectedWeek = if (selectedWeek == index) null else index
                        },
                    )
                    if (bestWeek != null && model.volumeByWeek[bestWeek] > 0f) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Best week ${model.weekLabels[bestWeek]}: " +
                                Weights.formatVolume(model.volumeByWeek[bestWeek].toDouble(), unit) +
                                " · Weekly average: " +
                                Weights.formatVolume(
                                    model.volumeByWeek.ifEmpty { listOf(0f) }.average(),
                                    unit,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    val index = selectedWeek
                    if (index != null && index in model.volumeByWeek.indices) {
                        InfoNote(
                            "Week of ${model.weekLabels[index]}: " +
                                Weights.formatVolume(model.volumeByWeek[index].toDouble(), unit) +
                                " of working volume, measured from your logged sets.",
                        )
                        Spacer(Modifier.size(8.dp))
                        SecondaryButton(
                            text = "Open that week's sessions",
                            onClick = { onOpenWeekHistory(model.weekStartEpochDays[index]) },
                        )
                    } else {
                        Text(
                            "Tap a bar for that week's total. Volume is weight × reps for working sets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                CadenceCard {
                    SectionHeader(
                        title = "Consistency",
                        subtitle = "Sessions per week against your ${model.targetSessionsPerWeek}-day target",
                    )
                    Spacer(Modifier.size(14.dp))
                    val averageSessions = model.consistencyByWeek
                        .ifEmpty { listOf(0f) }
                        .average()
                    LineChart(
                        values = model.consistencyByWeek,
                        labels = model.weekLabels,
                        targetLine = model.targetSessionsPerWeek.toFloat(),
                        highlightIndex = selectedConsistency,
                        description = "Sessions completed each week for ${window.displayName}, " +
                            "against a target of ${model.targetSessionsPerWeek}. " +
                            "Average is ${Weights.formatAmount(averageSessions)} sessions a week.",
                        onSelect = { index ->
                            selectedConsistency = if (selectedConsistency == index) null else index
                        },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Averaging ${Weights.formatAmount(averageSessions)} of " +
                            "${model.targetSessionsPerWeek} planned sessions a week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(10.dp))
                    val index = selectedConsistency
                    if (index != null && index in model.consistencyByWeek.indices) {
                        InfoNote(
                            "Week of ${model.weekLabels[index]}: " +
                                "${model.consistencyByWeek[index].toInt()} of " +
                                "${model.targetSessionsPerWeek} planned sessions.",
                        )
                        Spacer(Modifier.size(8.dp))
                        SecondaryButton(
                            text = "Open that week's sessions",
                            onClick = { onOpenWeekHistory(model.weekStartEpochDays[index]) },
                        )
                    } else {
                        Text(
                            "Tap a point to see that week. The line shows how many sessions you finished.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                CadenceCard {
                    SectionHeader(title = "Summary", subtitle = "Last 28 days")
                    Spacer(Modifier.size(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        StatTile(
                            "Sessions",
                            model.summary.sessionsLast28Days.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Records",
                            model.summary.recordsLast28Days.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Streak",
                            "${model.summary.streakWeeks}w",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    model.summary.volumeChangeFraction?.let { change ->
                        Spacer(Modifier.size(12.dp))
                        val percent = (change * 100).roundToInt()
                        Text(
                            if (percent == 0) {
                                "Volume is in line with last week."
                            } else if (change > 0) {
                                "Volume is up about $percent% on last week."
                            } else {
                                "Volume is down about ${-percent}% on last week. " +
                                    "A lighter week is a normal part of training."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    model.summary.averageSessionMinutes?.let { minutes ->
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Typical session length: about $minutes minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (model.recentRecords.isNotEmpty()) {
                    CadenceCard {
                        SectionHeader(
                            title = "Recent records",
                            subtitle = "Set in the last 28 days",
                        )
                        Spacer(Modifier.size(10.dp))
                        model.recentRecords.take(8).forEach { record ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CadenceIcon(
                                    CadenceIcon.Trophy,
                                    size = 18.dp,
                                    tint = CadenceTheme.tones.warning,
                                )
                                Spacer(Modifier.size(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        viewModel.catalog.byId(record.exerciseId)?.name
                                            ?: record.exerciseId,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "${record.type.displayName} · " +
                                            when (record.type) {
                                                PersonalRecordType.MOST_REPS -> "${record.reps} reps"
                                                PersonalRecordType.MOST_VOLUME ->
                                                    Weights.formatVolume(record.value, unit)
                                                else -> Weights.formatWithUnit(record.value, unit)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (record.type == PersonalRecordType.BEST_ESTIMATED_1RM) {
                                    Badge(
                                        text = "Estimate",
                                        container = MaterialTheme.colorScheme.surfaceVariant,
                                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.size(6.dp))
                                }
                                FilterChip(
                                    label = "Open",
                                    selected = false,
                                    onClick = { onOpenExercise(record.exerciseId) },
                                )
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "Weight, reps and volume are measured from logged sets. An estimated " +
                                "1RM is a formula, not a tested maximum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                MuscleBalanceCard(model, onOpenBodyMap)

                model.plateau?.let { plateau -> PlateauCard(plateau) }

                model.level?.let { level -> LevelCard(level, onHide = {
                    viewModel.saveSettings(state.settings.copy(showProgressLevel = false))
                }) }

                if (model.level == null) {
                    SecondaryButton(
                        text = "Show my progress level",
                        onClick = {
                            viewModel.saveSettings(state.settings.copy(showProgressLevel = true))
                        },
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

/** Which muscle groups got the work, and which did not. Informational only. */
@Composable
private fun MuscleBalanceCard(model: com.cadence.fit.ui.ProgressModel, onOpenBodyMap: () -> Unit) {
    val window = model.muscleWindow
    val ranking = ScreenModels.muscleRanking(window)
    val total = ranking.sumOf { it.second }
    CadenceCard {
        SectionHeader(
            title = "Muscle balance",
            subtitle = "Share of working volume, last 4 weeks",
        )
        Spacer(Modifier.size(12.dp))
        if (ranking.isEmpty() || total <= 0.0) {
            Text(
                "Log a few sets and this fills in with the muscles you have trained.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ranking.take(5).forEach { (muscle, volume) ->
                val share = volume / total
                Row(
                    Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(0.38f)) {
                        Text(
                            muscle.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${window.setsByMuscle[muscle] ?: 0} sets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CadenceProgressBar(
                        fraction = share.toFloat(),
                        modifier = Modifier.weight(0.5f),
                        height = 6.dp,
                        color = when (muscleBand(volume, window.maxVolume)) {
                            1 -> CadenceTheme.tones.heatLow
                            2 -> CadenceTheme.tones.heatMid
                            3 -> CadenceTheme.tones.heatHigh
                            4 -> CadenceTheme.tones.heatPeak
                            else -> CadenceTheme.tones.heatEmpty
                        },
                    )
                    Text(
                        "${(share * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            window.untrainedGroups.takeIf { it.isNotEmpty() }?.let { missed ->
                Spacer(Modifier.size(10.dp))
                val names = buildString {
                    append(missed.take(4).joinToString(", ") { it.displayName.lowercase() })
                    if (missed.size > 4) append(" and ${missed.size - 4} more")
                }
                InfoNote(
                    "No logged work for $names in this window. That is a fact about your log, " +
                        "not a problem to fix.",
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        SecondaryButton(text = "Open the body map", onClick = onOpenBodyMap)
    }
}

@Composable
private fun PlateauCard(insight: PlateauInsight) {
    CadenceCard {
        SectionHeader(
            title = if (insight.isDeloadRecommended) "Consider a lighter week" else "Progress check",
            subtitle = insight.headline,
        )
        Spacer(Modifier.size(10.dp))
        Text(insight.detail, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(10.dp))
        insight.suggestions.forEach { suggestion ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                CadenceIcon(CadenceIcon.Info, size = 16.dp, tint = CadenceTheme.tones.rest)
                Spacer(Modifier.size(8.dp))
                Text(suggestion, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.size(10.dp))
        InfoNote(
            "These are options, not instructions. Plenty of people keep the same loads for weeks " +
                "and still make progress.",
        )
    }
}

@Composable
private fun LevelCard(level: LevelAssessment, onHide: () -> Unit) {
    CadenceCard {
        SectionHeader(
            title = "Consistency level",
            subtitle = "Optional, and only about your own logged training",
        )
        Spacer(Modifier.size(12.dp))
        Text(level.headline, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(6.dp))
        Text(level.detail, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(10.dp))
        Text(level.nextStepHint, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile("Active weeks", level.consistencyWeeks.toString(), modifier = Modifier.weight(1f))
            StatTile(
                "Per week",
                Weights.formatAmount(level.sessionsPerWeek),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                "Last 28 days",
                level.sessionsLast28Days.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(12.dp))
        InfoNote(level.disclaimer, icon = CadenceIcon.Info)
        Spacer(Modifier.size(8.dp))
        Text(
            level.benchmark,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        SecondaryButton(text = "Hide this", onClick = onHide)
    }
}

/**
 * Rank hero plus the four anchors that produced it. The rank is a benchmark
 * against the user's own body weight, not a judgement or a competition class.
 */
@Composable
private fun RankCard(
    assessment: StrengthRankAssessment,
    unit: com.cadence.fit.domain.units.WeightUnit,
    onOpenProfile: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val gradients = CadenceTheme.gradients
    val rank = assessment.rank
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankCrest(rank = rank, size = 68.dp, onDark = true)
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "YOUR RANK",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        color = gradients.heroGlow,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        rank?.displayName ?: "Unranked",
                        style = MaterialTheme.typography.headlineSmall,
                        color = gradients.heroContent,
                    )
                    Text(
                        when {
                            rank != null -> "${assessment.anchorCount} of " +
                                "${assessment.totalAnchors} anchors · best e1RM vs bodyweight"
                            assessment.bodyweightKg == null ->
                                "Add bodyweight to compare your lifts"
                            else ->
                                "Log a barbell squat, bench, deadlift or overhead press"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = gradients.heroContent.copy(alpha = 0.72f),
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
            when {
                rank != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Next: ${assessment.nextRank?.displayName ?: "Top of the ladder"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = gradients.heroContent.copy(alpha = 0.72f),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (assessment.nextRank == null) {
                                "Max"
                            } else {
                                "${(assessment.progressToNext * 100).roundToInt()}%"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = gradients.heroGlow,
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    HeroProgressBar(assessment.progressToNext)
                }
                assessment.bodyweightKg == null -> {
                    Text(
                        "A rank needs one current bodyweight reading. It is stored only " +
                            "on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = gradients.heroContent.copy(alpha = 0.82f),
                    )
                    Spacer(Modifier.size(12.dp))
                    HeroButton(
                        text = "Add bodyweight",
                        icon = CadenceIcon.User,
                        onClick = onOpenProfile,
                    )
                }
                else -> {
                    Text(
                        "Your rank starts with a barbell squat, bench press, deadlift or " +
                            "overhead press.",
                        style = MaterialTheme.typography.bodySmall,
                        color = gradients.heroContent.copy(alpha = 0.82f),
                    )
                    Spacer(Modifier.size(12.dp))
                    HeroButton(
                        text = "Browse barbell lifts",
                        icon = CadenceIcon.Book,
                        onClick = onOpenLibrary,
                    )
                }
            }
        }

        CadenceCard {
            SectionHeader(
                title = "Rank breakdown",
                subtitle = "5 tiers · 4 anchor lifts · best estimated 1RM",
            )
            Spacer(Modifier.size(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StrengthRank.entries.forEach { tier ->
                    RankPill(tier, highlighted = tier == assessment.rank)
                }
            }
            Spacer(Modifier.size(12.dp))
            RankAnchor.entries.forEachIndexed { index, anchor ->
                val lift = assessment.lifts.firstOrNull { it.anchor == anchor }
                if (index > 0) ThinDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(anchor.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            lift?.exerciseName ?: "Not logged yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (lift != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                lift.rank?.let { liftRank ->
                                    RankPill(liftRank, highlighted = true)
                                    Spacer(Modifier.size(6.dp))
                                }
                                Text(
                                    Weights.formatWithUnit(lift.bestE1rmKg, unit),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Text(
                                lift.ratio?.let { "${Weights.formatAmount(it)}× bodyweight" }
                                    ?: "Bodyweight needed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "Ranks compare your best logged estimated 1RMs with current bodyweight. " +
                    "They are broad training benchmarks, not tested maximums, competition " +
                    "classes or health assessments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
