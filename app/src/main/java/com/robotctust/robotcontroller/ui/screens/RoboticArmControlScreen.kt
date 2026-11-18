package com.robotctust.robotcontroller.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotctust.robotcontroller.bluetooth.BluetoothHelper
import com.robotctust.robotcontroller.bluetooth.ConnectionState
import com.robotctust.robotcontroller.ui.components.AnalogJoystick
import com.robotctust.robotcontroller.ui.components.ARM_MAX_PWM
import com.robotctust.robotcontroller.ui.components.driveMix
import com.robotctust.robotcontroller.ui.components.percentToPwm
import com.robotctust.robotcontroller.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoboticArmControlScreen(
    bluetoothHelper: BluetoothHelper,
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    val connectionState by bluetoothHelper.connectionState.collectAsState()
    val connectedDeviceName by bluetoothHelper.connectedDeviceName.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var speedSliderValue by remember { mutableFloatStateOf(50f) } // 最高速度 %（搖桿推到底時）
    var drivePosition by remember { mutableStateOf(Offset.Zero) } // 行進搖桿位置，-1～1，螢幕座標（下為 +y）

    // 連線狀態監控：連線完成時強制保持靜止 ('S')，斷線時重設速度
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            // 藍芽連線完成，立刻送出停止訊號，確保車輛在收到移動訊號前為靜止狀態
            bluetoothHelper.sendDirectionForce('S')
        } else {
            speedSliderValue = 50f
        }
    }

    // 4 Axis Servo Angles (0 - 180 degrees, default 90 degrees)
    var baseAngle by remember { mutableFloatStateOf(90f) }
    var shoulderAngle by remember { mutableFloatStateOf(90f) }
    var elbowAngle by remember { mutableFloatStateOf(90f) }
    var gripperAngle by remember { mutableFloatStateOf(90f) }

    // Position of the arm analog joystick (-1.0 to 1.0)
    var armJoystickPosition by remember { mutableStateOf(Offset.Zero) }

    // 行進搖桿差速控制：每 50ms（20Hz）把搖桿位置換算成左右輪 PWM 送出，內容沒變就不送
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            while (true) {
                val (left, right) = driveMix(drivePosition.x, -drivePosition.y, speedSliderValue.toInt())
                bluetoothHelper.sendDrive(percentToPwm(left, ARM_MAX_PWM), percentToPwm(right, ARM_MAX_PWM))
                delay(50)
            }
        }
    }

    // Rate control loop for Shoulder & Elbow via analog joystick
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            while (true) {
                val pos = armJoystickPosition
                if (pos != Offset.Zero) {
                    val rate = 2.5f // Degrees per 50ms
                    val newShoulder = (shoulderAngle - pos.y * rate).coerceIn(0f, 180f)
                    val newElbow = (elbowAngle + pos.x * rate).coerceIn(0f, 180f)
                    shoulderAngle = newShoulder
                    elbowAngle = newElbow
                }
                delay(50)
            }
        }
    }

    // Sync servo angles when connected
    LaunchedEffect(baseAngle, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            bluetoothHelper.sendServoAngle(1, baseAngle.toInt())
        }
    }
    LaunchedEffect(shoulderAngle, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            bluetoothHelper.sendServoAngle(2, shoulderAngle.toInt())
        }
    }
    LaunchedEffect(elbowAngle, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            bluetoothHelper.sendServoAngle(3, elbowAngle.toInt())
        }
    }
    LaunchedEffect(gripperAngle, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            bluetoothHelper.sendServoAngle(4, gripperAngle.toInt())
        }
    }

    // Send Stop on leave
    DisposableEffect(Unit) {
        onDispose {
            bluetoothHelper.sendDirectionForce('S')
        }
    }

    // ── 專為 4 指操作打造的橫向全螢幕佈局 ──
    // [左食指: 左上2按鈕 (底座)]                 [右食指: 右上2按鈕 (夾爪)]
    // [左拇指: 左下圓形搖桿]    [中央: 方形面板]   [右拇指: 右下圓形搖桿]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── 左側區域：左手食指按鈕(上) + 左手拇指搖桿(下) ──
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 左上方：2 個按鈕上下排列 (底座旋轉)
                Column(
                    modifier = Modifier.width(120.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "底座旋轉 (${baseAngle.toInt()}°)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )

                    // 上按鈕：底座左旋
                    FourFingerButton(
                        title = "◀ 底座左旋",
                        color = CyberPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        onTick = {
                            baseAngle = (baseAngle - 2f).coerceIn(0f, 180f)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 下按鈕：底座右旋
                    FourFingerButton(
                        title = "底座右旋 ▶",
                        color = CyberPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        onTick = {
                            baseAngle = (baseAngle + 2f).coerceIn(0f, 180f)
                        }
                    )
                }

                // 左下方：圓形方向搖桿 (JOYSTICK - 尺吋適中不卡手指)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "方向控制",
                        fontSize = 10.sp,
                        color = CyberPrimary.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    AnalogJoystick(
                        size = 135.dp,
                        thumbSize = 46.dp,
                        onPositionChanged = { x, y -> drivePosition = Offset(x, y) }
                    )
                }
            }

            // ── 正中央：方形面板 (中央狀態監控 + 速度滑桿 + 藍牙連線) ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .border(1.dp, CyberSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. 頂部列：返回鍵 + 標題 + 藍牙連線燈號
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                bluetoothHelper.sendDirectionForce('S')
                                onBack()
                            },
                            modifier = Modifier
                                .background(CyberSurfaceVariant, RoundedCornerShape(6.dp))
                                .size(28.dp)
                        ) {
                            ArrowBackIcon(
                                color = TextPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        Text(
                            text = "機械手臂自走車",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )

                        // 藍牙狀態燈
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        ConnectionState.CONNECTED -> CyberTertiary
                                        ConnectionState.CONNECTING -> CyberSecondary
                                        ConnectionState.ERROR -> Color.Red
                                        ConnectionState.IDLE -> TextSecondary
                                    }
                                )
                        )
                    }

                    // 2. 四軸即時角度監視 (ARM STATUS)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "4軸角度",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "復位 90°",
                                    fontSize = 8.sp,
                                    color = CyberPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(CyberSurface)
                                        .clickable {
                                            baseAngle = 90f
                                            shoulderAngle = 90f
                                            elbowAngle = 90f
                                            gripperAngle = 90f
                                        }
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MiniAngleBadge("底座", "${baseAngle.toInt()}°", CyberPrimary)
                                MiniAngleBadge("大臂", "${shoulderAngle.toInt()}°", CyberSecondary)
                                MiniAngleBadge("小臂", "${elbowAngle.toInt()}°", CyberSecondary)
                                MiniAngleBadge("夾爪", "${gripperAngle.toInt()}°", CyberTertiary)
                            }
                        }
                    }

                    // 3. 車子速度滑桿 (PWM)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberSurfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("車速", fontSize = 9.sp, color = CyberPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${speedSliderValue.toInt()} %", fontSize = 10.sp, color = CyberPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = speedSliderValue,
                            onValueChange = { speedSliderValue = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberPrimary,
                                activeTrackColor = CyberPrimary,
                                inactiveTrackColor = CyberSurfaceVariant
                            ),
                            modifier = Modifier.height(18.dp)
                        )
                    }

                    // 4. 藍牙控制列 (顯示車輛名稱 Robot-Car0X + 連線按鈕)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberSurfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val displayName = connectedDeviceName ?: when (connectionState) {
                                ConnectionState.IDLE -> "未連線"
                                ConnectionState.CONNECTING -> "連線中..."
                                ConnectionState.ERROR -> "連線異常"
                                ConnectionState.CONNECTED -> "已連線"
                            }
                            Text(
                                text = displayName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (connectionState == ConnectionState.CONNECTED) CyberTertiary else TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }

                        if (!hasBluetoothPermission) {
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("授權", color = TextDark, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            if (connectionState == ConnectionState.CONNECTED) {
                                Button(
                                    onClick = { bluetoothHelper.disconnect() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("中斷", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { showDeviceDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                    enabled = connectionState != ConnectionState.CONNECTING,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("連線車輛", color = TextDark, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ── 右側區域：右手食指按鈕(上) + 右手拇指搖桿(下) ──
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 右上方：2 個按鈕上下排列 (夾爪開合)
                Column(
                    modifier = Modifier.width(120.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "夾爪開合 (${gripperAngle.toInt()}°)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTertiary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )

                    // 上按鈕：夾爪張開
                    FourFingerButton(
                        title = "▲ 夾爪張開",
                        color = CyberTertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        onTick = {
                            gripperAngle = (gripperAngle + 2f).coerceIn(0f, 180f)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 下按鈕：夾爪閉合
                    FourFingerButton(
                        title = "夾爪閉合 ▼",
                        color = CyberTertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        onTick = {
                            gripperAngle = (gripperAngle - 2f).coerceIn(0f, 180f)
                        }
                    )
                }

                // 右下方：圓形雙軸類比搖桿 (ARM LIFTING - 大臂/小臂)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "手臂升降 (大臂/小臂)",
                        fontSize = 10.sp,
                        color = CyberSecondary.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    AnalogJoystick(
                        size = 135.dp,
                        thumbSize = 46.dp,
                        onPositionChanged = { x, y ->
                            armJoystickPosition = Offset(x, y)
                        }
                    )
                }
            }
        }

        // 藍牙配對彈窗 (支援自動重命名為 Robot-Car0X)
        if (showDeviceDialog) {
            val pairedDevices = remember(showDeviceDialog) { bluetoothHelper.getPairedDevices() }

            AlertDialog(
                onDismissRequest = { showDeviceDialog = false },
                title = {
                    Text(
                        text = "選擇要連線的自走車",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        if (pairedDevices.isEmpty()) {
                            Text("未找到已配對藍牙裝置，請先至系統設定配對。", color = Color.Red.copy(alpha = 0.8f))
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 180.dp)
                            ) {
                                items(pairedDevices) { device ->
                                    val displayName = BluetoothHelper.getDeviceDisplayName(device)
                                    val address = device.address ?: ""
                                    val isKnownCar = BluetoothHelper.KNOWN_CAR_MACS.containsKey(address.uppercase())
                                    val originalName = try { device.name } catch (e: SecurityException) { null }

                                    Button(
                                        onClick = {
                                            bluetoothHelper.connect(device)
                                            showDeviceDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isKnownCar) CyberSurface else CyberSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .border(
                                                width = if (isKnownCar) 1.dp else 0.dp,
                                                color = if (isKnownCar) CyberPrimary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = displayName,
                                                    color = if (isKnownCar) CyberPrimary else TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                )
                                                if (isKnownCar && !originalName.isNullOrBlank() && originalName != displayName) {
                                                    Text(
                                                        text = "原始模組: $originalName",
                                                        color = TextSecondary,
                                                        fontSize = 8.sp
                                                    )
                                                }
                                            }
                                            Text(
                                                text = address,
                                                color = TextSecondary,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDeviceDialog = false }) {
                        Text("關閉", color = CyberPrimary)
                    }
                },
                containerColor = CyberSurface,
                textContentColor = TextPrimary
            )
        }
    }
}

/**
 * 4 指操作專用食指按鈕：緊湊外型，支援點擊與長按連續觸發
 */
@Composable
fun FourFingerButton(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    onTick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onTick()
            delay(250) // 長按判定
            while (isPressed) {
                onTick()
                delay(35) // 每 35ms 平滑觸發
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) color.copy(alpha = 0.35f) else CyberSurface)
            .border(
                width = 1.2.dp,
                color = if (isPressed) color else color.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) color else TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 迷你角度徽章
 */
@Composable
fun MiniAngleBadge(
    label: String,
    angle: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, color = TextSecondary)
        Text(
            text = angle,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}
