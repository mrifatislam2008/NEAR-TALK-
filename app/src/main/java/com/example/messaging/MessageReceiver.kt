package com.example.messaging

import com.example.bluetooth.Packet
import com.example.core.DeviceIdentityManager
import com.example.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageReceiver @Inject constructor(
    private val identityManager: DeviceIdentityManager,
    private val messageRepository: MessageRepository,
    private val acknowledgement: MessageAcknowledgement
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _activeOpenConversationId = MutableStateFlow<String?>(null)
    val activeOpenConversationId: StateFlow<String?> = _activeOpenConversationId.asStateFlow()

    fun setActiveOpenConversation(conversationId: String?) {
        _activeOpenConversationId.value = conversationId
    }

    suspend fun handleIncomingTextPacket(packet: Packet): Message? {
        val wire = runCatching {
            json.decodeFromString<WireTextMessage>(String(packet.payload, Charsets.UTF_8))
        }.getOrNull() ?: run {
            Logger.w(TAG, "Malformed incoming text message payload")
            return null
        }

        val localId = identityManager.ensureDeviceId()
        val isConversationOpen = _activeOpenConversationId.value == packet.senderId

        val message = Message(
            id = wire.id,
            conversationId = packet.senderId,
            senderId = packet.senderId,
            receiverId = localId,
            content = wire.content,
            timestamp = wire.timestamp,
            status = if (isConversationOpen) MessageStatus.READ else MessageStatus.DELIVERED,
            sequenceNumber = wire.sequenceNumber,
            isIncoming = true,
            isReadLocally = isConversationOpen
        )

        val inserted = messageRepository.saveMessageIfNew(message)
        // Always send delivery ACK (even on duplicate retry so sender stops retrying)
        acknowledgement.sendDeliveryAck(wire.id, packet.senderId)
        if (isConversationOpen) {
            acknowledgement.sendReadReceipts(listOf(wire.id), packet.senderId)
        }
        return if (inserted) message else null
    }

    companion object {
        private const val TAG = "MessageReceiver"
    }
}
