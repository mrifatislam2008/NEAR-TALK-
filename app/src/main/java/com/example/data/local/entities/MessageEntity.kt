package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "timestamp"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val status: String, // PENDING, SENT, DELIVERED, READ, FAILED
    val sequenceNumber: Int,
    val isIncoming: Boolean = false,
    val isReadLocally: Boolean = true
)
