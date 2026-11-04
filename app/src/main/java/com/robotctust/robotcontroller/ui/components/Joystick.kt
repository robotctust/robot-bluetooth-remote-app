package com.robotctust.robotcontroller.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.robotctust.robotcontroller.ui.theme.CyberPrimary
import com.robotctust.robotcontroller.ui.theme.CyberSecondary
import com.robotctust.robotcontroller.ui.theme.CyberSurfaceVariant
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    thumbSize: Dp = 60.dp,
    onDirectionChanged: (Char) -> Unit
) {
    val density = LocalDensity.current
    val sizePx = remember(size) { with(density) { size.toPx() } }
    val thumbSizePx = remember(thumbSize) { with(density) { thumbSize.toPx() } }
    val radiusPx = sizePx / 2f
    val maxDragPx = radiusPx - (thumbSizePx / 2f)

    // Animated offset to allow returning to center smoothly on release
    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()

    // Keep track of the current active command to avoid sending redundant triggers in gesture loop
    val activeCommand = remember { mutableMapOf("cmd" to 'S') }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(sizePx, thumbSizePx) {
                detectDragGestures(
                    onDragStart = {
                        // Reset when drag starts
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val newX = animatedOffset.value.x + dragAmount.x
                            val newY = animatedOffset.value.y + dragAmount.y
                            val dist = sqrt(newX * newX + newY * newY)

                            val finalOffset = if (dist <= maxDragPx) {
                                Offset(newX, newY)
                            } else {
                                Offset(newX / dist * maxDragPx, newY / dist * maxDragPx)
                            }
                            animatedOffset.snapTo(finalOffset)

                            // Calculate direction command
                            val cmd = calculateDirection(finalOffset.x, finalOffset.y, maxDragPx)
                            if (activeCommand["cmd"] != cmd) {
                                activeCommand["cmd"] = cmd
                                onDirectionChanged(cmd)
                            }
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            animatedOffset.animateTo(Offset.Zero)
                            if (activeCommand["cmd"] != 'S') {
                                activeCommand["cmd"] = 'S'
                                onDirectionChanged('S')
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animatedOffset.animateTo(Offset.Zero)
                            if (activeCommand["cmd"] != 'S') {
                                activeCommand["cmd"] = 'S'
                                onDirectionChanged('S')
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(sizePx / 2, sizePx / 2)
            
            // Draw background glow of outer ring
            drawCircle(
                color = CyberSecondary.copy(alpha = 0.1f),
                radius = radiusPx,
                center = center
            )
            
            // Draw outer border ring (neon styled)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(CyberPrimary, CyberSecondary, CyberPrimary),
                    center = center
                ),
                radius = radiusPx,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Draw crosshairs / grids for a techy cockpit look
            val crosshairLen = 16.dp.toPx()
            val crosshairStroke = 1.5.dp.toPx()
            // Top cross
            drawLine(
                color = CyberPrimary.copy(alpha = 0.6f),
                start = Offset(center.x, center.y - radiusPx + 10.dp.toPx()),
                end = Offset(center.x, center.y - radiusPx + 10.dp.toPx() + crosshairLen),
                strokeWidth = crosshairStroke
            )
            // Bottom cross
            drawLine(
                color = CyberPrimary.copy(alpha = 0.6f),
                start = Offset(center.x, center.y + radiusPx - 10.dp.toPx()),
                end = Offset(center.x, center.y + radiusPx - 10.dp.toPx() - crosshairLen),
                strokeWidth = crosshairStroke
            )
            // Left cross
            drawLine(
                color = CyberPrimary.copy(alpha = 0.6f),
                start = Offset(center.x - radiusPx + 10.dp.toPx(), center.y),
                end = Offset(center.x - radiusPx + 10.dp.toPx() + crosshairLen, center.y),
                strokeWidth = crosshairStroke
            )
            // Right cross
            drawLine(
                color = CyberPrimary.copy(alpha = 0.6f),
                start = Offset(center.x + radiusPx - 10.dp.toPx(), center.y),
                end = Offset(center.x + radiusPx - 10.dp.toPx() - crosshairLen, center.y),
                strokeWidth = crosshairStroke
            )

            // Draw inner thumb (joystick control)
            val thumbCenter = center + animatedOffset.value
            
            // Thumb outer neon glow
            drawCircle(
                color = CyberPrimary.copy(alpha = 0.25f),
                radius = thumbSizePx / 2f + 6.dp.toPx(),
                center = thumbCenter
            )
            
            // Thumb base
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyberSurfaceVariant, CyberSecondary.copy(alpha = 0.8f)),
                    center = thumbCenter,
                    radius = thumbSizePx / 2f
                ),
                radius = thumbSizePx / 2f,
                center = thumbCenter
            )
            
            // Thumb top ring
            drawCircle(
                color = CyberPrimary,
                radius = thumbSizePx / 4f,
                center = thumbCenter,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

/**
 * Calculates direction character based on coordinates.
 * - Up (Forward): 'F'
 * - Down (Backward): 'B'
 * - Spin Left: 'L'
 * - Spin Right: 'R'
 * - Center (Stop): 'S'
 */
private fun calculateDirection(x: Float, y: Float, maxDrag: Float): Char {
    val distance = sqrt(x * x + y * y)
    val deadZone = maxDrag * 0.25f // 25% deadzone to avoid accidental drift
    if (distance < deadZone) {
        return 'S'
    }

    val angleRad = atan2(y, x)
    val angleDeg = Math.toDegrees(angleRad.toDouble())


    return when {
        // Sector right: [-45, 45] -> 'R'
        angleDeg >= -45 && angleDeg <= 45 -> 'R'
        // Sector down: [45, 135] -> 'B'
        angleDeg > 45 && angleDeg < 135 -> 'B'
        // Sector up: [-135, -45] -> 'F'
        angleDeg > -135 && angleDeg < -45 -> 'F'
        // Sector left: [135, 180] or [-180, -135] -> 'L'
        else -> 'L'
    }
}

@Composable
fun AnalogJoystick(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    thumbSize: Dp = 60.dp,
    onPositionChanged: (x: Float, y: Float) -> Unit
) {
    val density = LocalDensity.current
    val sizePx = remember(size) { with(density) { size.toPx() } }
    val thumbSizePx = remember(thumbSize) { with(density) { thumbSize.toPx() } }
    val radiusPx = sizePx / 2f
    val maxDragPx = radiusPx - (thumbSizePx / 2f)

    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(sizePx, thumbSizePx) {
                detectDragGestures(
                    onDragStart = {},
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val newX = animatedOffset.value.x + dragAmount.x
                            val newY = animatedOffset.value.y + dragAmount.y
                            val dist = sqrt(newX * newX + newY * newY)

                            val finalOffset = if (dist <= maxDragPx) {
                                Offset(newX, newY)
                            } else {
                                Offset(newX / dist * maxDragPx, newY / dist * maxDragPx)
                            }
                            animatedOffset.snapTo(finalOffset)

                            // Emit normalised coordinates in range [-1.0f, 1.0f]
                            val normX = finalOffset.x / maxDragPx
                            val normY = finalOffset.y / maxDragPx
                            onPositionChanged(normX, normY)
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            animatedOffset.animateTo(Offset.Zero)
                            onPositionChanged(0f, 0f)
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animatedOffset.animateTo(Offset.Zero)
                            onPositionChanged(0f, 0f)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(sizePx / 2, sizePx / 2)
            
            // Draw background glow
            drawCircle(
                color = CyberPrimary.copy(alpha = 0.1f),
                radius = radiusPx,
                center = center
            )
            
            // Draw outer border ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(CyberSecondary, CyberPrimary, CyberSecondary),
                    center = center
                ),
                radius = radiusPx,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Tech crosshairs
            val crosshairLen = 16.dp.toPx()
            val crosshairStroke = 1.5.dp.toPx()
            drawLine(
                color = CyberSecondary.copy(alpha = 0.6f),
                start = Offset(center.x, center.y - radiusPx + 10.dp.toPx()),
                end = Offset(center.x, center.y - radiusPx + 10.dp.toPx() + crosshairLen),
                strokeWidth = crosshairStroke
            )
            drawLine(
                color = CyberSecondary.copy(alpha = 0.6f),
                start = Offset(center.x, center.y + radiusPx - 10.dp.toPx()),
                end = Offset(center.x, center.y + radiusPx - 10.dp.toPx() - crosshairLen),
                strokeWidth = crosshairStroke
            )
            drawLine(
                color = CyberSecondary.copy(alpha = 0.6f),
                start = Offset(center.x - radiusPx + 10.dp.toPx(), center.y),
                end = Offset(center.x - radiusPx + 10.dp.toPx() + crosshairLen, center.y),
                strokeWidth = crosshairStroke
            )
            drawLine(
                color = CyberSecondary.copy(alpha = 0.6f),
                start = Offset(center.x + radiusPx - 10.dp.toPx(), center.y),
                end = Offset(center.x + radiusPx - 10.dp.toPx() - crosshairLen, center.y),
                strokeWidth = crosshairStroke
            )

            // Draw inner thumb
            val thumbCenter = center + animatedOffset.value
            drawCircle(
                color = CyberSecondary.copy(alpha = 0.25f),
                radius = thumbSizePx / 2f + 6.dp.toPx(),
                center = thumbCenter
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyberSurfaceVariant, CyberPrimary.copy(alpha = 0.8f)),
                    center = thumbCenter,
                    radius = thumbSizePx / 2f
                ),
                radius = thumbSizePx / 2f,
                center = thumbCenter
            )
            drawCircle(
                color = CyberSecondary,
                radius = thumbSizePx / 4f,
                center = thumbCenter,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
