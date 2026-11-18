package com.example.audio

import com.example.util.Constants
import java.util.TreeMap

/**
 * Thread-safe adaptive JitterBuffer for 20ms PCM frames.
 * - Reorders out-of-order frames by sequence number
 * - Maintains a low-latency target playout depth (40-80ms = 2-4 frames)
 * - Performs Packet Loss Concealment (PLC) by attenuating the previous frame on missing packets
 * - Trims excessive backlog to prevent latency drift
 */
class JitterBuffer(
    private val targetDelayFrames: Int = 3,
    private val maxBufferedFrames: Int = 12
) {
    private val lock = Any()
    private val frameQueue = TreeMap<Int, ShortArray>()
    private var nextExpectedSeq: Int = -1
    private var primed: Boolean = false
    private var lastPlayedFrame: ShortArray = ShortArray(Constants.AUDIO_FRAME_SAMPLES)
    private var consecutiveLossCount: Int = 0

    var totalReceived: Long = 0L
        private set
    var totalLost: Long = 0L
        private set

    fun push(sequenceNumber: Int, pcmSamples: ShortArray) {
        synchronized(lock) {
            totalReceived++
            if (nextExpectedSeq == -1) {
                nextExpectedSeq = sequenceNumber
            } else if (sequenceNumber < nextExpectedSeq) {
                // Late frame already concealed/passed
                return
            }

            frameQueue[sequenceNumber] = pcmSamples

            // Prevent latency buildup if network bursts
            while (frameQueue.size > maxBufferedFrames) {
                val oldestKey = frameQueue.firstKey()
                frameQueue.remove(oldestKey)
                nextExpectedSeq = oldestKey + 1
            }

            if (!primed && frameQueue.size >= targetDelayFrames) {
                primed = true
            }
        }
    }

    fun popNextFrame(): ShortArray? {
        synchronized(lock) {
            if (!primed) {
                return null
            }
            val exact = frameQueue.remove(nextExpectedSeq)
            if (exact != null) {
                nextExpectedSeq++
                lastPlayedFrame = exact
                consecutiveLossCount = 0
                return exact
            }

            // Frame missing -> Packet Loss Concealment (attenuate last frame toward silence)
            if (frameQueue.isNotEmpty()) {
                totalLost++
                nextExpectedSeq++
                consecutiveLossCount++
                return if (consecutiveLossCount <= 4) {
                    val decay = 1.0f / (1 shl consecutiveLossCount)
                    ShortArray(lastPlayedFrame.size) { i ->
                        (lastPlayedFrame[i] * decay).toInt().toShort()
                    }.also { lastPlayedFrame = it }
                } else {
                    ShortArray(Constants.AUDIO_FRAME_SAMPLES)
                }
            }

            // Buffer drained; re-prime smoothly
            primed = false
            return null
        }
    }

    fun packetLossRatio(): Float {
        synchronized(lock) {
            val total = totalReceived + totalLost
            return if (total == 0L) 0f else totalLost.toFloat() / total.toFloat()
        }
    }

    fun reset() {
        synchronized(lock) {
            frameQueue.clear()
            nextExpectedSeq = -1
            primed = false
            lastPlayedFrame = ShortArray(Constants.AUDIO_FRAME_SAMPLES)
            consecutiveLossCount = 0
            totalReceived = 0L
            totalLost = 0L
        }
    }
}
