package com.fitbuds.app.protocol

import com.fitbuds.app.audio.FindToneGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitBudsProtocolTest {

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }

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

    @Test
    fun `makeRequest encodes all 13 Windows commands identically`() {
        // battery (0xF2, {})
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xF2, 0x00, 0x00, 0x88),
            FitBudsProtocol.makeRequest(FitBudsProtocol.OPCODE_BATTERY),
        )
        // anc-deep (0xC1, {0x16, 1}) -> masked {0xB3, 0xA4}
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC1, 0x00, 0x02, 0xB3, 0xA4, 0xB0),
            FitBudsProtocol.makeRequest(
                FitBudsCommand.SetNoiseMode(NoiseMode.DEEP).opcode,
                FitBudsCommand.SetNoiseMode(NoiseMode.DEEP).payload,
            ),
        )
        // anc-wide, anc-wind, ambient, anc-off
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC1, 0x00, 0x02, 0xB3, 0xA7, 0xB3),
            FitBudsProtocol.makeRequest(0xC1, intArrayOf(0x16, 2)),
        )
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC1, 0x00, 0x02, 0xB3, 0xA6, 0xB2),
            FitBudsProtocol.makeRequest(0xC1, intArrayOf(0x16, 3)),
        )
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC1, 0x00, 0x02, 0xB3, 0xA1, 0xAD),
            FitBudsProtocol.makeRequest(0xC1, intArrayOf(0x16, 4)),
        )
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC1, 0x00, 0x02, 0xB3, 0xA0, 0xAC),
            FitBudsProtocol.makeRequest(0xC1, intArrayOf(0x16, 5)),
        )
        // eq-classic (0), eq-pop (1), eq-classical (2), eq-rock (3)
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC4, 0x00, 0x01, 0xA5, 0x00),
            FitBudsProtocol.makeRequest(0xC4, intArrayOf(EqPreset.CLASSIC.code)),
        )
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC4, 0x00, 0x01, 0xA4, 0xFF),
            FitBudsProtocol.makeRequest(0xC4, intArrayOf(EqPreset.POP.code)),
        )
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC4, 0x00, 0x01, 0xA7, 0x02),
            FitBudsProtocol.makeRequest(0xC4, intArrayOf(EqPreset.CLASSICAL.code)),
        )
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xC4, 0x00, 0x01, 0xA6, 0x01),
            FitBudsProtocol.makeRequest(0xC4, intArrayOf(EqPreset.ROCK.code)),
        )
        // game-on (1), game-off (0)
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0x09, 0x00, 0x01, 0xA4, 0x44),
            FitBudsProtocol.makeRequest(0x09, intArrayOf(1)),
        )
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0x09, 0x00, 0x01, 0xA5, 0x45),
            FitBudsProtocol.makeRequest(0x09, intArrayOf(0)),
        )
        // power-off (0xCE, {})
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xCE, 0x00, 0x00, 0x64),
            FitBudsProtocol.makeRequest(FitBudsProtocol.OPCODE_POWER_OFF),
        )
    }

    @Test
    fun `decodeBattery handles dual, single-ear, zero case, invalid levels, and bad length`() {
        val dual = FitBudsProtocol.decodeBattery(
            makeResponse(0xF2, intArrayOf(3, 85, 92, 64, 0, 0)),
        )
        assertEquals(WearMode.BOTH, dual.wearMode)
        assertEquals(85, dual.left)
        assertEquals(92, dual.right)
        assertEquals(64, dual.caseLevel)

        val leftOnly = FitBudsProtocol.decodeBattery(
            makeResponse(0xF2, intArrayOf(1, 70, 50, 0, 0, 0)),
        )
        assertEquals(WearMode.LEFT_ONLY, leftOnly.wearMode)
        assertEquals(70, leftOnly.left)
        assertNull(leftOnly.right)
        assertNull(leftOnly.caseLevel)

        val rightOnlyWithBadLevels = FitBudsProtocol.decodeBattery(
            makeResponse(0xF2, intArrayOf(2, 40, 255, 101, 0, 0)),
        )
        assertEquals(WearMode.RIGHT_ONLY, rightOnlyWithBadLevels.wearMode)
        assertNull(rightOnlyWithBadLevels.left)
        assertNull(rightOnlyWithBadLevels.right)
        assertNull(rightOnlyWithBadLevels.caseLevel)

        val noneOnline = FitBudsProtocol.decodeBattery(
            makeResponse(0xF2, intArrayOf(0, 100, 100, 80, 0, 0)),
        )
        assertEquals(WearMode.NONE, noneOnline.wearMode)
        assertNull(noneOnline.left)
        assertNull(noneOnline.right)
        assertEquals(80, noneOnline.caseLevel)
    }

    @Test(expected = ProtocolException::class)
    fun `decodeBattery throws on unexpected frame length`() {
        FitBudsProtocol.decodeBattery(makeResponse(0xF2, intArrayOf(3, 80, 80)))
    }

    @Test
    fun `decodeMode validates CC, D5, and 08 responses`() {
        assertEquals(1, FitBudsProtocol.decodeMode(0xCC, makeResponse(0xCC, intArrayOf(0x16, 1))))
        assertEquals(5, FitBudsProtocol.decodeMode(0xCC, makeResponse(0xCC, intArrayOf(0x16, 5))))
        assertNull(FitBudsProtocol.decodeMode(0xCC, makeResponse(0xCC, intArrayOf(0x15, 1))))
        assertNull(FitBudsProtocol.decodeMode(0xCC, makeResponse(0xCC, intArrayOf(0x16, 6))))
        assertNull(FitBudsProtocol.decodeMode(0xCC, makeResponse(0xCC, intArrayOf(0x16))))

        assertEquals(3, FitBudsProtocol.decodeMode(0xD5, makeResponse(0xD5, intArrayOf(3))))
        assertNull(FitBudsProtocol.decodeMode(0xD5, makeResponse(0xD5, intArrayOf(4))))
        assertNull(FitBudsProtocol.decodeMode(0xD5, makeResponse(0xD5, intArrayOf(0, 1))))

        assertEquals(1, FitBudsProtocol.decodeMode(0x08, makeResponse(0x08, intArrayOf(1))))
        assertEquals(0, FitBudsProtocol.decodeMode(0x08, makeResponse(0x08, intArrayOf(0))))
        assertNull(FitBudsProtocol.decodeMode(0x08, makeResponse(0x08, intArrayOf(2))))
    }

    @Test
    fun `isCommandAllowed matches wear bitmask rules`() {
        val deep = FitBudsCommand.SetNoiseMode(NoiseMode.DEEP)
        val ambient = FitBudsCommand.SetNoiseMode(NoiseMode.AMBIENT)
        val off = FitBudsCommand.SetNoiseMode(NoiseMode.OFF)
        val eq = FitBudsCommand.SetEqPreset(EqPreset.ROCK)
        val game = FitBudsCommand.SetGameMode(true)
        val powerOff = FitBudsCommand.PowerOff

        // Present = 0 -> nothing allowed
        assertFalse(FitBudsProtocol.isCommandAllowed(deep, 0))
        assertFalse(FitBudsProtocol.isCommandAllowed(ambient, 0))
        assertFalse(FitBudsProtocol.isCommandAllowed(eq, 0))
        assertFalse(FitBudsProtocol.isCommandAllowed(powerOff, 0))

        // Present = 1 or 2 -> only ambient, anc-off, eq, game, power-off allowed
        for (mask in listOf(1, 2)) {
            assertFalse(FitBudsProtocol.isCommandAllowed(deep, mask))
            assertFalse(FitBudsProtocol.isCommandAllowed(FitBudsCommand.SetNoiseMode(NoiseMode.WIDE), mask))
            assertFalse(FitBudsProtocol.isCommandAllowed(FitBudsCommand.SetNoiseMode(NoiseMode.WIND), mask))
            assertTrue(FitBudsProtocol.isCommandAllowed(ambient, mask))
            assertTrue(FitBudsProtocol.isCommandAllowed(off, mask))
            assertTrue(FitBudsProtocol.isCommandAllowed(eq, mask))
            assertTrue(FitBudsProtocol.isCommandAllowed(game, mask))
            assertTrue(FitBudsProtocol.isCommandAllowed(powerOff, mask))
        }

        // Present = 3 -> all allowed
        assertTrue(FitBudsProtocol.isCommandAllowed(deep, 3))
        assertTrue(FitBudsProtocol.isCommandAllowed(ambient, 3))
        assertTrue(FitBudsProtocol.isCommandAllowed(eq, 3))
    }

    @Test
    fun `FrameParser handles split prefixes, garbage, bad checksums, oversized frames, and concatenated frames`() {
        val parser = FrameParser()
        val frame1 = makeResponse(0xD5, intArrayOf(2))
        val frame2 = makeResponse(0x08, intArrayOf(1))

        // Garbage + false BB EC frame with bad checksum + split valid frames
        val badChecksumFrame = frame1.copyOf().apply { this[lastIndex] = (this[lastIndex] + 1).toByte() }
        val oversizedHeader = bytes(0xBB, 0xEC, 0xF2, 0x05, 0x00, 0x00) // payloadLen = 1280 > 1024

        val stream = bytes(0x00, 0xFF) +
            badChecksumFrame +
            oversizedHeader +
            frame1 +
            frame2

        // Feed byte-by-byte to stress chunk boundary handling (including split 0xBB / 0xEC).
        val parsed = mutableListOf<ByteArray>()
        for (b in stream) {
            parsed += parser.feed(byteArrayOf(b))
        }

        assertEquals(2, parsed.size)
        assertArrayEquals(frame1, parsed[0])
        assertArrayEquals(frame2, parsed[1])
    }

    @Test
    fun `isFitBudsName matches case-insensitively`() {
        assertTrue(FitBudsProtocol.isFitBudsName("EDIFIER FitBuds Pro"))
        assertTrue(FitBudsProtocol.isFitBudsName("fitbuds lite"))
        assertFalse(FitBudsProtocol.isFitBudsName("EDIFIER W820NB"))
        assertFalse(FitBudsProtocol.isFitBudsName(null))
    }

    @Test
    fun `makeRequest encodes gesture and timer commands`() {
        // gesture read 0xF0 {slot=1} -> masked {0xA4}
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xF0, 0x00, 0x01, 0xA4, 0x2B),
            FitBudsProtocol.makeRequest(FitBudsProtocol.OPCODE_GESTURE_READ, intArrayOf(1)),
        )
        // gesture write 0xF1 {slot=8, action=5, cycle=0x11} -> masked {0xAD, 0xA0, 0xB4}
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xF1, 0x00, 0x03, 0xAD, 0xA0, 0xB4, 0x8B),
            FitBudsProtocol.makeRequest(
                FitBudsProtocol.OPCODE_GESTURE_WRITE,
                intArrayOf(8, GestureAction.ANC_CYCLE.code, FitBudsProtocol.DEFAULT_ANC_CYCLE),
            ),
        )
        // timer read 0xD3 {}
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xD3, 0x00, 0x00, 0x69),
            FitBudsProtocol.makeRequest(FitBudsProtocol.OPCODE_TIMER_READ),
        )
        // timer set 0xD1 {0, 30} -> masked {0xA5, 0xBB}
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xD1, 0x00, 0x02, 0xA5, 0xBB, 0xC9),
            FitBudsProtocol.makeRequest(FitBudsProtocol.OPCODE_TIMER_SET, intArrayOf(0, 30)),
        )
        // timer cancel 0xD2 {}
        assertArrayEquals(
            bytes(0xAA, 0xEC, 0xD2, 0x00, 0x00, 0x68),
            FitBudsProtocol.makeRequest(FitBudsProtocol.OPCODE_TIMER_CANCEL),
        )
    }

    @Test
    fun `anc cycle validation and mode decoding match Windows rules`() {
        assertFalse(FitBudsProtocol.isValidAncCycle(0x00))
        assertFalse(FitBudsProtocol.isValidAncCycle(0x01)) // only 1 mode
        assertTrue(FitBudsProtocol.isValidAncCycle(FitBudsProtocol.DEFAULT_ANC_CYCLE)) // 0x11 = DEEP + OFF
        assertTrue(FitBudsProtocol.isValidAncCycle(0x19)) // 3 modes: DEEP + AMBIENT + OFF
        assertFalse(FitBudsProtocol.isValidAncCycle(0x1F)) // 5 modes (> 3)
        assertFalse(FitBudsProtocol.isValidAncCycle(0x23)) // invalid high bit 0x20

        assertEquals(
            listOf(NoiseMode.DEEP, NoiseMode.OFF),
            FitBudsProtocol.ancCycleModes(FitBudsProtocol.DEFAULT_ANC_CYCLE),
        )
        assertEquals(2, FitBudsProtocol.cycleCount(0x11))
    }

    @Test
    fun `decodeGesture and decodeTimer handle valid and invalid frames`() {
        val simpleGesture = FitBudsProtocol.decodeGesture(
            makeResponse(0xF0, intArrayOf(GestureSlot.LEFT_DOUBLE_TAP.code, GestureAction.PLAY_PAUSE.code)),
            GestureSlot.LEFT_DOUBLE_TAP,
        )
        assertEquals(
            GestureSetting(GestureSlot.LEFT_DOUBLE_TAP, GestureAction.PLAY_PAUSE, 0),
            simpleGesture,
        )

        val ancGesture = FitBudsProtocol.decodeGesture(
            makeResponse(
                0xF0,
                intArrayOf(GestureSlot.RIGHT_LONG_PRESS.code, GestureAction.ANC_CYCLE.code, 0x19),
            ),
            GestureSlot.RIGHT_LONG_PRESS,
        )
        assertEquals(
            GestureSetting(GestureSlot.RIGHT_LONG_PRESS, GestureAction.ANC_CYCLE, 0x19),
            ancGesture,
        )

        assertNull(
            FitBudsProtocol.decodeGesture(
                makeResponse(0xF0, intArrayOf(GestureSlot.RIGHT_DOUBLE_TAP.code, 2)),
                GestureSlot.LEFT_DOUBLE_TAP,
            ),
        )
        assertNull(
            FitBudsProtocol.decodeGesture(
                makeResponse(0xF0, intArrayOf(GestureSlot.LEFT_DOUBLE_TAP.code, 10)),
                GestureSlot.LEFT_DOUBLE_TAP,
            ),
        )
        assertNull(
            FitBudsProtocol.decodeGesture(
                makeResponse(0xF0, intArrayOf(GestureSlot.LEFT_DOUBLE_TAP.code)),
                GestureSlot.LEFT_DOUBLE_TAP,
            ),
        )

        assertEquals(60, FitBudsProtocol.decodeTimer(makeResponse(0xD3, intArrayOf(0, 60))))
        assertEquals(0, FitBudsProtocol.decodeTimer(makeResponse(0xD3, intArrayOf(0, 0))))
        assertNull(FitBudsProtocol.decodeTimer(makeResponse(0xD3, intArrayOf(1, 30))))
        assertNull(FitBudsProtocol.decodeTimer(makeResponse(0xD3, intArrayOf(0))))
    }

    @Test
    fun `FindToneGenerator produces panned stereo PCM16 with envelope and silent gaps`() {
        // Left only: right channel stays 0, left channel has non-zero waveform inside pulse.
        val leftBuf = ShortArray(2000 * 2)
        FindToneGenerator.fillStereoPcm16(leftBuf, startFrame = 0, frameCount = 2000, earsMask = 1)
        assertEquals(0, leftBuf[0].toInt()) // frame 0 has fade envelope 0.0
        assertEquals(0, leftBuf[1].toInt())
        var leftNonZero = false
        for (frame in 10 until 2000) {
            if (leftBuf[frame * 2].toInt() != 0) leftNonZero = true
            assertEquals(0, leftBuf[frame * 2 + 1].toInt())
        }
        assertTrue(leftNonZero)

        // Right only: left channel stays 0, right channel has non-zero waveform inside pulse.
        val rightBuf = ShortArray(2000 * 2)
        FindToneGenerator.fillStereoPcm16(rightBuf, startFrame = 0, frameCount = 2000, earsMask = 2)
        var rightNonZero = false
        for (frame in 10 until 2000) {
            assertEquals(0, rightBuf[frame * 2].toInt())
            if (rightBuf[frame * 2 + 1].toInt() != 0) rightNonZero = true
        }
        assertTrue(rightNonZero)

        // Both ears equal during second pulse (1120 Hz) and silent during inter-pulse gap.
        val bothBuf = ShortArray(500 * 2)
        FindToneGenerator.fillStereoPcm16(
            bothBuf,
            startFrame = FindToneGenerator.PERIOD_FRAMES + 2000,
            frameCount = 500,
            earsMask = 3,
        )
        for (frame in 0 until 500) {
            assertEquals(bothBuf[frame * 2], bothBuf[frame * 2 + 1])
        }

        val gapBuf = ShortArray(500 * 2)
        FindToneGenerator.fillStereoPcm16(
            gapBuf,
            startFrame = FindToneGenerator.PULSE_FRAMES + 100,
            frameCount = 500,
            earsMask = 3,
        )
        for (sample in gapBuf) {
            assertEquals(0, sample.toInt())
        }
    }
}
