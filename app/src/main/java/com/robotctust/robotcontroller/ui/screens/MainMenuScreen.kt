package com.robotctust.robotcontroller.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotctust.robotcontroller.ui.theme.CyberBackground
import com.robotctust.robotcontroller.ui.theme.CyberPrimary
import com.robotctust.robotcontroller.ui.theme.CyberSecondary
import com.robotctust.robotcontroller.ui.theme.CyberSurface
import com.robotctust.robotcontroller.ui.theme.TextPrimary
import com.robotctust.robotcontroller.ui.theme.TextSecondary

@Composable
fun MainMenuScreen(
    onSelectNormalCar: () -> Unit,
    onSelectRoboticArm: () -> Unit
) {
    // Pulse animation for neon ambient glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Sci-fi diagonal mesh lines in background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 100.dp.toPx()
            val strokeWidth = 0.5.dp.toPx()
            val lineColor = CyberPrimary.copy(alpha = 0.05f)
            
            // Draw diagonal grid lines
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = strokeWidth
                )
                x += step
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // App Title Header
            Text(
                text = "中臺機器人遙控APP",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = CyberPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Text(
                text = "CTUST ROBOTIC REMOTE CONTROL  APP",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Side-by-side Mode Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Card 1: Normal Remote Control Car
                ModeSelectorCard(
                    title = "普通遙控車模式",
                    engTitle = "NORMAL REMOTE CAR",
                    description = "支援搖桿靈敏方向操控\n搭配 0~9 檔位滑桿無段調速",
                    accentColor = CyberPrimary,
                    glowAlpha = glowAlpha,
                    onClick = onSelectNormalCar,
                    modifier = Modifier
                        .weight(1f)
                        .maxHeight(300.dp)
                        .padding(horizontal = 16.dp),
                    iconContent = { color ->
                        CarVectorIcon(color = color)
                    }
                )

                // Card 2: Car + Robotic Arm
                ModeSelectorCard(
                    title = "機械手臂自走車模式",
                    engTitle = "CAR & ROBOTIC ARM",
                    description = "整合前進底盤操縱系統\n支援 4 軸機械手臂精密伺服控制",
                    accentColor = CyberSecondary,
                    glowAlpha = glowAlpha,
                    onClick = onSelectRoboticArm,
                    modifier = Modifier
                        .weight(1f)
                        .maxHeight(300.dp)
                        .padding(horizontal = 16.dp),
                    iconContent = { color ->
                        ArmVectorIcon(color = color)
                    }
                )
            }
        }
    }
}

