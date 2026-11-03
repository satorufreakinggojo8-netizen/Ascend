package com.cadence.fit.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cadence.fit.domain.rank.StrengthRank
import com.cadence.fit.ui.theme.CadenceTheme

@Composable
fun rankAccent(rank: StrengthRank?): Color = when (rank) {
    StrengthRank.NEWBIE -> MaterialTheme.colorScheme.onSurfaceVariant
    StrengthRank.ROOKIE -> CadenceTheme.tones.rest
    StrengthRank.INTERMEDIATE -> MaterialTheme.colorScheme.primary
    StrengthRank.PRO -> CadenceTheme.tones.warning
    StrengthRank.CHAMPION -> MaterialTheme.colorScheme.tertiary
    null -> MaterialTheme.colorScheme.outline
}

/** A small five-segment crest: one filled segment per tier already reached. */
@Composable
fun RankCrest(
    rank: StrengthRank?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onDark: Boolean = false,
) {
    val accent = rankAccent(rank)
    val gradients = CadenceTheme.gradients
    val track = if (onDark) {
        gradients.heroContent.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val disc = if (onDark) {
        gradients.hero.last().copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val label = rank?.displayName ?: "Unranked"

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Training rank: $label" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = this.size.minDimension * 0.065f
            val inset = stroke * 1.4f
            val arcSize = Size(
                width = this.size.width - inset * 2f,
                height = this.size.height - inset * 2f,
            )
            StrengthRank.entries.forEach { tier ->
                val active = rank != null && tier.order <= rank.order
                drawArc(
                    color = if (active) accent else track,
                    startAngle = -90f + tier.order * 72f + 4f,
                    sweepAngle = 64f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            drawCircle(
                color = disc,
                radius = this.size.minDimension * 0.30f,
                center = center,
            )
        }
        CadenceIcon(
            CadenceIcon.Trophy,
            size = size * 0.30f,
            tint = accent,
            contentDescription = null,
        )
    }
}

@Composable
fun RankPill(
    rank: StrengthRank?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val accent = rankAccent(rank)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = if (highlighted) 0.18f else 0.10f))
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = accent.copy(alpha = if (highlighted) 0.65f else 0.22f),
                shape = CircleShape,
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            rank?.displayName ?: "Unranked",
            style = MaterialTheme.typography.labelSmall,
            color = if (rank == null) MaterialTheme.colorScheme.onSurfaceVariant else accent,
        )
    }
}

/** Light progress track used on the dark rank hero. */
@Composable
fun HeroProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val gradients = CadenceTheme.gradients
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "rankProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(gradients.heroContent.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceAtLeast(0.001f))
                .height(6.dp)
                .clip(CircleShape)
                .background(gradients.heroGlow),
        )
    }
}