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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.math.PersonalRecordType
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.progression.ProgressionRecommendation
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.art.ExerciseArtwork
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.components.ThinDivider
import com.cadence.fit.ui.theme.CadenceTheme

/**
 * Everything about one movement: how to do it, what it trains, your records, the
 * progression suggestion, and the session-by-session history behind it.
 */
@Composable
fun ExerciseDetailScreen(
    viewModel: CadenceViewModel,
    exerciseId: String,
    routineExerciseId: String?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    val now = viewModel.nowMs()
    val model = remember(state, exerciseId, routineExerciseId, now) {
        ScreenModels.exerciseDetail(state, viewModel.catalog, exerciseId, unit, now, routineExerciseId)
    }

    if (model == null) {
        Column(Modifier.fillMaxSize()) {
            CadenceTopBar(title = "Exercise", onBack = onBack)
            InfoNote("That exercise is not in the library.")
        }
        return
    }

    val owner = remember(state.routines, exerciseId, routineExerciseId) {
        findSlot(state.routines, exerciseId, routineExerciseId)
    }
    var applied by remember(exerciseId) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(title = model.exercise.name, onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExerciseArtwork(
                pattern = model.exercise.pattern,
                muscleAccent = CadenceTheme.tones.heatHigh,
                contentDescription = "${model.exercise.name} movement illustration",
            )

            CadenceCard {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    model.exercise.primaryMuscles.forEach { Badge(it.displayName) }
                    model.exercise.secondaryMuscles.forEach { muscle ->
                        Badge(
                            text = "${muscle.displayName} (secondary)",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                ThinDivider()
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    MiniStat(model.exercise.equipment.displayName, "equipment")
                    MiniStat(model.exercise.difficulty.displayName, "difficulty")
                    MiniStat(model.exercise.pattern.displayName, "pattern")
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    if (model.exercise.isUnilateral) {
                        "Train each side separately."
                    } else {
                        "Both sides work together."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            model.recommendation?.let { recommendation ->
                ProgressionCard(
                    recommendation = recommendation,
                    unit = unit,
                    owner = owner,
                    applied = applied,
                    onApply = { message ->
                        owner?.let { (routine, slot) ->
                            viewModel.saveRoutine(applySuggestion(routine, slot, recommendation, unit))
                        }
                        applied = message
                    },
                    onDismiss = { applied = "Not now — your plan is unchanged." },
                )
            }

            if (applied != null) {
                InfoNote(applied!!, emphasise = true)
            }

            CadenceCard {
                SectionHeader(
                    title = "Your numbers",
                    subtitle = if (model.history.isEmpty()) "Nothing logged yet" else
                        "${model.history.size} sessions logged",
                )
                Spacer(Modifier.size(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StatTile("Sets", model.totalSets.toString(), modifier = Modifier.weight(1f))
                    StatTile(
                        "Volume",
                        Weights.formatVolume(model.totalVolumeKg, unit),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        "Last",
                        model.previous?.let { TimeRanges.relativeDay(it.completedAtMs, now) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (model.records.isNotEmpty()) {
                CadenceCard {
                    SectionHeader(
                        title = "Personal records",
                        subtitle = "Your best logged values for this movement",
                    )
                    Spacer(Modifier.size(10.dp))
                    model.records.forEach { record ->
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
                                    record.type.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    recordValue(record.type, record.value, record.reps, unit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                TimeRanges.relativeDay(record.achievedAtMs, now),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (model.history.isNotEmpty()) {
                CadenceCard {
                    SectionHeader(title = "History", subtitle = "Most recent first")
                    Spacer(Modifier.size(10.dp))
                    model.history.take(8).forEachIndexed { index, session ->
                        if (index > 0) ThinDivider()
                        val sets = session.setsFor(exerciseId).sortedBy { it.setIndex }
                        val top = sets.maxByOrNull { it.weightKg }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    TimeRanges.relativeDay(session.completedAtMs, now),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "${sets.size} sets · " +
                                        Weights.formatVolume(
                                            sets.sumOf { it.volumeKg },
                                            unit,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            top?.let { set ->
                                Text(
                                    "${Weights.format(set.weightKg, unit)} ${unit.abbreviation} × ${set.reps}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            CadenceCard {
                SectionHeader(title = "How to do it")
                Spacer(Modifier.size(10.dp))
                model.exercise.instructions.forEachIndexed { index, step ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CadenceTheme.tones.rest,
                            modifier = Modifier.size(width = 22.dp, height = 20.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (model.exercise.tips.isNotEmpty()) {
                CadenceCard {
                    SectionHeader(title = "Form cues")
                    Spacer(Modifier.size(8.dp))
                    model.exercise.tips.forEach { tip ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            CadenceIcon(
                                CadenceIcon.Check,
                                size = 16.dp,
                                tint = CadenceTheme.tones.success,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(tip, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            InfoNote(
                "Shared as general training information, not medical or physiotherapy advice. " +
                    "Stop if something hurts, and adjust loads to what you can control.",
                icon = CadenceIcon.Warning,
            )
            Spacer(Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ProgressionCard(
    recommendation: ProgressionRecommendation,
    unit: com.cadence.fit.domain.units.WeightUnit,
    owner: Pair<Routine, RoutineExercise>?,
    applied: String?,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CadenceCard {
        SectionHeader(
            title = "Next step suggestion",
            subtitle = "Based on ${recommendation.basedOnSessions} logged " +
                if (recommendation.basedOnSessions == 1) "session" else "sessions",
        )
        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(recommendation.headline, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Badge(recommendation.kind.displayName)
        }
        Spacer(Modifier.size(6.dp))
        Text(recommendation.detail, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(6.dp))
        Text(
            recommendation.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        recommendation.suggestedWeightKg?.let { suggested ->
            recommendation.lastWeightKg?.let { previous ->
                Spacer(Modifier.size(8.dp))
                val jump = ((suggested - previous) / previous) * 100.0
                Text(
                    "Suggested working weight: ${Weights.format(suggested, unit)} ${unit.abbreviation} " +
                        "(was ${Weights.format(previous, unit)}, about ${jump.toInt()}%).",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (jump >= 6.0) {
                    Spacer(Modifier.size(8.dp))
                    InfoNote(
                        "That is a big single jump. Reaching the top of the rep range on every set " +
                            "first is the safer signal to move up.",
                        icon = CadenceIcon.Warning,
                    )
                }
            }
        }

        Spacer(Modifier.size(14.dp))
        if (applied == null && owner != null) {
            PrimaryButton(
                text = "Apply to ${owner.first.name}",
                icon = CadenceIcon.Check,
                onClick = { onApply(appliedMessage(recommendation, unit)) },
            )
            Spacer(Modifier.size(8.dp))
        }
        SecondaryButton(text = "Not now", onClick = onDismiss)
        Spacer(Modifier.size(8.dp))
        Text(
            "Nothing is ever changed automatically. Applying only updates the rep range, " +
                "set count and a target note in your routine.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun appliedMessage(
    recommendation: ProgressionRecommendation,
    unit: com.cadence.fit.domain.units.WeightUnit,
): String {
    val weight = recommendation.suggestedWeightKg
        ?.let { " Target ${Weights.formatWithUnit(it, unit)}." }
        .orEmpty()
    return "Applied to your routine: ${recommendation.headline.lowercase()}.$weight"
}

/** Updates the routine slot this suggestion came from. */
private fun applySuggestion(
    routine: Routine,
    slot: RoutineExercise,
    recommendation: ProgressionRecommendation,
    unit: com.cadence.fit.domain.units.WeightUnit,
): Routine {
    val target = recommendation.suggestedWeightKg
        ?.let { "Target ${Weights.formatWithUnit(it, unit)}" }
    // Local copies so the compiler can smart-cast across the module boundary.
    val minReps = recommendation.suggestedMinReps
    val maxReps = recommendation.suggestedMaxReps
    val updated = slot.copy(
        targetSets = recommendation.suggestedSets ?: slot.targetSets,
        repRange = if (minReps != null && maxReps != null) minReps..maxReps else slot.repRange,
        note = target ?: slot.note,
    )
    return routine.copy(exercises = routine.exercises.map { if (it.id == slot.id) updated else it })
}

private fun findSlot(
    routines: List<Routine>,
    exerciseId: String,
    routineExerciseId: String?,
): Pair<Routine, RoutineExercise>? {
    routines.forEach { routine ->
        routine.exercises.forEach { slot ->
            val matchesId = routineExerciseId == null || slot.id == routineExerciseId
            if (slot.exerciseId == exerciseId && matchesId) return routine to slot
        }
    }
    return null
}

private fun recordValue(
    type: PersonalRecordType,
    value: Double,
    reps: Int,
    unit: com.cadence.fit.domain.units.WeightUnit,
): String = when (type) {
    PersonalRecordType.HEAVIEST_WEIGHT -> "${Weights.formatWithUnit(value, unit)}"
    PersonalRecordType.BEST_ESTIMATED_1RM -> "${Weights.formatWithUnit(value, unit)} (estimate)"
    PersonalRecordType.MOST_REPS -> "$reps reps"
    PersonalRecordType.MOST_VOLUME -> "${Weights.formatVolume(value, unit)} in one session"
}
