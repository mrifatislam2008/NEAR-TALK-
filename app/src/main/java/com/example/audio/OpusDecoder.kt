package com.example.audio

import android.media.MediaCodec
import android.media.MediaFormat
import com.example.util.Constants
import com.example.util.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Speech decoder supporting both RFC 7845 Opus (`audio/opus`) via MediaCodec
 * and IMA-ADPCM frames seamlessly.
 */
class OpusDecoder {
    private var mediaCodec: MediaCodec? = null
    private var opusInitialized: Boolean = false
    private var presentationTimeUs: Long = 0L

    fun initialize(): Boolean {
        release()
        presentationTimeUs = 0L
        opusInitialized = runCatching {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                Constants.AUDIO_SAMPLE_RATE,
                1
            ).apply {
                setByteBuffer("csd-0", buildOpusHeadBuffer(Constants.AUDIO_SAMPLE_RATE, 1))
                setByteBuffer("csd-1", buildLongBuffer(3_840_000L)) // 80ms pre-skip ns
                setByteBuffer("csd-2", buildLongBuffer(80_000_000L)) // 80ms seek pre-roll ns
            }
            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            codec.configure(format, null, null, 0)
            codec.start()
            mediaCodec = codec
            Logger.i(TAG, "Platform Opus decoder initialized")
            true
        }.getOrElse { e ->
            Logger.w(TAG, "Platform Opus decoder init failed: ${e.message}")
            false
        }
        return true
    }

    fun decode(packet: AudioPacket): ShortArray {
        return when (packet.codecType) {
            AudioCodecType.OPUS_16K -> decodeOpus(packet.encodedPayload)
                ?: ShortArray(Constants.AUDIO_FRAME_SAMPLES)
            AudioCodecType.ADPCM_16K -> decodeAdpcm(packet.encodedPayload)
        }
    }

    private fun decodeOpus(encoded: ByteArray): ShortArray? {
        if (!opusInitialized) initialize()
        val codec = mediaCodec ?: return null
        return try {
            val inIdx = codec.dequeueInputBuffer(5_000L)
            if (inIdx >= 0) {
                val inBuf = codec.getInputBuffer(inIdx)
                inBuf?.clear()
                inBuf?.put(encoded)
                codec.queueInputBuffer(inIdx, 0, encoded.size, presentationTimeUs, 0)
                presentationTimeUs += Constants.AUDIO_FRAME_DURATION_MS * 1000L
            }

            val info = MediaCodec.BufferInfo()
            var outIdx = codec.dequeueOutputBuffer(info, 5_000L)
            var decodedSamples: ShortArray? = null
            while (outIdx >= 0) {
                if (info.size > 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        outBuf.get(bytes)
                        val samples = ShortArray(bytes.size / 2)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
                        decodedSamples = samples
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                outIdx = codec.dequeueOutputBuffer(info, 0L)
            }
            decodedSamples
        } catch (e: Exception) {
            Logger.w(TAG, "Opus decode error: ${e.message}")
            null
        }
    }

    private fun decodeAdpcm(encoded: ByteArray): ShortArray {
        if (encoded.size <= 3) return ShortArray(Constants.AUDIO_FRAME_SAMPLES)
        var predictor = (encoded[0].toInt() and 0xFF) or ((encoded[1].toInt() and 0xFF) shl 8)
        if (predictor >= 32768) predictor -= 65536
        var stepIndex = (encoded[2].toInt() and 0xFF).coerceIn(0, 88)

        val nibbleBytes = encoded.size - 3
        val samples = ShortArray(minOf(nibbleBytes * 2, Constants.AUDIO_FRAME_SAMPLES))
        var sampleIdx = 0

        for (i in 3 until encoded.size) {
            val b = encoded[i].toInt() and 0xFF
            val lowNibble = b and 0x0F
            val highNibble = (b shr 4) and 0x0F

            if (sampleIdx < samples.size) {
                val (s1, nextPred1, nextIdx1) = decodeAdpcmNibble(lowNibble, predictor, stepIndex)
                predictor = nextPred1
                stepIndex = nextIdx1
                samples[sampleIdx++] = s1
            }
            if (sampleIdx < samples.size) {
                val (s2, nextPred2, nextIdx2) = decodeAdpcmNibble(highNibble, predictor, stepIndex)
                predictor = nextPred2
                stepIndex = nextIdx2
                samples[sampleIdx++] = s2
            }
        }
        return samples
    }

    private fun decodeAdpcmNibble(nibble: Int, predictorIn: Int, stepIndexIn: Int): Triple<Short, Int, Int> {
        val step = IMA_STEP_TABLE[stepIndexIn]
        var delta = step shr 3
        if ((nibble and 4) != 0) delta += step
        if ((nibble and 2) != 0) delta += step shr 1
        if ((nibble and 1) != 0) delta += step shr 2

        var predictor = if ((nibble and 8) != 0) predictorIn - delta else predictorIn + delta
        predictor = predictor.coerceIn(-32768, 32767)
        val stepIndex = (stepIndexIn + IMA_INDEX_TABLE[nibble]).coerceIn(0, 88)
        return Triple(predictor.toShort(), predictor, stepIndex)
    }

    private fun buildOpusHeadBuffer(sampleRate: Int, channels: Int): ByteBuffer {
        val buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("OpusHead".toByteArray(Charsets.US_ASCII))
        buf.put(1.toByte()) // Version
        buf.put(channels.toByte())
        buf.putShort(0) // Pre-skip
        buf.putInt(sampleRate)
        buf.putShort(0) // Output gain
        buf.put(0.toByte()) // Channel mapping family 0
        buf.flip()
        return buf
    }

    private fun buildLongBuffer(value: Long): ByteBuffer {
        return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).apply { flip() }
    }

    fun release() {
        runCatching {
            mediaCodec?.stop()
            mediaCodec?.release()
        }
        mediaCodec = null
        opusInitialized = false
    }

    companion object {
        private const val TAG = "OpusDecoder"
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
