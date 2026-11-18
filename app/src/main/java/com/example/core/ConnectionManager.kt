package com.example.core

import android.bluetooth.BluetoothSocket
import com.example.bluetooth.BleAdvertiser
import com.example.bluetooth.BleScanner
import com.example.bluetooth.BluetoothTransport
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.DiscoveredDevice
import com.example.bluetooth.Packet
import com.example.bluetooth.PacketType
import com.example.bluetooth.PendingConnectionRequest
import com.example.bluetooth.RfcommClient
import com.example.bluetooth.RfcommServer
import com.example.data.local.entities.DeviceEntity
import com.example.data.repository.DeviceRepository
import com.example.util.Constants
import com.example.util.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyPair
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class HandshakePayload(
    val deviceId: String,
    val displayName: String,
    val deviceNickname: String,
    val publicKeyBase64: String,
    val nonceBase64: String
)

@Serializable
private data class AuthProofPayload(
    val deviceId: String,
    val proofBase64: String
)

@Singleton
class ConnectionManager @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val bleScanner: BleScanner,
    private val bleAdvertiser: BleAdvertiser,
    private val rfcommServer: RfcommServer,
    private val rfcommClient: RfcommClient,
    private val transport: BluetoothTransport,
    private val securityManager: SecurityManager,
    private val identityManager: DeviceIdentityManager,
    private val deviceRepository: DeviceRepository,
    private val permissionManager: PermissionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedPeer = MutableStateFlow<DeviceEntity?>(null)
    val connectedPeer: StateFlow<DeviceEntity?> = _connectedPeer.asStateFlow()

    private val _pendingRequest = MutableStateFlow<PendingConnectionRequest?>(null)
    val pendingRequest: StateFlow<PendingConnectionRequest?> = _pendingRequest.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var userDecisionDeferred: CompletableDeferred<Boolean>? = null
    private var handshakeCompletionJob: Job? = null
    private var reconnectJob: Job? = null
    private var userInitiatedDisconnect = false
    private var lastConnectedAddress: String? = null

    // Ephemeral handshake state
    private var localKeyPair: KeyPair? = null
    private var localNonce: ByteArray? = null
    private var remoteNonce: ByteArray? = null
    private var pendingRemoteHandshake: HandshakePayload? = null
    private var isInitiatorRole: Boolean = false

    init {
        observeIncomingPackets()
        observeScanResults()
    }

    private fun observeScanResults() {
        scope.launch {
            bleScanner.isScanning.collect { scanning ->
                val current = _connectionState.value
                if (scanning && current in setOf(ConnectionState.IDLE, ConnectionState.DISCONNECTED, ConnectionState.DEVICE_FOUND, ConnectionState.ERROR)) {
                    _connectionState.value = ConnectionState.SCANNING
                } else if (!scanning && current == ConnectionState.SCANNING) {
                    _connectionState.value = if (bleScanner.discoveredDevices.value.isNotEmpty()) {
                        ConnectionState.DEVICE_FOUND
                    } else {
                        ConnectionState.IDLE
                    }
                }
            }
        }
        scope.launch {
            bleScanner.discoveredDevices.collect { devices ->
                if (devices.isNotEmpty() && _connectionState.value == ConnectionState.SCANNING) {
                    _connectionState.value = ConnectionState.DEVICE_FOUND
                }
            }
        }
    }

    private fun observeIncomingPackets() {
        scope.launch {
            transport.incomingPackets.collect { packet ->
                when (packet.type) {
                    PacketType.HANDSHAKE -> handleInboundHandshake(packet)
                    PacketType.AUTH_REQUEST -> handleInboundAuthRequest(packet)
                    PacketType.AUTH_RESPONSE -> handleInboundAuthResponse(packet)
                    PacketType.CONNECTION_READY -> handleInboundConnectionReady(packet)
                    PacketType.DISCONNECT -> handleRemoteDisconnect()
                    else -> Unit
                }
            }
        }
    }

    fun startDiscoveryAndListening() {
        _errorMessage.value = null
        if (!permissionManager.evaluateStatus().allBluetoothGranted) {
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "Bluetooth permissions are required before scanning."
            return
        }
        if (bluetoothManager.adapter?.isEnabled != true) {
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "Bluetooth is disabled. Enable Bluetooth to discover devices."
            return
        }
        ensureServerListening()
        bleAdvertiser.startAdvertising()
        bleScanner.startScan()
    }

    fun stopDiscovery() {
        bleScanner.stopScan()
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.IDLE
        }
    }

    fun ensureServerListening() {
        if (!permissionManager.hasBluetoothConnectPermission()) return
        if (bluetoothManager.adapter?.isEnabled != true) return
        bleAdvertiser.startAdvertising()
        rfcommServer.startListening(
            onClientConnected = { socket -> handleAcceptedServerSocket(socket) },
            onError = { msg ->
                Logger.w(TAG, "Server listener error: $msg")
            }
        )
    }

    fun connectToDevice(device: DiscoveredDevice) {
        if (_connectionState.value in setOf(ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING, ConnectionState.CONNECTED)) {
            return
        }
        scope.launch {
            userInitiatedDisconnect = false
            _errorMessage.value = null
            bleScanner.stopScan()
            _connectionState.value = ConnectionState.CONNECTING
            lastConnectedAddress = device.bluetoothAddress

            val result = if (device.bluetoothDevice != null) {
                rfcommClient.connect(device.bluetoothDevice)
            } else {
                rfcommClient.connectByAddress(device.bluetoothAddress)
            }

            result.fold(
                onSuccess = { socket ->
                    beginAuthenticatedHandshake(socket, isInitiator = true, remoteAddress = device.bluetoothAddress)
                },
                onFailure = { error ->
                    _connectionState.value = ConnectionState.ERROR
                    _errorMessage.value = error.message ?: "Unable to connect to ${device.displayName}."
                }
            )
        }
    }

    fun connectToSavedDevice(device: DeviceEntity) {
        val address = device.lastBluetoothAddress
        if (address.isNullOrBlank()) {
            _errorMessage.value = "Scan nearby first to locate ${device.displayName}."
            return
        }
        connectToDevice(
            DiscoveredDevice(
                deviceId = device.id,
                displayName = device.displayName,
                deviceNickname = device.deviceNickname,
                bluetoothAddress = address,
                rssi = -50,
                lastSeenAt = System.currentTimeMillis(),
                isTrusted = device.isTrusted,
                isBlocked = device.isBlocked
            )
        )
    }

    private fun handleAcceptedServerSocket(socket: BluetoothSocket) {
        scope.launch {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                // Already in an active 1-to-1 session; reject simultaneous second peer
                runCatching { socket.close() }
                return@launch
            }
            userInitiatedDisconnect = false
            _errorMessage.value = null
            lastConnectedAddress = socket.remoteDevice?.address
            beginAuthenticatedHandshake(
                socket = socket,
                isInitiator = false,
                remoteAddress = socket.remoteDevice?.address ?: ""
            )
        }
    }

    private suspend fun beginAuthenticatedHandshake(
        socket: BluetoothSocket,
        isInitiator: Boolean,
        remoteAddress: String
    ) {
        securityManager.clearSession()
        isInitiatorRole = isInitiator
        localKeyPair = securityManager.generateEphemeralKeyPair()
        localNonce = securityManager.generateRandomBytes(32)
        remoteNonce = null
        pendingRemoteHandshake = null

        transport.attachSocket(socket) { reason ->
            onTransportDropped(reason)
        }

        _connectionState.value = if (isInitiator) ConnectionState.AUTHENTICATING else ConnectionState.REQUESTING

        val identity = identityManager.getSnapshot()
        val handshake = HandshakePayload(
            deviceId = identity.deviceId,
            displayName = identity.displayName.ifBlank { identity.deviceNickname },
            deviceNickname = identity.deviceNickname,
            publicKeyBase64 = Base64.getEncoder().encodeToString(localKeyPair!!.public.encoded),
            nonceBase64 = Base64.getEncoder().encodeToString(localNonce!!)
        )
        val payloadBytes = json.encodeToString(handshake).toByteArray(Charsets.UTF_8)

        if (isInitiator) {
            transport.sendPacket(
                type = PacketType.HANDSHAKE,
                senderId = identity.deviceId,
                receiverId = "",
                payload = payloadBytes,
                encryptIfActive = false
            )
        }

        handshakeCompletionJob?.cancel()
        handshakeCompletionJob = scope.launch {
            val completed = withTimeoutOrNull(Constants.AUTH_TIMEOUT_MS + 15_000L) {
                while (_connectionState.value != ConnectionState.CONNECTED &&
                    _connectionState.value != ConnectionState.ERROR &&
                    _connectionState.value != ConnectionState.DISCONNECTED
                ) {
                    delay(100)
                }
                _connectionState.value == ConnectionState.CONNECTED
            } ?: false

            if (!completed && _connectionState.value !in setOf(ConnectionState.DISCONNECTED, ConnectionState.IDLE)) {
                Logger.w(TAG, "Authenticated handshake timed out (addr=$remoteAddress)")
                transport.detach("Authentication timed out")
                securityManager.clearSession()
                _pendingRequest.value = null
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "Connection handshake timed out."
            }
        }
    }

    private suspend fun handleInboundHandshake(packet: Packet) {
        val remote = runCatching {
            json.decodeFromString<HandshakePayload>(String(packet.payload, Charsets.UTF_8))
        }.getOrNull() ?: return

        val existing = deviceRepository.getDevice(remote.deviceId)
        if (existing?.isBlocked == true) {
            Logger.w(TAG, "Blocked device attempted connection; rejecting")
            rejectIncomingConnection()
            return
        }

        pendingRemoteHandshake = remote
        remoteNonce = Base64.getDecoder().decode(remote.nonceBase64)

        val identity = identityManager.getSnapshot()

        if (!isInitiatorRole) {
            // Responder requires explicit user approval unless auto-accept for trusted devices is enabled
            val shouldAutoAccept = existing?.isTrusted == true && identity.autoAcceptTrusted
            val approved = if (shouldAutoAccept) {
                true
            } else {
                val deferred = CompletableDeferred<Boolean>()
                userDecisionDeferred = deferred
                _connectionState.value = ConnectionState.REQUESTING
                _pendingRequest.value = PendingConnectionRequest(
                    remoteDeviceId = remote.deviceId,
                    remoteDisplayName = remote.displayName,
                    remoteNickname = remote.deviceNickname,
                    bluetoothAddress = lastConnectedAddress.orEmpty(),
                    isTrusted = existing?.isTrusted == true
                )
                deferred.await()
            }
            _pendingRequest.value = null

            if (!approved) {
                transport.sendPacket(
                    type = PacketType.DISCONNECT,
                    senderId = identity.deviceId,
                    receiverId = remote.deviceId,
                    payload = "REJECTED".toByteArray(),
                    encryptIfActive = false
                )
                transport.detach("Connection rejected by user")
                securityManager.clearSession()
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            _connectionState.value = ConnectionState.AUTHENTICATING
            val localHs = HandshakePayload(
                deviceId = identity.deviceId,
                displayName = identity.displayName.ifBlank { identity.deviceNickname },
                deviceNickname = identity.deviceNickname,
                publicKeyBase64 = Base64.getEncoder().encodeToString(localKeyPair!!.public.encoded),
                nonceBase64 = Base64.getEncoder().encodeToString(localNonce!!)
            )
            transport.sendPacket(
                type = PacketType.HANDSHAKE,
                senderId = identity.deviceId,
                receiverId = remote.deviceId,
                payload = json.encodeToString(localHs).toByteArray(Charsets.UTF_8),
                encryptIfActive = false
            )
        }

        // Derive session keys via ECDH + HKDF-SHA256
        val remotePubBytes = Base64.getDecoder().decode(remote.publicKeyBase64)
        val derived = securityManager.deriveSessionKeys(
            localKeyPair = localKeyPair!!,
            remotePublicKeyEncoded = remotePubBytes,
            localNonce = localNonce!!,
            remoteNonce = remoteNonce!!,
            isInitiator = isInitiatorRole
        )

        if (!derived) {
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "Cryptographic handshake failed."
            transport.detach("Key derivation failed")
            return
        }

        if (isInitiatorRole) {
            val proof = securityManager.computeAuthProof(localNonce!!, remoteNonce!!, identity.deviceId)
            val authReq = AuthProofPayload(identity.deviceId, Base64.getEncoder().encodeToString(proof))
            transport.sendPacket(
                type = PacketType.AUTH_REQUEST,
                senderId = identity.deviceId,
                receiverId = remote.deviceId,
                payload = json.encodeToString(authReq).toByteArray(Charsets.UTF_8),
                encryptIfActive = false
            )
        }
    }

    private suspend fun handleInboundAuthRequest(packet: Packet) {
        val remote = pendingRemoteHandshake ?: return
        val rNonce = remoteNonce ?: return
        val lNonce = localNonce ?: return
        val authReq = runCatching {
            json.decodeFromString<AuthProofPayload>(String(packet.payload, Charsets.UTF_8))
        }.getOrNull() ?: return

        val proofBytes = Base64.getDecoder().decode(authReq.proofBase64)
        if (!securityManager.verifyAuthProof(proofBytes, rNonce, lNonce, remote.deviceId)) {
            Logger.w(TAG, "Peer AUTH_REQUEST HMAC failed")
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "Peer identity verification failed."
            transport.detach("Auth proof invalid")
            return
        }

        val identity = identityManager.getSnapshot()
        val ourProof = securityManager.computeAuthProof(lNonce, rNonce, identity.deviceId)
        val authResp = AuthProofPayload(identity.deviceId, Base64.getEncoder().encodeToString(ourProof))
        transport.sendPacket(
            type = PacketType.AUTH_RESPONSE,
            senderId = identity.deviceId,
            receiverId = remote.deviceId,
            payload = json.encodeToString(authResp).toByteArray(Charsets.UTF_8),
            encryptIfActive = false
        )

        transport.sendPacket(
            type = PacketType.CONNECTION_READY,
            senderId = identity.deviceId,
            receiverId = remote.deviceId,
            payload = byteArrayOf(Constants.PROTOCOL_VERSION),
            encryptIfActive = true
        )
        finalizeConnectedPeer(remote)
    }

    private suspend fun handleInboundAuthResponse(packet: Packet) {
        val remote = pendingRemoteHandshake ?: return
        val rNonce = remoteNonce ?: return
        val lNonce = localNonce ?: return
        val authResp = runCatching {
            json.decodeFromString<AuthProofPayload>(String(packet.payload, Charsets.UTF_8))
        }.getOrNull() ?: return

        val proofBytes = Base64.getDecoder().decode(authResp.proofBase64)
        if (!securityManager.verifyAuthProof(proofBytes, rNonce, lNonce, remote.deviceId)) {
            Logger.w(TAG, "Peer AUTH_RESPONSE HMAC failed")
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "Remote device failed authentication."
            transport.detach("Auth response invalid")
            return
        }

        val identity = identityManager.getSnapshot()
        transport.sendPacket(
            type = PacketType.CONNECTION_READY,
            senderId = identity.deviceId,
            receiverId = remote.deviceId,
            payload = byteArrayOf(Constants.PROTOCOL_VERSION),
            encryptIfActive = true
        )
        finalizeConnectedPeer(remote)
    }

    private suspend fun handleInboundConnectionReady(packet: Packet) {
        // Encrypted CONNECTION_READY confirms both peers have matching AES-GCM keys
        pendingRemoteHandshake?.let { finalizeConnectedPeer(it) }
    }

    private suspend fun finalizeConnectedPeer(remote: HandshakePayload) {
        if (_connectionState.value == ConnectionState.CONNECTED && _connectedPeer.value?.id == remote.deviceId) {
            return
        }
        val existing = deviceRepository.getDevice(remote.deviceId)
        val entity = DeviceEntity(
            id = remote.deviceId,
            displayName = remote.displayName,
            deviceNickname = remote.deviceNickname,
            profileImage = existing?.profileImage,
            lastBluetoothAddress = lastConnectedAddress ?: existing?.lastBluetoothAddress,
            lastConnectedAt = System.currentTimeMillis(),
            isTrusted = existing?.isTrusted ?: false,
            isBlocked = false
        )
        deviceRepository.upsertDevice(entity)
        _connectedPeer.value = entity
        _connectionState.value = ConnectionState.CONNECTED
        _errorMessage.value = null

        val localId = identityManager.ensureDeviceId()
        transport.startHeartbeatLoop(localId, remote.deviceId)
        Logger.i(TAG, "Secure encrypted session established with ${remote.displayName}")
    }

    fun acceptIncomingConnection() {
        userDecisionDeferred?.complete(true)
    }

    fun rejectIncomingConnection() {
        userDecisionDeferred?.complete(false)
        _pendingRequest.value = null
    }

    fun disconnect(clearError: Boolean = true) {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        handshakeCompletionJob?.cancel()
        val peerId = _connectedPeer.value?.id ?: ""
        scope.launch {
            if (peerId.isNotEmpty() && securityManager.hasActiveSession()) {
                val localId = identityManager.ensureDeviceId()
                runCatching {
                    transport.sendPacket(
                        type = PacketType.DISCONNECT,
                        senderId = localId,
                        receiverId = peerId,
                        payload = "USER_DISCONNECT".toByteArray(),
                        encryptIfActive = true
                    )
                }
            }
            transport.detach("User disconnected")
            securityManager.clearSession()
            _connectedPeer.value = null
            _pendingRequest.value = null
            _connectionState.value = ConnectionState.DISCONNECTED
            if (clearError) _errorMessage.value = null
        }
    }

    private fun handleRemoteDisconnect() {
        Logger.i(TAG, "Remote device sent DISCONNECT")
        userInitiatedDisconnect = true
        transport.detach("Remote device disconnected")
        securityManager.clearSession()
        _connectedPeer.value = null
        _pendingRequest.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _errorMessage.value = "Remote device ended the connection."
    }

    private fun onTransportDropped(reason: String?) {
        securityManager.clearSession()
        val previousPeer = _connectedPeer.value
        _connectedPeer.value = null
        _pendingRequest.value = null

        if (userInitiatedDisconnect) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        if (reason == "Connection rejected by user" || reason == "Remote device disconnected") {
            _connectionState.value = ConnectionState.DISCONNECTED
            _errorMessage.value = reason
            return
        }

        // Unexpected drop -> attempt automatic reconnection if address is known
        val addr = lastConnectedAddress ?: previousPeer?.lastBluetoothAddress
        if (!addr.isNullOrBlank() && bluetoothManager.adapter?.isEnabled == true) {
            attemptReconnect(addr, previousPeer?.displayName ?: "peer", reason)
        } else {
            _connectionState.value = ConnectionState.DISCONNECTED
            _errorMessage.value = reason ?: "Bluetooth connection lost."
        }
    }

    private fun attemptReconnect(address: String, peerName: String, initialReason: String?) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            _errorMessage.value = "Connection interrupted. Reconnecting to $peerName…"
            for (attempt in 1..Constants.MAX_RECONNECT_ATTEMPTS) {
                if (userInitiatedDisconnect || bluetoothManager.adapter?.isEnabled != true) break
                delay(Constants.RECONNECT_BASE_DELAY_MS * attempt)
                Logger.i(TAG, "Reconnect attempt $attempt/${Constants.MAX_RECONNECT_ATTEMPTS}")
                val res = rfcommClient.connectByAddress(address, timeoutMs = 8_000L)
                if (res.isSuccess) {
                    beginAuthenticatedHandshake(res.getOrThrow(), isInitiator = true, remoteAddress = address)
                    return@launch
                }
            }
            _connectionState.value = ConnectionState.DISCONNECTED
            _errorMessage.value = initialReason ?: "Unable to reconnect to $peerName."
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "ConnectionManager"
    }
}
