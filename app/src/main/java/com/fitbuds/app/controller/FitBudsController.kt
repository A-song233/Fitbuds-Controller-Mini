package com.fitbuds.app.controller

import com.fitbuds.app.audio.FindAudioResult
import com.fitbuds.app.audio.FindEarbudsAudioPlayer
import com.fitbuds.app.bluetooth.BluetoothConnector
import com.fitbuds.app.bluetooth.PairedDeviceInfo
import com.fitbuds.app.protocol.BatteryStatus
import com.fitbuds.app.protocol.EqPreset
import com.fitbuds.app.protocol.FindEarTarget
import com.fitbuds.app.protocol.FitBudsCommand
import com.fitbuds.app.protocol.FitBudsProtocol
import com.fitbuds.app.protocol.FitBudsSession
import com.fitbuds.app.protocol.GestureSetting
import com.fitbuds.app.protocol.GestureSlot
import com.fitbuds.app.protocol.NoiseMode
import com.fitbuds.app.protocol.ProtocolException
import com.fitbuds.app.protocol.RfcommException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable

/**
 * Sequential RFCOMM task processor faithfully porting `TrayApp::process_tasks`
 * from `.WindowsApp/fitbuds_tray.cpp`.
 */
class FitBudsController(
    private val connector: BluetoothConnector,
    private val findAudioPlayer: FindEarbudsAudioPlayer,
    initialSelectedAddress: String? = null,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val wakeupChannel = Channel<Unit>(Channel.CONFLATED)

    private val commandsPending = ArrayDeque<FitBudsCommand>()
    private var fullRefreshRequested = false
    private var batteryRefreshRequested = false
    private var forceReconnectRequested = false
    private var pullRefreshActive = false
    private var recoveringFromPowerOff = false
    private var findPending = false
    private var selectedDeviceAddress: String? = initialSelectedAddress
    private var settingsInProgress = 0
    private var activeCommandLabel: String? = null
    private var isForeground = true

    private val _uiState = MutableStateFlow(FitBudsUiState())
    val uiState: StateFlow<FitBudsUiState> = _uiState.asStateFlow()

    init {
        scope.launch { runWorkerLoop() }
        scope.launch { runPeriodicRefreshLoop() }
    }

    fun onPermissionUpdated() {
        scope.launch {
            mutex.withLock {
                if (connector.hasConnectPermission()) {
                    fullRefreshRequested = true
                }
            }
            wakeupChannel.trySend(Unit)
        }
    }

    fun onForegroundChanged(foreground: Boolean) {
        scope.launch {
            var shouldRefresh = false
            mutex.withLock {
                val becameForeground = !isForeground && foreground
                isForeground = foreground
                if (becameForeground && !recoveringFromPowerOff) {
                    batteryRefreshRequested = true
                    shouldRefresh = true
                }
            }
            if (shouldRefresh || foreground) {
                wakeupChannel.trySend(Unit)
            }
        }
    }

    fun refreshAll(fromPullGesture: Boolean = false) {
        scope.launch {
            mutex.withLock {
                fullRefreshRequested = true
                recoveringFromPowerOff = false
                if (fromPullGesture) {
                    pullRefreshActive = true
                }
            }
            if (fromPullGesture) {
                _uiState.update { it.copy(isPullRefreshing = true, isRefreshing = true) }
            }
            wakeupChannel.trySend(Unit)
        }
    }

    fun requestBatteryRefresh() {
        scope.launch {
            mutex.withLock {
                if (commandsPending.isEmpty() && !recoveringFromPowerOff) {
                    batteryRefreshRequested = true
                }
            }
            wakeupChannel.trySend(Unit)
        }
    }

    fun selectDevice(address: String?) {
        scope.launch {
            mutex.withLock {
                selectedDeviceAddress = address
                recoveringFromPowerOff = false
                forceReconnectRequested = true
                fullRefreshRequested = true
                pullRefreshActive = false
            }
            wakeupChannel.trySend(Unit)
        }
    }

    fun enqueueCommand(command: FitBudsCommand) {
        scope.launch {
            mutex.withLock {
                commandsPending.addLast(command)
                settingsInProgress++
                recoveringFromPowerOff = false
            }
            wakeupChannel.trySend(Unit)
        }
    }

    fun enqueueFindEarbuds(target: FindEarTarget) {
        scope.launch {
            var enqueued = false
            mutex.withLock {
                if (!findPending) {
                    findPending = true
                    commandsPending.addLast(
                        FitBudsCommand.FindEarbuds(
                            target = target,
                            expiresAtMonotonicMs = monotonicMs() + 8_000L,
                        ),
                    )
                    settingsInProgress++
                    enqueued = true
                }
            }
            if (enqueued) {
                wakeupChannel.trySend(Unit)
            }
        }
    }

    fun stopFindEarbuds() {
        findAudioPlayer.stop()
    }

    fun onBluetoothStateChanged() {
        scope.launch {
            mutex.withLock {
                if (connector.isBluetoothEnabled() && connector.hasConnectPermission()) {
                    fullRefreshRequested = true
                }
            }
            wakeupChannel.trySend(Unit)
        }
    }

    fun onAclChanged(address: String?, connected: Boolean) {
        connector.updateAclConnectionState(address, connected)
        scope.launch {
            mutex.withLock {
                if (connected) {
                    recoveringFromPowerOff = false
                    fullRefreshRequested = true
                } else {
                    batteryRefreshRequested = true
                }
            }
            wakeupChannel.trySend(Unit)
        }
    }

    private suspend fun runPeriodicRefreshLoop() {
        while (scope.isActive) {
            delay(FitBudsProtocol.AUTO_REFRESH_INTERVAL_MS)
            var shouldTrigger = false
            mutex.withLock {
                if (isForeground &&
                    commandsPending.isEmpty() &&
                    !recoveringFromPowerOff &&
                    connector.hasConnectPermission() &&
                    connector.isBluetoothEnabled()
                ) {
                    batteryRefreshRequested = true
                    shouldTrigger = true
                }
            }
            if (shouldTrigger) {
                wakeupChannel.trySend(Unit)
            }
        }
    }

    private suspend fun runWorkerLoop() {
        val refreshOpcodes = FitBudsProtocol.REFRESH_OPCODES
        val gestureSlots = GestureSlot.entries
        val refreshDone = FitBudsProtocol.REFRESH_STEPS_COUNT

        var session: FitBudsSession? = null
        var activeDevice: PairedDeviceInfo? = null
        var refreshStep = refreshDone
        var lastBatteryQueryMs = Long.MIN_VALUE / 4
        var nextSettingAtMs = Long.MIN_VALUE / 4
        var noiseReadAtMs = Long.MIN_VALUE / 4
        var batteryRetryAtMs = Long.MIN_VALUE / 4
        var batteryAttemptsLeft = 0

        var phase = ConnectionPhase.CONNECTING
        var battery: BatteryStatus? = null
        var noiseMode: NoiseMode? = null
        var eqPreset: EqPreset? = null
        var gameModeEnabled: Boolean? = null
        var gestures: Map<GestureSlot, GestureSetting> = emptyMap()
        var timerMinutes: Int? = null
        var findAudioPlaying = false
        var statusMessage = "正在等待电量"
        var lastUpdatedMillis: Long? = null
        var waitingSlot = false

        fun clearCachedSettings() {
            battery = null
            noiseMode = null
            eqPreset = null
            gameModeEnabled = null
            gestures = emptyMap()
            timerMinutes = null
        }

        suspend fun publish(refreshingOverride: Boolean? = null) {
            val (pendingCount, preferredAddr, pendingLabel, pullActive) = mutex.withLock {
                val keepPull = if (!fullRefreshRequested &&
                    (refreshStep >= refreshDone ||
                        (phase != ConnectionPhase.CONNECTED && phase != ConnectionPhase.CONNECTING))
                ) {
                    pullRefreshActive = false
                    false
                } else {
                    pullRefreshActive
                }
                PublishSnapshot(settingsInProgress, selectedDeviceAddress, activeCommandLabel, keepPull)
            }
            val devices = connector.listPairedDevices()
            val refreshing = refreshingOverride ?: (refreshStep < refreshDone)
            _uiState.value = FitBudsUiState(
                phase = phase,
                connectedDevice = activeDevice,
                pairedDevices = devices,
                selectedDeviceAddress = preferredAddr,
                battery = battery,
                noiseMode = noiseMode,
                eqPreset = eqPreset,
                gameModeEnabled = gameModeEnabled,
                gestures = gestures,
                timerMinutes = timerMinutes,
                isFindAudioPlaying = findAudioPlaying,
                statusMessage = statusMessage,
                pendingSettingsCount = pendingCount,
                pendingCommandLabel = pendingLabel,
                isRefreshing = refreshing,
                isPullRefreshing = pullActive && refreshing,
                isWaitingSettingSlot = waitingSlot,
                lastUpdatedMillis = lastUpdatedMillis,
            )
        }

        suspend fun closeSession() {
            session?.close()
            session = null
        }

        suspend fun clearPendingSettingsOnGateFailure() {
            mutex.withLock {
                commandsPending.clear()
                settingsInProgress = 0
                activeCommandLabel = null
                findPending = false
                fullRefreshRequested = false
                batteryRefreshRequested = false
                forceReconnectRequested = false
                pullRefreshActive = false
            }
        }

        suspend fun waitForSettingSlot(): Boolean {
            val remaining = nextSettingAtMs - monotonicMs()
            if (remaining <= 0L) return scope.isActive
            waitingSlot = true
            statusMessage = "等待耳机接收下一条设置…"
            publish(refreshingOverride = false)
            delay(remaining)
            waitingSlot = false
            return scope.isActive
        }

        suspend fun sendSetting(activeSession: FitBudsSession, command: FitBudsCommand): Boolean {
            val now = monotonicMs()
            nextSettingAtMs = now + FitBudsProtocol.SETTING_INTERVAL_MS
            if (command is FitBudsCommand.SetNoiseMode) {
                noiseReadAtMs = nextSettingAtMs
            }
            return activeSession.applyModeFast(command)
        }

        suspend fun updateBattery(activeSession: FitBudsSession, attempts: Int): Boolean {
            lastBatteryQueryMs = monotonicMs()
            val decoded = activeSession.readBattery(attempts = attempts, timeoutMs = 300L)
            if (decoded != null) {
                battery = decoded
                lastUpdatedMillis = wallClockMs()
                return true
            }
            return false
        }

        // Trigger an initial refresh on startup.
        mutex.withLock { fullRefreshRequested = true }

        try {
            while (scope.isActive) {
                if (!connector.hasConnectPermission()) {
                    closeSession()
                    activeDevice = null
                    clearCachedSettings()
                    refreshStep = refreshDone
                    batteryAttemptsLeft = 0
                    phase = ConnectionPhase.NO_PERMISSION
                    statusMessage = "需要蓝牙连接权限"
                    clearPendingSettingsOnGateFailure()
                    publish(refreshingOverride = false)
                    wakeupChannel.receive()
                    continue
                }

                if (!connector.isBluetoothEnabled()) {
                    closeSession()
                    activeDevice = null
                    clearCachedSettings()
                    refreshStep = refreshDone
                    batteryAttemptsLeft = 0
                    phase = ConnectionPhase.BLUETOOTH_OFF
                    statusMessage = "请先开启系统蓝牙"
                    clearPendingSettingsOnGateFailure()
                    publish(refreshingOverride = false)
                    wakeupChannel.receive()
                    continue
                }

                var command: FitBudsCommand? = null
                var batteryOnly = false
                var deferNoiseReadMs: Long? = null
                var waitRetryMs: Long? = null
                var shouldSleepUntilTask = false
                var preferredAddr: String? = null
                var forceReconnect = false

                mutex.withLock {
                    preferredAddr = selectedDeviceAddress
                    if (forceReconnectRequested) {
                        forceReconnect = true
                        forceReconnectRequested = false
                    }
                    when {
                        commandsPending.isNotEmpty() -> {
                            command = commandsPending.removeFirst()
                            activeCommandLabel = command?.displayName
                        }

                        fullRefreshRequested -> {
                            fullRefreshRequested = false
                            batteryRefreshRequested = false
                            batteryAttemptsLeft = 0
                            recoveringFromPowerOff = false
                            refreshStep = 0
                        }

                        refreshStep == refreshDone -> {
                            batteryRefreshRequested = false
                            pullRefreshActive = false
                            if (recoveringFromPowerOff) {
                                shouldSleepUntilTask = true
                            } else {
                                val now = monotonicMs()
                                if (batteryAttemptsLeft > 0) {
                                    if (now < batteryRetryAtMs) {
                                        waitRetryMs = batteryRetryAtMs - now
                                    } else {
                                        batteryOnly = true
                                    }
                                } else if (lastBatteryQueryMs > now - FitBudsProtocol.BATTERY_MIN_GAP_MS) {
                                    shouldSleepUntilTask = true
                                } else {
                                    batteryAttemptsLeft = 3
                                    batteryOnly = true
                                }
                            }
                        }

                        refreshStep < refreshOpcodes.size &&
                            refreshOpcodes[refreshStep] == FitBudsProtocol.OPCODE_NOISE_GET &&
                            monotonicMs() < noiseReadAtMs -> {
                            deferNoiseReadMs = noiseReadAtMs - monotonicMs()
                        }
                    }
                }

                if (deferNoiseReadMs != null) {
                    withTimeoutOrNull(deferNoiseReadMs!!.coerceAtLeast(1L)) {
                        wakeupChannel.receive()
                    }
                    continue
                }

                if (waitRetryMs != null) {
                    withTimeoutOrNull(waitRetryMs!!.coerceAtLeast(1L)) {
                        wakeupChannel.receive()
                    }
                    continue
                }

                if (shouldSleepUntilTask && !forceReconnect) {
                    wakeupChannel.receive()
                    continue
                }

                // Handle FindEarbuds audio playback independently of RFCOMM command slot spacing.
                val findCommand = command as? FitBudsCommand.FindEarbuds
                if (findCommand != null) {
                    if (monotonicMs() >= findCommand.expiresAtMonotonicMs) {
                        statusMessage = "找回确认已过期，请重新选择"
                    } else {
                        val targetAddress = activeDevice?.address
                            ?: connector.findPreferredDevice(preferredAddr)?.address
                        findAudioPlaying = true
                        statusMessage = "正在播放找回提示音…"
                        publish(refreshingOverride = false)
                        val result = if (targetAddress != null && monotonicMs() < findCommand.expiresAtMonotonicMs) {
                            findAudioPlayer.play(
                                deviceAddress = targetAddress,
                                earsMask = findCommand.target.earsMask,
                                expiresAtMonotonicMs = findCommand.expiresAtMonotonicMs,
                            )
                        } else {
                            FindAudioResult.ENDPOINT_UNAVAILABLE
                        }
                        statusMessage = when (result) {
                            FindAudioResult.PLAYED -> "找回提示音播放完毕"
                            FindAudioResult.STOPPED -> "已停止播放找回提示音"
                            FindAudioResult.ENDPOINT_UNAVAILABLE -> "找回耳机需要连接 FitBuds 的立体声音频"
                            FindAudioResult.PLAYBACK_FAILED -> "找回提示音播放失败"
                        }
                    }
                    findAudioPlaying = false
                    mutex.withLock {
                        findPending = false
                        if (settingsInProgress > 0) settingsInProgress--
                        activeCommandLabel = null
                    }
                    publish()
                    continue
                }

                val poweringOff = command is FitBudsCommand.PowerOff
                var consumedCommand = false

                try {
                    if (command != null) {
                        publish()
                        if (!waitForSettingSlot()) break
                    }

                    if (forceReconnect) {
                        closeSession()
                        activeDevice = null
                        clearCachedSettings()
                    }

                    session?.let { activeSession ->
                        try {
                            activeSession.checkConnected()
                        } catch (_: RfcommException) {
                            closeSession()
                        }
                    }

                    if (session == null) {
                        val targetDevice = connector.findPreferredDevice(preferredAddr)
                        clearCachedSettings()

                        if (targetDevice == null) {
                            activeDevice = null
                            phase = ConnectionPhase.NO_DEVICE
                            statusMessage = if (preferredAddr != null) {
                                "未找到指定设备，请检查已配对蓝牙列表"
                            } else {
                                "未找到已配对的 FitBuds，请检查系统蓝牙设置"
                            }
                            refreshStep = refreshDone
                            batteryAttemptsLeft = 0
                            mutex.withLock { pullRefreshActive = false }
                            if (command != null) {
                                consumedCommand = true
                            }
                            if (consumedCommand) {
                                mutex.withLock {
                                    if (settingsInProgress > 0) settingsInProgress--
                                    activeCommandLabel = null
                                }
                            }
                            publish(refreshingOverride = false)
                            continue
                        }

                        activeDevice = targetDevice
                        phase = ConnectionPhase.CONNECTING
                        statusMessage = "正在连接 ${targetDevice.name}…"
                        publish(refreshingOverride = true)

                        val transport = connector.connect(targetDevice.address)
                        val newSession = FitBudsSession(transport)
                        session = newSession
                        // A fresh RFCOMM connection can drop its first application frame.
                        newSession.warmup()
                        phase = ConnectionPhase.CONNECTED
                        refreshStep = 0
                        batteryOnly = false
                        batteryAttemptsLeft = 0
                        mutex.withLock { recoveringFromPowerOff = false }
                    }

                    val activeSession = session!!
                    when (val currentCommand = command) {
                        is FitBudsCommand.SetGesture -> {
                            consumedCommand = true
                            nextSettingAtMs = monotonicMs() + FitBudsProtocol.SETTING_INTERVAL_MS
                            var confirmed = activeSession.writeGesture(currentCommand.setting)
                            if (!confirmed) {
                                if (!waitForSettingSlot()) break
                                confirmed = activeSession.readGesture(currentCommand.setting.slot) ==
                                    currentCommand.setting
                                if (!confirmed) {
                                    nextSettingAtMs = monotonicMs() + FitBudsProtocol.SETTING_INTERVAL_MS
                                    confirmed = activeSession.writeGesture(currentCommand.setting)
                                }
                            }
                            gestures = if (confirmed) {
                                gestures + (currentCommand.setting.slot to currentCommand.setting)
                            } else {
                                gestures - currentCommand.setting.slot
                            }
                            statusMessage = if (confirmed) "设置已确认" else "耳机未确认该设置"
                        }

                        is FitBudsCommand.SetAutoPowerOffTimer -> {
                            consumedCommand = true
                            nextSettingAtMs = monotonicMs() + FitBudsProtocol.SETTING_INTERVAL_MS
                            val confirmed = activeSession.writeTimer(currentCommand.minutes)
                            timerMinutes = if (confirmed) currentCommand.minutes else null
                            statusMessage = if (confirmed) "设置已确认" else "耳机未确认该设置"
                        }

                        is FitBudsCommand.SetNoiseMode,
                        is FitBudsCommand.SetEqPreset,
                        is FitBudsCommand.SetGameMode,
                        FitBudsCommand.PowerOff -> {
                            consumedCommand = true
                            if (battery == null) {
                                updateBattery(activeSession, attempts = 2)
                                if (battery != null && refreshStep == 0) {
                                    refreshStep = 1
                                }
                            }

                            val currentBattery = battery
                            if (currentBattery == null) {
                                statusMessage = "未能识别当前佩戴模式"
                            } else if (!FitBudsProtocol.isCommandAllowed(currentCommand, currentBattery.present)) {
                                statusMessage = "当前佩戴模式下不可用"
                            } else if (poweringOff) {
                                sendSetting(activeSession, currentCommand)
                                statusMessage = "耳机关机后已断开"
                            } else {
                                val targetValue = currentCommand.targetValue!!
                                var confirmed = sendSetting(activeSession, currentCommand)
                                if (!confirmed) {
                                    // Check for a lost acknowledgement before retrying the same setting once.
                                    if (!waitForSettingSlot()) break
                                    val readbackValue = currentCommand.readbackOpcode?.let { opcode ->
                                        activeSession.readMode(opcode, attempts = 1, timeoutMs = 250L)
                                    }
                                    confirmed = (readbackValue == targetValue) ||
                                        sendSetting(activeSession, currentCommand)
                                }
                                applyModeUpdate(
                                    readbackOpcode = currentCommand.readbackOpcode,
                                    value = if (confirmed) targetValue else null,
                                    setNoise = { noiseMode = it },
                                    setEq = { eqPreset = it },
                                    setGame = { gameModeEnabled = it },
                                )
                                statusMessage = if (confirmed) "设置已确认" else "耳机未确认该设置"
                            }
                        }

                        is FitBudsCommand.FindEarbuds -> Unit

                        null -> {
                            if (batteryOnly) {
                                if (updateBattery(activeSession, attempts = 1)) {
                                    batteryAttemptsLeft = 0
                                    if (statusMessage == "耳机未上报电量") statusMessage = ""
                                } else if (--batteryAttemptsLeft > 0) {
                                    batteryRetryAtMs = monotonicMs() + FitBudsProtocol.BATTERY_RETRY_DELAY_MS
                                } else {
                                    battery = null
                                    statusMessage = "耳机未上报电量"
                                }
                            } else if (refreshStep == 0) {
                                publish(refreshingOverride = true)
                                if (updateBattery(activeSession, attempts = 3)) {
                                    statusMessage = ""
                                } else {
                                    battery = null
                                    statusMessage = "耳机未上报电量"
                                }
                                refreshStep++
                            } else if (refreshStep < refreshOpcodes.size) {
                                val opcode = refreshOpcodes[refreshStep++]
                                val value = activeSession.readMode(opcode, attempts = 1, timeoutMs = 300L)
                                applyModeUpdate(
                                    readbackOpcode = opcode,
                                    value = value,
                                    setNoise = { noiseMode = it },
                                    setEq = { eqPreset = it },
                                    setGame = { gameModeEnabled = it },
                                )
                            } else if (refreshStep < refreshDone - 1) {
                                val slot = gestureSlots[refreshStep++ - refreshOpcodes.size]
                                val setting = activeSession.readGesture(slot)
                                gestures = if (setting != null) {
                                    gestures + (slot to setting)
                                } else {
                                    gestures - slot
                                }
                            } else {
                                refreshStep++
                                timerMinutes = activeSession.readTimer()
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: RfcommException) {
                    closeSession()
                    refreshStep = refreshDone
                    batteryAttemptsLeft = 0
                    clearCachedSettings()
                    phase = ConnectionPhase.DISCONNECTED
                    statusMessage = "蓝牙连接已断开或无法建立"
                    mutex.withLock { pullRefreshActive = false }
                    if (command != null) consumedCommand = true
                } catch (_: ProtocolException) {
                    if (command == null) {
                        refreshStep = refreshDone
                        mutex.withLock { pullRefreshActive = false }
                    }
                    if (batteryOnly) {
                        batteryAttemptsLeft = 0
                        battery = null
                    }
                    statusMessage = if (poweringOff) "关机后未检测到断开" else "耳机回复无法确认"
                    if (command != null) consumedCommand = true
                }

                if (poweringOff) {
                    closeSession()
                    refreshStep = refreshDone
                    clearCachedSettings()
                    batteryAttemptsLeft = 0
                    phase = ConnectionPhase.POWERED_OFF
                    mutex.withLock {
                        recoveringFromPowerOff = true
                        pullRefreshActive = false
                    }
                }

                if (command != null || consumedCommand) {
                    mutex.withLock {
                        if (settingsInProgress > 0) settingsInProgress--
                        activeCommandLabel = null
                    }
                }
                publish()
            }
        } finally {
            closeSession()
        }
    }

    private inline fun applyModeUpdate(
        readbackOpcode: Int?,
        value: Int?,
        setNoise: (NoiseMode?) -> Unit,
        setEq: (EqPreset?) -> Unit,
        setGame: (Boolean?) -> Unit,
    ) {
        when (readbackOpcode) {
            FitBudsProtocol.OPCODE_NOISE_GET -> setNoise(NoiseMode.fromCode(value))
            FitBudsProtocol.OPCODE_EQ_GET -> setEq(EqPreset.fromCode(value))
            FitBudsProtocol.OPCODE_GAME_GET -> setGame(value?.let { it == 1 })
        }
    }

    override fun close() {
        findAudioPlayer.stop()
        scope.cancel()
        wakeupChannel.close()
    }

    private data class PublishSnapshot(
        val pendingCount: Int,
        val preferredAddr: String?,
        val pendingLabel: String?,
        val pullActive: Boolean,
    )
}
