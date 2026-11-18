package com.example.messaging

import com.example.bluetooth.BluetoothTransport
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.PacketType
import com.example.core.ConnectionManager
import com.example.core.DeviceIdentityManager
import com.example.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WireTextMessage(
    val id: String,
    val content: String,
    val timestamp: Long,
    val sequenceNumber: Int
)

@Singleton
class MessageSender @Inject constructor(
    private val transport: BluetoothTransport,
    private val connectionManager: ConnectionManager,
    private val identityManager: DeviceIdentityManager,
    private val messageRepository: MessageRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendOrQueueTextMessage(
        conversationId: String,
        content: String,
        sequenceNumber: Int
    ): Message {
        val localId = identityManager.ensureDeviceId()
        val msg = Message(
            id = java.util.UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = localId,
            receiverId = conversationId,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING,
            sequenceNumber = sequenceNumber,
            isIncoming = false,
            isReadLocally = true
        )

        messageRepository.saveMessageIfNew(msg)
        tryTransmitMessage(msg)
        return msg
    }

    suspend fun retryPendingForPeer(conversationId: String) {
        val pending = messageRepository.getPendingMessages(conversationId)
        for (msg in pending) {
            if (connectionManager.connectionState.value != ConnectionState.CONNECTED ||
                connectionManager.connectedPeer.value?.id != conversationId
            ) {
                break
            }
            tryTransmitMessage(msg)
        }
    }

    private suspend fun tryTransmitMessage(msg: Message) {
        val connectedPeerId = connectionManager.connectedPeer.value?.id
        if (connectionManager.connectionState.value != ConnectionState.CONNECTED ||
            connectedPeerId != msg.conversationId
        ) {
            // Keep PENDING until device reconnects — never pretend delivered
            return
        }

        val wire = WireTextMessage(
            id = msg.id,
            content = msg.content,
            timestamp = msg.timestamp,
            sequenceNumber = msg.sequenceNumber
        )
        runCatching {
            transport.sendPacket(
                type = PacketType.TEXT_MESSAGE,
                senderId = msg.senderId,
                receiverId = msg.receiverId,
                payload = json.encodeToString(wire).toByteArray(Charsets.UTF_8),
                encryptIfActive = true
            )
            // Mark SENT once handed to RFCOMM output queue; DELIVERED waits strictly for MESSAGE_ACK
            messageRepository.updateStatus(msg.id, MessageStatus.SENT)
        }.onFailure { e ->
            Logger.w(TAG, "Failed to transmit message; leaving PENDING: ${e.message}")
            messageRepository.updateStatus(msg.id, MessageStatus.PENDING)
        }
    }

    companion object {
        private const val TAG = "MessageSender"
    }
}
