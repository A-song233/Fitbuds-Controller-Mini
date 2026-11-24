@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fitbuds.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fitbuds.app.bluetooth.PairedDeviceInfo
import com.fitbuds.app.controller.ConnectionPhase
import com.fitbuds.app.controller.FitBudsUiState
import com.fitbuds.app.protocol.BatteryStatus
import com.fitbuds.app.protocol.EqPreset
import com.fitbuds.app.protocol.FindEarTarget
import com.fitbuds.app.protocol.FitBudsProtocol
import com.fitbuds.app.protocol.GestureAction
import com.fitbuds.app.protocol.GestureSetting
import com.fitbuds.app.protocol.GestureSlot
import com.fitbuds.app.protocol.NoiseMode
import com.fitbuds.app.ui.components.BatteryHeroCard
import com.fitbuds.app.ui.components.DeviceActionsSection
import com.fitbuds.app.ui.components.DevicePickerSheet
import com.fitbuds.app.ui.components.EqPresetSection
import com.fitbuds.app.ui.components.FindEarbudsSection
import com.fitbuds.app.ui.components.NoiseControlSection
import com.fitbuds.app.ui.components.OnboardingStateCard
import com.fitbuds.app.ui.components.TouchControlsSection
import com.fitbuds.app.ui.theme.FitbudsTheme

