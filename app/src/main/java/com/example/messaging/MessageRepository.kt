package com.example.messaging

import com.example.data.local.ConversationSummaryTuple
import com.example.data.local.MessageDao
import com.example.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao
) {
    fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeMessagesForConversation(conversationId).map { list ->
            list.map { it.toDomain() }
        }

    fun observeConversationSummaries(): Flow<List<ConversationSummaryTuple>> =
        messageDao.observeConversationSummaries()

    suspend fun saveMessageIfNew(message: Message): Boolean {
        val insertedRow = messageDao.insertMessageIfAbsent(message.toEntity())
        return insertedRow != -1L
    }

    suspend fun updateStatus(messageId: String, status: MessageStatus) {
        val existing = messageDao.getMessageById(messageId) ?: return
        val current = MessageStatus.fromString(existing.status)
        // Never downgrade status (e.g., READ -> DELIVERED)
        val shouldUpdate = when {
            status == MessageStatus.FAILED -> current == MessageStatus.PENDING
            status == MessageStatus.SENT -> current == MessageStatus.PENDING || current == MessageStatus.FAILED
            status == MessageStatus.DELIVERED -> current != MessageStatus.READ
            status == MessageStatus.READ -> true
            else -> false
        }
        if (shouldUpdate) {
            messageDao.updateMessageStatus(messageId, status.value)
        }
    }

    suspend fun getPendingMessages(conversationId: String): List<Message> =
        messageDao.getPendingMessagesForConversation(conversationId).map { it.toDomain() }

    suspend fun markConversationReadLocally(conversationId: String): List<String> {
        val unackedIds = messageDao.getUnacknowledgedIncomingIds(conversationId)
        messageDao.markConversationReadLocally(conversationId)
        unackedIds.forEach { id ->
            messageDao.updateMessageStatus(id, MessageStatus.READ.value)
        }
        return unackedIds
    }

    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteConversation(conversationId)
    }

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        timestamp = timestamp,
        status = MessageStatus.fromString(status),
        sequenceNumber = sequenceNumber,
        isIncoming = isIncoming,
        isReadLocally = isReadLocally
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        timestamp = timestamp,
        status = status.value,
        sequenceNumber = sequenceNumber,
        isIncoming = isIncoming,
        isReadLocally = isReadLocally
    )
}
