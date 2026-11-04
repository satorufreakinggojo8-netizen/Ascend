package com.cadence.fit.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.fit.domain.model.BodySide
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.ui.theme.CadenceTheme

private const val FIGURE_W = 100f
private const val FIGURE_H = 200f

/** A muscle drawn as a closed, smoothed outline. */
private data class Region(val muscle: MuscleGroup, val shapes: List<List<Offset>>)

private fun points(vararg coordinates: Float): List<Offset> =
    coordinates.toList().chunked(2).map { (x, y) -> Offset(x, y) }

/** Mirrors a left-side outline across the figure's centre line. */
private fun mirrored(shape: List<Offset>): List<Offset> = shape.map { Offset(FIGURE_W - it.x, it.y) }

private fun bothSides(shape: List<Offset>): List<List<Offset>> = listOf(shape, mirrored(shape))

private val FRONT_REGIONS = listOf(
    Region(
        MuscleGroup.SHOULDERS,
        bothSides(
            points(
                28f, 36f, 35f, 35f, 40f, 40f, 39f, 47f, 34f, 51f, 28f, 47f, 26f, 40f,
            ),
        ),
    ),
    Region(
        MuscleGroup.CHEST,
        bothSides(
            points(
                34f, 41f, 42f, 40f, 49f, 42f, 49f, 50f, 45f, 56f, 37f, 53f, 33f, 47f,
            ),
        ),
    ),
    Region(
        MuscleGroup.BICEPS,
        bothSides(
            points(
                26f, 52f, 31f, 53f, 31f, 64f, 27f, 72f, 23f, 66f, 22f, 58f,
            ),
        ),
    ),
    Region(
        MuscleGroup.FOREARMS,
        bothSides(
            points(
                21f, 74f, 26f, 76f, 23f, 96f, 19f, 106f, 16f, 96f, 16f, 84f,
            ),
        ),
    ),
    // Six blocks and a lower panel, so the middle reads as a trained abdomen
    // rather than one long slab.
    Region(
        MuscleGroup.ABS,
        listOf(
            points(45f, 53f, 49.2f, 53f, 49.2f, 62f, 45f, 62f),
            points(50.8f, 53f, 55f, 53f, 55f, 62f, 50.8f, 62f),
            points(45f, 63.5f, 49.2f, 63.5f, 49.2f, 71.5f, 45f, 71.5f),
            points(50.8f, 63.5f, 55f, 63.5f, 55f, 71.5f, 50.8f, 71.5f),
            points(45f, 73f, 49.2f, 73f, 49.2f, 80f, 45f, 80f),
            points(50.8f, 73f, 55f, 73f, 55f, 80f, 50.8f, 80f),
            points(45.5f, 81.5f, 54.5f, 81.5f, 53.5f, 90f, 46.5f, 90f),
        ),
    ),
    Region(
        MuscleGroup.OBLIQUES,
        bothSides(
            points(
                39f, 54f, 44f, 57f, 45f, 82f, 42f, 93f, 37f, 82f, 36f, 66f,
            ),
        ),
    ),
    Region(
        MuscleGroup.QUADS,
        bothSides(
            points(38f, 113f, 48f, 116f, 47f, 140f, 43f, 152f, 37f, 150f, 35f, 132f),
        ) + bothSides(
            // Vastus medialis: the teardrop above the knee.
            points(33f, 136f, 38f, 134f, 39f, 146f, 37f, 154f, 33f, 150f, 31.5f, 143f),
        ),
    ),
    Region(
        MuscleGroup.CALVES,
        bothSides(
            points(
                39f, 157f, 45f, 157f, 44f, 173f, 41f, 182f, 38f, 174f, 37f, 166f,
            ),
        ),
    ),
)

