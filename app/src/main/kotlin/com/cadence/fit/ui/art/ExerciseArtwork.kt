package com.cadence.fit.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cadence.fit.domain.model.MovementPattern

/**
 * Movement illustrations, drawn from scratch as simple stick-and-shape figures.
 *
 * They exist to show *how the body moves*, not to be anatomical diagrams, so each
 * one is a clean posture plus a soft highlight over the region the movement
 * works. No third-party artwork is bundled anywhere in this app.
 */
private const val FIGURE_WIDTH = 100f
private const val FIGURE_HEIGHT = 140f

private data class Pose(
    val head: Offset,
    val shoulder: Offset,
    val hip: Offset,
    val elbow: Offset,
    val hand: Offset,
    val elbow2: Offset,
    val hand2: Offset,
    val knee: Offset,
    val foot: Offset,
    val knee2: Offset,
    val foot2: Offset,
    /** Where the soft highlight sits, in figure coordinates. */
    val region: Offset,
    val regionRadius: Float,
    val equipment: Equipment = Equipment.None,
)

private enum class Equipment { None, Bar, Dumbbells, Floor, Machine }

@Composable
fun ExerciseArtwork(
    pattern: MovementPattern,
    muscleAccent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
    contentDescription: String? = null,
) {
    val backdrop = MaterialTheme.colorScheme.surfaceVariant
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val described = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Box(
        described
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(backdrop),
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val scale = minOf(size.width / FIGURE_WIDTH, size.height / FIGURE_HEIGHT) * 0.86f
            val originX = (size.width - FIGURE_WIDTH * scale) / 2f
            val originY = (size.height - FIGURE_HEIGHT * scale) / 2f
            fun at(x: Float, y: Float) = Offset(originX + x * scale, originY + y * scale)

            val pose = poseFor(pattern)
            val region = at(pose.region.x, pose.region.y)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(muscleAccent.copy(alpha = 0.34f), Color.Transparent),
                    center = region,
                    radius = pose.regionRadius * scale,
                ),
                radius = pose.regionRadius * scale,
                center = region,
            )
            drawFigure(pose, scale, originX, originY, bodyColor)
            drawEquipment(pose.equipment, scale, originX, originY, bodyColor, ::at)
        }
    }
}

private fun DrawScope.drawFigure(
    pose: Pose,
    scale: Float,
    originX: Float,
    originY: Float,
    color: Color,
) {
    fun at(x: Float, y: Float) = Offset(originX + x * scale, originY + y * scale)
    val lineWidth = 4.4f * scale
    val torsoWidth = 13f * scale

    // Torso as a tapered bar between shoulder and hip.
    drawLine(
        color = color,
        start = at(pose.shoulder.x, pose.shoulder.y),
        end = at(pose.hip.x, pose.hip.y),
        strokeWidth = torsoWidth,
        cap = StrokeCap.Round,
    )

    listOf(
        pose.elbow, pose.hand, pose.elbow2, pose.hand2,
        pose.knee, pose.foot, pose.knee2, pose.foot2,
    )
    // Arms.
    drawLine(color, at(pose.shoulder.x, pose.shoulder.y), at(pose.elbow.x, pose.elbow.y), lineWidth, StrokeCap.Round)
    drawLine(color, at(pose.elbow.x, pose.elbow.y), at(pose.hand.x, pose.hand.y), lineWidth, StrokeCap.Round)
    drawLine(color, at(pose.shoulder.x, pose.shoulder.y), at(pose.elbow2.x, pose.elbow2.y), lineWidth, StrokeCap.Round)
    drawLine(color, at(pose.elbow2.x, pose.elbow2.y), at(pose.hand2.x, pose.hand2.y), lineWidth, StrokeCap.Round)

    // Legs.
    drawLine(color, at(pose.hip.x, pose.hip.y), at(pose.knee.x, pose.knee.y), lineWidth, StrokeCap.Round)
    drawLine(color, at(pose.knee.x, pose.knee.y), at(pose.foot.x, pose.foot.y), lineWidth, StrokeCap.Round)
    drawLine(color, at(pose.hip.x, pose.hip.y), at(pose.knee2.x, pose.knee2.y), lineWidth, StrokeCap.Round)
    drawLine(color, at(pose.knee2.x, pose.knee2.y), at(pose.foot2.x, pose.foot2.y), lineWidth, StrokeCap.Round)

    // Head.
    drawCircle(color, radius = 7.4f * scale, center = at(pose.head.x, pose.head.y))
}

