package com.cadence.fit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every icon in the app is drawn here as simple geometry. Nothing is copied from
 * an icon pack, so the app ships no third-party artwork.
 */
enum class CadenceIcon {
    Home,
    Plan,
    Progress,
    Body,
    Settings,
    Sleep,
    Dumbbell,
    Plus,
    Minus,
    Check,
    Undo,
    Back,
    ChevronRight,
    Search,
    Edit,
    Trash,
    Close,
    Info,
    Share,
    Timer,
    Trophy,
    TrendUp,
    Calendar,
    Book,
    Warning,
}

@Composable
fun CadenceIcon(
    icon: CadenceIcon,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    strokeWidth: Dp = 1.8.dp,
) {
    val described = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Canvas(described.size(size)) {
        val unit = this.size.minDimension / 24f
        val width = strokeWidth.toPx()
        when (icon) {
            CadenceIcon.Home -> drawHome(unit, tint, width)
            CadenceIcon.Plan -> drawPlan(unit, tint, width)
            CadenceIcon.Progress -> drawProgress(unit, tint, width)
            CadenceIcon.Body -> drawBody(unit, tint, width)
            CadenceIcon.Settings -> drawSettings(unit, tint, width)
            CadenceIcon.Sleep -> drawSleep(unit, tint, width)
            CadenceIcon.Dumbbell -> drawDumbbell(unit, tint, width)
            CadenceIcon.Plus -> drawCross(unit, tint, width)
            CadenceIcon.Minus -> lineAt(unit, 6f, 12f, 18f, 12f, tint, width)
            CadenceIcon.Check -> drawCheck(unit, tint, width)
            CadenceIcon.Undo -> drawUndo(unit, tint, width)
            CadenceIcon.Back -> drawChevron(unit, tint, width, pointingLeft = true)
            CadenceIcon.ChevronRight -> drawChevron(unit, tint, width, pointingLeft = false)
            CadenceIcon.Search -> drawSearch(unit, tint, width)
            CadenceIcon.Edit -> drawEdit(unit, tint, width)
            CadenceIcon.Trash -> drawTrash(unit, tint, width)
            CadenceIcon.Close -> drawClose(unit, tint, width)
            CadenceIcon.Info -> drawInfo(unit, tint, width)
            CadenceIcon.Share -> drawShare(unit, tint, width)
            CadenceIcon.Timer -> drawTimer(unit, tint, width)
            CadenceIcon.Trophy -> drawTrophy(unit, tint, width)
            CadenceIcon.TrendUp -> drawTrend(unit, tint, width)
            CadenceIcon.Calendar -> drawCalendar(unit, tint, width)
            CadenceIcon.Book -> drawBook(unit, tint, width)
            CadenceIcon.Warning -> drawWarning(unit, tint, width)
        }
    }
}

private fun DrawScope.strokeStyle(unit: Float, width: Float) =
    Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.lineAt(
    unit: Float,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    color: Color,
    width: Float,
) = line(x1 * unit, y1 * unit, x2 * unit, y2 * unit, color, width)

private fun DrawScope.drawHome(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    val roof = Path().apply {
        moveTo(3f * unit, 10.5f * unit)
        lineTo(12f * unit, 3.5f * unit)
        lineTo(21f * unit, 10.5f * unit)
    }
    drawPath(roof, color, style = stroke)
    drawRoundRect(
        color = color,
        topLeft = Offset(6f * unit, 10f * unit),
        size = androidx.compose.ui.geometry.Size(12f * unit, 10.5f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.6f * unit, 1.6f * unit),
        style = stroke,
    )
}

