package com.cadence.fit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cadence.fit.ui.theme.CadenceTheme

/**
 * Charts are drawn directly with Canvas: no charting dependency, and the styling
 * follows the app's own tokens.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    highlightIndex: Int? = null,
    height: Dp = 140.dp,
    onSelect: ((Int) -> Unit)? = null,
) {
    if (values.isEmpty()) return
    val maxValue = values.max().takeIf { it > 0f } ?: 1f
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val highlight = CadenceTheme.tones.heatHigh

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    if (onSelect != null) {
                        Modifier.pointerInput(values.size) {
                            detectTapGestures { offset ->
                                val slot = size.width.toFloat() / values.size
                                val index = (offset.x / slot).toInt().coerceIn(0, values.size - 1)
                                onSelect(index)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val slot = size.width / values.size
            val barWidth = (slot * 0.62f).coerceAtLeast(4f)
            val baseline = size.height
            drawLine(
                color = outline,
                start = Offset(0f, baseline - 1f),
                end = Offset(size.width, baseline - 1f),
                strokeWidth = 2f,
            )
            values.forEachIndexed { index, value ->
                val ratio = (value / maxValue).coerceIn(0f, 1f)
                val barHeight = (size.height - 6f) * ratio
                val left = slot * index + (slot - barWidth) / 2f
                val isHighlighted = highlightIndex == index
                drawRoundRect(
                    color = when {
                        isHighlighted -> highlight
                        value <= 0f -> muted
                        else -> accent
                    },
                    topLeft = Offset(left, baseline - barHeight - 1f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2.4f),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            thinnedLabels(labels).forEachIndexed { index, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = when {
                        labels.size <= 4 -> TextAlign.Center
                        index == 0 -> TextAlign.Start
                        index == thinnedLabels(labels).lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Keeps at most five labels so the axis stays readable on a small screen; the
 * first and last are always kept.
 */
private fun thinnedLabels(labels: List<String>): List<String> {
    if (labels.size <= 5) return labels
    val step = (labels.size - 1) / 4.0
    return (0 until 5).map { position ->
        val index = (position * step).toInt().coerceIn(0, labels.lastIndex)
        labels[index]
    }
}

@Composable
fun LineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    highlightIndex: Int? = null,
    height: Dp = 160.dp,
    targetLine: Float? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    if (values.size < 2) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Not enough sessions yet to draw a trend",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 550),
        label = "lineChartProgress",
    )
    val minValue = values.min()
    val maxValue = values.max()
    val span = (maxValue - minValue).takeIf { it > 0.01f } ?: 1f
    val grid = MaterialTheme.colorScheme.outlineVariant
    val pointColor = CadenceTheme.tones.heatHigh
    // Read theme colours here: the Canvas draw lambda is not @Composable.
    val targetLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val pointFill = MaterialTheme.colorScheme.surface

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    if (onSelect != null) {
                        Modifier.pointerInput(values.size) {
                            detectTapGestures { offset ->
                                val step = if (values.size > 1) size.width.toFloat() / (values.size - 1) else 0f
                                val index = if (step <= 0f) {
                                    0
                                } else {
                                    ((offset.x - step * 0.5f) / step).toInt().coerceIn(0, values.size - 1)
                                }
                                onSelect(index)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val topPadding = 14f
            val bottomPadding = 14f
            val usable = size.height - topPadding - bottomPadding
            fun yOf(value: Float): Float =
                topPadding + usable * (1f - ((value - minValue) / span))

            drawLine(
                color = grid,
                start = Offset(0f, size.height - bottomPadding),
                end = Offset(size.width, size.height - bottomPadding),
                strokeWidth = 2f,
            )

            targetLine?.let { target ->
                val y = yOf(target)
                drawLine(
                    color = targetLineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }

            val step = size.width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = step * index
                val y = yOf(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 4f, cap = StrokeCap.Round),
            )

            values.forEachIndexed { index, value ->
                val x = step * index
                val y = yOf(value)
                val isHighlighted = highlightIndex == index
                drawCircle(
                    color = if (isHighlighted) pointColor else pointFill,
                    radius = if (isHighlighted) 8f else 5f,
                    center = Offset(x, y),
                )
                drawCircle(
                    color = accent,
                    radius = if (isHighlighted) 8f else 5f,
                    center = Offset(x, y),
                    style = Stroke(width = 3f),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val thinned = thinnedLabels(labels)
            thinned.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Compact trend line for the home screen. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 40.dp,
) {
    if (values.size < 2) {
        Spacer(modifier.height(height))
        return
    }
    val minValue = values.min()
    val maxValue = values.max()
    val span = (maxValue - minValue).takeIf { it > 0.01f } ?: 1f
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = step * index
            val y = size.height * (1f - ((value - minValue) / span))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

/** Legend explaining a heat scale, used by the body map. */
@Composable
fun HeatLegend(
    stops: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stops.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .padding(end = 0.dp)
                        .then(Modifier),
                ) {
                    Canvas(Modifier.size(12.dp)) { drawCircle(color) }
                }
                Spacer(Modifier.size(5.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
