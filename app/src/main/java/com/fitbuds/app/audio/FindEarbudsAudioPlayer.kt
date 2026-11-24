package com.fitbuds.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.fitbuds.app.protocol.FitBudsProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

enum class FindAudioResult {
    PLAYED,
    STOPPED,
    ENDPOINT_UNAVAILABLE,
    PLAYBACK_FAILED,
}

/**
 * Pure PCM16 stereo chime generator matching `.WindowsApp/fitbuds_find_audio.hpp`.
 * Produces 4 pulses across ~2.8s alternating between 880 Hz and 1120 Hz.
 */
object FindToneGenerator {
    const val SAMPLE_RATE: Int = 48_000
    const val PERIOD_FRAMES: Int = SAMPLE_RATE * 7 / 10 // 33,600 (0.7s)
    const val PULSE_FRAMES: Int = SAMPLE_RATE * 2 / 5 // 19,200 (0.4s)
    const val FADE_FRAMES: Int = SAMPLE_RATE / 40 // 1,200 (25ms)
    const val TOTAL_FRAMES: Int = 4 * PERIOD_FRAMES // 134,400 (2.8s)
    const val AMPLITUDE: Double = 7000.0

    fun fillStereoPcm16(
        out: ShortArray,
        startFrame: Int,
        frameCount: Int,
        earsMask: Int,
    ) {
        val playLeft = (earsMask and 1) != 0
        val playRight = (earsMask and 2) != 0
        for (i in 0 until frameCount) {
            val pos = startFrame + i
            val phase = pos % PERIOD_FRAMES
            val sample: Short = if (phase < PULSE_FRAMES) {
                val envelope = minOf(
                    1.0,
                    phase.toDouble() / FADE_FRAMES,
                    (PULSE_FRAMES - phase).toDouble() / FADE_FRAMES,
                )
                val frequency = if ((pos / PERIOD_FRAMES) % 2 == 1) 1120.0 else 880.0
                (AMPLITUDE * envelope * sin(2.0 * PI * frequency * phase / SAMPLE_RATE))
                    .toInt()
                    .toShort()
            } else {
                0
            }
            out[i * 2] = if (playLeft) sample else 0
            out[i * 2 + 1] = if (playRight) sample else 0
        }
    }
}

interface FindEarbudsAudioPlayer {
    suspend fun play(
        deviceAddress: String?,
        earsMask: Int,
        expiresAtMonotonicMs: Long,
    ): FindAudioResult

    fun stop()
}

class AndroidFindEarbudsAudioPlayer(
    context: Context,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : FindEarbudsAudioPlayer {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val stopRequested = AtomicBoolean(false)

    override fun stop() {
        stopRequested.set(true)
    }

    override suspend fun play(
        deviceAddress: String?,
        earsMask: Int,
        expiresAtMonotonicMs: Long,
    ): FindAudioResult = withContext(Dispatchers.IO) {
        if (earsMask !in 1..3 || monotonicMs() >= expiresAtMonotonicMs) {
            return@withContext FindAudioResult.PLAYBACK_FAILED
        }
        stopRequested.set(false)

        val targetDevice = findA2dpTarget(deviceAddress)
            ?: return@withContext FindAudioResult.ENDPOINT_UNAVAILABLE

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .build()

        val focusGranted = audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        var audioTrack: AudioTrack? = null

        try {
            val channelMask = AudioFormat.CHANNEL_OUT_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufferBytes = AudioTrack.getMinBufferSize(
                FindToneGenerator.SAMPLE_RATE,
                channelMask,
                encoding,
            )
            if (minBufferBytes <= 0) {
                return@withContext FindAudioResult.PLAYBACK_FAILED
            }

            val chunkFrames = FindToneGenerator.SAMPLE_RATE / 20 // 2400 frames = 50ms
            val bufferBytes = maxOf(minBufferBytes, chunkFrames * 2 * Short.SIZE_BYTES * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(FindToneGenerator.SAMPLE_RATE)
                        .setEncoding(encoding)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = track

            if (!track.setPreferredDevice(targetDevice)) {
                return@withContext FindAudioResult.ENDPOINT_UNAVAILABLE
            }
            if (monotonicMs() >= expiresAtMonotonicMs || stopRequested.get()) {
                return@withContext if (stopRequested.get()) FindAudioResult.STOPPED else FindAudioResult.PLAYBACK_FAILED
            }

            track.play()

            val chunkBuffer = ShortArray(chunkFrames * 2)
            var framePosition = 0
            val playbackDeadline = monotonicMs() + 5_000L

            while (
                currentCoroutineContext().isActive &&
                !stopRequested.get() &&
                monotonicMs() < playbackDeadline &&
                framePosition < FindToneGenerator.TOTAL_FRAMES
            ) {
                val routed = track.routedDevice
                if (routed != null && routed.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    return@withContext FindAudioResult.ENDPOINT_UNAVAILABLE
                }
                val framesToWrite = minOf(chunkFrames, FindToneGenerator.TOTAL_FRAMES - framePosition)
                FindToneGenerator.fillStereoPcm16(
                    out = chunkBuffer,
                    startFrame = framePosition,
                    frameCount = framesToWrite,
                    earsMask = earsMask,
                )
                val written = track.write(chunkBuffer, 0, framesToWrite * 2, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    return@withContext FindAudioResult.PLAYBACK_FAILED
                }
                framePosition += written / 2
            }

            if (stopRequested.get()) {
                return@withContext FindAudioResult.STOPPED
            }
            if (framePosition < FindToneGenerator.TOTAL_FRAMES) {
                return@withContext FindAudioResult.PLAYBACK_FAILED
            }

            // Wait briefly for the remaining buffered audio frames to finish playing through A2DP.
            while (
                currentCoroutineContext().isActive &&
                !stopRequested.get() &&
                monotonicMs() < playbackDeadline &&
                track.playbackHeadPosition < FindToneGenerator.TOTAL_FRAMES
            ) {
                val routed = track.routedDevice
                if (routed != null && routed.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    return@withContext FindAudioResult.ENDPOINT_UNAVAILABLE
                }
                delay(20L)
            }

            return@withContext when {
                stopRequested.get() -> FindAudioResult.STOPPED
                track.playbackHeadPosition >= FindToneGenerator.TOTAL_FRAMES -> FindAudioResult.PLAYED
                else -> FindAudioResult.PLAYED
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return@withContext FindAudioResult.PLAYBACK_FAILED
        } finally {
            audioTrack?.let { track ->
                runCatching {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush()
                    }
                }
                runCatching { track.stop() }
                runCatching { track.release() }
            }
            if (focusGranted) {
                runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
            }
        }
    }

    private fun findA2dpTarget(deviceAddress: String?): AudioDeviceInfo? {
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (!deviceAddress.isNullOrBlank()) {
            outputs.firstOrNull { it.address.equals(deviceAddress, ignoreCase = true) }?.let { return it }
        }
        return outputs.firstOrNull { FitBudsProtocol.isFitBudsName(it.productName?.toString()) }
    }
}