private fun DrawScope.drawPlan(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    // A barbell seen end-on: bar with two plates.
    line(12f * unit, 6f * unit, 12f * unit, 18f * unit, color, width)
    line(6.5f * unit, 9f * unit, 6.5f * unit, 15f * unit, color, width)
    line(17.5f * unit, 9f * unit, 17.5f * unit, 15f * unit, color, width)
    drawRoundRect(
        color = color,
        topLeft = Offset(3.5f * unit, 10.5f * unit),
        size = androidx.compose.ui.geometry.Size(3f * unit, 3f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * unit, 1f * unit),
        style = stroke,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(17.5f * unit, 10.5f * unit),
        size = androidx.compose.ui.geometry.Size(3f * unit, 3f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * unit, 1f * unit),
        style = stroke,
    )
}

private fun DrawScope.drawProgress(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    val bars = listOf(5f to 14f, 12f to 9f, 19f to 4f)
    bars.forEach { (x, top) ->
        line(x * unit, top * unit, x * unit, 20f * unit, color, width * 1.6f)
    }
    drawPath(
        Path().apply {
            moveTo(4f * unit, 12f * unit)
            lineTo(12f * unit, 7f * unit)
            lineTo(20f * unit, 2.5f * unit)
        },
        color,
        style = stroke,
    )
}

private fun DrawScope.drawBody(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    drawCircle(color, radius = 2.6f * unit, center = Offset(12f * unit, 4.6f * unit))
    line(12f * unit, 7.4f * unit, 12f * unit, 15f * unit, color, width)
    line(6.5f * unit, 9.5f * unit, 17.5f * unit, 9.5f * unit, color, width)
    line(12f * unit, 15f * unit, 8.5f * unit, 21f * unit, color, width)
    line(12f * unit, 15f * unit, 15.5f * unit, 21f * unit, color, width)
}

private fun DrawScope.drawSettings(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    drawCircle(color, radius = 3.4f * unit, center = Offset(12f * unit, 12f * unit), style = stroke)
    repeat(6) { index ->
        val angle = Math.toRadians((index * 60).toDouble())
        val dx = kotlin.math.cos(angle).toFloat()
        val dy = kotlin.math.sin(angle).toFloat()
        line(
            (12f + dx * 6.4f) * unit,
            (12f + dy * 6.4f) * unit,
            (12f + dx * 8.6f) * unit,
            (12f + dy * 8.6f) * unit,
            color,
            width,
        )
    }
}

private fun DrawScope.drawSleep(unit: Float, color: Color, width: Float) {
    val crescent = Path().apply {
        moveTo(20f * unit, 14.5f * unit)
        cubicTo(
            15f * unit, 18f * unit,
            8f * unit, 15f * unit,
            8.5f * unit, 9f * unit,
        )
        cubicTo(
            9f * unit, 5.5f * unit,
            12f * unit, 3.5f * unit,
            15f * unit, 3.5f * unit,
        )
        cubicTo(
            11.5f * unit, 6f * unit,
            11f * unit, 11.5f * unit,
            20f * unit, 14.5f * unit,
        )
        close()
    }
    drawPath(crescent, color, style = strokeStyle(unit, width))
}

private fun DrawScope.drawDumbbell(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    line(8f * unit, 12f * unit, 16f * unit, 12f * unit, color, width)
    drawRoundRect(
        color = color,
        topLeft = Offset(4f * unit, 8.5f * unit),
        size = androidx.compose.ui.geometry.Size(4f * unit, 7f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * unit, 1.4f * unit),
        style = stroke,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(16f * unit, 8.5f * unit),
        size = androidx.compose.ui.geometry.Size(4f * unit, 7f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * unit, 1.4f * unit),
        style = stroke,
    )
}

private fun DrawScope.drawCross(unit: Float, color: Color, width: Float) {
    line(12f * unit, 5f * unit, 12f * unit, 19f * unit, color, width)
    line(5f * unit, 12f * unit, 19f * unit, 12f * unit, color, width)
}

private fun DrawScope.drawCheck(unit: Float, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(5f * unit, 12.5f * unit)
        lineTo(10f * unit, 17.5f * unit)
        lineTo(19f * unit, 6.5f * unit)
    }
    drawPath(path, color, style = strokeStyle(unit, width))
}

