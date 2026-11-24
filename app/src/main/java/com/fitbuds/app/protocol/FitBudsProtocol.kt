package com.fitbuds.app.protocol

import java.util.UUID

/**
 * EDIFIER FitBuds Pro classic Bluetooth RFCOMM protocol definitions and frame codecs.
 * Ported 1:1 from `.WindowsApp/fitbuds_protocol.hpp`.
 */
object FitBudsProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("edf00000-edfe-dfed-fedf-edfedfedfedf")

    const val XOR_MASK: Int = 0xA5

    const val REQ_PREFIX_0: Byte = 0xAA.toByte()
    const val REQ_PREFIX_1: Byte = 0xEC.toByte()
    const val RESP_PREFIX_0: Byte = 0xBB.toByte()
    const val RESP_PREFIX_1: Byte = 0xEC.toByte()

    const val OPCODE_WARMUP: Int = 0xD8
    const val OPCODE_BATTERY: Int = 0xF2
    const val OPCODE_NOISE_SET: Int = 0xC1
    const val OPCODE_NOISE_GET: Int = 0xCC
    const val SUBCMD_NOISE: Int = 0x16
    const val OPCODE_EQ_SET: Int = 0xC4
    const val OPCODE_EQ_GET: Int = 0xD5
    const val OPCODE_GAME_SET: Int = 0x09
    const val OPCODE_GAME_GET: Int = 0x08
    const val OPCODE_POWER_OFF: Int = 0xCE
    const val OPCODE_GESTURE_READ: Int = 0xF0
    const val OPCODE_GESTURE_WRITE: Int = 0xF1
    const val OPCODE_TIMER_SET: Int = 0xD1
    const val OPCODE_TIMER_CANCEL: Int = 0xD2
    const val OPCODE_TIMER_READ: Int = 0xD3

    const val DEFAULT_ANC_CYCLE: Int = 0x11
    val TIMER_CHOICES: List<Int> = listOf(0, 10, 20, 30, 60, 90)

    const val SETTING_INTERVAL_MS: Long = 3200L
    const val AUTO_REFRESH_INTERVAL_MS: Long = 45_000L
    const val BATTERY_MIN_GAP_MS: Long = 30_000L
    const val BATTERY_RETRY_DELAY_MS: Long = 800L

    val REFRESH_OPCODES: IntArray = intArrayOf(
        OPCODE_BATTERY,
        OPCODE_EQ_GET,
        OPCODE_GAME_GET,
        OPCODE_NOISE_GET,
    )

    val REFRESH_STEPS_COUNT: Int = REFRESH_OPCODES.size + GestureSlot.entries.size + 1

    fun isFitBudsName(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false
        return name.contains("FitBuds", ignoreCase = true)
    }

    fun checksum(bytes: ByteArray, length: Int = bytes.size): Int {
        var sum = 0
        for (i in 0 until length) {
            sum += bytes[i].toInt() and 0xFF
        }
        return sum and 0xFF
    }

    fun makeRequest(opcode: Int, payload: IntArray = intArrayOf()): ByteArray {
        val frame = ByteArray(6 + payload.size)
        frame[0] = REQ_PREFIX_0
        frame[1] = REQ_PREFIX_1
        frame[2] = (opcode and 0xFF).toByte()
        frame[3] = 0
        frame[4] = (payload.size and 0xFF).toByte()
        for (i in payload.indices) {
            frame[5 + i] = ((payload[i] and 0xFF) xor XOR_MASK).toByte()
        }
        frame[frame.lastIndex] = checksum(frame, frame.size - 1).toByte()
        return frame
    }

    private fun reportedLevel(rawByte: Byte): Int? {
        val value = rawByte.toInt() and 0xFF
        return if (value <= 100) value else null
    }

    fun decodeBattery(reply: ByteArray): BatteryStatus {
        if (reply.size != 12) {
            throw ProtocolException("Unexpected battery response length: ${reply.size}")
        }
        val payload = ByteArray(6) { index ->
            ((reply[5 + index].toInt() and 0xFF) xor XOR_MASK).toByte()
        }
        val present = payload[0].toInt() and 3
        val left = if ((present and 1) != 0) reportedLevel(payload[1]) else null
        val right = if ((present and 2) != 0) reportedLevel(payload[2]) else null
        // Zero indicates placeholder when charging case level is unavailable.
        val caseLevel = if (payload[3].toInt() != 0) reportedLevel(payload[3]) else null
        return BatteryStatus(
            present = present,
            left = left,
            right = right,
            caseLevel = caseLevel,
            rawPayload = payload,
        )
    }

    fun decodeMode(readbackOpcode: Int, reply: ByteArray): Int? {
        return when (readbackOpcode) {
            OPCODE_NOISE_GET -> {
                if (reply.size < 8) return null
                val subcommand = (reply[5].toInt() and 0xFF) xor XOR_MASK
                if (subcommand != SUBCMD_NOISE) return null
                val value = (reply[6].toInt() and 0xFF) xor XOR_MASK
                if (value in 1..5) value else null
            }

            OPCODE_EQ_GET -> {
                if (reply.size != 7) return null
                val value = (reply[5].toInt() and 0xFF) xor XOR_MASK
                if (value in 0..3) value else null
            }

            OPCODE_GAME_GET -> {
                if (reply.size != 7) return null
                val value = (reply[5].toInt() and 0xFF) xor XOR_MASK
                if (value in 0..1) value else null
            }

            else -> null
        }
    }

    fun isCommandAllowed(command: FitBudsCommand, present: Int): Boolean {
        if (present == 0) return false
        if (command is FitBudsCommand.SetNoiseMode) {
            return present == 3 || command.mode.code >= 4
        }
        return true
    }

    fun matchesAck(command: FitBudsCommand, request: ByteArray, packet: ByteArray): Boolean {
        if (packet.size < 3 || (packet[2].toInt() and 0xFF) != command.opcode) return false
        if (command.opcode == OPCODE_EQ_SET) {
            return packet.size == 7
        }
        if (packet.size < request.size) return false
        for (i in 5 until request.size - 1) {
            if (request[i] != packet[i]) return false
        }
        return true
    }

    fun cycleCount(cycleMask: Int): Int {
        var count = 0
        var bit = 1
        while (bit <= 0x10) {
            if ((cycleMask and bit) != 0) count++
            bit = bit shl 1
        }
        return count
    }

    fun isValidAncCycle(cycleMask: Int): Boolean {
        return (cycleMask and 0x1F.inv()) == 0 && cycleCount(cycleMask) in 2..3
    }

    fun ancCycleModes(cycleMask: Int): List<NoiseMode> {
        val modes = mutableListOf<NoiseMode>()
        if ((cycleMask and 0x01) != 0) modes.add(NoiseMode.DEEP)
        if ((cycleMask and 0x02) != 0) modes.add(NoiseMode.WIDE)
        if ((cycleMask and 0x04) != 0) modes.add(NoiseMode.WIND)
        if ((cycleMask and 0x08) != 0) modes.add(NoiseMode.AMBIENT)
        if ((cycleMask and 0x10) != 0) modes.add(NoiseMode.OFF)
        return modes
    }

    fun formatTimerMinutes(minutes: Int): String = when (minutes) {
        0 -> "不开启"
        60 -> "1 小时后"
        90 -> "1.5 小时后"
        else -> "$minutes 分钟后"
    }

    fun decodeGesture(packet: ByteArray, expectedSlot: GestureSlot): GestureSetting? {
        if (packet.size !in 8..9) return null
        val slotCode = (packet[5].toInt() and 0xFF) xor XOR_MASK
        if (slotCode != expectedSlot.code) return null
        val actionCode = (packet[6].toInt() and 0xFF) xor XOR_MASK
        val action = GestureAction.fromCode(actionCode) ?: return null
        val cycleMask = if (action == GestureAction.ANC_CYCLE && packet.size == 9) {
            (packet[7].toInt() and 0xFF) xor XOR_MASK
        } else {
            0
        }
        return GestureSetting(
            slot = expectedSlot,
            action = action,
            cycleMask = cycleMask,
        )
    }

    fun decodeTimer(packet: ByteArray): Int? {
        if (packet.size != 8) return null
        val marker = (packet[5].toInt() and 0xFF) xor XOR_MASK
        if (marker != 0) return null
        return (packet[6].toInt() and 0xFF) xor XOR_MASK
    }
}

