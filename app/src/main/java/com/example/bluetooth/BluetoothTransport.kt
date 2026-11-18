package com.example.bluetooth

import android.bluetooth.BluetoothSocket
import com.example.core.SecurityManager
import com.example.util.Constants
import com.example.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated BluetoothTransport layer managing:
 * - Buffered RFCOMM input & output streams
 * - Packet framing via PacketEncoder / PacketDecoder
 * - Dual-priority bounded queues (AUDIO_PACKET strictly prioritized over messages/control)
 * - AES-256-GCM encryption/decryption once session keys are established
 * - Heartbeat keep-alive & dead-link detection
 * - Live measured Bluetooth throughput estimation (bytes/sec) for adaptive Opus bitrate tuning
 */
@Singleton
class BluetoothTransport @Inject constructor(
    private val securityManager: SecurityManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decoder = PacketDecoder()

    private val packetIdCounter = AtomicLong(1L)
    private val sequenceCounter = AtomicInteger(1)

    // Bounded priority queues preventing unbounded memory growth
    private val audioOutboundQueue = Channel<Packet>(
        capacity = Constants.AUDIO_QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val controlOutboundQueue = Channel<Packet>(
        capacity = Constants.MESSAGE_QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    private val _incomingPackets = MutableSharedFlow<Packet>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<Packet> = _incomingPackets.asSharedFlow()

    private val _measuredThroughputBps = MutableStateFlow(Constants.OPUS_DEFAULT_BITRATE_BPS * 4L)
    val measuredThroughputBps: StateFlow<Long> = _measuredThroughputBps.asStateFlow()

    private val _lastHeartbeatRttMs = MutableStateFlow(0L)
    val lastHeartbeatRttMs: StateFlow<Long> = _lastHeartbeatRttMs.asStateFlow()

    @Volatile
    private var activeSocket: BluetoothSocket? = null
    private var inputStream: BufferedInputStream? = null
    private var outputStream: BufferedOutputStream? = null

    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private var heartbeatJob: Job? = null

    @Volatile
    private var lastInboundTimestampMs: Long = 0L

    @Volatile
    private var isRunning: Boolean = false

    private var onTransportClosed: ((String?) -> Unit)? = null

    fun attachSocket(
        socket: BluetoothSocket,
        onClosed: (reason: String?) -> Unit
    ) {
        detach("Re-attaching new socket")
        activeSocket = socket
        onTransportClosed = onClosed
        decoder.reset()
        sequenceCounter.set(1)
        lastInboundTimestampMs = System.currentTimeMillis()
        isRunning = true

        try {
            inputStream = BufferedInputStream(socket.inputStream, 16 * 1024)
            outputStream = BufferedOutputStream(socket.outputStream, 16 * 1024)
        } catch (e: IOException) {
            Logger.e(TAG, "Failed to open buffered streams on RFCOMM socket", e)
            detach("Failed to open Bluetooth data streams")
            return
        }

        startWriterLoop()
        startReaderLoop()
    }

    fun startHeartbeatLoop(localId: String, remoteId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isRunning) {
                delay(Constants.HEARTBEAT_INTERVAL_MS)
                val now = System.currentTimeMillis()
                if (now - lastInboundTimestampMs > Constants.HEARTBEAT_TIMEOUT_MS) {
                    Logger.w(TAG, "Heartbeat timeout exceeded; closing transport")
                    notifyAndDetach("Connection lost (remote device stopped responding)")
                    break
                }
                val tsPayload = java.nio.ByteBuffer.allocate(8).putLong(now).array()
                sendPacket(
                    type = PacketType.HEARTBEAT,
                    senderId = localId,
                    receiverId = remoteId,
                    payload = tsPayload,
                    encryptIfActive = securityManager.hasActiveSession()
                )
            }
        }
    }

    suspend fun sendPacket(
        type: PacketType,
        senderId: String,
        receiverId: String,
        payload: ByteArray,
        encryptIfActive: Boolean = true
    ): Long {
        val pktId = packetIdCounter.getAndIncrement()
        val seq = sequenceCounter.getAndIncrement()
        val useEncryption = encryptIfActive && securityManager.hasActiveSession()
        val finalPayload = if (useEncryption) {
            val aad = PacketEncoder.buildAad(
                version = Constants.PROTOCOL_VERSION,
                type = type,
                packetId = pktId,
                senderId = senderId,
                receiverId = receiverId,
                sequenceNumber = seq
            )
            securityManager.encryptPayload(payload, aad)
        } else {
            payload
        }

        val packet = Packet(
            version = Constants.PROTOCOL_VERSION,
            type = type,
            isEncrypted = useEncryption,
            packetId = pktId,
            senderId = senderId,
            receiverId = receiverId,
            sequenceNumber = seq,
            payload = finalPayload
        )

        if (type == PacketType.AUDIO_PACKET) {
            audioOutboundQueue.trySend(packet)
        } else {
            controlOutboundQueue.send(packet)
        }
        return pktId
    }

    fun sendAudioPacketFast(
        senderId: String,
        receiverId: String,
        payload: ByteArray
    ): Boolean {
        if (!isRunning) return false
        val pktId = packetIdCounter.getAndIncrement()
        val seq = sequenceCounter.getAndIncrement()
        val useEncryption = securityManager.hasActiveSession()
        val finalPayload = if (useEncryption) {
            val aad = PacketEncoder.buildAad(
                version = Constants.PROTOCOL_VERSION,
                type = PacketType.AUDIO_PACKET,
                packetId = pktId,
                senderId = senderId,
                receiverId = receiverId,
                sequenceNumber = seq
            )
            securityManager.encryptPayload(payload, aad)
        } else {
            payload
        }
        val packet = Packet(
            version = Constants.PROTOCOL_VERSION,
            type = PacketType.AUDIO_PACKET,
            isEncrypted = useEncryption,
            packetId = pktId,
            senderId = senderId,
            receiverId = receiverId,
            sequenceNumber = seq,
            payload = finalPayload
        )
        return audioOutboundQueue.trySend(packet).isSuccess
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startWriterLoop() {
        writerJob = scope.launch {
            var bytesInWindow = 0L
            var windowStartNs = System.nanoTime()

            while (isActive && isRunning) {
                try {
                    // Strict priority: always drain high-priority audio queue first
                    val packet: Packet = audioOutboundQueue.tryReceive().getOrNull()
                        ?: select {
                            audioOutboundQueue.onReceive { it }
                            controlOutboundQueue.onReceive { it }
                        }

                    val encoded = PacketEncoder.encode(packet)
                    val out = outputStream ?: break
                    out.write(encoded)
                    // Flush immediately on audio frames or control packets for minimal latency
                    if (packet.type == PacketType.AUDIO_PACKET || audioOutboundQueue.isEmpty) {
                        out.flush()
                    }

                    bytesInWindow += encoded.size
                    val elapsedNs = System.nanoTime() - windowStartNs
                    if (elapsedNs >= 1_000_000_000L) {
                        val bps = (bytesInWindow * 8L * 1_000_000_000L) / elapsedNs
                        if (bps > 0) {
                            _measuredThroughputBps.value = bps
                        }
                        bytesInWindow = 0L
                        windowStartNs = System.nanoTime()
                    }
                } catch (e: IOException) {
                    Logger.w(TAG, "Bluetooth write error: ${e.message}")
                    notifyAndDetach("Bluetooth connection lost while sending data")
                    break
                } catch (e: Exception) {
                    Logger.e(TAG, "Unexpected writer loop exception", e)
                }
            }
        }
    }

    private fun startReaderLoop() {
        readerJob = scope.launch {
            val readBuffer = ByteArray(8192)
            while (isActive && isRunning) {
                try {
                    val stream = inputStream ?: break
                    val bytesRead = stream.read(readBuffer)
                    if (bytesRead == -1) {
                        Logger.i(TAG, "RFCOMM stream reached EOF")
                        notifyAndDetach("Remote device disconnected")
                        break
                    }
                    lastInboundTimestampMs = System.currentTimeMillis()
                    val packets = decoder.feed(readBuffer, bytesRead)
                    for (rawPacket in packets) {
                        processInboundPacket(rawPacket)
                    }
                } catch (e: IOException) {
                    if (isRunning) {
                        Logger.w(TAG, "Bluetooth read error: ${e.message}")
                        notifyAndDetach("Bluetooth connection interrupted")
                    }
                    break
                } catch (e: Exception) {
                    Logger.e(TAG, "Unexpected reader error", e)
                }
            }
        }
    }

    private suspend fun processInboundPacket(packet: Packet) {
        if (packet.isEncrypted) {
            if (!securityManager.hasActiveSession()) {
                Logger.w(TAG, "Received encrypted packet without active session; dropping")
                return
            }
            if (!securityManager.checkAndAcceptSequence(packet.sequenceNumber)) {
                Logger.w(TAG, "Replay or stale sequence detected (${packet.sequenceNumber}); dropping")
                return
            }
            val aad = PacketEncoder.buildAad(
                version = packet.version,
                type = packet.type,
                packetId = packet.packetId,
                senderId = packet.senderId,
                receiverId = packet.receiverId,
                sequenceNumber = packet.sequenceNumber
            )
            val decrypted = try {
                securityManager.decryptPayload(packet.payload, aad)
            } catch (e: Exception) {
                Logger.w(TAG, "Packet authentication/decryption failed: ${e.message}")
                return
            }
            val clearPacket = packet.copy(isEncrypted = false, payload = decrypted)
            if (clearPacket.type == PacketType.HEARTBEAT && clearPacket.payload.size >= 8) {
                val sentTs = java.nio.ByteBuffer.wrap(clearPacket.payload).getLong()
                val delta = (System.currentTimeMillis() - sentTs).coerceAtLeast(0L)
                _lastHeartbeatRttMs.value = delta
            }
            _incomingPackets.emit(clearPacket)
        } else {
            // Only handshake/auth/disconnect packets are permitted unencrypted
            if (packet.type in ALLOWED_UNENCRYPTED_TYPES) {
                _incomingPackets.emit(packet)
            } else {
                Logger.w(TAG, "Rejected unencrypted ${packet.type} packet")
            }
        }
    }

    private fun notifyAndDetach(reason: String?) {
        val cb = onTransportClosed
        detach(reason)
        cb?.invoke(reason)
    }

    fun detach(reason: String? = null) {
        if (!isRunning && activeSocket == null) return
        Logger.i(TAG, "Detaching BluetoothTransport: ${reason ?: "normal shutdown"}")
        isRunning = false
        onTransportClosed = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        readerJob?.cancel()
        readerJob = null
        writerJob?.cancel()
        writerJob = null
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { activeSocket?.close() }
        inputStream = null
        outputStream = null
        activeSocket = null
        decoder.reset()
        // Drain stale queued audio frames
        while (audioOutboundQueue.tryReceive().isSuccess) { /* drain */ }
        while (controlOutboundQueue.tryReceive().isSuccess) { /* drain */ }
    }

    companion object {
        private const val TAG = "BluetoothTransport"
        private val ALLOWED_UNENCRYPTED_TYPES = setOf(
            PacketType.HANDSHAKE,
            PacketType.AUTH_REQUEST,
            PacketType.AUTH_RESPONSE,
            PacketType.DISCONNECT,
            PacketType.HEARTBEAT
        )
    }
}
