package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

data class ConversationSummaryTuple(
    val conversationId: String,
    val lastContent: String,
    val lastTimestamp: Long,
    val lastStatus: String,
    val lastIsIncoming: Boolean,
    val unreadCount: Int
)

@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM messages 
        WHERE conversationId = :conversationId 
        ORDER BY timestamp ASC, sequenceNumber ASC
        """
    )
    fun observeMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT m.conversationId AS conversationId,
               m.content AS lastContent,
               m.timestamp AS lastTimestamp,
               m.status AS lastStatus,
               m.isIncoming AS lastIsIncoming,
               (SELECT COUNT(*) FROM messages u 
                WHERE u.conversationId = m.conversationId 
                  AND u.isIncoming = 1 
                  AND u.isReadLocally = 0) AS unreadCount
        FROM messages m
        INNER JOIN (
            SELECT conversationId, MAX(timestamp) AS maxTs
            FROM messages
            GROUP BY conversationId
        ) latest
        ON m.conversationId = latest.conversationId AND m.timestamp = latest.maxTs
        GROUP BY m.conversationId
        ORDER BY m.timestamp DESC
        """
    )
    fun observeConversationSummaries(): Flow<List<ConversationSummaryTuple>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageIfAbsent(message: MessageEntity): Long

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query(
        """
        UPDATE messages 
        SET isReadLocally = 1 
        WHERE conversationId = :conversationId AND isIncoming = 1 AND isReadLocally = 0
        """
    )
    suspend fun markConversationReadLocally(conversationId: String)

    @Query(
        """
        SELECT id FROM messages 
        WHERE conversationId = :conversationId 
          AND isIncoming = 1 
          AND status != 'READ'
        """
    )
    suspend fun getUnacknowledgedIncomingIds(conversationId: String): List<String>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)
}
