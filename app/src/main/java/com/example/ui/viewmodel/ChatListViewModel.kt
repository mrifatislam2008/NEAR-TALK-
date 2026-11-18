package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.ConnectionState
import com.example.core.ConnectionManager
import com.example.data.repository.DeviceRepository
import com.example.messaging.MessageManager
import com.example.messaging.MessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ConversationPreviewUi(
    val peerId: String,
    val peerName: String,
    val peerAvatarUri: String?,
    val lastMessage: String,
    val lastTimestamp: Long,
    val lastStatus: MessageStatus,
    val lastIsIncoming: Boolean,
    val unreadCount: Int,
    val isConnected: Boolean,
    val isTrusted: Boolean
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    messageManager: MessageManager,
    deviceRepository: DeviceRepository,
    connectionManager: ConnectionManager
) : ViewModel() {

    val conversations: StateFlow<List<ConversationPreviewUi>> = combine(
        messageManager.observeConversationSummaries(),
        deviceRepository.allDevices,
        connectionManager.connectedPeer,
        connectionManager.connectionState
    ) { summaries, devices, connectedPeer, connState ->
        val deviceMap = devices.associateBy { it.id }
        summaries.mapNotNull { summary ->
            val dev = deviceMap[summary.conversationId]
            if (dev?.isBlocked == true) return@mapNotNull null
            val isConnected = connState == ConnectionState.CONNECTED && connectedPeer?.id == summary.conversationId
            ConversationPreviewUi(
                peerId = summary.conversationId,
                peerName = dev?.displayName?.ifBlank { "NEAR TALK Peer" } ?: "NEAR TALK Peer",
                peerAvatarUri = dev?.profileImage,
                lastMessage = summary.lastContent,
                lastTimestamp = summary.lastTimestamp,
                lastStatus = MessageStatus.fromString(summary.lastStatus),
                lastIsIncoming = summary.lastIsIncoming,
                unreadCount = summary.unreadCount,
                isConnected = isConnected,
                isTrusted = dev?.isTrusted == true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
