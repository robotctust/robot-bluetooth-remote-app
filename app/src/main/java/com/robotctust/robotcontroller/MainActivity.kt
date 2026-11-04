package com.robotctust.robotcontroller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.robotctust.robotcontroller.bluetooth.BluetoothHelper
import com.robotctust.robotcontroller.ui.screens.CarControlScreen
import com.robotctust.robotcontroller.ui.screens.MainMenuScreen
import com.robotctust.robotcontroller.ui.screens.RoboticArmControlScreen
import com.robotctust.robotcontroller.ui.theme.SelfdrivingCarRemoteControlTheme

enum class AppScreen {
    MAIN_MENU,
    CAR_CONTROL,
    ROBOTIC_ARM_CONTROL
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SelfdrivingCarRemoteControlTheme {
                val context = LocalContext.current
                val bluetoothHelper = remember { BluetoothHelper(context.applicationContext) }

                // State to track if Bluetooth permissions are granted
                var hasBluetoothPermission by remember {
                    mutableStateOf(checkBluetoothPermissions(context))
                }

                // Register launcher for requesting multiple permissions at runtime
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasBluetoothPermission = permissions.entries.all { it.value }
                }

                // Helper to launch permission request
                val requestPermissions = {
                    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                    } else {
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN
                        )
                    }
                    permissionLauncher.launch(permissionsToRequest)
                }

                // Screen Navigation State
                var currentScreen by remember { mutableStateOf(AppScreen.MAIN_MENU) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Make sure screen fills size, margins handled inside screens
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            AppScreen.MAIN_MENU -> {
                                MainMenuScreen(
                                    onSelectNormalCar = {
                                        currentScreen = AppScreen.CAR_CONTROL
                                    },
                                    onSelectRoboticArm = {
                                        currentScreen = AppScreen.ROBOTIC_ARM_CONTROL
                                    }
                                )
                            }
                            AppScreen.CAR_CONTROL -> {
                                CarControlScreen(
                                    bluetoothHelper = bluetoothHelper,
                                    hasBluetoothPermission = hasBluetoothPermission,
                                    onRequestPermission = requestPermissions,
                                    onBack = {
                                        bluetoothHelper.disconnect()
                                        currentScreen = AppScreen.MAIN_MENU
                                    }
                                )
                            }
                            AppScreen.ROBOTIC_ARM_CONTROL -> {
                                RoboticArmControlScreen(
                                    bluetoothHelper = bluetoothHelper,
                                    hasBluetoothPermission = hasBluetoothPermission,
                                    onRequestPermission = requestPermissions,
                                    onBack = {
                                        bluetoothHelper.disconnect()
                                        currentScreen = AppScreen.MAIN_MENU
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if appropriate permissions are granted according to API level.
     */
    private fun checkBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}