private fun DrawScope.drawEquipment(
    equipment: Equipment,
    scale: Float,
    originX: Float,
    originY: Float,
    color: Color,
    at: (Float, Float) -> Offset,
) {
    val outline = color.copy(alpha = 0.55f)
    val width = 3.4f * scale
    when (equipment) {
        Equipment.Bar -> {
            drawLine(outline, at(14f, 26f), at(86f, 26f), width, StrokeCap.Round)
            drawCircle(outline, radius = 5f * scale, center = at(16f, 26f))
            drawCircle(outline, radius = 5f * scale, center = at(84f, 26f))
        }

        Equipment.Dumbbells -> {
            drawRoundRect(
                color = outline,
                topLeft = at(8f, 22f),
                size = Size(9f * scale, 5f * scale),
                cornerRadius = CornerRadius(2f * scale),
                style = Stroke(width),
            )
            drawRoundRect(
                color = outline,
                topLeft = at(83f, 22f),
                size = Size(9f * scale, 5f * scale),
                cornerRadius = CornerRadius(2f * scale),
                style = Stroke(width),
            )
        }

        Equipment.Floor -> {
            drawLine(outline, at(4f, 132f), at(96f, 132f), width, StrokeCap.Round)
        }

        Equipment.Machine -> {
            drawRoundRect(
                color = outline,
                topLeft = at(70f, 40f),
                size = Size(24f * scale, 78f * scale),
                cornerRadius = CornerRadius(6f * scale),
                style = Stroke(width),
            )
        }

        Equipment.None -> Unit
    }
}

