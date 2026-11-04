package com.robotctust.robotcontroller.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotctust.robotcontroller.bluetooth.BluetoothHelper
import com.robotctust.robotcontroller.bluetooth.ConnectionState
import com.robotctust.robotcontroller.ui.components.Joystick
import com.robotctust.robotcontroller.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarControlScreen(
    bluetoothHelper: BluetoothHelper,
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    val connectionState by bluetoothHelper.connectionState.collectAsState()
    val connectedDeviceName by bluetoothHelper.connectedDeviceName.collectAsState()
    
    var showDeviceDialog by remember { mutableStateOf(false) }
    var speedSliderValue by remember { mutableFloatStateOf(100f) } // Default 50% speed (100 PWM)

    // 連線狀態監控：連線完成時強制保持靜止 ('S')，斷線時重設速度
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            // 藍芽連線完成，立刻送出停止訊號，確保車輛在收到移動訊號前為靜止狀態
            bluetoothHelper.sendDirectionForce('S')
        } else {
            speedSliderValue = 100f
        }
    }

    // Sync speed with BluetoothHelper when screen loads or changes
    LaunchedEffect(speedSliderValue, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            val level = if (speedSliderValue <= 0f) 0 else (((speedSliderValue / 200f) * 8f).toInt() + 1).coerceIn(1, 9)
            val speedChar = (level + '0'.code).toChar()
            bluetoothHelper.sendSpeed(speedChar)
        }
    }

    // Automatically send STOP ('S') if we enter or leave the screen
    DisposableEffect(Unit) {
        onDispose {
            bluetoothHelper.sendDirectionForce('S')
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        // Global Top Header Bar (Unified Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        bluetoothHelper.sendDirectionForce('S')
                        onBack()
                    },
                    modifier = Modifier
                        .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
                        .size(36.dp)
                ) {
                    ArrowBackIcon(
                        color = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "普通遙控車遙控模式",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Quick Status Badge (與手臂模式一致)
            Surface(
                color = when (connectionState) {
                    ConnectionState.CONNECTED -> CyberTertiary.copy(alpha = 0.15f)
                    ConnectionState.CONNECTING -> CyberSecondary.copy(alpha = 0.15f)
                    ConnectionState.ERROR -> Color.Red.copy(alpha = 0.15f)
                    ConnectionState.IDLE -> CyberSurfaceVariant
                },
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (connectionState) {
                        ConnectionState.CONNECTED -> CyberTertiary.copy(alpha = 0.5f)
                        ConnectionState.CONNECTING -> CyberSecondary.copy(alpha = 0.5f)
                        ConnectionState.ERROR -> Color.Red.copy(alpha = 0.5f)
                        ConnectionState.IDLE -> Color.Transparent
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> connectedDeviceName ?: "已連線"
                            ConnectionState.CONNECTING -> "連線中..."
                            ConnectionState.ERROR -> "連線異常"
                            ConnectionState.IDLE -> "藍牙未連線"
                        },
                        fontSize = 11.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Main Dashboard Body
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Joystick Area
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "方向控制 (JOYSTICK)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Joystick(
                    size = 200.dp,
                    thumbSize = 65.dp,
                    onDirectionChanged = { dir ->
                        bluetoothHelper.sendDirection(dir)
                    }
                )
            }

            // Right Column: Speed Control & Bluetooth Controls
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Speed Slider panel
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "速度設定 (THRUST)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyberPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${speedSliderValue.toInt()} PWM",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Slider(
                            value = speedSliderValue,
                            onValueChange = { speedSliderValue = it },
                            valueRange = 0f..200f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberPrimary,
                                activeTrackColor = CyberPrimary,
                                inactiveTrackColor = CyberSurfaceVariant
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Bluetooth Connection panel (與手臂模式完全一致)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberSecondary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "藍牙模組",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            val targetName = connectedDeviceName ?: when (connectionState) {
                                ConnectionState.IDLE -> "未連線"
                                ConnectionState.CONNECTING -> "連線中..."
                                ConnectionState.ERROR -> "連線異常"
                                ConnectionState.CONNECTED -> "已連線"
                            }
                            Text(
                                text = targetName,
                                fontSize = 13.sp,
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
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("授權", color = TextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            if (connectionState == ConnectionState.CONNECTED) {
                                Button(
                                    onClick = { bluetoothHelper.disconnect() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("中斷", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { showDeviceDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                    enabled = connectionState != ConnectionState.CONNECTING,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    BluetoothIcon(
                                        color = TextDark,
                                        modifier = Modifier.size(13.dp),
                                        isSearching = (connectionState == ConnectionState.CONNECTING)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("連線車輛", color = TextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bluetooth Device Selection Dialog (與手臂模式完全一致)
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

// ───── Custom Vector Drawing Icons ─────

@Composable
fun ArrowBackIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        // Horizontal stem
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.15f, h * 0.5f), strokeWidth = stroke)
        // Arrowhead top
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.45f, h * 0.2f), strokeWidth = stroke)
        // Arrowhead bottom
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.45f, h * 0.8f), strokeWidth = stroke)
    }
}

@Composable
fun BluetoothIcon(
    color: Color,
    modifier: Modifier = Modifier,
    isSearching: Boolean = false
) {
    val scale = if (isSearching) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        animatedScale
    } else {
        1.0f
    }

    Canvas(modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        
        // Draw Bluetooth symbol
        // Center spine
        drawLine(color, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.9f), strokeWidth = stroke)
        // Upper loop
        drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.8f, h * 0.3f), strokeWidth = stroke)
        drawLine(color, Offset(w * 0.8f, h * 0.3f), Offset(w * 0.5f, h * 0.1f), strokeWidth = stroke)
        // Lower loop
        drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.8f, h * 0.7f), strokeWidth = stroke)
        drawLine(color, Offset(w * 0.8f, h * 0.7f), Offset(w * 0.5f, h * 0.9f), strokeWidth = stroke)
    }
}
