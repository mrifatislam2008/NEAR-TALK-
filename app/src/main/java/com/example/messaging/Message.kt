package com.example.messaging

enum class MessageStatus(val value: String) {
    PENDING("PENDING"),
    SENT("SENT"),
    DELIVERED("DELIVERED"),
    READ("READ"),
    FAILED("FAILED");

    companion object {
        fun fromString(raw: String): MessageStatus =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus,
    val sequenceNumber: Int,
    val isIncoming: Boolean,
    val isReadLocally: Boolean
)
