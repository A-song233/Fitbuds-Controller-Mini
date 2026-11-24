package com.fitbuds.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.fitbuds.app.protocol.FitBudsProtocol
import com.fitbuds.app.protocol.FitBudsTransport
import com.fitbuds.app.protocol.FrameParser
import com.fitbuds.app.protocol.RfcommException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface BluetoothConnector : Closeable {
    fun hasConnectPermission(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun listPairedDevices(): List<PairedDeviceInfo>
    fun findPreferredDevice(preferredAddress: String?): PairedDeviceInfo?
    fun updateAclConnectionState(address: String?, connected: Boolean)
    suspend fun connect(address: String): FitBudsTransport
}

class AndroidBluetoothConnector(
    context: Context,
) : BluetoothConnector {

    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val a2dpProxy = AtomicReference<BluetoothProfile?>(null)
    private val headsetProxy = AtomicReference<BluetoothProfile?>(null)
    private val aclConnectedAddresses = ConcurrentHashMap.newKeySet<String>()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy.set(proxy)
                BluetoothProfile.HEADSET -> headsetProxy.set(proxy)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy.set(null)
                BluetoothProfile.HEADSET -> headsetProxy.set(null)
            }
        }
    }

    init {
        adapter?.let { btAdapter ->
            try {
                btAdapter.getProfileProxy(appContext, profileListener, BluetoothProfile.A2DP)
                btAdapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET)
            } catch (_: SecurityException) {
                // Permission might not be granted yet at creation time.
            }
        }
    }

    override fun hasConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    override fun updateAclConnectionState(address: String?, connected: Boolean) {
        if (address.isNullOrBlank()) return
        if (connected) {
            aclConnectedAddresses.add(address)
        } else {
            aclConnectedAddresses.remove(address)
        }
    }

    @SuppressLint("MissingPermission")
    override fun listPairedDevices(): List<PairedDeviceInfo> {
        if (!hasConnectPermission()) return emptyList()
        val btAdapter = adapter ?: return emptyList()
        if (!btAdapter.isEnabled) return emptyList()

        val connectedAddresses = resolveConnectedAddresses()
        val bonded = try {
            btAdapter.bondedDevices.orEmpty()
        } catch (_: SecurityException) {
            emptySet()
        }

        return bonded.map { device ->
            val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
            PairedDeviceInfo(
                name = name,
                address = device.address,
                isFitBuds = FitBudsProtocol.isFitBudsName(name),
                isConnected = device.address in connectedAddresses,
            )
        }.sortedWith(
            compareByDescending<PairedDeviceInfo> { it.isFitBuds && it.isConnected }
                .thenByDescending { it.isFitBuds }
                .thenByDescending { it.isConnected }
                .thenBy { it.name.lowercase() },
        )
    }

    override fun findPreferredDevice(preferredAddress: String?): PairedDeviceInfo? {
        val devices = listPairedDevices()
        if (!preferredAddress.isNullOrBlank()) {
            val matched = devices.firstOrNull { it.address.equals(preferredAddress, ignoreCase = true) }
            if (matched != null) return matched
        }
        return devices.firstOrNull { it.isFitBuds && it.isConnected }
            ?: devices.firstOrNull { it.isFitBuds }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String): FitBudsTransport {
        if (!hasConnectPermission()) {
            throw RfcommException("缺少蓝牙连接权限")
        }
        val btAdapter = adapter ?: throw RfcommException("当前设备不支持蓝牙")
        if (!btAdapter.isEnabled) {
            throw RfcommException("系统蓝牙未开启")
        }

        val device = try {
            btAdapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw RfcommException("无效的蓝牙设备地址: $address", e)
        }

        return try {
            connectSocketWithTimeout(device, secure = true)
        } catch (e: CancellationException) {
            throw e
        } catch (firstError: IOException) {
            try {
                connectSocketWithTimeout(device, secure = false)
            } catch (e: CancellationException) {
                throw e
            } catch (secondError: IOException) {
                throw RfcommException(
                    "RFCOMM 连接失败: ${secondError.message ?: firstError.message ?: "未知错误"}",
                    secondError,
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectSocketWithTimeout(
        device: BluetoothDevice,
        secure: Boolean,
    ): FitBudsTransport = withContext(Dispatchers.IO) {
        withTimeout(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val socket = try {
                    if (secure) {
                        device.createRfcommSocketToServiceRecord(FitBudsProtocol.SERVICE_UUID)
                    } else {
                        device.createInsecureRfcommSocketToServiceRecord(FitBudsProtocol.SERVICE_UUID)
                    }
                } catch (e: SecurityException) {
                    continuation.resumeWithException(RfcommException("缺少蓝牙连接权限", e))
                    return@suspendCancellableCoroutine
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    try {
                        socket.close()
                    } catch (_: IOException) {
                    }
                }

                try {
                    // Never call cancelDiscovery() — only paired devices are queried.
                    socket.connect()
                    if (continuation.isActive) {
                        continuation.resume(AndroidRfcommTransport(socket))
                    } else {
                        try {
                            socket.close()
                        } catch (_: IOException) {
                        }
                    }
                } catch (e: SecurityException) {
                    try {
                        socket.close()
                    } catch (_: IOException) {
                    }
                    if (continuation.isActive) {
                        continuation.resumeWithException(RfcommException("缺少蓝牙连接权限", e))
                    }
                } catch (e: IOException) {
                    try {
                        socket.close()
                    } catch (_: IOException) {
                    }
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveConnectedAddresses(): Set<String> {
        val result = HashSet<String>(aclConnectedAddresses)
        if (!hasConnectPermission()) return result
        listOfNotNull(a2dpProxy.get(), headsetProxy.get()).forEach { proxy ->
            try {
                proxy.connectedDevices.forEach { device ->
                    result.add(device.address)
                }
            } catch (_: SecurityException) {
            }
        }
        return result
    }

    override fun close() {
        val btAdapter = adapter ?: return
        a2dpProxy.getAndSet(null)?.let { proxy ->
            runCatching { btAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
        }
        headsetProxy.getAndSet(null)?.let { proxy ->
            runCatching { btAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}

private class AndroidRfcommTransport(
    private val socket: BluetoothSocket,
) : FitBudsTransport {

    private val disconnectedFlag = AtomicBoolean(false)
    private val frameChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val parser = FrameParser()
    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val isDisconnected: Boolean
        get() = disconnectedFlag.get()

    init {
        readerScope.launch {
            val buffer = ByteArray(2048)
            try {
                val input = socket.inputStream
                while (!disconnectedFlag.get()) {
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    val frames = parser.feed(buffer, read)
                    for (frame in frames) {
                        frameChannel.trySend(frame)
                    }
                }
            } catch (_: IOException) {
                // Socket closed or link lost.
            } finally {
                disconnectedFlag.set(true)
                frameChannel.close()
            }
        }
    }

    override fun checkConnected() {
        if (disconnectedFlag.get() || !socket.isConnected) {
            throw RfcommException("RFCOMM disconnected")
        }
    }

    override suspend fun write(bytes: ByteArray) {
        checkConnected()
        try {
            withContext(Dispatchers.IO) {
                withTimeout(WRITE_TIMEOUT_MS) {
                    val output = socket.outputStream
                    output.write(bytes)
                    output.flush()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            disconnectedFlag.set(true)
            throw RfcommException("RFCOMM send failed", e)
        }
    }

    override suspend fun drainIncoming() {
        while (frameChannel.tryReceive().getOrNull() != null) {
            // Discard stale frames queued before the new request.
        }
    }

    override suspend fun receiveFrameMatching(
        timeoutMs: Long,
        predicate: (ByteArray) -> Boolean,
    ): ByteArray? {
        val deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            val remainingMs = ((deadlineNs - System.nanoTime()) / 1_000_000L)
            if (remainingMs <= 0L) {
                if (disconnectedFlag.get()) {
                    throw RfcommException("RFCOMM disconnected before reply")
                }
                return null
            }
            val result = withTimeoutOrNull(remainingMs) {
                frameChannel.receiveCatching()
            } ?: return null

            if (result.isClosed) {
                disconnectedFlag.set(true)
                throw RfcommException("RFCOMM disconnected before reply")
            }
            val packet = result.getOrNull() ?: continue
            if (predicate(packet)) {
                return packet
            }
        }
    }

    override suspend fun waitForDisconnect(timeoutMs: Long): Boolean {
        if (disconnectedFlag.get()) return true
        val deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L
        while (!disconnectedFlag.get()) {
            val remainingMs = ((deadlineNs - System.nanoTime()) / 1_000_000L)
            if (remainingMs <= 0L) break
            val result = withTimeoutOrNull(remainingMs) {
                frameChannel.receiveCatching()
            } ?: break
            if (result.isClosed) {
                disconnectedFlag.set(true)
                return true
            }
        }
        return disconnectedFlag.get()
    }

    override fun close() {
        disconnectedFlag.set(true)
        try {
            socket.close()
        } catch (_: IOException) {
        }
        frameChannel.close()
        readerScope.cancel()
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 3_000L
    }
}
