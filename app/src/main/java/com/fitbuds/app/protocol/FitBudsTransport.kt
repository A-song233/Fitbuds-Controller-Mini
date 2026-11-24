package com.fitbuds.app.protocol

import java.io.Closeable
import java.io.IOException

class RfcommException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class ProtocolException(
    message: String,
) : RuntimeException(message)

interface FitBudsTransport : Closeable {
    val isDisconnected: Boolean

    suspend fun write(bytes: ByteArray)

    suspend fun receiveFrameMatching(
        timeoutMs: Long,
        predicate: (ByteArray) -> Boolean,
    ): ByteArray?

    suspend fun drainIncoming()

    suspend fun waitForDisconnect(timeoutMs: Long): Boolean

    fun checkConnected()
}
