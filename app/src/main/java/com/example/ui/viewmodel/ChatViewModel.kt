package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.CallManager
import com.example.bluetooth.ConnectionState
import com.example.core.ConnectionManager
import com.example.data.local.entities.DeviceEntity
import com.example.data.repository.DeviceRepository
import com.example.messaging.Message
import com.example.messaging.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageManager: MessageManager,
    private val deviceRepository: DeviceRepository,
    private val connectionManager: ConnectionManager,
    private val callManager: CallManager
) : ViewModel() {

    private val peerIdFlow = MutableStateFlow<String?>(null)

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    val peerDevice: StateFlow<DeviceEntity?> = peerIdFlow
        .filterNotNull()
        .flatMapLatest { id -> deviceRepository.observeDevice(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> = peerIdFlow
        .filterNotNull()
        .flatMapLatest { id -> messageManager.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPeerConnected: StateFlow<Boolean> = combine(
        peerIdFlow,
        connectionManager.connectedPeer,
        connectionManager.connectionState
    ) { targetId, activePeer, state ->
        targetId != null && state == ConnectionState.CONNECTED && activePeer?.id == targetId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    fun openChat(peerId: String) {
        if (peerIdFlow.value != peerId) {
            peerIdFlow.value = peerId
        }
        viewModelScope.launch {
            messageManager.openConversation(peerId)
        }
    }

    fun closeChat() {
        peerIdFlow.value?.let { messageManager.closeConversation(it) }
    }

    fun onComposerChanged(value: String) {
        _composerText.value = value
    }

    fun sendMessage() {
        val peerId = peerIdFlow.value ?: return
        val text = _composerText.value.trim()
        if (text.isEmpty()) return
        _composerText.value = ""
        viewModelScope.launch {
            messageManager.sendMessage(peerId, text)
        }
    }

    fun retryPending() {
        val peerId = peerIdFlow.value ?: return
        viewModelScope.launch {
            messageManager.retryPendingMessages(peerId)
        }
    }

    fun startVoiceCall() {
        if (isPeerConnected.value) {
            callManager.startOutgoingCall()
        }
    }

    fun reconnectPeer() {
        val dev = peerDevice.value ?: return
        connectionManager.connectToSavedDevice(dev)
    }

    fun toggleTrusted() {
        val dev = peerDevice.value ?: return
        viewModelScope.launch {
            deviceRepository.setTrusted(dev.id, !dev.isTrusted)
        }
    }

    fun blockPeer() {
        val dev = peerDevice.value ?: return
        viewModelScope.launch {
            deviceRepository.setBlocked(dev.id, true)
            if (isPeerConnected.value) {
                connectionManager.disconnect()
            }
        }
    }

    override fun onCleared() {
        closeChat()
        super.onCleared()
    }
}
