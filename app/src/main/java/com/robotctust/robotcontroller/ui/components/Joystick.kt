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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// 直走微調：搖桿偏離正前／正後 FINE_ANGLE 度以內轉向很鈍，到 FINE_ANGLE 時轉向量才 FINE_TURN（1 = 原地旋轉）。
// ponytail: 兩段線性曲線，手感不對就調這兩個數字
private const val FINE_ANGLE = 30f
private const val FINE_TURN = 0.15f
private const val DEAD_ZONE = 0.1f

/**
 * 搖桿位置 → 左右輪速度百分比（-100～100），差速混控。
 * x 向右為正、y 向前為正，範圍 -1～1。
 * - 偏離正前／正後 0～30°：轉向緩慢增加，方便直走微調
 * - 30～90°：轉向加快補回，正左／正右 (90°) 為原地旋轉
 * - 後退同理；推動距離決定整體速度，最大為 maxPercent
 */
fun driveMix(x: Float, y: Float, maxPercent: Int): Pair<Int, Int> {
    val distance = min(1f, hypot(x, y))
    if (distance < DEAD_ZONE) return 0 to 0

    val angle = Math.toDegrees(atan2(abs(x), abs(y)).toDouble()).toFloat() // 0 = 正前／正後，90 = 正左／正右
    val turn = if (angle <= FINE_ANGLE) {
        FINE_TURN * angle / FINE_ANGLE
    } else {
        FINE_TURN + (1 - FINE_TURN) * (angle - FINE_ANGLE) / (90 - FINE_ANGLE)
    }
    val inner = 1 - 2 * turn // 外側輪全速；內側輪從全速（直走）降到反轉全速（原地旋轉）

    var (left, right) = if (x >= 0) 1f to inner else inner to 1f
    if (y < 0) { // 後退：兩輪反轉並左右對調，在 90° 與前進側連續
        val l = left
        left = -right
        right = -l
    }
    val scale = distance * maxPercent
    return (left * scale).roundToInt() to (right * scale).roundToInt()
}

// 韌體的 M<左>,<右> 把數值直接當 PWM 寫進馬達，PWM 低於 MIN_PWM 推不動車，
// 所以 1～100% 對應 MIN_PWM～最高 PWM（與速度等級 1～9 的範圍相同）。
// ponytail: 線性對應，車子起步推不動就調高 MIN_PWM，太快就調低最高 PWM
const val MIN_PWM = 100
const val CAR_MAX_PWM = 200
const val ARM_MAX_PWM = 220

/** 速度百分比（-100～100）→ 帶正負號的 PWM（0 或 ±MIN_PWM～maxPwm）。 */
fun percentToPwm(percent: Int, maxPwm: Int): Int {
    if (percent == 0) return 0
    val pwm = MIN_PWM + (abs(percent).coerceAtMost(100) - 1) * (maxPwm - MIN_PWM) / 99
    return if (percent > 0) pwm else -pwm
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
