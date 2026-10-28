package com.cadence.fit.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cadence.fit.domain.model.BodySide
import com.cadence.fit.domain.model.MuscleGroup
import com.cadence.fit.ui.theme.CadenceTheme

private const val FIGURE_W = 100f
private const val FIGURE_H = 200f

private sealed interface Shape {
    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : Shape

    data class Poly(val points: List<Offset>) : Shape
}

private data class MuscleRegion(val muscle: MuscleGroup, val shapes: List<Shape>)

private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): Shape = Shape.Ellipse(cx, cy, rx, ry)

private fun poly(vararg coordinates: Float): Shape = Shape.Poly(
    coordinates.toList().chunked(2).map { (x, y) -> Offset(x, y) },
)

private val FRONT_REGIONS = listOf(
    MuscleRegion(
        MuscleGroup.SHOULDERS,
        listOf(ellipse(31f, 54f, 11f, 9f), ellipse(69f, 54f, 11f, 9f)),
    ),
    MuscleRegion(
        MuscleGroup.CHEST,
        listOf(ellipse(42f, 64f, 10.5f, 9f), ellipse(58f, 64f, 10.5f, 9f)),
    ),
    MuscleRegion(
        MuscleGroup.BICEPS,
        listOf(ellipse(22f, 80f, 7f, 11f), ellipse(78f, 80f, 7f, 11f)),
    ),
    MuscleRegion(
        MuscleGroup.FOREARMS,
        listOf(ellipse(16f, 106f, 6f, 13f), ellipse(84f, 106f, 6f, 13f)),
    ),
    MuscleRegion(
        MuscleGroup.ABS,
        listOf(poly(45f, 84f, 55f, 84f, 55f, 116f, 45f, 116f)),
    ),
    MuscleRegion(
        MuscleGroup.OBLIQUES,
        listOf(
            poly(37f, 86f, 45f, 86f, 45f, 114f, 39f, 110f),
            poly(55f, 86f, 63f, 86f, 61f, 110f, 55f, 114f),
        ),
    ),
    MuscleRegion(
        MuscleGroup.QUADS,
        listOf(ellipse(42f, 148f, 8.5f, 22f), ellipse(58f, 148f, 8.5f, 22f)),
    ),
)

private val BACK_REGIONS = listOf(
    MuscleRegion(
        MuscleGroup.TRAPS,
        listOf(poly(50f, 40f, 62f, 52f, 50f, 66f, 38f, 52f)),
    ),
    MuscleRegion(
        MuscleGroup.SHOULDERS,
        listOf(ellipse(30f, 57f, 11f, 10f), ellipse(70f, 57f, 11f, 10f)),
    ),
    MuscleRegion(
        MuscleGroup.TRICEPS,
        listOf(ellipse(21f, 82f, 7f, 11f), ellipse(79f, 82f, 7f, 11f)),
    ),
    MuscleRegion(
        MuscleGroup.FOREARMS,
        listOf(ellipse(15f, 108f, 6f, 13f), ellipse(85f, 108f, 6f, 13f)),
    ),
    MuscleRegion(
        MuscleGroup.UPPER_BACK,
        listOf(poly(42f, 60f, 58f, 60f, 56f, 84f, 44f, 84f)),
    ),
    MuscleRegion(
        MuscleGroup.LATS,
        listOf(
            poly(34f, 66f, 42f, 64f, 44f, 92f, 36f, 88f),
            poly(58f, 64f, 66f, 66f, 64f, 88f, 56f, 92f),
        ),
    ),
    MuscleRegion(
        MuscleGroup.LOWER_BACK,
        listOf(poly(45f, 88f, 55f, 88f, 54f, 112f, 46f, 112f)),
    ),
    MuscleRegion(
        MuscleGroup.GLUTES,
        listOf(ellipse(42f, 128f, 9.5f, 12f), ellipse(58f, 128f, 9.5f, 12f)),
    ),
    MuscleRegion(
        MuscleGroup.HAMSTRINGS,
        listOf(ellipse(42f, 158f, 8f, 19f), ellipse(58f, 158f, 8f, 19f)),
    ),
    MuscleRegion(
        MuscleGroup.CALVES,
        listOf(ellipse(41f, 186f, 7f, 11f), ellipse(59f, 186f, 7f, 11f)),
    ),
)

/**
 * Front and back figures shaded by training volume.
 *
 * The map is a *training summary*: it shows which muscle groups your logged
 * volume went to. It is not a body-composition measurement, and it makes no
 * claim about how anyone looks or about injury.
 */