// Extends Modifier to constraint max height for landscape layout
fun Modifier.maxHeight(max: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val maxHeightPx = max.roundToPx()
    val childConstraints = constraints.copy(maxHeight = constraints.maxHeight.coerceAtMost(maxHeightPx))
    val placeable = measurable.measure(childConstraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}

@Composable
fun ModeSelectorCard(
    title: String,
    engTitle: String,
    description: String,
    accentColor: Color,
    glowAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (Color) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = accentColor.copy(alpha = 0.2f),
                spotColor = accentColor
            )
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CyberSurface,
                            CyberSurface.copy(alpha = 0.9f)
                        )
                    )
                )
                // Border glow
                .padding(1.dp)
        ) {
            // Highlight border
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = accentColor.copy(alpha = glowAlpha),
                    size = size,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Vector Graphic Icon
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent(accentColor)
                }

                // Text details
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = engTitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CarVectorIcon(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val scale = 0.8f
        val cw = w * scale
        val ch = h * scale
        val left = (w - cw) / 2f
        val top = (h - ch) / 2f

        // Draw Car Body (Wireframe style)
        val bodyBrush = Brush.linearGradient(
            colors = listOf(color, color.copy(alpha = 0.5f)),
            start = Offset(left, top),
            end = Offset(left + cw, top + ch)
        )
        
        // Main cabin chassis
        drawRoundRect(
            brush = bodyBrush,
            topLeft = Offset(left + cw * 0.15f, top + ch * 0.3f),
            size = Size(cw * 0.7f, ch * 0.4f),
            cornerRadius = CornerRadius(cw * 0.1f, ch * 0.1f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Cabin roof
        drawRoundRect(
            color = color,
            topLeft = Offset(left + cw * 0.3f, top + ch * 0.1f),
            size = Size(cw * 0.4f, ch * 0.22f),
            cornerRadius = CornerRadius(cw * 0.05f, ch * 0.05f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Left Front Wheel
        drawRoundRect(
            color = color,
            topLeft = Offset(left + cw * 0.05f, top + ch * 0.2f),
            size = Size(cw * 0.12f, ch * 0.22f),
            cornerRadius = CornerRadius(cw * 0.02f, ch * 0.02f)
        )
        // Right Front Wheel
        drawRoundRect(
            color = color,
            topLeft = Offset(left + cw * 0.83f, top + ch * 0.2f),
            size = Size(cw * 0.12f, ch * 0.22f),
            cornerRadius = CornerRadius(cw * 0.02f, ch * 0.02f)
        )
        // Left Rear Wheel
        drawRoundRect(
            color = color,
            topLeft = Offset(left + cw * 0.05f, top + ch * 0.58f),
            size = Size(cw * 0.12f, ch * 0.22f),
            cornerRadius = CornerRadius(cw * 0.02f, ch * 0.02f)
        )
        // Right Rear Wheel
        drawRoundRect(
            color = color,
            topLeft = Offset(left + cw * 0.83f, top + ch * 0.58f),
            size = Size(cw * 0.12f, ch * 0.22f),
            cornerRadius = CornerRadius(cw * 0.02f, ch * 0.02f)
        )

        // Axle lines
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(left + cw * 0.17f, top + ch * 0.31f),
            end = Offset(left + cw * 0.83f, top + ch * 0.31f),
            strokeWidth = 1.5.dp.toPx()
        )
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(left + cw * 0.17f, top + ch * 0.69f),
            end = Offset(left + cw * 0.83f, top + ch * 0.69f),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
fun ArmVectorIcon(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val scale = 0.8f
        val cw = w * scale
        val ch = h * scale
        val left = (w - cw) / 2f
        val top = (h - ch) / 2f

        // Draw Robotic Arm Base
        drawRect(
            color = color,
            topLeft = Offset(left + cw * 0.25f, top + ch * 0.8f),
            size = Size(cw * 0.5f, ch * 0.12f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Lower arm segment
        drawLine(
            color = color,
            start = Offset(left + cw * 0.5f, top + ch * 0.8f),
            end = Offset(left + cw * 0.35f, top + ch * 0.45f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Upper arm segment
        drawLine(
            color = color,
            start = Offset(left + cw * 0.35f, top + ch * 0.45f),
            end = Offset(left + cw * 0.65f, top + ch * 0.25f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Servo joints (circles)
        drawCircle(
            color = color,
            radius = 5.dp.toPx(),
            center = Offset(left + cw * 0.5f, top + ch * 0.8f)
        )
        drawCircle(
            color = color,
            radius = 5.dp.toPx(),
            center = Offset(left + cw * 0.35f, top + ch * 0.45f)
        )
        drawCircle(
            color = color,
            radius = 5.dp.toPx(),
            center = Offset(left + cw * 0.65f, top + ch * 0.25f)
        )

        // Gripper claw
        // Left prong
        drawLine(
            color = color,
            start = Offset(left + cw * 0.65f, top + ch * 0.25f),
            end = Offset(left + cw * 0.58f, top + ch * 0.12f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(left + cw * 0.58f, top + ch * 0.12f),
            end = Offset(left + cw * 0.64f, top + ch * 0.08f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Right prong
        drawLine(
            color = color,
            start = Offset(left + cw * 0.65f, top + ch * 0.25f),
            end = Offset(left + cw * 0.72f, top + ch * 0.12f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(left + cw * 0.72f, top + ch * 0.12f),
            end = Offset(left + cw * 0.66f, top + ch * 0.08f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
