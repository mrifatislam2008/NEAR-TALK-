package com.example.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.bluetooth.BluetoothTransport
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.Packet
import com.example.bluetooth.PacketType
import com.example.core.ConnectionManager
import com.example.core.DeviceIdentityManager
import com.example.core.PermissionManager
import com.example.data.local.entities.CallHistoryEntity
import com.example.data.repository.CallHistoryRepository
import com.example.service.ActiveCallService
import com.example.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class CallState {
    IDLE,
    OUTGOING,
    INCOMING,
    CONNECTING,
    ACTIVE,
    MUTED,
    ENDING,
    ENDED,
    FAILED
}

data class ActiveCallInfo(
    val callId: String = "",
    val remoteDeviceId: String = "",
    val remoteDisplayName: String = "",
    val isOutgoing: Boolean = true,
    val startTimeMs: Long = 0L,
    val connectedTimeMs: Long = 0L,
    val durationSeconds: Long = 0L,
    val errorMessage: String? = null
)

@Serializable
private data class CallSignalPayload(
    val callId: String,
    val callerName: String,
    val reason: String = ""
)

@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transport: BluetoothTransport,
    private val connectionManager: ConnectionManager,
    private val identityManager: DeviceIdentityManager,
    private val permissionManager: PermissionManager,
    private val audioEngine: AudioEngine,
    private val audioSessionManager: AudioSessionManager,
    private val callHistoryRepository: CallHistoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _activeCallInfo = MutableStateFlow(ActiveCallInfo())
    val activeCallInfo: StateFlow<ActiveCallInfo> = _activeCallInfo.asStateFlow()

    val isMuted: StateFlow<Boolean> = audioEngine.isMuted
    val isSpeakerOn: StateFlow<Boolean> = audioSessionManager.isSpeakerOn
    val callQuality: StateFlow<CallQuality> = audioEngine.callQuality

    private var durationJob: Job? = null
    private var ringTimeoutJob: Job? = null

    init {
        observeCallPackets()
        observeConnectionDrops()
    }

    private fun observeCallPackets() {
        scope.launch {
            transport.incomingPackets.collect { packet ->
                when (packet.type) {
                    PacketType.CALL_REQUEST -> handleIncomingCallRequest(packet)
                    PacketType.CALL_ACCEPT -> handleRemoteCallAccept(packet)
                    PacketType.CALL_REJECT -> handleRemoteCallReject(packet)
                    PacketType.CALL_END -> handleRemoteCallEnd(packet)
                    else -> Unit
                }
            }
        }
    }

    private fun observeConnectionDrops() {
        scope.launch {
            connectionManager.connectionState.collect { connState ->
                val activeStates = setOf(
                    CallState.OUTGOING,
                    CallState.INCOMING,
                    CallState.CONNECTING,
                    CallState.ACTIVE,
                    CallState.MUTED
                )
                if (connState in setOf(ConnectionState.DISCONNECTED, ConnectionState.ERROR) &&
                    _callState.value in activeStates
                ) {
                    Logger.w(TAG, "Bluetooth disconnected during call; releasing audio resources safely")
                    terminateCallLocally(
                        finalStatus = if (_callState.value in setOf(CallState.ACTIVE, CallState.MUTED)) "COMPLETED" else "FAILED",
                        newState = CallState.FAILED,
                        errorMessage = "Bluetooth connection lost during call."
                    )
                }
            }
        }
    }

    fun startOutgoingCall() {
        if (_callState.value !in setOf(CallState.IDLE, CallState.ENDED, CallState.FAILED)) return
        val peer = connectionManager.connectedPeer.value
        if (connectionManager.connectionState.value != ConnectionState.CONNECTED || peer == null) {
            _callState.value = CallState.FAILED
            _activeCallInfo.value = ActiveCallInfo(errorMessage = "Connect to a nearby device before calling.")
            return
        }
        if (!permissionManager.hasAudioPermission()) {
            _callState.value = CallState.FAILED
            _activeCallInfo.value = ActiveCallInfo(errorMessage = "Microphone permission is required to place voice calls.")
            return
        }

        val callId = UUID.randomUUID().toString()
        _activeCallInfo.value = ActiveCallInfo(
            callId = callId,
            remoteDeviceId = peer.id,
            remoteDisplayName = peer.displayName,
            isOutgoing = true,
            startTimeMs = System.currentTimeMillis()
        )
        _callState.value = CallState.OUTGOING

        scope.launch {
            val identity = identityManager.getSnapshot()
            val signal = CallSignalPayload(
                callId = callId,
                callerName = identity.displayName.ifBlank { identity.deviceNickname }
            )
            runCatching {
                transport.sendPacket(
                    type = PacketType.CALL_REQUEST,
                    senderId = identity.deviceId,
                    receiverId = peer.id,
                    payload = json.encodeToString(signal).toByteArray(Charsets.UTF_8),
                    encryptIfActive = true
                )
            }.onFailure { e ->
                terminateCallLocally("FAILED", CallState.FAILED, "Failed to initiate call: ${e.message}")
                return@launch
            }

            ringTimeoutJob?.cancel()
            ringTimeoutJob = scope.launch {
                delay(30_000L)
                if (_callState.value == CallState.OUTGOING) {
                    endCall("NO_ANSWER")
                }
            }
        }
    }

    private fun handleIncomingCallRequest(packet: Packet) {
        val signal = runCatching {
            json.decodeFromString<CallSignalPayload>(String(packet.payload, Charsets.UTF_8))
        }.getOrNull() ?: return

        if (_callState.value !in setOf(CallState.IDLE, CallState.ENDED, CallState.FAILED)) {
            // Busy in another call -> reject automatically
            scope.launch {
                val localId = identityManager.ensureDeviceId()
                val busySignal = CallSignalPayload(signal.callId, "", "BUSY")
                transport.sendPacket(
                    type = PacketType.CALL_REJECT,
                    senderId = localId,
                    receiverId = packet.senderId,
                    payload = json.encodeToString(busySignal).toByteArray(Charsets.UTF_8),
                    encryptIfActive = true
                )
            }
            return
        }

        val peerName = connectionManager.connectedPeer.value?.displayName ?: signal.callerName
        _activeCallInfo.value = ActiveCallInfo(
            callId = signal.callId,
            remoteDeviceId = packet.senderId,
            remoteDisplayName = peerName,
            isOutgoing = false,
            startTimeMs = System.currentTimeMillis()
        )
        _callState.value = CallState.INCOMING
    }

    fun acceptIncomingCall() {
        val info = _activeCallInfo.value
        if (_callState.value != CallState.INCOMING) return
        if (!permissionManager.hasAudioPermission()) {
            rejectIncomingCall("MICROPHONE_PERMISSION_DENIED")
            _activeCallInfo.value = info.copy(errorMessage = "Microphone permission is required to answer calls.")
            return
        }

        _callState.value = CallState.CONNECTING
        scope.launch {
            val identity = identityManager.getSnapshot()
            val signal = CallSignalPayload(
                callId = info.callId,
                callerName = identity.displayName.ifBlank { identity.deviceNickname }
            )
            transport.sendPacket(
                type = PacketType.CALL_ACCEPT,
                senderId = identity.deviceId,
                receiverId = info.remoteDeviceId,
                payload = json.encodeToString(signal).toByteArray(Charsets.UTF_8),
                encryptIfActive = true
            )
            startActiveAudioSession(info.remoteDeviceId, identity.speakerByDefault)
        }
    }

    fun rejectIncomingCall(reason: String = "DECLINED") {
        val info = _activeCallInfo.value
        if (_callState.value != CallState.INCOMING) return
        scope.launch {
            val localId = identityManager.ensureDeviceId()
            val signal = CallSignalPayload(info.callId, "", reason)
            runCatching {
                transport.sendPacket(
                    type = PacketType.CALL_REJECT,
                    senderId = localId,
                    receiverId = info.remoteDeviceId,
                    payload = json.encodeToString(signal).toByteArray(Charsets.UTF_8),
                    encryptIfActive = true
                )
            }
            terminateCallLocally("REJECTED", CallState.ENDED, null)
        }
    }

    private fun handleRemoteCallAccept(packet: Packet) {
        if (_callState.value != CallState.OUTGOING) return
        ringTimeoutJob?.cancel()
        _callState.value = CallState.CONNECTING
        scope.launch {
            val identity = identityManager.getSnapshot()
            startActiveAudioSession(_activeCallInfo.value.remoteDeviceId, identity.speakerByDefault)
        }
    }

    private fun handleRemoteCallReject(packet: Packet) {
        if (_callState.value !in setOf(CallState.OUTGOING, CallState.CONNECTING)) return
        ringTimeoutJob?.cancel()
        val signal = runCatching {
            json.decodeFromString<CallSignalPayload>(String(packet.payload, Charsets.UTF_8))
        }.getOrNull()
        val msg = if (signal?.reason == "BUSY") "Recipient is busy on another call." else "Call was declined."
        terminateCallLocally("REJECTED", CallState.ENDED, msg)
    }

    private fun handleRemoteCallEnd(packet: Packet) {
        if (_callState.value in setOf(CallState.IDLE, CallState.ENDED)) return
        val wasAnswered = _callState.value in setOf(CallState.ACTIVE, CallState.MUTED, CallState.CONNECTING)
        val status = if (wasAnswered) "COMPLETED" else "MISSED"
        terminateCallLocally(status, CallState.ENDED, null)
    }

    private suspend fun startActiveAudioSession(remoteDeviceId: String, speakerDefault: Boolean) {
        startCallForegroundService(_activeCallInfo.value.remoteDisplayName)
        val result = audioEngine.start(remoteDeviceId, speakerDefault)
        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: "Audio initialization failed."
            endCall("AUDIO_ERROR")
            _callState.value = CallState.FAILED
            _activeCallInfo.value = _activeCallInfo.value.copy(errorMessage = msg)
            return
        }

        val now = System.currentTimeMillis()
        _activeCallInfo.value = _activeCallInfo.value.copy(connectedTimeMs = now, durationSeconds = 0L)
        _callState.value = CallState.ACTIVE

        durationJob?.cancel()
        durationJob = scope.launch {
            while (isActive && _callState.value in setOf(CallState.ACTIVE, CallState.MUTED)) {
                delay(1000L)
                val secs = (System.currentTimeMillis() - now) / 1000L
                _activeCallInfo.value = _activeCallInfo.value.copy(durationSeconds = secs)
            }
        }
    }

    fun toggleMute() {
        if (_callState.value !in setOf(CallState.ACTIVE, CallState.MUTED)) return
        val newMuted = !audioEngine.isMuted.value
        audioEngine.setMuted(newMuted)
        _callState.value = if (newMuted) CallState.MUTED else CallState.ACTIVE
    }

    fun toggleSpeaker() {
        val next = !audioSessionManager.isSpeakerOn.value
        audioEngine.setSpeaker(next)
    }

    fun endCall(reason: String = "HANGUP") {
        val currentState = _callState.value
        if (currentState in setOf(CallState.IDLE, CallState.ENDING, CallState.ENDED)) return
        val info = _activeCallInfo.value
        _callState.value = CallState.ENDING
        ringTimeoutJob?.cancel()

        scope.launch {
            val localId = identityManager.ensureDeviceId()
            if (connectionManager.connectionState.value == ConnectionState.CONNECTED) {
                val signal = CallSignalPayload(info.callId, "", reason)
                runCatching {
                    transport.sendPacket(
                        type = PacketType.CALL_END,
                        senderId = localId,
                        receiverId = info.remoteDeviceId,
                        payload = json.encodeToString(signal).toByteArray(Charsets.UTF_8),
                        encryptIfActive = true
                    )
                }
            }
            val status = when {
                currentState in setOf(CallState.ACTIVE, CallState.MUTED) -> "COMPLETED"
                reason == "NO_ANSWER" -> "MISSED"
                else -> "COMPLETED"
            }
            terminateCallLocally(status, CallState.ENDED, null)
        }
    }

    private fun terminateCallLocally(
        finalStatus: String,
        newState: CallState,
        errorMessage: String?
    ) {
        ringTimeoutJob?.cancel()
        durationJob?.cancel()
        audioEngine.stop()
        stopCallForegroundService()

        val info = _activeCallInfo.value
        val endMs = System.currentTimeMillis()
        if (info.callId.isNotBlank() && info.remoteDeviceId.isNotBlank()) {
            val durationSecs = if (info.connectedTimeMs > 0L) {
                ((endMs - info.connectedTimeMs) / 1000L).coerceAtLeast(0L)
            } else {
                0L
            }
            scope.launch {
                callHistoryRepository.recordCall(
                    CallHistoryEntity(
                        id = info.callId,
                        remoteDeviceId = info.remoteDeviceId,
                        remoteDisplayName = info.remoteDisplayName.ifBlank { "NEAR TALK Peer" },
                        callType = if (info.isOutgoing) "OUTGOING" else "INCOMING",
                        startTime = info.startTimeMs.takeIf { it > 0L } ?: endMs,
                        endTime = endMs,
                        duration = durationSecs,
                        callStatus = finalStatus
                    )
                )
            }
        }

        _activeCallInfo.value = info.copy(errorMessage = errorMessage)
        _callState.value = newState
    }

    fun resetCallState() {
        if (_callState.value in setOf(CallState.ENDED, CallState.FAILED)) {
            _callState.value = CallState.IDLE
            _activeCallInfo.value = ActiveCallInfo()
        }
    }

    private fun startCallForegroundService(peerName: String) {
        runCatching {
            val intent = Intent(context, ActiveCallService::class.java).apply {
                putExtra(ActiveCallService.EXTRA_PEER_NAME, peerName)
            }
            ContextCompat.startForegroundService(context, intent)
        }.onFailure {
            Logger.w(TAG, "Unable to start ActiveCallService: ${it.message}")
        }
    }

    private fun stopCallForegroundService() {
        runCatching {
            context.stopService(Intent(context, ActiveCallService::class.java))
        }
    }

    companion object {
        private const val TAG = "CallManager"
    }
}
