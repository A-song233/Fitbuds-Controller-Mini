package com.fitbuds.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitbuds.app.controller.FitBudsViewModel
import com.fitbuds.app.ui.FitBudsScreen
import com.fitbuds.app.ui.theme.FitbudsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FitBudsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        viewModel.onPermissionUpdated()
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        viewModel.refreshAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!viewModel.hasConnectPermission()) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        setContent {
            FitbudsTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> viewModel.onForegroundChanged(true)
                            Lifecycle.Event.ON_STOP -> viewModel.onForegroundChanged(false)
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                FitBudsScreen(
                    uiState = uiState,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    },
                    onEnableBluetooth = {
                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        runCatching { enableBluetoothLauncher.launch(intent) }
                            .onFailure { openBluetoothSettings() }
                    },
                    onOpenBluetoothSettings = ::openBluetoothSettings,
                    onOpenAppSettings = ::openAppSettings,
                    onRefresh = { viewModel.refreshAll(fromPullGesture = false) },
                    onPullRefresh = { viewModel.refreshAll(fromPullGesture = true) },
                    onSelectDevice = viewModel::selectDevice,
                    onResetAutoDevice = viewModel::resetAutoDevice,
                    onSelectNoiseMode = viewModel::setNoiseMode,
                    onSelectEqPreset = viewModel::setEqPreset,
                    onToggleGameMode = viewModel::setGameMode,
                    onSetGesture = viewModel::setGesture,
                    onSetTimerMinutes = viewModel::setAutoPowerOffTimer,
                    onFindEarbuds = viewModel::findEarbuds,
                    onStopFindEarbuds = viewModel::stopFindEarbuds,
                    onPowerOff = viewModel::powerOffEarbuds,
                )
            }
        }
    }

    private fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        startActivity(intent)
    }
}
