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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.SectionHeader

/**
 * The nutrition connection area.
 *
 * There is no integration, no network call and no hidden button. This page states
 * the boundary a future integration would have to respect, so the area is honest
 * rather than a disabled switch pretending a feature exists.
 */
@Composable
fun NutritionScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Nutrition app",
            subtitle = "Not connected",
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CadenceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CadenceIcon(
                        CadenceIcon.Info,
                        size = 22.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("Not connected yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Badge(
                        text = "Planned",
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    "A separate nutrition app for calories and micronutrients is planned. " +
                        "Cadence has no nutrition data, does not estimate calories or macros, " +
                        "and is not connected to anything today.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            CadenceCard {
                SectionHeader(
                    title = "What could be shared later",
                    subtitle = "Only what you approve, field by field",
                )
                Spacer(Modifier.size(10.dp))
                BoundaryLine("Training days and completed sessions", shared = true)
                BoundaryLine("Your current plan name and goal", shared = true)
                BoundaryLine("A weight reading you choose to share", shared = true)
                Spacer(Modifier.size(10.dp))
                Text(
                    "Any future connection would be off by default, revocable, and documented " +
                        "here before it is enabled. Cadence would not send anything in the " +
                        "background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CadenceCard {
                SectionHeader(
                    title = "What is never shared",
                    subtitle = "Regardless of any future integration",
                )
                Spacer(Modifier.size(10.dp))
                BoundaryLine("Nocta sleep sessions and journal notes", shared = false)
                BoundaryLine("Your workout history and set notes", shared = false)
                BoundaryLine("Age, height or weight unless you pick them", shared = false)
                BoundaryLine("Anything at all today", shared = false)
            }

            InfoNote(
                "Cadence has no INTERNET permission, so this page cannot contact a nutrition " +
                    "service even by accident. A real integration would need a documented, " +
                    "user-consented boundary and an explicit app update.",
                icon = CadenceIcon.Warning,
                emphasise = true,
            )

            CadenceCard {
                SectionHeader(
                    title = "How the boundary is designed",
                    subtitle = "Proposed, not implemented",
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "The smallest useful boundary is a one-way, read-only summary: the nutrition " +
                        "app asks for a short training summary, the user approves specific fields, " +
                        "and Cadence exports only those fields. Sleep would stay separate in " +
                        "Health Connect and would not be forwarded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "See docs/nutrition-integration.md in the project for the proposed fields, " +
                        "consent flow and the changes that would be required before any sync is " +
                        "claimed to work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BoundaryLine(text: String, shared: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CadenceIcon(
            if (shared) CadenceIcon.Check else CadenceIcon.Minus,
            size = 16.dp,
            tint = if (shared) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (shared) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
