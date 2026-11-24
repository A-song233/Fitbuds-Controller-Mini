package com.fitbuds.app.protocol

/**
 * Incremental RFCOMM response frame parser matching `Rfcomm::take_frame()` in `fitbuds_protocol.hpp`.
 * Frames start with `0xBB 0xEC`, carry a big-endian 16-bit payload length at [3..4],
 * and end with a single additive checksum byte over all preceding frame bytes.
 */
class FrameParser {
    private var buffer = ByteArray(256)
    private var size = 0

    fun feed(chunk: ByteArray, length: Int = chunk.size): List<ByteArray> {
        if (length <= 0) return emptyList()
        ensureCapacity(size + length)
        System.arraycopy(chunk, 0, buffer, size, length)
        size += length
        return drainValidFrames()
    }

    fun clear() {
        size = 0
    }

    private fun drainValidFrames(): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        while (size >= 2) {
            val prefixIndex = findPrefixIndex()
            if (prefixIndex < 0) {
                if (buffer[size - 1] == FitBudsProtocol.RESP_PREFIX_0) {
                    buffer[0] = FitBudsProtocol.RESP_PREFIX_0
                    size = 1
                } else {
                    size = 0
                }
                break
            }
            if (prefixIndex > 0) {
                consumePrefix(prefixIndex)
            }
            if (size < 6) break

            val payloadLen = ((buffer[3].toInt() and 0xFF) shl 8) or (buffer[4].toInt() and 0xFF)
            val frameSize = 6 + payloadLen
            if (frameSize > MAX_FRAME_SIZE) {
                consumePrefix(1)
                continue
            }
            if (size < frameSize) break

            val expectedChecksum = FitBudsProtocol.checksum(buffer, frameSize - 1)
            val actualChecksum = buffer[frameSize - 1].toInt() and 0xFF
            if (expectedChecksum == actualChecksum) {
                frames.add(buffer.copyOfRange(0, frameSize))
                consumePrefix(frameSize)
            } else {
                consumePrefix(1)
            }
        }
        return frames
    }

    private fun findPrefixIndex(): Int {
        for (i in 0 until size - 1) {
            if (buffer[i] == FitBudsProtocol.RESP_PREFIX_0 &&
                buffer[i + 1] == FitBudsProtocol.RESP_PREFIX_1
            ) {
                return i
            }
        }
        return -1
    }

    private fun consumePrefix(count: Int) {
        if (count <= 0) return
        if (count >= size) {
            size = 0
            return
        }
        System.arraycopy(buffer, count, buffer, 0, size - count)
        size -= count
    }

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var newCapacity = buffer.size
        while (newCapacity < required) {
            newCapacity *= 2
        }
        buffer = buffer.copyOf(newCapacity)
    }

    companion object {
        const val MAX_FRAME_SIZE: Int = 1024
    }
}