private fun DrawScope.drawUndo(unit: Float, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(5f * unit, 11f * unit)
        cubicTo(8f * unit, 6f * unit, 16f * unit, 6f * unit, 19f * unit, 12f * unit)
        cubicTo(20.5f * unit, 15f * unit, 18f * unit, 18.5f * unit, 14.5f * unit, 18.5f * unit)
    }
    drawPath(path, color, style = strokeStyle(unit, width))
    line(5f * unit, 11f * unit, 5f * unit, 5f * unit, color, width)
    line(5f * unit, 11f * unit, 11f * unit, 11f * unit, color, width)
}

private fun DrawScope.drawChevron(unit: Float, color: Color, width: Float, pointingLeft: Boolean) {
    val path = Path().apply {
        if (pointingLeft) {
            moveTo(14.5f * unit, 5f * unit)
            lineTo(8.5f * unit, 12f * unit)
            lineTo(14.5f * unit, 19f * unit)
        } else {
            moveTo(9.5f * unit, 5f * unit)
            lineTo(15.5f * unit, 12f * unit)
            lineTo(9.5f * unit, 19f * unit)
        }
    }
    drawPath(path, color, style = strokeStyle(unit, width))
}

private fun DrawScope.drawSearch(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    drawCircle(color, radius = 5.4f * unit, center = Offset(10.5f * unit, 10.5f * unit), style = stroke)
    line(14.6f * unit, 14.6f * unit, 19.5f * unit, 19.5f * unit, color, width)
}

private fun DrawScope.drawEdit(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    line(5f * unit, 19f * unit, 5f * unit, 16f * unit, color, width)
    line(5f * unit, 16f * unit, 16f * unit, 5f * unit, color, width)
    line(16f * unit, 5f * unit, 19f * unit, 8f * unit, color, width)
    line(19f * unit, 8f * unit, 8f * unit, 19f * unit, color, width)
    line(5f * unit, 19f * unit, 8f * unit, 19f * unit, color, width)
    drawPath(Path().apply { moveTo(5f * unit, 19f * unit); lineTo(5f * unit, 16f * unit); lineTo(8f * unit, 19f * unit); close() }, color, style = stroke)
}

private fun DrawScope.drawTrash(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    line(4.5f * unit, 7f * unit, 19.5f * unit, 7f * unit, color, width)
    drawRoundRect(
        color = color,
        topLeft = Offset(7f * unit, 7f * unit),
        size = androidx.compose.ui.geometry.Size(10f * unit, 13f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.6f * unit, 1.6f * unit),
        style = stroke,
    )
    line(10f * unit, 4.5f * unit, 14f * unit, 4.5f * unit, color, width)
    line(10.5f * unit, 10.5f * unit, 10.5f * unit, 16.5f * unit, color, width * 0.7f)
    line(13.5f * unit, 10.5f * unit, 13.5f * unit, 16.5f * unit, color, width * 0.7f)
}

private fun DrawScope.drawClose(unit: Float, color: Color, width: Float) {
    line(6f * unit, 6f * unit, 18f * unit, 18f * unit, color, width)
    line(18f * unit, 6f * unit, 6f * unit, 18f * unit, color, width)
}

private fun DrawScope.drawInfo(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    drawCircle(color, radius = 8f * unit, center = Offset(12f * unit, 12f * unit), style = stroke)
    line(12f * unit, 11f * unit, 12f * unit, 16.5f * unit, color, width)
    drawCircle(color, radius = width * 0.75f, center = Offset(12f * unit, 7.6f * unit))
}

