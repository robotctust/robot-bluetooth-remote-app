package com.robotctust.robotcontroller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

enum class ConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BluetoothHelper(private val context: Context) {

    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName = _connectedDeviceName.asStateFlow()

    // Redundancy prevention state
    private var lastDirectionChar: Char? = null
    private var lastSpeedChar: Char? = null
    private val lastServoAngles = mutableMapOf<Int, Int>()
    private val sendMutex = Mutex()

    companion object {
        private const val TAG = "BluetoothHelper"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // 藍芽 MAC 位址與自走車輛名稱對應表 (檢測到即自動重新命名並顯示)
        val KNOWN_CAR_MACS = mapOf(
            "85:45:68:60:15:54" to "Robot-Car01",
            "85:45:68:60:15:72" to "Robot-Car02",
            "85:45:68:60:14:6D" to "Robot-Car03",
            "85:45:68:60:14:76" to "Robot-Car04",
            "85:45:68:60:14:91" to "Robot-Car05",
            "85:45:68:60:14:53" to "Robot-Car06",
            "04:46:64:23:31:80" to "Robot-Car07"
        )

        /**
         * 取得裝置顯示名稱，若 MAC 位址吻合對照表則優先顯示車輛編號 (例如 Robot-Car02)
         */
        fun getDeviceDisplayName(device: BluetoothDevice): String {
            val mac = device.address?.uppercase() ?: ""
            return KNOWN_CAR_MACS[mac] ?: try {
                device.name?.takeIf { it.isNotBlank() } ?: "未知藍牙裝置"
            } catch (e: SecurityException) {
                "未知藍牙裝置"
            }
        }

        /**
         * 取得自走車的數字編號（若為已知儲存車輛），如 Robot-Car02 -> 2
         * 若非已知車輛則回傳 null
         */
        fun getCarNumber(device: BluetoothDevice): Int? {
            val mac = device.address?.uppercase() ?: ""
            val carName = KNOWN_CAR_MACS[mac] ?: return null
            return Regex("""\d+""").find(carName)?.value?.toIntOrNull()
        }
    }

