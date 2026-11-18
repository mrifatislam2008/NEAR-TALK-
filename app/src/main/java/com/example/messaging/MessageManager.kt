package com.example.messaging

import com.example.bluetooth.BluetoothTransport
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.PacketType
import com.example.core.ConnectionManager
import com.example.data.local.ConversationSummaryTuple
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageManager @Inject constructor(
    private val transport: BluetoothTransport,
    private val connectionManager: ConnectionManager,
    private val messageRepository: MessageRepository,
    private val messageSender: MessageSender,
    private val messageReceiver: MessageReceiver,
    private val acknowledgement: MessageAcknowledgement
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outboundSequence = AtomicInteger(1)

    private val _incomingMessageEvents = MutableSharedFlow<Message>(extraBufferCapacity = 32)
    val incomingMessageEvents: SharedFlow<Message> = _incomingMessageEvents.asSharedFlow()

    init {
        observePackets()
        observeReconnections()
    }

    private fun observePackets() {
        scope.launch {
            transport.incomingPackets.collect { packet ->
                when (packet.type) {
                    PacketType.TEXT_MESSAGE -> {
                        val saved = messageReceiver.handleIncomingTextPacket(packet)
                        if (saved != null) {
                            _incomingMessageEvents.emit(saved)
                        }
                    }
                    PacketType.MESSAGE_ACK -> acknowledgement.handleIncomingDeliveryAck(packet.payload)
                    PacketType.MESSAGE_READ -> acknowledgement.handleIncomingReadReceipt(packet.payload)
                    else -> Unit
                }
            }
        }
    }

    private fun observeReconnections() {
        scope.launch {
            connectionManager.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    val peerId = connectionManager.connectedPeer.value?.id
                    if (!peerId.isNullOrBlank()) {
                        messageSender.retryPendingForPeer(peerId)
                    }
                }
            }
        }
    }

    fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageRepository.observeMessages(conversationId)

    fun observeConversationSummaries(): Flow<List<ConversationSummaryTuple>> =
        messageRepository.observeConversationSummaries()

    suspend fun sendMessage(conversationId: String, content: String): Message? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        return messageSender.sendOrQueueTextMessage(
            conversationId = conversationId,
            content = trimmed,
            sequenceNumber = outboundSequence.getAndIncrement()
        )
    }

    suspend fun retryPendingMessages(conversationId: String) {
        messageSender.retryPendingForPeer(conversationId)
    }

    suspend fun openConversation(conversationId: String) {
        messageReceiver.setActiveOpenConversation(conversationId)
        val newlyReadIds = messageRepository.markConversationReadLocally(conversationId)
        if (connectionManager.connectionState.value == ConnectionState.CONNECTED &&
            connectionManager.connectedPeer.value?.id == conversationId
        ) {
            acknowledgement.sendReadReceipts(newlyReadIds, conversationId)
        }
    }

    fun closeConversation(conversationId: String) {
        if (messageReceiver.activeOpenConversationId.value == conversationId) {
            messageReceiver.setActiveOpenConversation(null)
        }
    }
}
