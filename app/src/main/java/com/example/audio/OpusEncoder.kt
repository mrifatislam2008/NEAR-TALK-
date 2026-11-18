package com.example.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import com.example.util.Constants
import com.example.util.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Speech encoder prioritizing hardware/platform Opus (`audio/opus`, API 29+)
 * configured for 16 kHz mono 20ms frames with dynamic bitrate adaptation,
 * and clean IMA-ADPCM 4:1 compression fallback for Android 8.0-9.0 (API 26-28).
 */
class OpusEncoder {
    private var mediaCodec: MediaCodec? = null
    private var useOpusCodec: Boolean = false
    private var currentBitrate: Int = Constants.OPUS_DEFAULT_BITRATE_BPS
    private var presentationTimeUs: Long = 0L

    // IMA-ADPCM state for fallback path
    private var adpcmPredictor: Int = 0
    private var adpcmStepIndex: Int = 0

    val activeCodecType: AudioCodecType
        get() = if (useOpusCodec) AudioCodecType.OPUS_16K else AudioCodecType.ADPCM_16K

    fun initialize(bitrateBps: Int = Constants.OPUS_DEFAULT_BITRATE_BPS): Boolean {
        release()
        currentBitrate = bitrateBps.coerceIn(Constants.OPUS_MIN_BITRATE_BPS, Constants.OPUS_MAX_BITRATE_BPS)
        presentationTimeUs = 0L
        adpcmPredictor = 0
        adpcmStepIndex = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val opusReady = runCatching {
                val format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_OPUS,
                    Constants.AUDIO_SAMPLE_RATE,
                    1
                ).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, currentBitrate)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Constants.AUDIO_FRAME_PCM_BYTES * 2)
                    setInteger(MediaFormat.KEY_COMPLEXITY, 5)
                }
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                mediaCodec = codec
                useOpusCodec = true
                Logger.i(TAG, "Platform Opus encoder initialized (${Constants.AUDIO_SAMPLE_RATE}Hz, ${currentBitrate}bps)")
                true
            }.getOrElse { e ->
                Logger.w(TAG, "Platform Opus encoder unavailable, using ADPCM speech compression: ${e.message}")
                false
            }
            if (opusReady) return true
        }

        useOpusCodec = false
        Logger.i(TAG, "Initialized 16kHz IMA-ADPCM low-latency speech encoder")
        return true
    }

    fun updateBitrate(newBitrateBps: Int) {
        val clamped = newBitrateBps.coerceIn(Constants.OPUS_MIN_BITRATE_BPS, Constants.OPUS_MAX_BITRATE_BPS)
        if (kotlin.math.abs(clamped - currentBitrate) < 2000) return
        currentBitrate = clamped
        val codec = mediaCodec
        if (useOpusCodec && codec != null) {
            runCatching {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, clamped)
                }
                codec.setParameters(params)
            }
        }
    }

    fun encodeFrame(pcmBytes: ByteArray, byteCount: Int): ByteArray? {
        if (byteCount <= 0) return null
        return if (useOpusCodec) {
            encodeOpus(pcmBytes, byteCount) ?: encodeAdpcmFallback(pcmBytes, byteCount)
        } else {
            encodeAdpcmFallback(pcmBytes, byteCount)
        }
    }

    private fun encodeOpus(pcmBytes: ByteArray, byteCount: Int): ByteArray? {
        val codec = mediaCodec ?: return null
        return try {
            val inputIndex = codec.dequeueInputBuffer(5_000L)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmBytes, 0, byteCount)
                codec.queueInputBuffer(inputIndex, 0, byteCount, presentationTimeUs, 0)
                presentationTimeUs += Constants.AUDIO_FRAME_DURATION_MS * 1000L
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
            var encodedResult: ByteArray? = null

            while (outputIndex >= 0) {
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                    val outBuf = codec.getOutputBuffer(outputIndex)
                    if (outBuf != null) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        encodedResult = ByteArray(bufferInfo.size)
                        outBuf.get(encodedResult)
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
            encodedResult
        } catch (e: Exception) {
            Logger.w(TAG, "Opus encode error, switching to ADPCM: ${e.message}")
            useOpusCodec = false
            runCatching { codec.stop(); codec.release() }
            mediaCodec = null
            null
        }
    }

    /**
     * Standard 4-bit IMA-ADPCM encoding with 3-byte frame header (predictor + stepIndex)
     * so every packet is independently decodable even under Bluetooth packet loss!
     */
    private fun encodeAdpcmFallback(pcmBytes: ByteArray, byteCount: Int): ByteArray {
        val sampleCount = byteCount / 2
        val packedNibbles = (sampleCount + 1) / 2
        val out = ByteArray(3 + packedNibbles)

        val shortBuf = ByteBuffer.wrap(pcmBytes, 0, byteCount).order(ByteOrder.LITTLE_ENDIAN)
        // Store initial predictor & stepIndex per frame for packet-loss resilience
        val firstSample = if (sampleCount > 0) shortBuf.getShort(0).toInt() else 0
        adpcmPredictor = firstSample
        out[0] = (adpcmPredictor and 0xFF).toByte()
        out[1] = ((adpcmPredictor shr 8) and 0xFF).toByte()
        out[2] = adpcmStepIndex.toByte()

        var outPos = 3
        var highNibble = false
        var currentByte = 0

        for (i in 0 until sampleCount) {
            val sample = shortBuf.getShort(i * 2).toInt()
            val nibble = encodeAdpcmSample(sample)
            if (!highNibble) {
                currentByte = nibble and 0x0F
                highNibble = true
            } else {
                currentByte = currentByte or ((nibble and 0x0F) shl 4)
                out[outPos++] = currentByte.toByte()
                highNibble = false
            }
        }
        if (highNibble && outPos < out.size) {
            out[outPos] = currentByte.toByte()
        }
        return out
    }

    private fun encodeAdpcmSample(sample: Int): Int {
        var step = IMA_STEP_TABLE[adpcmStepIndex]
        var diff = sample - adpcmPredictor
        var nibble = 0
        if (diff < 0) {
            nibble = 8
            diff = -diff
        }
        var delta = step shr 3
        if (diff >= step) {
            nibble = nibble or 4
            diff -= step
            delta += step
        }
        step = step shr 1
        if (diff >= step) {
            nibble = nibble or 2
            diff -= step
            delta += step
        }
        step = step shr 1
        if (diff >= step) {
            nibble = nibble or 1
            delta += step
        }

        if ((nibble and 8) != 0) {
            adpcmPredictor -= delta
        } else {
            adpcmPredictor += delta
        }
        adpcmPredictor = adpcmPredictor.coerceIn(-32768, 32767)
        adpcmStepIndex = (adpcmStepIndex + IMA_INDEX_TABLE[nibble]).coerceIn(0, 88)
        return nibble
    }

    fun release() {
        runCatching {
            mediaCodec?.stop()
            mediaCodec?.release()
        }
        mediaCodec = null
        useOpusCodec = false
    }

    companion object {
        private const val TAG = "OpusEncoder"
        private val IMA_INDEX_TABLE = intArrayOf(
            -1, -1, -1, -1, 2, 4, 6, 8,
            -1, -1, -1, -1, 2, 4, 6, 8
        )
        private val IMA_STEP_TABLE = intArrayOf(
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
            19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
            50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
            130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
            876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
            2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
            5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
        )
    }
}