enum class NoiseMode(
    val code: Int,
    val label: String,
    val shortLabel: String,
    val requiresBothEars: Boolean,
) {
    DEEP(1, "深度降噪", "深度降噪", true),
    WIDE(2, "宽频降噪", "宽频降噪", true),
    WIND(3, "防风噪", "防风噪", true),
    AMBIENT(4, "环境声", "环境声", false),
    OFF(5, "降噪关", "降噪关", false),
    ;

    companion object {
        fun fromCode(code: Int?): NoiseMode? = entries.firstOrNull { it.code == code }
    }
}

enum class EqPreset(
    val code: Int,
    val label: String,
    val subtitle: String,
) {
    CLASSIC(0, "经典", "均衡自然"),
    CLASSICAL(2, "古典", "清透弦乐"),
    ROCK(3, "摇滚", "澎湃低频"),
    POP(1, "流行", "人声突出"),
    ;

    companion object {
        fun fromCode(code: Int?): EqPreset? = entries.firstOrNull { it.code == code }
    }
}

enum class WearMode(val bitmask: Int, val label: String) {
    NONE(0, "无在线耳机"),
    LEFT_ONLY(1, "仅左耳"),
    RIGHT_ONLY(2, "仅右耳"),
    BOTH(3, "双耳"),
    ;

