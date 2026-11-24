package com.fitbuds.app.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class FitBudsSessionTest {

    private fun makeResponse(opcode: Int, payload: IntArray): ByteArray {
        val frame = ByteArray(6 + payload.size)
        frame[0] = 0xBB.toByte()
        frame[1] = 0xEC.toByte()
        frame[2] = (opcode and 0xFF).toByte()
        frame[3] = ((payload.size shr 8) and 0xFF).toByte()
        frame[4] = (payload.size and 0xFF).toByte()
        for (i in payload.indices) {
            frame[5 + i] = ((payload[i] and 0xFF) xor FitBudsProtocol.XOR_MASK).toByte()
        }
        frame[frame.lastIndex] = FitBudsProtocol.checksum(frame, frame.size - 1).toByte()
        return frame
    }

    private class FakeTransport(
        private val onWrite: (FakeTransport, ByteArray) -> Unit,
    ) : FitBudsTransport {
        override var isDisconnected: Boolean = false
        val incoming = ArrayDeque<ByteArray>()
        val writes = mutableListOf<ByteArray>()
        var disconnectOnWait: Boolean = false

        override suspend fun write(bytes: ByteArray) {
            writes.add(bytes)
            onWrite(this, bytes)
        }

        override suspend fun receiveFrameMatching(
            timeoutMs: Long,
            predicate: (ByteArray) -> Boolean,
        ): ByteArray? {
            while (incoming.isNotEmpty()) {
                val next = incoming.removeFirst()
                if (predicate(next)) return next
            }
            if (isDisconnected) throw RfcommException("Disconnected")
            return null
        }

        override suspend fun drainIncoming() {
            incoming.clear()
        }

        override suspend fun waitForDisconnect(timeoutMs: Long): Boolean {
            if (disconnectOnWait) isDisconnected = true
            return isDisconnected
        }

        override fun checkConnected() {
            if (isDisconnected) throw RfcommException("Disconnected")
        }

        override fun close() {
            isDisconnected = true
        }
    }

    @Test
    fun `readBattery and warmup work over session`() = runBlocking {
        val transport = FakeTransport { fake, req ->
            val opcode = req[2].toInt() and 0xFF
            if (opcode == FitBudsProtocol.OPCODE_BATTERY) {
                fake.incoming.addLast(makeResponse(0xF2, intArrayOf(3, 88, 86, 55, 0, 0)))
            }
        }
        val session = FitBudsSession(transport)

        session.warmup()
        val battery = session.readBattery()

        assertNotNull(battery)
        assertEquals(88, battery!!.left)
        assertEquals(86, battery.right)
        assertEquals(55, battery.caseLevel)
        assertEquals(WearMode.BOTH, battery.wearMode)
    }

    @Test
    fun `applyModeFast verifies ANC payload echo and EQ D5 readback`() = runBlocking {
        val transport = FakeTransport { fake, req ->
            when (req[2].toInt() and 0xFF) {
                FitBudsProtocol.OPCODE_NOISE_SET -> {
                    fake.incoming.addLast(makeResponse(0xC1, intArrayOf(0x16, NoiseMode.WIND.code)))
                }

                FitBudsProtocol.OPCODE_EQ_SET -> {
                    // C4 always replies with generic 7-byte ack regardless of preset.
                    fake.incoming.addLast(makeResponse(0xC4, intArrayOf(0)))
                }

                FitBudsProtocol.OPCODE_EQ_GET -> {
                    fake.incoming.addLast(makeResponse(0xD5, intArrayOf(EqPreset.ROCK.code)))
                }

                FitBudsProtocol.OPCODE_GAME_SET -> {
                    fake.incoming.addLast(makeResponse(0x09, intArrayOf(1)))
                }
            }
        }
        val session = FitBudsSession(transport)

        assertTrue(session.applyModeFast(FitBudsCommand.SetNoiseMode(NoiseMode.WIND)))
        assertTrue(session.applyModeFast(FitBudsCommand.SetEqPreset(EqPreset.ROCK)))
        assertFalse(session.applyModeFast(FitBudsCommand.SetEqPreset(EqPreset.POP)))
        assertTrue(session.applyModeFast(FitBudsCommand.SetGameMode(true)))
    }

    @Test
    fun `applyModeFast handles PowerOff disconnect observation`() = runBlocking {
        val disconnectingTransport = FakeTransport { fake, _ ->
            fake.disconnectOnWait = true
        }
        val session = FitBudsSession(disconnectingTransport)
        assertTrue(session.applyModeFast(FitBudsCommand.PowerOff))
    }

    @Test(expected = ProtocolException::class)
    fun `applyModeFast throws ProtocolException if PowerOff disconnect not observed`() = runBlocking {
        val stickyTransport = FakeTransport { _, _ -> }
        val session = FitBudsSession(stickyTransport)
        session.applyModeFast(FitBudsCommand.PowerOff)
        Unit
    }

    @Test
    fun `readGesture and writeGesture verify normal and ANC cycle settings`() = runBlocking {
        var storedCycle = FitBudsProtocol.DEFAULT_ANC_CYCLE
        val transport = FakeTransport { fake, req ->
            val opcode = req[2].toInt() and 0xFF
            when (opcode) {
                FitBudsProtocol.OPCODE_GESTURE_READ -> {
                    val slot = (req[5].toInt() and 0xFF) xor FitBudsProtocol.XOR_MASK
                    if (slot == GestureSlot.LEFT_LONG_PRESS.code) {
                        fake.incoming.addLast(
                            makeResponse(
                                0xF0,
                                intArrayOf(slot, GestureAction.ANC_CYCLE.code, storedCycle),
                            ),
                        )
                    } else {
                        fake.incoming.addLast(
                            makeResponse(0xF0, intArrayOf(slot, GestureAction.NEXT_TRACK.code)),
                        )
                    }
                }

                FitBudsProtocol.OPCODE_GESTURE_WRITE -> {
                    val slot = (req[5].toInt() and 0xFF) xor FitBudsProtocol.XOR_MASK
                    val action = (req[6].toInt() and 0xFF) xor FitBudsProtocol.XOR_MASK
                    if (action == GestureAction.ANC_CYCLE.code && req.size == 9) {
                        val cycle = (req[7].toInt() and 0xFF) xor FitBudsProtocol.XOR_MASK
                        storedCycle = cycle
                        fake.incoming.addLast(makeResponse(0xF1, intArrayOf(slot, action, cycle)))
                    } else {
                        fake.incoming.addLast(makeResponse(0xF1, intArrayOf(slot, action)))
                    }
                }
            }
        }
        val session = FitBudsSession(transport)

        assertEquals(
            GestureSetting(GestureSlot.RIGHT_TRIPLE_TAP, GestureAction.NEXT_TRACK, 0),
            session.readGesture(GestureSlot.RIGHT_TRIPLE_TAP),
        )
        assertEquals(
            GestureSetting(GestureSlot.LEFT_LONG_PRESS, GestureAction.ANC_CYCLE, FitBudsProtocol.DEFAULT_ANC_CYCLE),
            session.readGesture(GestureSlot.LEFT_LONG_PRESS),
        )

        assertTrue(
            session.writeGesture(
                GestureSetting(GestureSlot.LEFT_LONG_PRESS, GestureAction.ANC_CYCLE, 0x19),
            ),
        )
        assertFalse(
            session.writeGesture(
                GestureSetting(GestureSlot.LEFT_LONG_PRESS, GestureAction.ANC_CYCLE, 0x01),
            ),
        )
        assertTrue(
            session.writeGesture(
                GestureSetting(GestureSlot.RIGHT_DOUBLE_TAP, GestureAction.VOICE_ASSISTANT),
            ),
        )
    }

    @Test
    fun `readTimer and writeTimer verify timer set and cancel`() = runBlocking {
        var timerState: Int? = 0
        val transport = FakeTransport { fake, req ->
            when (req[2].toInt() and 0xFF) {
                FitBudsProtocol.OPCODE_TIMER_SET -> {
                    timerState = (req[6].toInt() and 0xFF) xor FitBudsProtocol.XOR_MASK
                    fake.incoming.addLast(makeResponse(0xD1, intArrayOf(1)))
                }

                FitBudsProtocol.OPCODE_TIMER_CANCEL -> {
                    timerState = 0
                    fake.incoming.addLast(makeResponse(0xD2, intArrayOf(1)))
                }

                FitBudsProtocol.OPCODE_TIMER_READ -> {
                    timerState?.let { state ->
                        fake.incoming.addLast(makeResponse(0xD3, intArrayOf(0, state)))
                    }
                }
            }
        }
        val session = FitBudsSession(transport)

        assertTrue(session.writeTimer(30))
        assertEquals(30, session.readTimer())
        assertTrue(session.writeTimer(0))
        assertEquals(0, session.readTimer())

        // Also confirm D2 direct ACK succeeds when D3 readback does not reply.
        timerState = null
        assertTrue(session.writeTimer(0))
        assertFalse(session.writeTimer(15))
    }
}
