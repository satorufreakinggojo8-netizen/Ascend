package com.cadence.fit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.fit.domain.units.PlateCalculator
import com.cadence.fit.domain.units.PlateSolution
import com.cadence.fit.domain.units.Weights
import com.cadence.fit.domain.units.WeightUnit
import com.cadence.fit.ui.theme.CadenceTheme
import kotlin.math.abs

/**
 * A plate-loading helper that is deliberately independent of the set being logged.
 *
 * It reads the current weight as a starting target and can *offer* to fill the
 * weight field, but it never logs a set, edits an existing set, or changes
 * anything without an explicit tap on the confirm button.
 */
@Composable
fun PlateCalculatorSheet(
    initialWeight: Double,
    unit: WeightUnit,
    barWeight: Double,
    onBarWeightChange: (Double) -> Unit,
    onUseWeight: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf(initialWeight.coerceAtLeast(0.0)) }
    val solution = remember(target, barWeight, unit) {
        PlateCalculator.solve(target = target, bar = barWeight, unit = unit)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plate calculator") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Plates for a ${Weights.formatAmount(barWeight)} ${unit.abbreviation} bar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Target",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    StepButton(
                        icon = CadenceIcon.Minus,
                        contentDescription = "Decrease target weight",
                        size = 52.dp,
                        onClick = {
                            target = (target - unit.fineIncrement).coerceAtLeast(0.0)
                        },
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "${Weights.formatAmount(target)} ${unit.abbreviation}",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    StepButton(
                        icon = CadenceIcon.Plus,
                        contentDescription = "Increase target weight",
                        size = 52.dp,
                        onClick = { target += unit.increment },
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-unit.increment, unit.increment, unit.increment * 2).forEach { delta ->
                        FilterChip(
                            label = if (delta > 0) "+${Weights.formatAmount(delta)}" else
                                "-${Weights.formatAmount(abs(delta))}",
                            selected = false,
                            onClick = { target = (target + delta).coerceAtLeast(0.0) },
                        )
                    }
                }

                Text("Bar", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlateCalculator.barsFor(unit).forEach { option ->
                        FilterChip(
                            label = if (option == 0.0) "No bar" else Weights.formatAmount(option),
                            selected = abs(option - barWeight) < 1e-6,
                            onClick = { onBarWeightChange(option) },
                        )
                    }
                }

                PlateResultCard(solution)

                Text(
                    "This only fills in the weight field. Nothing is logged or changed " +
                        "until you tap Log set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onUseWeight(solution.achievable)
                    onDismiss()
                },
                enabled = solution.achievable > 0.0,
            ) {
                Text("Use ${Weights.formatAmount(solution.achievable)} ${unit.abbreviation}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun PlateResultCard(solution: PlateSolution) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        when {
            solution.belowBar -> InfoNote(
                "That is lighter than the bar. The lightest load here is " +
                    "${Weights.formatAmount(solution.bar)} ${solution.unit.abbreviation}.",
                icon = CadenceIcon.Warning,
            )

            solution.platesPerSide.isEmpty() -> Text(
                "Just the bar. No plates needed.",
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> {
                Text(
                    "${Weights.formatAmount(solution.achievable)} ${solution.unit.abbreviation}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (solution.exact) "Exact target" else {
                        val short = abs(solution.difference)
                        "${Weights.formatAmount(short)} ${solution.unit.abbreviation} short of the target"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (solution.exact) CadenceTheme.tones.success else CadenceTheme.tones.warning,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "Per side",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    solution.platesPerSide.forEach { plate ->
                        Badge(
                            text = "${Weights.formatAmount(plate.plate)} × ${plate.countPerSide}",
                            container = CadenceTheme.tones.heatHigh.copy(alpha = 0.16f),
                            content = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "${solution.plateCount} plates in total, both sides counted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                solution.nextAchievable?.let { next ->
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Next loadable weight: ${Weights.formatAmount(next)} " +
                            solution.unit.abbreviation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