private val BACK_REGIONS = listOf(
    Region(
        MuscleGroup.SHOULDERS,
        bothSides(
            points(
                28f, 35f, 35f, 34f, 40f, 39f, 39f, 47f, 34f, 51f, 28f, 47f, 26f, 39f,
            ),
        ),
    ),
    Region(
        MuscleGroup.UPPER_BACK,
        listOf(
            points(
                42f, 42f, 58f, 42f, 58f, 57f, 54f, 69f, 46f, 69f, 42f, 57f,
            ),
        ),
    ),
    Region(
        MuscleGroup.LATS,
        bothSides(
            points(
                34f, 47f, 42f, 55f, 43f, 77f, 38f, 90f, 32f, 79f, 31f, 61f,
            ),
        ),
    ),
    // Drawn above the lats and upper back so the trapezius reads as the surface
    // muscle, not a patch hidden underneath them.
    Region(
        MuscleGroup.TRAPS,
        listOf(
            points(
                45f, 24f, 55f, 24f, 59f, 36f, 56f, 49f, 50f, 60f, 44f, 49f, 41f, 36f,
            ),
        ),
    ),
    Region(
        MuscleGroup.LOWER_BACK,
        listOf(
            points(
                46f, 75f, 54f, 75f, 53f, 97f, 47f, 97f,
            ),
        ),
    ),
    Region(
        MuscleGroup.TRICEPS,
        bothSides(
            points(
                25f, 52f, 30f, 53f, 30f, 64f, 25f, 73f, 21f, 66f, 20f, 58f,
            ),
        ),
    ),
    Region(
        MuscleGroup.FOREARMS,
        bothSides(
            points(
                21f, 74f, 26f, 76f, 23f, 96f, 19f, 106f, 16f, 96f, 16f, 84f,
            ),
        ),
    ),
    Region(
        MuscleGroup.GLUTES,
        bothSides(
            points(
                36f, 107f, 46f, 104f, 50f, 115f, 46f, 128f, 37f, 129f, 33f, 117f,
            ),
        ),
    ),
    Region(
        MuscleGroup.HAMSTRINGS,
        bothSides(
            points(
                38f, 127f, 47f, 128f, 46f, 149f, 42f, 160f, 37f, 150f, 35f, 138f,
            ),
        ),
    ),
    Region(
        MuscleGroup.CALVES,
        bothSides(
            points(
                38f, 156f, 45f, 156f, 44f, 174f, 42f, 185f, 37f, 175f, 36f, 165f,
            ),
        ),
    ),
)

private fun smoothPath(shape: List<Offset>): Path {
    val path = Path()
    if (shape.size < 3) return path
    val start = midpoint(shape.last(), shape.first())
    path.moveTo(start.x, start.y)
    shape.forEachIndexed { index, point ->
        val next = shape[(index + 1) % shape.size]
        val middle = midpoint(point, next)
        path.quadraticTo(point.x, point.y, middle.x, middle.y)
    }
    path.close()
    return path
}