private fun DrawScope.drawShare(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    drawCircle(color, radius = 2.4f * unit, center = Offset(7f * unit, 12f * unit), style = stroke)
    drawCircle(color, radius = 2.4f * unit, center = Offset(17f * unit, 6.5f * unit), style = stroke)
    drawCircle(color, radius = 2.4f * unit, center = Offset(17f * unit, 17.5f * unit), style = stroke)
    line(9.2f * unit, 10.8f * unit, 14.8f * unit, 7.7f * unit, color, width * 0.7f)
    line(9.2f * unit, 13.2f * unit, 14.8f * unit, 16.3f * unit, color, width * 0.7f)
}

private fun DrawScope.drawTimer(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    drawCircle(color, radius = 7.4f * unit, center = Offset(12f * unit, 13f * unit), style = stroke)
    line(12f * unit, 13f * unit, 12f * unit, 8.6f * unit, color, width)
    line(9.5f * unit, 3.4f * unit, 14.5f * unit, 3.4f * unit, color, width)
}

private fun DrawScope.drawTrophy(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    val cup = Path().apply {
        moveTo(7f * unit, 3.5f * unit)
        lineTo(17f * unit, 3.5f * unit)
        lineTo(16f * unit, 11f * unit)
        lineTo(12f * unit, 14f * unit)
        lineTo(8f * unit, 11f * unit)
        close()
    }
    drawPath(cup, color, style = stroke)
    line(7.5f * unit, 6f * unit, 4.5f * unit, 6f * unit, color, width * 0.7f)
    line(16.5f * unit, 6f * unit, 19.5f * unit, 6f * unit, color, width * 0.7f)
    line(12f * unit, 14f * unit, 12f * unit, 17.5f * unit, color, width)
    line(8.5f * unit, 17.5f * unit, 15.5f * unit, 17.5f * unit, color, width)
}

private fun DrawScope.drawTrend(unit: Float, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(4f * unit, 17f * unit)
        lineTo(10f * unit, 11f * unit)
        lineTo(14f * unit, 14.5f * unit)
        lineTo(20f * unit, 7f * unit)
    }
    drawPath(path, color, style = strokeStyle(unit, width))
    line(20f * unit, 7f * unit, 20f * unit, 11.5f * unit, color, width)
    line(20f * unit, 7f * unit, 15.5f * unit, 7f * unit, color, width)
}

private fun DrawScope.drawCalendar(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    drawRoundRect(
        color = color,
        topLeft = Offset(4f * unit, 6f * unit),
        size = androidx.compose.ui.geometry.Size(16f * unit, 14f * unit),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.4f * unit, 2.4f * unit),
        style = stroke,
    )
    line(4f * unit, 10.5f * unit, 20f * unit, 10.5f * unit, color, width)
    line(8.5f * unit, 3.8f * unit, 8.5f * unit, 7f * unit, color, width)
    line(15.5f * unit, 3.8f * unit, 15.5f * unit, 7f * unit, color, width)
}

private fun DrawScope.drawBook(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    val book = Path().apply {
        moveTo(4f * unit, 5f * unit)
        lineTo(12f * unit, 7f * unit)
        lineTo(20f * unit, 5f * unit)
        lineTo(20f * unit, 18f * unit)
        lineTo(12f * unit, 20f * unit)
        lineTo(4f * unit, 18f * unit)
        close()
    }
    drawPath(book, color, style = stroke)
    line(12f * unit, 7f * unit, 12f * unit, 20f * unit, color, width * 0.7f)
}

private fun DrawScope.drawWarning(unit: Float, color: Color, width: Float) {
    val stroke = strokeStyle(unit, width)
    val triangle = Path().apply {
        moveTo(12f * unit, 3.5f * unit)
        lineTo(21f * unit, 19.5f * unit)
        lineTo(3f * unit, 19.5f * unit)
        close()
    }
    drawPath(triangle, color, style = stroke)
    line(12f * unit, 9.5f * unit, 12f * unit, 14f * unit, color, width)
    drawCircle(color, radius = width * 0.75f, center = Offset(12f * unit, 16.8f * unit))
}