    /**
     * Checks if Bluetooth is supported and enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Retrieves the list of bonded (paired) devices.
     * 有儲存車號的車輛優先排在最前面，且按照車號由小到大 (01 -> 02 -> ... -> 07) 排序。
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            val devices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            devices.sortedWith(
                compareBy<BluetoothDevice> { device ->
                    // 1. 已儲存的車輛優先排在最前面 (0)，其他排後面 (1)
                    if (getCarNumber(device) != null) 0 else 1
                }.thenBy { device ->
                    // 2. 已儲存的車輛按照車號數字由小到大排序
                    getCarNumber(device) ?: Int.MAX_VALUE
                }.thenBy { device ->
                    // 3. 其他裝置依名稱字母排序
                    try { device.name ?: "" } catch (e: SecurityException) { "" }
                }
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permission to get paired devices", e)
            emptyList()
        }
    }

    /**
     * Connects to a selected Bluetooth device.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.CONNECTING || 
            _connectionState.value == ConnectionState.CONNECTED) {
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        _connectedDeviceName.value = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Cancel discovery if running, because it slows down connection
                    try {
                        bluetoothAdapter?.cancelDiscovery()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Could not cancel discovery", e)
                    }

                    var clientSocket: BluetoothSocket? = null
                    try {
                        clientSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        clientSocket.connect()
                    } catch (e: Exception) {
                        Log.w(TAG, "標準 SPP UUID 連線失敗，嘗試使用反射 Channel 1 Fallback (適用 BT06/HC-06)...", e)
                        try {
                            clientSocket?.close()
                        } catch (_: Exception) {}
                        
                        try {
                            // BT06 / HC-06 常見的 Channel 1 隱式連接
                            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            clientSocket = method.invoke(device, 1) as BluetoothSocket
                            clientSocket.connect()
                            Log.d(TAG, "Channel 1 Fallback 連線成功！")
                        } catch (fallbackException: Exception) {
                            Log.w(TAG, "Channel 1 失敗，嘗試 createInsecureRfcommSocketToServiceRecord...", fallbackException)
                            try {
                                clientSocket?.close()
                            } catch (_: Exception) {}

                            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                            insecureSocket.connect()
                            clientSocket = insecureSocket
                            Log.d(TAG, "Insecure SPP 連線成功！")
                        }
                    }
                    
                    socket = clientSocket
                    outputStream = clientSocket.outputStream
                    
                    // Reset redundancy states on new connection
                    lastDirectionChar = null
                    lastSpeedChar = null
                    lastServoAngles.clear()
                    
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Connection failed", e)
                    try {
                        socket?.close()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Could not close the client socket", closeException)
                    }
                    socket = null
                    outputStream = null
                    false
                }
            }

            if (result) {
                _connectionState.value = ConnectionState.CONNECTED
                _connectedDeviceName.value = getDeviceDisplayName(device)
                Log.d(TAG, "Bluetooth connected successfully: ${_connectedDeviceName.value}")

                // 藍牙連線成功後，立即發送靜止指令 'S'，並在 100ms 緩衝後再次確認發送
                // 確保透傳穩定且車輛一連上必定處於靜止狀態，直到使用者發出移動訊號
                scope.launch {
                    sendDirectionForce('S')
                    kotlinx.coroutines.delay(100)
                    sendRaw("S")
                    lastDirectionChar = 'S'
                    Log.d(TAG, "已確認發送靜止指令 'S'，車輛處於初始靜止狀態")
                }
            } else {
                _connectionState.value = ConnectionState.ERROR
                Log.d(TAG, "Bluetooth connection failed")
            }
        }
    }

    /**
     * Disconnects the current Bluetooth socket.
     */
    fun disconnect() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 中斷前主動送出停止訊號 'S'，防止斷線後車輛繼續滑行
                    try {
                        outputStream?.write("S".toByteArray())
                        outputStream?.flush()
                    } catch (_: Exception) {}
                    outputStream?.close()
                    socket?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing Bluetooth socket", e)
                } finally {
                    socket = null
                    outputStream = null
                }
            }
            _connectionState.value = ConnectionState.IDLE
            _connectedDeviceName.value = null
        }
    }

    /**
     * Sends a raw String command to the Bluetooth device.
     */
    fun sendRaw(cmd: String) {
        val out = outputStream
        if (out != null && _connectionState.value == ConnectionState.CONNECTED) {
            scope.launch(Dispatchers.IO) {
                sendMutex.withLock {
                    try {
                        val currentOut = outputStream
                        if (currentOut != null && _connectionState.value == ConnectionState.CONNECTED) {
                            currentOut.write(cmd.toByteArray())
                            currentOut.flush()
                            Log.d(TAG, "Sent raw: $cmd")
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Send failed", e)
                        // Auto-disconnect on output error
                        withContext(Dispatchers.Main) {
                            disconnect()
                            _connectionState.value = ConnectionState.ERROR
                        }
                    }
                }
            }
        }
    }

    /**
     * Sends a direction command character ('F', 'B', 'L', 'R', 'S') if it changed.
     */
    fun sendDirection(dir: Char) {
        if (dir != lastDirectionChar) {
            lastDirectionChar = dir
            sendRaw(dir.toString())
            Log.d(TAG, "Direction changed -> Sent: $dir")
        }
    }

    /**
     * 強制發送方向指令 (即使與 lastDirectionChar 相同亦強制送出，用於連線初始化或緊急停止)
     */
    fun sendDirectionForce(dir: Char) {
        lastDirectionChar = dir
        sendRaw(dir.toString())
        Log.d(TAG, "Force sent direction: $dir")
    }

    /**
     * Sends a speed command character ('0' - '9') if it changed.
     */
    fun sendSpeed(speed: Char) {
        if (speed != lastSpeedChar) {
            lastSpeedChar = speed
            sendRaw(speed.toString())
            Log.d(TAG, "Speed changed -> Sent: $speed")
        }
    }

    /**
     * Sends a robotic arm servo command: "M<servoId><3-digit angle>".
     * E.g. Servo 1 at 90 deg -> "M1090"
     */
    fun sendServoAngle(servoId: Int, angle: Int) {
        val lastAngle = lastServoAngles[servoId]
        if (angle != lastAngle) {
            lastServoAngles[servoId] = angle
            // Pad angle to 3 digits (e.g., 90 becomes 090)
            val paddedAngle = String.format("%03d", angle)
            val command = "M$servoId$paddedAngle"
            sendRaw(command)
            Log.d(TAG, "Servo $servoId angle changed -> Sent: $command")
        }
    }
}
