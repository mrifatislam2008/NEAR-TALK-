package com.example.audio

import com.example.bluetooth.BluetoothTransport
import com.example.bluetooth.PacketType
import com.example.core.DeviceIdentityManager
import com.example.util.Constants
import com.example.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class CallQuality(val label: String) {
    EXCELLENT("HD Link"),
    GOOD("Stable"),
    FAIR("Low Bandwidth"),
    POOR("Weak Signal")
}

/**
 * Real-time full-duplex audio pipeline:
 * Mic -> AudioRecord -> OpusEncoder -> AudioPacket -> BluetoothTransport
 * BluetoothTransport -> AudioPacket -> OpusDecoder -> JitterBuffer -> AudioTrack -> Speaker
 */
@Singleton
class AudioEngine @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val audioSessionManager: AudioSessionManager,
    private val transport: BluetoothTransport,
    private val identityManager: DeviceIdentityManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val encoder = OpusEncoder()
    private val decoder = OpusDecoder()
    private val jitterBuffer = JitterBuffer(targetDelayFrames = 3, maxBufferedFrames = 10)
    private val audioSequence = AtomicInteger(1)

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _callQuality = MutableStateFlow(CallQuality.GOOD)
    val callQuality: StateFlow<CallQuality> = _callQuality.asStateFlow()

    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var inboundListenerJob: Job? = null
    private var qualityMonitorJob: Job? = null

    @Volatile
    private var isActiveEngine = false

    suspend fun start(remoteDeviceId: String, speakerDefault: Boolean = false): Result<Unit> {
        if (isActiveEngine) return Result.success(Unit)
        val localId = identityManager.ensureDeviceId()

        audioSessionManager.acquireCallAudioSession(speakerDefault)
        encoder.initialize(Constants.OPUS_DEFAULT_BITRATE_BPS)
        decoder.initialize()
        jitterBuffer.reset()
        audioSequence.set(1)
        _isMuted.value = false

        val recRes = audioRecorder.start()
        if (recRes.isFailure) {
            stop()
            return recRes
        }

        val playRes = audioPlayer.start()
        if (playRes.isFailure) {
            stop()
            return playRes
        }

        isActiveEngine = true

        inboundListenerJob = scope.launch {
            transport.incomingPackets.collect { packet ->
                if (!isActiveEngine) return@collect
                if (packet.type == PacketType.AUDIO_PACKET) {
                    val audioPkt = AudioPacket.fromWireBytes(packet.payload) ?: return@collect
                    val pcm = decoder.decode(audioPkt)
                    jitterBuffer.push(audioPkt.sequenceNumber, pcm)
                }
            }
        }

        captureJob = scope.launch {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val pcmFrame = ByteArray(Constants.AUDIO_FRAME_PCM_BYTES)
            while (isActive && isActiveEngine) {
                val read = audioRecorder.readFrame(pcmFrame)
                if (read <= 0) {
                    delay(5)
                    continue
                }
                if (_isMuted.value) {
                    // Do not transmit microphone audio when muted
                    continue
                }
                val encoded = encoder.encodeFrame(pcmFrame, read) ?: continue
                val audioPacket = AudioPacket(
                    codecType = encoder.activeCodecType,
                    sequenceNumber = audioSequence.getAndIncrement(),
                    timestampMs = System.currentTimeMillis(),
                    frameDurationMs = Constants.AUDIO_FRAME_DURATION_MS.toShort(),
                    encodedPayload = encoded
                )
                transport.sendAudioPacketFast(
                    senderId = localId,
                    receiverId = remoteDeviceId,
                    payload = audioPacket.toWireBytes()
                )
            }
        }

        playbackJob = scope.launch {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val silence = ShortArray(Constants.AUDIO_FRAME_SAMPLES)
            while (isActive && isActiveEngine) {
                val frame = jitterBuffer.popNextFrame()
                if (frame != null) {
                    audioPlayer.writeSamples(frame)
                } else {
                    audioPlayer.writeSamples(silence)
                    delay(Constants.AUDIO_FRAME_DURATION_MS.toLong())
                }
            }
        }

        qualityMonitorJob = scope.launch {
            while (isActive && isActiveEngine) {
                delay(2000L)
                val throughput = transport.measuredThroughputBps.value
                val loss = jitterBuffer.packetLossRatio()
                // Adapt Opus bitrate dynamically to measured Bluetooth RFCOMM throughput
                val targetBitrate = when {
                    throughput < 28_000L || loss > 0.12f -> Constants.OPUS_MIN_BITRATE_BPS
                    throughput < 56_000L || loss > 0.05f -> 18_000
                    else -> Constants.OPUS_DEFAULT_BITRATE_BPS
                }
                encoder.updateBitrate(targetBitrate)

                _callQuality.value = when {
                    loss > 0.12f || throughput < 20_000L -> CallQuality.POOR
                    loss > 0.05f || throughput < 40_000L -> CallQuality.FAIR
                    loss < 0.02f && throughput >= 64_000L -> CallQuality.EXCELLENT
                    else -> CallQuality.GOOD
                }
            }
        }

        Logger.i(TAG, "Full-duplex AudioEngine started with peer=$remoteDeviceId")
        return Result.success(Unit)
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun setSpeaker(enabled: Boolean) {
        audioSessionManager.setSpeakerphone(enabled)
    }

    fun stop() {
        isActiveEngine = false
        captureJob?.cancel()
        playbackJob?.cancel()
        inboundListenerJob?.cancel()
        qualityMonitorJob?.cancel()
        captureJob = null
        playbackJob = null
        inboundListenerJob = null
        qualityMonitorJob = null

        audioRecorder.stop()
        audioPlayer.stop()
        encoder.release()
        decoder.release()
        jitterBuffer.reset()
        audioSessionManager.releaseCallAudioSession()
        _isMuted.value = false
        Logger.i(TAG, "AudioEngine stopped and all audio resources released")
    }

    companion object {
        private const val TAG = "AudioEngine"
    }
}