@Composable
fun FitBudsScreen(
    uiState: FitBudsUiState,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPullRefresh: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onResetAutoDevice: () -> Unit,
    onSelectNoiseMode: (NoiseMode) -> Unit,
    onSelectEqPreset: (EqPreset) -> Unit,
    onToggleGameMode: (Boolean) -> Unit,
    onSetGesture: (GestureSetting) -> Unit,
    onSetTimerMinutes: (Int) -> Unit,
    onFindEarbuds: (FindEarTarget) -> Unit,
    onStopFindEarbuds: () -> Unit,
    onPowerOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pullRefreshState = rememberPullToRefreshState()
    var showDevicePicker by remember { mutableStateOf(false) }

    val subtitleText = when (uiState.phase) {
        ConnectionPhase.NO_PERMISSION -> "等待授予蓝牙连接权限"
        ConnectionPhase.BLUETOOTH_OFF -> "请开启系统蓝牙"
        ConnectionPhase.NO_DEVICE -> "未找到已配对耳机"
        ConnectionPhase.CONNECTING -> "正在建立经典蓝牙控制会话…"
        ConnectionPhase.CONNECTED -> {
            val deviceName = uiState.connectedDevice?.name ?: "FitBuds Pro"
            val wear = uiState.battery?.wearMode?.label ?: "已连接"
            "$deviceName · $wear"
        }

        ConnectionPhase.DISCONNECTED -> "蓝牙控制通道已断开"
        ConnectionPhase.POWERED_OFF -> "耳机已远程关机"
    }

    val controlsEnabled = uiState.phase == ConnectionPhase.CONNECTED &&
        (uiState.battery == null || uiState.battery.present != 0)
    val canFindBase = uiState.phase != ConnectionPhase.NO_PERMISSION &&
        uiState.phase != ConnectionPhase.BLUETOOTH_OFF &&
        uiState.phase != ConnectionPhase.NO_DEVICE &&
        uiState.phase != ConnectionPhase.POWERED_OFF
    val canRefresh = uiState.phase != ConnectionPhase.NO_PERMISSION &&
        uiState.phase != ConnectionPhase.BLUETOOTH_OFF &&
        !uiState.isRefreshing
    val pullRefreshEnabled = uiState.phase == ConnectionPhase.CONNECTED

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("FitBuds Pro") },
                subtitle = { Text(subtitleText) },
                actions = {
                    FilledTonalIconButton(
                        onClick = { showDevicePicker = true },
                        enabled = uiState.phase != ConnectionPhase.NO_PERMISSION &&
                            uiState.phase != ConnectionPhase.BLUETOOTH_OFF,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = "选择蓝牙设备",
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onRefresh,
                        enabled = canRefresh,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新电量与设置",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isPullRefreshing,
            onRefresh = onPullRefresh,
            state = pullRefreshState,
            enabled = pullRefreshEnabled,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isPullRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val showSetupAtTop = uiState.phase == ConnectionPhase.NO_PERMISSION ||
                    uiState.phase == ConnectionPhase.BLUETOOTH_OFF ||
                    uiState.phase == ConnectionPhase.NO_DEVICE

                if (showSetupAtTop) {
                    OnboardingStateCard(
                        phase = uiState.phase,
                        statusMessage = uiState.statusMessage,
                        onRequestPermission = onRequestPermission,
                        onEnableBluetooth = onEnableBluetooth,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onOpenAppSettings = onOpenAppSettings,
                        onOpenDevicePicker = { showDevicePicker = true },
                        onReconnect = onRefresh,
                    )
                } else {
                    BatteryHeroCard(uiState = uiState)

                    AnimatedVisibility(
                        visible = uiState.phase == ConnectionPhase.DISCONNECTED ||
                            uiState.phase == ConnectionPhase.POWERED_OFF,
                    ) {
                        OnboardingStateCard(
                            phase = uiState.phase,
                            statusMessage = uiState.statusMessage,
                            onRequestPermission = onRequestPermission,
                            onEnableBluetooth = onEnableBluetooth,
                            onOpenBluetoothSettings = onOpenBluetoothSettings,
                            onOpenAppSettings = onOpenAppSettings,
                            onOpenDevicePicker = { showDevicePicker = true },
                            onReconnect = onRefresh,
                        )
                    }

                    NoiseControlSection(
                        activeMode = uiState.noiseMode,
                        battery = uiState.battery,
                        controlsEnabled = controlsEnabled,
                        onSelectMode = onSelectNoiseMode,
                    )

                    EqPresetSection(
                        activePreset = uiState.eqPreset,
                        controlsEnabled = controlsEnabled,
                        onSelectPreset = onSelectEqPreset,
                    )

                    TouchControlsSection(
                        gestures = uiState.gestures,
                        controlsEnabled = controlsEnabled,
                        onSetGesture = onSetGesture,
                    )

                    FindEarbudsSection(
                        battery = uiState.battery,
                        canFindBase = canFindBase,
                        isPlaying = uiState.isFindAudioPlaying,
                        onFindEarbuds = onFindEarbuds,
                        onStopFindEarbuds = onStopFindEarbuds,
                    )

                    DeviceActionsSection(
                        gameModeEnabled = uiState.gameModeEnabled,
                        timerMinutes = uiState.timerMinutes,
                        controlsEnabled = controlsEnabled,
                        isRefreshing = uiState.isRefreshing,
                        showInlineRefreshSpinner = uiState.isRefreshing &&
                            !uiState.isPullRefreshing &&
                            uiState.phase == ConnectionPhase.CONNECTED,
                        lastUpdatedMillis = uiState.lastUpdatedMillis,
                        onToggleGameMode = onToggleGameMode,
                        onSetTimerMinutes = onSetTimerMinutes,
                        onRefresh = onRefresh,
                        onPowerOffConfirmed = onPowerOff,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDevicePicker) {
        DevicePickerSheet(
            devices = uiState.pairedDevices,
            selectedAddress = uiState.selectedDeviceAddress,
            connectedAddress = uiState.connectedDevice?.address,
            onSelectDevice = onSelectDevice,
            onResetAutoDetect = onResetAutoDevice,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
            onDismiss = { showDevicePicker = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FitBudsScreenConnectedPreview() {
    FitbudsTheme(dynamicColor = false) {
        FitBudsScreen(
            uiState = FitBudsUiState(
                phase = ConnectionPhase.CONNECTED,
                connectedDevice = PairedDeviceInfo(
                    name = "EDIFIER FitBuds Pro",
                    address = "11:22:33:44:55:66",
                    isFitBuds = true,
                    isConnected = true,
                ),
                battery = BatteryStatus(
                    present = 3,
                    left = 85,
                    right = 90,
                    caseLevel = 64,
                ),
                noiseMode = NoiseMode.DEEP,
                eqPreset = EqPreset.ROCK,
                gameModeEnabled = true,
                gestures = mapOf(
                    GestureSlot.LEFT_DOUBLE_TAP to GestureSetting(
                        slot = GestureSlot.LEFT_DOUBLE_TAP,
                        action = GestureAction.PLAY_PAUSE,
                    ),
                    GestureSlot.LEFT_LONG_PRESS to GestureSetting(
                        slot = GestureSlot.LEFT_LONG_PRESS,
                        action = GestureAction.ANC_CYCLE,
                        cycleMask = FitBudsProtocol.DEFAULT_ANC_CYCLE,
                    ),
                ),
                timerMinutes = 30,
                statusMessage = "设置已确认",
            ),
            onRequestPermission = {},
            onEnableBluetooth = {},
            onOpenBluetoothSettings = {},
            onOpenAppSettings = {},
            onRefresh = {},
            onPullRefresh = {},
            onSelectDevice = {},
            onResetAutoDevice = {},
            onSelectNoiseMode = {},
            onSelectEqPreset = {},
            onToggleGameMode = {},
            onSetGesture = {},
            onSetTimerMinutes = {},
            onFindEarbuds = {},
            onStopFindEarbuds = {},
            onPowerOff = {},
        )
    }
}