    companion object {
        fun fromBitmask(bitmask: Int): WearMode =
            entries.firstOrNull { it.bitmask == (bitmask and 3) } ?: NONE
    }
}

data class BatteryStatus(
    val present: Int,
    val left: Int?,
    val right: Int?,
    val caseLevel: Int?,
    val rawPayload: ByteArray = byteArrayOf(),
) {
    val wearMode: WearMode get() = WearMode.fromBitmask(present)
    val isLeftPresent: Boolean get() = (present and 1) != 0
    val isRightPresent: Boolean get() = (present and 2) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BatteryStatus) return false
        return present == other.present &&
            left == other.left &&
            right == other.right &&
            caseLevel == other.caseLevel &&
            rawPayload.contentEquals(other.rawPayload)
    }

    override fun hashCode(): Int {
        var result = present
        result = 31 * result + (left ?: 0)
        result = 31 * result + (right ?: 0)
        result = 31 * result + (caseLevel ?: 0)
        result = 31 * result + rawPayload.contentHashCode()
        return result
    }
}

sealed class FitBudsCommand(
    val opcode: Int,
    val payload: IntArray,
    val readbackOpcode: Int?,
    val targetValue: Int?,
    val displayName: String,
) {
    data class SetNoiseMode(val mode: NoiseMode) : FitBudsCommand(
        opcode = FitBudsProtocol.OPCODE_NOISE_SET,
        payload = intArrayOf(FitBudsProtocol.SUBCMD_NOISE, mode.code),
        readbackOpcode = FitBudsProtocol.OPCODE_NOISE_GET,
        targetValue = mode.code,
        displayName = mode.label,
    )

    data class SetEqPreset(val preset: EqPreset) : FitBudsCommand(
        opcode = FitBudsProtocol.OPCODE_EQ_SET,
        payload = intArrayOf(preset.code),
        readbackOpcode = FitBudsProtocol.OPCODE_EQ_GET,
        targetValue = preset.code,
        displayName = preset.label,
    )

    data class SetGameMode(val enabled: Boolean) : FitBudsCommand(
        opcode = FitBudsProtocol.OPCODE_GAME_SET,
        payload = intArrayOf(if (enabled) 1 else 0),
        readbackOpcode = FitBudsProtocol.OPCODE_GAME_GET,
        targetValue = if (enabled) 1 else 0,
        displayName = if (enabled) "游戏模式开启" else "游戏模式关闭",
    )

    data object PowerOff : FitBudsCommand(
        opcode = FitBudsProtocol.OPCODE_POWER_OFF,
        payload = intArrayOf(),
        readbackOpcode = null,
        targetValue = null,
        displayName = "耳机关机",
    )

    data class SetGesture(val setting: GestureSetting) : FitBudsCommand(
        opcode = FitBudsProtocol.OPCODE_GESTURE_WRITE,
        payload = if (setting.action == GestureAction.ANC_CYCLE) {
            intArrayOf(setting.slot.code, setting.action.code, setting.cycleMask)
        } else {
            intArrayOf(setting.slot.code, setting.action.code)
        },
        readbackOpcode = FitBudsProtocol.OPCODE_GESTURE_READ,
        targetValue = setting.action.code,
        displayName = "${setting.slot.ear.label}${setting.slot.label}: ${setting.action.label}",
    )

    data class SetAutoPowerOffTimer(val minutes: Int) : FitBudsCommand(
        opcode = if (minutes > 0) FitBudsProtocol.OPCODE_TIMER_SET else FitBudsProtocol.OPCODE_TIMER_CANCEL,
        payload = if (minutes > 0) intArrayOf(0, minutes) else intArrayOf(),
        readbackOpcode = FitBudsProtocol.OPCODE_TIMER_READ,
        targetValue = minutes,
        displayName = "定时关机: ${FitBudsProtocol.formatTimerMinutes(minutes)}",
    )

    data class FindEarbuds(
        val target: FindEarTarget,
        val expiresAtMonotonicMs: Long,
    ) : FitBudsCommand(
        opcode = 0,
        payload = intArrayOf(),
        readbackOpcode = null,
        targetValue = target.earsMask,
        displayName = "找回${target.label}",
    )
}

