package com.fitbuds.app.protocol

import kotlinx.coroutines.delay

/**
 * Protocol session helper over a [FitBudsTransport], mirroring the query/read/apply helpers
 * in `.WindowsApp/fitbuds_protocol.hpp`.
 */
class FitBudsSession(
    private val transport: FitBudsTransport,
) : AutoCloseable {

    val isDisconnected: Boolean
        get() = transport.isDisconnected

    fun checkConnected() {
        transport.checkConnected()
    }

    suspend fun warmup() {
        query(FitBudsProtocol.OPCODE_WARMUP, attempts = 1, timeoutMs = 250L)
    }

    suspend fun query(
        opcode: Int,
        attempts: Int = 3,
        timeoutMs: Long = 650L,
    ): ByteArray? {
        for (attempt in 0 until attempts) {
            transport.drainIncoming()
            transport.write(FitBudsProtocol.makeRequest(opcode))
            val reply = transport.receiveFrameMatching(timeoutMs) { packet ->
                packet.size >= 3 && (packet[2].toInt() and 0xFF) == opcode
            }
            if (reply != null) return reply
        }
        return null
    }

    suspend fun readBattery(
        attempts: Int = 3,
        timeoutMs: Long = 300L,
    ): BatteryStatus? {
        val reply = query(FitBudsProtocol.OPCODE_BATTERY, attempts = attempts, timeoutMs = timeoutMs)
            ?: return null
        return FitBudsProtocol.decodeBattery(reply)
    }

    suspend fun readMode(
        readbackOpcode: Int,
        attempts: Int = 1,
        timeoutMs: Long = 300L,
    ): Int? {
        val reply = query(readbackOpcode, attempts = attempts, timeoutMs = timeoutMs) ?: return null
        return FitBudsProtocol.decodeMode(readbackOpcode, reply)
    }

    suspend fun applyModeFast(command: FitBudsCommand): Boolean {
        val request = FitBudsProtocol.makeRequest(command.opcode, command.payload)
        transport.drainIncoming()
        transport.write(request)

        if (command is FitBudsCommand.PowerOff) {
            if (!transport.waitForDisconnect(5000L)) {
                throw ProtocolException("关机后未检测到断开")
            }
            return true
        }

        val ack = transport.receiveFrameMatching(250L) { packet ->
            FitBudsProtocol.matchesAck(command, request, packet)
        }
        if (command.opcode != FitBudsProtocol.OPCODE_EQ_SET) {
            return ack != null
        }

        // Every EQ preset produces the same 0xC4 reply; read 0xD5 to confirm the active preset.
        if (ack != null) delay(35L)
        return readMode(FitBudsProtocol.OPCODE_EQ_GET, attempts = 1, timeoutMs = 250L) ==
            command.targetValue
    }

    suspend fun readGesture(
        slot: GestureSlot,
        timeoutMs: Long = 300L,
    ): GestureSetting? {
        transport.drainIncoming()
        transport.write(FitBudsProtocol.makeRequest(FitBudsProtocol.OPCODE_GESTURE_READ, intArrayOf(slot.code)))
        var decoded: GestureSetting? = null
        transport.receiveFrameMatching(timeoutMs) { packet ->
            if (packet.size < 3 || (packet[2].toInt() and 0xFF) != FitBudsProtocol.OPCODE_GESTURE_READ) {
                return@receiveFrameMatching false
            }
            decoded = FitBudsProtocol.decodeGesture(packet, slot)
            decoded != null
        }
        return decoded
    }

    suspend fun writeGesture(
        setting: GestureSetting,
        timeoutMs: Long = 300L,
    ): Boolean {
        if (setting.action == GestureAction.ANC_CYCLE && !FitBudsProtocol.isValidAncCycle(setting.cycleMask)) {
            return false
        }
        val payload = if (setting.action == GestureAction.ANC_CYCLE) {
            intArrayOf(setting.slot.code, setting.action.code, setting.cycleMask)
        } else {
            intArrayOf(setting.slot.code, setting.action.code)
        }
        transport.drainIncoming()
        transport.write(FitBudsProtocol.makeRequest(FitBudsProtocol.OPCODE_GESTURE_WRITE, payload))
        val ack = transport.receiveFrameMatching(timeoutMs) { packet ->
            packet.size >= 3 &&
                (packet[2].toInt() and 0xFF) == FitBudsProtocol.OPCODE_GESTURE_WRITE &&
                FitBudsProtocol.decodeGesture(packet, setting.slot) == setting
        }
        return ack != null
    }

    suspend fun readTimer(timeoutMs: Long = 300L): Int? {
        val reply = query(FitBudsProtocol.OPCODE_TIMER_READ, attempts = 1, timeoutMs = timeoutMs) ?: return null
        return FitBudsProtocol.decodeTimer(reply)
    }

    suspend fun writeTimer(minutes: Int): Boolean {
        if (minutes !in FitBudsProtocol.TIMER_CHOICES) return false
        val opcode = if (minutes > 0) {
            FitBudsProtocol.OPCODE_TIMER_SET
        } else {
            FitBudsProtocol.OPCODE_TIMER_CANCEL
        }
        val payload = if (minutes > 0) intArrayOf(0, minutes) else intArrayOf()
        transport.drainIncoming()
        transport.write(FitBudsProtocol.makeRequest(opcode, payload))
        val ack = transport.receiveFrameMatching(300L) { packet ->
            packet.size == 7 &&
                (packet[2].toInt() and 0xFF) == opcode &&
                ((packet[5].toInt() and 0xFF) xor FitBudsProtocol.XOR_MASK) == 1
        }
        if (minutes > 0) delay(140L)
        val state = readTimer(300L)
        // D2 acknowledges cancellation directly; D1 acknowledges write without echoing minutes.
        return if (minutes > 0) {
            state == minutes
        } else {
            state?.let { it == 0 } ?: (ack != null)
        }
    }

    override fun close() {
        transport.close()
    }
}
