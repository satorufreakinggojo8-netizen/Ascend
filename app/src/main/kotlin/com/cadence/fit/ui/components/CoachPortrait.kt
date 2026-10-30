package com.cadence.fit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cadence.fit.domain.coach.CoachPhotoSlot

/**
 * A coach portrait at a fixed 4:5 frame.
 *
 * The photo is resolved from the resource name in [CoachPhotoSlot.drawableName]
 * at runtime, so adding an approved photo to `res/drawable-nodpi/` makes it
 * appear without a code change. When the photo is not in the project the slot is
 * rendered as an obvious placeholder that names the exact missing resource —
 * it is never filled with a stock photo or a different person.
 */
@Composable
fun CoachPortrait(
    slot: CoachPhotoSlot,
    description: String,
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val drawableId = remember(slot.drawableName) {
        context.resources.getIdentifier(slot.drawableName, "drawable", context.packageName)
    }
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .clip(shape),
    ) {
        if (drawableId != 0) {
            Image(
                painter = painterResource(drawableId),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PhotoSlotPlaceholder(slot = slot, description = description, compact = compact)
        }
    }
}

@Composable
private fun PhotoSlotPlaceholder(
    slot: CoachPhotoSlot,
    description: String,
    compact: Boolean,
) {
    val outline = MaterialTheme.colorScheme.outline
    val content = MaterialTheme.colorScheme.onSurfaceVariant
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(9f, 7f)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash),
                )
            }
            .semantics {
                contentDescription =
                    "Placeholder for the $description portrait. The approved photo has not been added yet."
            }
            .padding(if (compact) 8.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp),
        ) {
            CadenceIcon(
                CadenceIcon.Coach,
                size = if (compact) 18.dp else 26.dp,
                tint = content,
            )
            Text(
                slot.expression.displayName,
                style = if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = content,
            )
            Text(
                "Awaiting photo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
            if (!compact) {
                Text(
                    slot.drawableName,
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