@Composable
fun MuscleBodyMap(
    volumeByMuscle: Map<MuscleGroup, Double>,
    modifier: Modifier = Modifier,
    maxVolume: Double = volumeByMuscle.values.maxOrNull() ?: 0.0,
    selected: MuscleGroup? = null,
    onSelect: ((MuscleGroup?) -> Unit)? = null,
    contentDescription: String = "Front and back muscle map shaded by training volume",
) {
    val tones = CadenceTheme.tones
    val outline = MaterialTheme.colorScheme.outlineVariant
    val silhouette = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val highlightRing = MaterialTheme.colorScheme.tertiary

    fun colorFor(muscle: MuscleGroup): Color {
        val volume = volumeByMuscle[muscle] ?: 0.0
        if (volume <= 0.0 || maxVolume <= 0.0) return tones.heatEmpty
        val fraction = (volume / maxVolume).coerceIn(0.0, 1.0)
        return when {
            fraction < 0.25 -> tones.heatLow
            fraction < 0.55 -> tones.heatMid
            fraction < 0.85 -> tones.heatHigh
            else -> tones.heatPeak
        }
    }

    val describe = Modifier.semantics { this.contentDescription = contentDescription }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BodySideFigure(
            label = "Front",
            regions = FRONT_REGIONS,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(FIGURE_W / FIGURE_H)
                .then(describe),
            colorFor = ::colorFor,
            selected = selected,
            silhouette = silhouette,
            outline = outline,
            highlightRing = highlightRing,
            onSelect = onSelect,
        )
        BodySideFigure(
            label = "Back",
            regions = BACK_REGIONS,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(FIGURE_W / FIGURE_H),
            colorFor = ::colorFor,
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
    regions: List<MuscleRegion>,
    modifier: Modifier,
    colorFor: (MuscleGroup) -> Color,
    selected: MuscleGroup?,
    silhouette: Color,
    outline: Color,
    highlightRing: Color,
    onSelect: ((MuscleGroup?) -> Unit)?,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(FIGURE_W / FIGURE_H)
                .then(
                    if (onSelect != null) {
                        Modifier.pointerInput(regions.size) {
                            detectTapGestures { tap ->
                                val box = fitBox(size.width.toFloat(), size.height.toFloat())
                                val local = Offset((tap.x - box.left) / box.scale, (tap.y - box.top) / box.scale)
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
            withTransform(
                {
                    translate(box.left, box.top)
                    scale(box.scale, box.scale, pivot = Offset.Zero)
                },
            ) {
                drawSilhouette(silhouette)
                regions.forEach { region ->
                    val fill = colorFor(region.muscle)
                    val isSelected = region.muscle == selected
                    region.shapes.forEach { shape ->
                        when (shape) {
                            is Shape.Ellipse -> drawOval(
                                color = fill,
                                topLeft = Offset(shape.cx - shape.rx, shape.cy - shape.ry),
                                size = Size(shape.rx * 2, shape.ry * 2),
                            )

                            is Shape.Poly -> drawPath(pathOf(shape), fill)
                        }
                    }
                    if (isSelected) {
                        region.shapes.forEach { shape ->
                            when (shape) {
                                is Shape.Ellipse -> drawOval(
                                    color = highlightRing,
                                    topLeft = Offset(shape.cx - shape.rx - 1f, shape.cy - shape.ry - 1f),
                                    size = Size(shape.rx * 2 + 2f, shape.ry * 2 + 2f),
                                    style = Stroke(width = 2.4f),
                                )

                                is Shape.Poly -> drawPath(pathOf(shape), highlightRing, style = Stroke(width = 2.4f))
                            }
                        }
                    }
                }
            }
        }
    }
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

private fun contains(shape: Shape, point: Offset): Boolean = when (shape) {
    is Shape.Ellipse -> {
        val dx = (point.x - shape.cx) / shape.rx
        val dy = (point.y - shape.cy) / shape.ry
        dx * dx + dy * dy <= 1f
    }

    // Ray casting: a point is inside when a ray crossing count is odd. Compose's
    // Path has no reliable point test across API levels, so this is explicit.
    is Shape.Poly -> {
        var inside = false
        val points = shape.points
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > point.y) != (b.y > point.y)) {
                val x = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
                if (point.x < x) inside = !inside
            }
            j = i
        }
        inside
    }
}

private fun pathOf(shape: Shape.Poly): Path = Path().apply {
    shape.points.forEachIndexed { index, point ->
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun DrawScope.drawSilhouette(color: Color) {
    val limb = 5.5f
    drawCircle(color, radius = 13f, center = Offset(50f, 24f))
    // torso
    drawPath(
        Path().apply {
            moveTo(34f, 52f)
            lineTo(66f, 52f)
            lineTo(60f, 126f)
            lineTo(40f, 126f)
            close()
        },
        color,
    )
    // arms
    drawLine(color, Offset(33f, 55f), Offset(21f, 84f), limb, StrokeCap.Round)
    drawLine(color, Offset(21f, 84f), Offset(15f, 112f), limb, StrokeCap.Round)
    drawLine(color, Offset(67f, 55f), Offset(79f, 84f), limb, StrokeCap.Round)
    drawLine(color, Offset(79f, 84f), Offset(85f, 112f), limb, StrokeCap.Round)
    // legs
    drawLine(color, Offset(44f, 126f), Offset(42f, 160f), limb + 1f, StrokeCap.Round)
    drawLine(color, Offset(42f, 160f), Offset(41f, 196f), limb + 1f, StrokeCap.Round)
    drawLine(color, Offset(56f, 126f), Offset(58f, 160f), limb + 1f, StrokeCap.Round)
    drawLine(color, Offset(58f, 160f), Offset(59f, 196f), limb + 1f, StrokeCap.Round)
}

/** The four-step colour legend shown under the map. */
@Composable
fun MuscleMapLegend(modifier: Modifier = Modifier) {
    val tones = CadenceTheme.tones
    Column(modifier) {
        Text(
            "What the colours mean",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendSwatch("None", tones.heatEmpty)
            LegendSwatch("Low", tones.heatLow)
            LegendSwatch("Moderate", tones.heatMid)
            LegendSwatch("High", tones.heatHigh)
            LegendSwatch("Most", tones.heatPeak)
        }
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) {
            drawRoundRect(color, cornerRadius = CornerRadius(4f, 4f))
        }
        Spacer(Modifier.size(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
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
