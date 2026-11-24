package com.fitbuds.app.controller

import com.fitbuds.app.bluetooth.PairedDeviceInfo
import com.fitbuds.app.protocol.BatteryStatus
import com.fitbuds.app.protocol.EqPreset
import com.fitbuds.app.protocol.GestureSetting
import com.fitbuds.app.protocol.GestureSlot
import com.fitbuds.app.protocol.NoiseMode

enum class ConnectionPhase {
    NO_PERMISSION,
    BLUETOOTH_OFF,
    NO_DEVICE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    POWERED_OFF,
}

data class FitBudsUiState(
    val phase: ConnectionPhase = ConnectionPhase.CONNECTING,
    val connectedDevice: PairedDeviceInfo? = null,
    val pairedDevices: List<PairedDeviceInfo> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val battery: BatteryStatus? = null,
    val noiseMode: NoiseMode? = null,
    val eqPreset: EqPreset? = null,
    val gameModeEnabled: Boolean? = null,
    val gestures: Map<GestureSlot, GestureSetting> = emptyMap(),
    val timerMinutes: Int? = null,
    val isFindAudioPlaying: Boolean = false,
    val statusMessage: String = "正在等待电量",
    val pendingSettingsCount: Int = 0,
    val pendingCommandLabel: String? = null,
    val isRefreshing: Boolean = false,
    val isPullRefreshing: Boolean = false,
    val isWaitingSettingSlot: Boolean = false,
    val lastUpdatedMillis: Long? = null,
)