private fun poseFor(pattern: MovementPattern): Pose = when (pattern) {
    // Lying press: shoulders on the floor, arms pressing up.
    MovementPattern.HORIZONTAL_PUSH -> Pose(
        head = Offset(24f, 96f),
        shoulder = Offset(38f, 98f),
        hip = Offset(64f, 100f),
        elbow = Offset(38f, 74f),
        hand = Offset(34f, 46f),
        elbow2 = Offset(52f, 74f),
        hand2 = Offset(56f, 46f),
        knee = Offset(80f, 92f),
        foot = Offset(90f, 116f),
        knee2 = Offset(84f, 96f),
        foot2 = Offset(94f, 120f),
        region = Offset(44f, 88f),
        regionRadius = 30f,
        equipment = Equipment.Bar,
    )

    // Standing press overhead.
    MovementPattern.VERTICAL_PUSH -> Pose(
        head = Offset(50f, 32f),
        shoulder = Offset(50f, 46f),
        hip = Offset(50f, 84f),
        elbow = Offset(30f, 30f),
        hand = Offset(24f, 8f),
        elbow2 = Offset(70f, 30f),
        hand2 = Offset(76f, 8f),
        knee = Offset(44f, 108f),
        foot = Offset(42f, 132f),
        knee2 = Offset(56f, 108f),
        foot2 = Offset(58f, 132f),
        region = Offset(50f, 34f),
        regionRadius = 30f,
        equipment = Equipment.Bar,
    )

    // Bent-over row.
    MovementPattern.HORIZONTAL_PULL -> Pose(
        head = Offset(26f, 56f),
        shoulder = Offset(38f, 62f),
        hip = Offset(66f, 74f),
        elbow = Offset(44f, 82f),
        hand = Offset(40f, 98f),
        elbow2 = Offset(54f, 84f),
        hand2 = Offset(52f, 100f),
        knee = Offset(70f, 100f),
        foot = Offset(72f, 126f),
        knee2 = Offset(76f, 100f),
        foot2 = Offset(78f, 126f),
        region = Offset(50f, 70f),
        regionRadius = 28f,
        equipment = Equipment.Bar,
    )

    // Hanging pull-up.
    MovementPattern.VERTICAL_PULL -> Pose(
        head = Offset(50f, 40f),
        shoulder = Offset(50f, 54f),
        hip = Offset(50f, 88f),
        elbow = Offset(32f, 40f),
        hand = Offset(28f, 12f),
        elbow2 = Offset(68f, 40f),
        hand2 = Offset(72f, 12f),
        knee = Offset(44f, 110f),
        foot = Offset(40f, 128f),
        knee2 = Offset(56f, 110f),
        foot2 = Offset(60f, 128f),
        region = Offset(50f, 52f),
        regionRadius = 29f,
        equipment = Equipment.Bar,
    )

    // Bottom of a squat.
    MovementPattern.SQUAT -> Pose(
        head = Offset(48f, 34f),
        shoulder = Offset(48f, 48f),
        hip = Offset(52f, 86f),
        elbow = Offset(36f, 58f),
        hand = Offset(30f, 40f),
        elbow2 = Offset(60f, 58f),
        hand2 = Offset(66f, 40f),
        knee = Offset(38f, 96f),
        foot = Offset(34f, 128f),
        knee2 = Offset(66f, 96f),
        foot2 = Offset(70f, 128f),
        region = Offset(48f, 100f),
        regionRadius = 30f,
        equipment = Equipment.Bar,
    )

    // Hinge: hips back, bar at mid-shin.
    MovementPattern.HINGE -> Pose(
        head = Offset(30f, 46f),
        shoulder = Offset(42f, 54f),
        hip = Offset(72f, 64f),
        elbow = Offset(42f, 80f),
        hand = Offset(42f, 104f),
        elbow2 = Offset(50f, 80f),
        hand2 = Offset(50f, 104f),
        knee = Offset(74f, 96f),
        foot = Offset(76f, 128f),
        knee2 = Offset(80f, 96f),
        foot2 = Offset(82f, 128f),
        region = Offset(56f, 78f),
        regionRadius = 28f,
        equipment = Equipment.Bar,
    )

    // Split stance.
    MovementPattern.LUNGE -> Pose(
        head = Offset(48f, 30f),
        shoulder = Offset(48f, 44f),
        hip = Offset(48f, 78f),
        elbow = Offset(36f, 52f),
        hand = Offset(34f, 74f),
        elbow2 = Offset(60f, 52f),
        hand2 = Offset(62f, 74f),
        knee = Offset(30f, 96f),
        foot = Offset(26f, 126f),
        knee2 = Offset(66f, 100f),
        foot2 = Offset(78f, 122f),
        region = Offset(40f, 90f),
        regionRadius = 26f,
        equipment = Equipment.Dumbbells,
    )

    // Loaded carry.
    MovementPattern.CARRY -> Pose(
        head = Offset(50f, 30f),
        shoulder = Offset(50f, 44f),
        hip = Offset(50f, 84f),
        elbow = Offset(32f, 60f),
        hand = Offset(30f, 84f),
        elbow2 = Offset(68f, 60f),
        hand2 = Offset(70f, 84f),
        knee = Offset(45f, 108f),
        foot = Offset(43f, 132f),
        knee2 = Offset(56f, 108f),
        foot2 = Offset(58f, 132f),
        region = Offset(50f, 62f),
        regionRadius = 28f,
        equipment = Equipment.Dumbbells,
    )

    // Front plank.
    MovementPattern.CORE -> Pose(
        head = Offset(22f, 74f),
        shoulder = Offset(34f, 82f),
        hip = Offset(62f, 88f),
        elbow = Offset(28f, 108f),
        hand = Offset(18f, 128f),
        elbow2 = Offset(40f, 104f),
        hand2 = Offset(30f, 128f),
        knee = Offset(80f, 96f),
        foot = Offset(92f, 126f),
        knee2 = Offset(84f, 100f),
        foot2 = Offset(96f, 130f),
        region = Offset(50f, 86f),
        regionRadius = 30f,
        equipment = Equipment.Floor,
    )

    // Curl.
    MovementPattern.CURL -> Pose(
        head = Offset(50f, 30f),
        shoulder = Offset(50f, 44f),
        hip = Offset(50f, 84f),
        elbow = Offset(38f, 64f),
        hand = Offset(48f, 48f),
        elbow2 = Offset(62f, 64f),
        hand2 = Offset(54f, 48f),
        knee = Offset(45f, 108f),
        foot = Offset(43f, 132f),
        knee2 = Offset(56f, 108f),
        foot2 = Offset(58f, 132f),
        region = Offset(48f, 58f),
        regionRadius = 22f,
        equipment = Equipment.Dumbbells,
    )

    // Overhead triceps extension.
    MovementPattern.EXTENSION -> Pose(
        head = Offset(50f, 34f),
        shoulder = Offset(50f, 48f),
        hip = Offset(50f, 86f),
        elbow = Offset(38f, 26f),
        hand = Offset(50f, 12f),
        elbow2 = Offset(62f, 26f),
        hand2 = Offset(50f, 10f),
        knee = Offset(45f, 108f),
        foot = Offset(43f, 132f),
        knee2 = Offset(56f, 108f),
        foot2 = Offset(58f, 132f),
        region = Offset(50f, 26f),
        regionRadius = 20f,
        equipment = Equipment.Dumbbells,
    )

    // Calf raise, heels off the floor.
    MovementPattern.CALF -> Pose(
        head = Offset(50f, 26f),
        shoulder = Offset(50f, 42f),
        hip = Offset(50f, 82f),
        elbow = Offset(38f, 58f),
        hand = Offset(36f, 80f),
        elbow2 = Offset(62f, 58f),
        hand2 = Offset(64f, 80f),
        knee = Offset(45f, 104f),
        foot = Offset(44f, 124f),
        knee2 = Offset(56f, 104f),
        foot2 = Offset(58f, 124f),
        region = Offset(52f, 116f),
        regionRadius = 20f,
        equipment = Equipment.Floor,
    )

    // Conditioning: high knee run.
    MovementPattern.CONDITIONING -> Pose(
        head = Offset(48f, 28f),
        shoulder = Offset(50f, 44f),
        hip = Offset(52f, 82f),
        elbow = Offset(36f, 60f),
        hand = Offset(30f, 46f),
        elbow2 = Offset(66f, 60f),
        hand2 = Offset(72f, 76f),
        knee = Offset(34f, 96f),
        foot = Offset(46f, 106f),
        knee2 = Offset(70f, 104f),
        foot2 = Offset(74f, 130f),
        region = Offset(50f, 92f),
        regionRadius = 26f,
        equipment = Equipment.Floor,
    )

    MovementPattern.MOBILITY -> Pose(
        head = Offset(50f, 62f),
        shoulder = Offset(50f, 74f),
        hip = Offset(64f, 92f),
        elbow = Offset(36f, 84f),
        hand = Offset(24f, 96f),
        elbow2 = Offset(46f, 62f),
        hand2 = Offset(34f, 44f),
        knee = Offset(74f, 108f),
        foot = Offset(72f, 130f),
        knee2 = Offset(84f, 106f),
        foot2 = Offset(88f, 128f),
        region = Offset(58f, 84f),
        regionRadius = 26f,
        equipment = Equipment.Floor,
    )
}
