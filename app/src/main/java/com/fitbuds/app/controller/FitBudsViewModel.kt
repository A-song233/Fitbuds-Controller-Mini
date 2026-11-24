package com.fitbuds.app.controller

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.fitbuds.app.audio.AndroidFindEarbudsAudioPlayer
import com.fitbuds.app.bluetooth.AndroidBluetoothConnector
import com.fitbuds.app.protocol.EqPreset
import com.fitbuds.app.protocol.FindEarTarget
import com.fitbuds.app.protocol.FitBudsCommand
import com.fitbuds.app.protocol.GestureSetting
import com.fitbuds.app.protocol.NoiseMode
import kotlinx.coroutines.flow.StateFlow

class FitBudsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val connector = AndroidBluetoothConnector(application)
    private val findAudioPlayer = AndroidFindEarbudsAudioPlayer(application)
    private val controller = FitBudsController(
        connector = connector,
        findAudioPlayer = findAudioPlayer,
        initialSelectedAddress = prefs.getString(KEY_SELECTED_ADDRESS, null),
    )

    val uiState: StateFlow<FitBudsUiState> = controller.uiState

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    controller.onBluetoothStateChanged()
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java,
                    )
                    controller.onAclChanged(device?.address, connected = true)
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java,
                    )
                    controller.onAclChanged(device?.address, connected = false)
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            application,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun hasConnectPermission(): Boolean = connector.hasConnectPermission()

    fun onPermissionUpdated() {
        controller.onPermissionUpdated()
    }

    fun onForegroundChanged(foreground: Boolean) {
        controller.onForegroundChanged(foreground)
    }

    fun refreshAll(fromPullGesture: Boolean = false) {
        controller.refreshAll(fromPullGesture = fromPullGesture)
    }

    fun selectDevice(address: String) {
        prefs.edit().putString(KEY_SELECTED_ADDRESS, address).apply()
        controller.selectDevice(address)
    }

    fun resetAutoDevice() {
        prefs.edit().remove(KEY_SELECTED_ADDRESS).apply()
        controller.selectDevice(null)
    }

    fun setNoiseMode(mode: NoiseMode) {
        controller.enqueueCommand(FitBudsCommand.SetNoiseMode(mode))
    }

    fun setEqPreset(preset: EqPreset) {
        controller.enqueueCommand(FitBudsCommand.SetEqPreset(preset))
    }

    fun setGameMode(enabled: Boolean) {
        controller.enqueueCommand(FitBudsCommand.SetGameMode(enabled))
    }

    fun setGesture(setting: GestureSetting) {
        controller.enqueueCommand(FitBudsCommand.SetGesture(setting))
    }

    fun setAutoPowerOffTimer(minutes: Int) {
        controller.enqueueCommand(FitBudsCommand.SetAutoPowerOffTimer(minutes))
    }

    fun findEarbuds(target: FindEarTarget) {
        controller.enqueueFindEarbuds(target)
    }

    fun stopFindEarbuds() {
        controller.stopFindEarbuds()
    }

    fun powerOffEarbuds() {
        controller.enqueueCommand(FitBudsCommand.PowerOff)
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(bluetoothReceiver) }
        controller.close()
        connector.close()
        super.onCleared()
    }

    private companion object {
        const val PREFS_NAME = "fitbuds_prefs"
        const val KEY_SELECTED_ADDRESS = "selected_device_address"
    }
}