enum class EarSide(val bitmask: Int, val label: String) {
    LEFT(1, "左耳"),
    RIGHT(2, "右耳"),
}

enum class GestureSlot(
    val code: Int,
    val ear: EarSide,
    val label: String,
) {
    LEFT_DOUBLE_TAP(1, EarSide.LEFT, "轻触两下"),
    LEFT_TRIPLE_TAP(3, EarSide.LEFT, "轻触三下"),
    LEFT_LONG_PRESS(8, EarSide.LEFT, "长按"),
    RIGHT_DOUBLE_TAP(2, EarSide.RIGHT, "轻触两下"),
    RIGHT_TRIPLE_TAP(4, EarSide.RIGHT, "轻触三下"),
    RIGHT_LONG_PRESS(9, EarSide.RIGHT, "长按"),
    ;

    companion object {
        fun forEar(ear: EarSide): List<GestureSlot> = entries.filter { it.ear == ear }
    }
}

enum class GestureAction(
    val code: Int,
    val label: String,
) {
    NONE(1, "无"),
    PLAY_PAUSE(2, "播放/暂停"),
    PREVIOUS_TRACK(3, "上一首"),
    NEXT_TRACK(4, "下一首"),
    ANC_CYCLE(5, "降噪循环"),
    GAME_MODE(6, "游戏模式"),
    VOLUME_UP(7, "音量加"),
    VOLUME_DOWN(8, "音量减"),
    VOICE_ASSISTANT(9, "唤醒语音助手"),
    ;

    companion object {
        fun fromCode(code: Int?): GestureAction? = entries.firstOrNull { it.code == code }
    }
}

data class GestureSetting(
    val slot: GestureSlot,
    val action: GestureAction,
    val cycleMask: Int = 0,
) {
    fun summary(): String {
        if (action != GestureAction.ANC_CYCLE) return action.label
        val modes = FitBudsProtocol.ancCycleModes(cycleMask)
        if (modes.isEmpty()) return action.label
        return "${action.label} · ${modes.joinToString(" / ") { it.shortLabel }}"
    }
}

enum class FindEarTarget(
    val earsMask: Int,
    val label: String,
) {
    LEFT(1, "左耳"),
    RIGHT(2, "右耳"),
    BOTH(3, "双耳"),
}
