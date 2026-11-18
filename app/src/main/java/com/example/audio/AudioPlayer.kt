package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.util.Constants
import com.example.util.Logger
import javax.inject.Inject

/**
 * Low-latency voice playback using AudioTrack configured for USAGE_VOICE_COMMUNICATION.
 */
class AudioPlayer @Inject constructor() {
    private var audioTrack: AudioTrack? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    fun start(): Result<Unit> {
        stop()
        val minBuf = AudioTrack.getMinBufferSize(
            Constants.AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            return Result.failure(IllegalStateException("Audio playback format unsupported on this device."))
        }
        val bufferSize = maxOf(minBuf, Constants.AUDIO_FRAME_PCM_BYTES * 4)

        return try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Constants.AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return Result.failure(IllegalStateException("Speaker audio track failed to initialize."))
            }

            track.play()
            audioTrack = track
            isPlaying = true
            Logger.i(TAG, "AudioTrack started (${Constants.AUDIO_SAMPLE_RATE}Hz, buf=$bufferSize)")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start AudioTrack", e)
            stop()
            Result.failure(IllegalStateException("Audio output initialization failed: ${e.message ?: "Unknown error"}"))
        }
    }

    fun writeSamples(pcmSamples: ShortArray) {
        val track = audioTrack ?: return
        if (!isPlaying) return
        runCatching {
            track.write(pcmSamples, 0, pcmSamples.size)
        }
    }

    fun stop() {
        isPlaying = false
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
        Logger.i(TAG, "AudioTrack stopped and released")
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}
