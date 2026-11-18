package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.example.core.PermissionManager
import com.example.util.Constants
import com.example.util.Logger
import javax.inject.Inject

/**
 * Low-latency microphone capture using AudioRecord configured for VOICE_COMMUNICATION
 * with hardware AcousticEchoCanceler, NoiseSuppressor, and AutomaticGainControl when supported.
 */
class AudioRecorder @Inject constructor(
    private val permissionManager: PermissionManager
) {
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun start(): Result<Unit> {
        if (!permissionManager.hasAudioPermission()) {
            return Result.failure(SecurityException("Microphone permission is required for voice calls."))
        }
        stop()

        val minBuf = AudioRecord.getMinBufferSize(
            Constants.AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            return Result.failure(IllegalStateException("Audio recording format unsupported on this device."))
        }
        val bufferSize = maxOf(minBuf, Constants.AUDIO_FRAME_PCM_BYTES * 4)

        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                Constants.AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return Result.failure(IllegalStateException("Microphone hardware failed to initialize."))
            }

            attachAudioEffects(record.audioSessionId)
            record.startRecording()
            audioRecord = record
            isRecording = true
            Logger.i(TAG, "AudioRecord started (${Constants.AUDIO_SAMPLE_RATE}Hz, buf=$bufferSize)")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start AudioRecord", e)
            stop()
            Result.failure(IllegalStateException("Unable to access microphone: ${e.message ?: "Unknown error"}"))
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            runCatching {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            runCatching {
                automaticGainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
        }
    }

    fun readFrame(destination: ByteArray): Int {
        val record = audioRecord ?: return -1
        if (!isRecording) return -1
        var totalRead = 0
        while (totalRead < destination.size && isRecording) {
            val read = record.read(destination, totalRead, destination.size - totalRead)
            if (read <= 0) {
                return if (totalRead > 0) totalRead else read
            }
            totalRead += read
        }
        return totalRead
    }

    fun stop() {
        isRecording = false
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { automaticGainControl?.release() }
        echoCanceler = null
        noiseSuppressor = null
        automaticGainControl = null

        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        Logger.i(TAG, "AudioRecord stopped and released")
    }

    companion object {
        private const val TAG = "AudioRecorder"
    }
}
