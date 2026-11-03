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
                30f, 38f, 37f, 39f, 39f, 47f, 35f, 53f, 29f, 50f, 26f, 43f,
            ),
        ),
    ),
    Region(
        MuscleGroup.CHEST,
        bothSides(
            points(
                34f, 44f, 41f, 43f, 48f, 45f, 48f, 53f, 44f, 59f, 37f, 55f, 33f, 49f,
            ),
        ),
    ),
    Region(
        MuscleGroup.BICEPS,
        bothSides(
            points(
                26f, 57f, 30f, 58f, 29f, 69f, 25f, 75f, 22f, 69f, 22f, 62f,
            ),
        ),
    ),
    Region(
        MuscleGroup.FOREARMS,
        bothSides(
            points(
                20f, 78f, 24f, 80f, 21f, 99f, 18f, 108f, 15f, 99f, 15f, 86f,
            ),
        ),
    ),
    Region(
        MuscleGroup.ABS,
        listOf(
            points(
                45f, 59f, 55f, 59f, 56f, 78f, 54f, 97f, 50f, 103f, 46f, 97f, 44f, 78f,
            ),
        ),
    ),
    Region(
        MuscleGroup.OBLIQUES,
        bothSides(
            points(
                38f, 61f, 44f, 64f, 45f, 92f, 42f, 101f, 37f, 90f, 36f, 74f,
            ),
        ),
    ),
    Region(
        MuscleGroup.QUADS,
        bothSides(
            points(
                39f, 118f, 50f, 121f, 49f, 144f, 45f, 156f, 39f, 154f, 34f, 139f,
            ),
        ),
    ),
)

private val BACK_REGIONS = listOf(
    Region(
        MuscleGroup.SHOULDERS,
        bothSides(
            points(
                30f, 40f, 37f, 40f, 39f, 48f, 35f, 54f, 29f, 50f, 26f, 44f,
            ),
        ),
    ),
    Region(
        MuscleGroup.UPPER_BACK,
        listOf(
            points(
                43f, 45f, 57f, 45f, 58f, 61f, 54f, 72f, 46f, 72f, 42f, 61f,
            ),
        ),
    ),
    Region(
        MuscleGroup.LATS,
        bothSides(
            points(
                35f, 51f, 43f, 57f, 44f, 76f, 40f, 88f, 34f, 79f, 33f, 64f,
            ),
        ),
    ),
    // Drawn above the lats and upper back so the trapezius reads as the surface
    // muscle, not a patch hidden underneath them.
    Region(
        MuscleGroup.TRAPS,
        listOf(
            points(
                46f, 27f, 54f, 27f, 59f, 41f, 55f, 52f, 50f, 62f, 45f, 52f, 41f, 41f,
            ),
        ),
    ),
    Region(
        MuscleGroup.LOWER_BACK,
        listOf(
            points(
                46f, 78f, 54f, 78f, 53f, 100f, 47f, 100f,
            ),
        ),
    ),
    Region(
        MuscleGroup.TRICEPS,
        bothSides(
            points(
                25f, 57f, 29f, 57f, 28f, 69f, 25f, 76f, 21f, 69f, 21f, 61f,
            ),
        ),
    ),
    Region(
        MuscleGroup.FOREARMS,
        bothSides(
            points(
                20f, 78f, 24f, 80f, 21f, 99f, 18f, 108f, 15f, 99f, 15f, 86f,
            ),
        ),
    ),
    Region(
        MuscleGroup.GLUTES,
        bothSides(
            points(
                37f, 112f, 46f, 109f, 50f, 119f, 47f, 131f, 39f, 132f, 34f, 123f,
            ),
        ),
    ),
    Region(
        MuscleGroup.HAMSTRINGS,
        bothSides(
            points(
                40f, 130f, 48f, 131f, 47f, 153f, 43f, 162f, 38f, 152f, 37f, 140f,
            ),
        ),
    ),
    Region(
        MuscleGroup.CALVES,
        bothSides(
            points(
                40f, 160f, 46f, 160f, 45f, 178f, 42f, 188f, 38f, 178f, 37f, 168f,
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
    val torso = smoothPath(
        points(
            45f, 28f, 55f, 28f, 64f, 33f, 69f, 42f, 65f, 59f, 60f, 82f,
            63f, 105f, 57f, 121f, 50f, 127f, 43f, 121f, 37f, 105f, 40f, 82f,
            35f, 59f, 31f, 42f, 36f, 33f,
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
        start = Offset(50f, 34f),
        end = Offset(50f, 103f),
        strokeWidth = 0.45f,
    )
    // Soft creases keep the limbs from reading as plain capsules.
    drawLine(outline.copy(alpha = 0.22f), Offset(22f, 73f), Offset(28f, 76f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.22f), Offset(72f, 76f), Offset(78f, 73f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.20f), Offset(38f, 150f), Offset(46f, 151f), strokeWidth = 0.4f)
    drawLine(outline.copy(alpha = 0.20f), Offset(54f, 151f), Offset(62f, 150f), strokeWidth = 0.4f)

    // Upper arms, forearms and hands.
    drawCapsule(Offset(33f, 38f), Offset(25f, 70f), 8.5f, fill, outline)
    drawCapsule(Offset(25f, 70f), Offset(18f, 106f), 6.6f, fill, outline)
    drawCapsule(Offset(67f, 38f), Offset(75f, 70f), 8.5f, fill, outline)
    drawCapsule(Offset(75f, 70f), Offset(82f, 106f), 6.6f, fill, outline)
    drawCircle(fill, radius = 3.6f, center = Offset(17f, 111f))
    drawCircle(fill, radius = 3.6f, center = Offset(83f, 111f))

    // Thighs, calves and feet.
    drawCapsule(Offset(44f, 120f), Offset(42f, 152f), 8.2f, fill, outline)
    drawCapsule(Offset(42f, 152f), Offset(39f, 184f), 6.2f, fill, outline)
    drawCapsule(Offset(56f, 120f), Offset(58f, 152f), 8.2f, fill, outline)
    drawCapsule(Offset(58f, 152f), Offset(61f, 184f), 6.2f, fill, outline)
    drawCapsule(Offset(39f, 184f), Offset(35f, 195f), 4.2f, fill, outline)
    drawCapsule(Offset(61f, 184f), Offset(65f, 195f), 4.2f, fill, outline)

    // Neck and head.
    val neck = smoothPath(points(45f, 24f, 55f, 24f, 56f, 31f, 44f, 31f))
    drawPath(neck, fill)
    drawOval(
        color = fill,
        topLeft = Offset(41f, 6f),
        size = Size(18f, 21f),
    )
    drawOval(
        color = outline,
        topLeft = Offset(41f, 6f),
        size = Size(18f, 21f),
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