private fun midpoint(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

/**
 * Front and back figures shaded by logged training volume.
 *
 * This is a training summary: it shows where working sets went. It is not a
 * body-composition measurement, a recovery score or a diagnosis. Colour is backed
 * by an explicit scale, the highest band is also hatched, and the same values are
 * printed as text below the figure.
 */
@Composable
fun MuscleBodyMap(
    volumeByMuscle: Map<MuscleGroup, Double>,
    modifier: Modifier = Modifier,
    maxVolume: Double = volumeByMuscle.values.maxOrNull() ?: 0.0,
    selected: MuscleGroup? = null,
    onSelect: ((MuscleGroup?) -> Unit)? = null,
    contentDescription: String = describeMap(volumeByMuscle, maxVolume),
) {
    val tones = CadenceTheme.tones
    val outline = MaterialTheme.colorScheme.outlineVariant
    val silhouette = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    val highlightRing = MaterialTheme.colorScheme.tertiary

    fun colorFor(muscle: MuscleGroup): Color {
        val volume = volumeByMuscle[muscle] ?: 0.0
        if (volume <= 0.0 || maxVolume <= 0.0) return tones.heatEmpty
        return when (muscleBand(volume, maxVolume)) {
            1 -> tones.heatLow
            2 -> tones.heatMid
            3 -> tones.heatHigh
            else -> tones.heatPeak
        }
    }

    fun isPeak(muscle: MuscleGroup): Boolean {
        val volume = volumeByMuscle[muscle] ?: 0.0
        return volume > 0.0 && maxVolume > 0.0 && muscleBand(volume, maxVolume) == 4
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BodySideFigure(
            label = "Front",
            regions = FRONT_REGIONS,
            modifier = Modifier.weight(1f),
            trainedCount = FRONT_REGIONS.map { it.muscle }.distinct()
                .count { (volumeByMuscle[it] ?: 0.0) > 0.0 },
            colorFor = ::colorFor,
            isPeak = ::isPeak,
            selected = selected,
            silhouette = silhouette,
            outline = outline,
            highlightRing = highlightRing,
            onSelect = onSelect,
        )
        BodySideFigure(
            label = "Back",
            regions = BACK_REGIONS,
            modifier = Modifier.weight(1f),
            trainedCount = BACK_REGIONS.map { it.muscle }.distinct()
                .count { (volumeByMuscle[it] ?: 0.0) > 0.0 },
            colorFor = ::colorFor,
            isPeak = ::isPeak,
            selected = selected,
            silhouette = silhouette,
            outline = outline,
            highlightRing = highlightRing,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun BodySideFigure(
    label: String,
    regions: List<Region>,
    modifier: Modifier,
    trainedCount: Int,
    colorFor: (MuscleGroup) -> Color,
    isPeak: (MuscleGroup) -> Boolean,
    selected: MuscleGroup?,
    silhouette: Color,
    outline: Color,
    highlightRing: Color,
    onSelect: ((MuscleGroup?) -> Unit)?,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "$trainedCount/${regions.map { it.muscle }.distinct().size} trained",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(FIGURE_W / FIGURE_H)
                .then(
                    if (onSelect != null) {
                        Modifier.pointerInput(regions.size) {
                            detectTapGestures { tap ->
                                val box = fitBox(size.width.toFloat(), size.height.toFloat())
                                val local = Offset(
                                    (tap.x - box.left) / box.scale,
                                    (tap.y - box.top) / box.scale,
                                )
                                val hit = regions.firstOrNull { region ->
                                    region.shapes.any { shape -> contains(shape, local) }
                                }
                                onSelect(if (hit == null || hit.muscle == selected) null else hit.muscle)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val box = fitBox(size.width, size.height)
            drawAura(highlightRing)
            withTransform(
                {
                    translate(box.left, box.top)
                    scale(box.scale, box.scale, pivot = Offset.Zero)
                },
            ) {
                drawAnatomyBase(silhouette, outline)
                regions.forEach { region ->
                    val fill = colorFor(region.muscle)
                    val hatched = isPeak(region.muscle)
                    region.shapes.forEach { shape ->
                        val path = smoothPath(shape)
                        val top = lerp(fill, Color.White, 0.18f)
                        val bottom = lerp(fill, Color.Black, 0.10f)
                        val bounds = shape.fold(Float.MAX_VALUE to -Float.MAX_VALUE) { acc, point ->
                            minOf(acc.first, point.y) to maxOf(acc.second, point.y)
                        }
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(top, bottom),
                                start = Offset(0f, bounds.first),
                                end = Offset(0f, bounds.second),
                            ),
                        )
                        drawPath(
                            path = path,
                            color = outline.copy(alpha = 0.38f),
                            style = Stroke(width = 0.45f),
                        )
                        if (hatched) {
                            clipPath(path) {
                                var offset = -FIGURE_H
                                while (offset < FIGURE_W + FIGURE_H) {
                                    drawLine(
                                        color = outline.copy(alpha = 0.55f),
                                        start = Offset(offset, 0f),
                                        end = Offset(offset + FIGURE_H, FIGURE_H),
                                        strokeWidth = 0.8f,
                                    )
                                    offset += 5.5f
                                }
                            }
                        }
                        if (region.muscle == selected) {
                            drawPath(path, highlightRing.copy(alpha = 0.18f), style = Stroke(width = 6f))
                            drawPath(path, highlightRing, style = Stroke(width = 1.8f))
                            clipPath(path) {
                                var y = bounds.first + 4f
                                while (y < bounds.second) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.16f),
                                        start = Offset(0f, y),
                                        end = Offset(FIGURE_W, y + 5f),
                                        strokeWidth = 0.6f,
                                    )
                                    y += 6.5f
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Soft studio lighting behind each figure, plus a grounded shadow. */
private fun DrawScope.drawAura(glow: Color) {
    val center = Offset(size.width / 2f, size.height * 0.34f)
    val radius = size.minDimension * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.14f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    drawOval(
        color = Color.Black.copy(alpha = 0.06f),
        topLeft = Offset(size.width * 0.24f, size.height * 0.955f),
        size = Size(size.width * 0.52f, size.height * 0.022f),
    )
}

private data class FitBox(val left: Float, val top: Float, val scale: Float)

private fun fitBox(width: Float, height: Float): FitBox {
    val scale = minOf(width / FIGURE_W, height / FIGURE_H)
    return FitBox(
        left = (width - FIGURE_W * scale) / 2f,
        top = (height - FIGURE_H * scale) / 2f,
        scale = scale,
    )
}

private fun contains(shape: List<Offset>, point: Offset): Boolean {
    var inside = false
    var j = shape.lastIndex
    for (i in shape.indices) {
        val a = shape[i]
        val b = shape[j]
        if ((a.y > point.y) != (b.y > point.y)) {
            val x = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            if (point.x < x) inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * A neutral body outline beneath the muscle shapes. Limbs are rounded strokes so
 * the figure reads as a body rather than a collection of blobs.
 */
private fun DrawScope.drawAnatomyBase(fill: Color, outline: Color) {
    // Wide at the shoulders, tapered at the waist, so the figure reads as a
    // trained physique rather than a neutral mannequin.
    val torso = smoothPath(
        points(
            45f, 25f, 55f, 25f, 62f, 29f, 69f, 35f, 71f, 43f, 66f, 52f,
            60f, 63f, 58f, 78f, 57f, 94f, 60f, 106f, 55f, 117f, 50f, 121f,
            45f, 117f, 40f, 106f, 43f, 94f, 42f, 78f, 40f, 63f, 34f, 52f,
            29f, 43f, 31f, 35f, 38f, 29f,
        ),
    )
    drawPath(
        torso,
        Brush.verticalGradient(
            colors = listOf(lerp(fill, Color.White, 0.10f), lerp(fill, Color.Black, 0.06f)),
            startY = 20f,
            endY = FIGURE_H,
        ),
    )
    drawPath(torso, outline, style = Stroke(width = 0.8f))
    // One centred seam works on both sides: linea alba in front, spine behind.
    drawLine(
        color = outline.copy(alpha = 0.30f),
        start = Offset(50f, 32f),
        end = Offset(50f, 100f),
        strokeWidth = 0.45f,
    )
    // Soft creases keep the limbs from reading as plain capsules.
    drawLine(outline.copy(alpha = 0.22f), Offset(23f, 70f), Offset(29f, 74f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.22f), Offset(71f, 74f), Offset(77f, 70f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.20f), Offset(37f, 148f), Offset(47f, 149f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.20f), Offset(53f, 149f), Offset(63f, 148f), strokeWidth = 0.4f)
    // Clavicle hollow and the line under each pectoral.
    drawLine(outline.copy(alpha = 0.18f), Offset(38f, 40f), Offset(48f, 41f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.18f), Offset(52f, 41f), Offset(62f, 40f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.18f), Offset(37f, 57f), Offset(49f, 55f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.18f), Offset(51f, 55f), Offset(63f, 57f), strokeWidth = 0.4f)

    // Upper arms, forearms and hands.
    drawCapsule(Offset(33f, 40f), Offset(26f, 70f), 9.0f, fill, outline)
    drawCapsule(Offset(26f, 70f), Offset(19f, 104f), 7.0f, fill, outline)
    drawCapsule(Offset(67f, 40f), Offset(74f, 70f), 9.0f, fill, outline)
    drawCapsule(Offset(74f, 70f), Offset(81f, 104f), 7.0f, fill, outline)
    drawCircle(fill, radius = 3.8f, center = Offset(18f, 108f))
    drawCircle(fill, radius = 3.8f, center = Offset(82f, 108f))

    // Thighs, calves and feet.
    drawCapsule(Offset(45f, 118f), Offset(43f, 148f), 9.5f, fill, outline)
    drawCapsule(Offset(43f, 148f), Offset(40f, 180f), 7.0f, fill, outline)
    drawCapsule(Offset(55f, 118f), Offset(57f, 148f), 9.5f, fill, outline)
    drawCapsule(Offset(57f, 148f), Offset(60f, 180f), 7.0f, fill, outline)
    drawCapsule(Offset(40f, 180f), Offset(36f, 192f), 4.4f, fill, outline)
    drawCapsule(Offset(60f, 180f), Offset(64f, 192f), 4.4f, fill, outline)

    // Neck and head.
    val neck = smoothPath(points(45f, 22f, 55f, 22f, 56f, 30f, 44f, 30f))
    drawPath(neck, fill)
    drawOval(
        color = fill,
        topLeft = Offset(41.5f, 5f),
        size = Size(17f, 20f),
    )
    drawOval(
        color = outline,
        topLeft = Offset(41.5f, 5f),
        size = Size(17f, 20f),
        style = Stroke(width = 0.8f),
    )
}

private fun DrawScope.drawCapsule(
    start: Offset,
    end: Offset,
    width: Float,
    fill: Color,
    outline: Color,
) {
    drawLine(fill, start, end, strokeWidth = width, cap = StrokeCap.Round)
    drawLine(outline, start, end, strokeWidth = width + 0.8f, cap = StrokeCap.Round, alpha = 0.5f)
    drawLine(fill, start, end, strokeWidth = width - 0.8f, cap = StrokeCap.Round)
}

/**
 * Four intensity bands plus an empty band. Exposed for tests so the boundary
 * values that drive the colours and the legend cannot drift apart.
 */
internal fun muscleBand(volume: Double, maxVolume: Double): Int {
    if (volume <= 0.0 || maxVolume <= 0.0) return 0
    val fraction = (volume / maxVolume).coerceIn(0.0, 1.0)
    return when {
        fraction < 0.25 -> 1
        fraction < 0.50 -> 2
        fraction < 0.75 -> 3
        else -> 4
    }
}

private fun bandLabel(band: Int): String = when (band) {
    0 -> "none"
    1 -> "low"
    2 -> "moderate"
    3 -> "high"
    else -> "most"
}

/** Screen-reader summary of the same information the colours encode. */
private fun describeMap(volumeByMuscle: Map<MuscleGroup, Double>, maxVolume: Double): String {
    val trained = volumeByMuscle.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }
        .map { (muscle, volume) -> "${muscle.displayName}: ${bandLabel(muscleBand(volume, maxVolume))}" }
    val body = if (trained.isEmpty()) {
        "No working sets in this window."
    } else {
        "Training volume by muscle: ${trained.joinToString(", ")}."
    }
    return "Front and back muscle map. $body This shows logged training volume, not body composition."
}

/** A continuous scale reads faster than five disconnected swatches. */
@Composable
fun MuscleMapLegend(
    modifier: Modifier = Modifier,
    title: String = "Share of logged volume",
) {
    val tones = CadenceTheme.tones
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Striped = most",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            tones.heatEmpty,
                            tones.heatLow,
                            tones.heatMid,
                            tones.heatHigh,
                            tones.heatPeak,
                        ),
                    ),
                ),
        )
        Spacer(Modifier.size(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("None", "Low", "Moderate", "High", "Most").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "Each muscle is shaded against the most-trained group in this window. " +
                "The top band is also striped, so intensity is never colour-only. " +
                "Tap a muscle for its numbers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Structural check used by the body map test: every drawn muscle is real. */
internal fun bodyMapMusclesFor(side: BodySide): List<MuscleGroup> =
    when (side) {
        BodySide.FRONT -> FRONT_REGIONS.map { it.muscle }
        BodySide.BACK -> BACK_REGIONS.map { it.muscle }
    }
