package com.example.messaging

import com.example.bluetooth.BluetoothTransport
import com.example.bluetooth.PacketType
import com.example.core.DeviceIdentityManager
import com.example.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WireAckPayload(
    val messageId: String,
    val ackTimestamp: Long
)

@Singleton
class MessageAcknowledgement @Inject constructor(
    private val transport: BluetoothTransport,
    private val identityManager: DeviceIdentityManager,
    private val messageRepository: MessageRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendDeliveryAck(messageId: String, remotePeerId: String) {
        val localId = identityManager.ensureDeviceId()
        val ack = WireAckPayload(messageId = messageId, ackTimestamp = System.currentTimeMillis())
        runCatching {
            transport.sendPacket(
                type = PacketType.MESSAGE_ACK,
                senderId = localId,
                receiverId = remotePeerId,
                payload = json.encodeToString(ack).toByteArray(Charsets.UTF_8),
                encryptIfActive = true
            )
        }.onFailure {
            Logger.w(TAG, "Unable to send delivery ACK: ${it.message}")
        }
    }

    suspend fun sendReadReceipts(messageIds: List<String>, remotePeerId: String) {
        if (messageIds.isEmpty()) return
        val localId = identityManager.ensureDeviceId()
        for (id in messageIds) {
            val receipt = WireAckPayload(messageId = id, ackTimestamp = System.currentTimeMillis())
            runCatching {
                transport.sendPacket(
                    type = PacketType.MESSAGE_READ,
                    senderId = localId,
                    receiverId = remotePeerId,
                    payload = json.encodeToString(receipt).toByteArray(Charsets.UTF_8),
                    encryptIfActive = true
                )
            }
        }
    }

    suspend fun handleIncomingDeliveryAck(payload: ByteArray) {
        val ack = runCatching {
            json.decodeFromString<WireAckPayload>(String(payload, Charsets.UTF_8))
        }.getOrNull() ?: return
        messageRepository.updateStatus(ack.messageId, MessageStatus.DELIVERED)
    }

    suspend fun handleIncomingReadReceipt(payload: ByteArray) {
        val receipt = runCatching {
            json.decodeFromString<WireAckPayload>(String(payload, Charsets.UTF_8))
        }.getOrNull() ?: return
        messageRepository.updateStatus(receipt.messageId, MessageStatus.READ)
    }

    companion object {
        private const val TAG = "MessageAcknowledgement"
    }
}
