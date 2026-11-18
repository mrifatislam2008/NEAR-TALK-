package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.CallManager
import com.example.bluetooth.ConnectionState
import com.example.core.ConnectionManager
import com.example.data.local.entities.CallHistoryEntity
import com.example.data.repository.CallHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CallFilter(val label: String) {
    ALL("All"),
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    MISSED("Missed")
}

@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val callHistoryRepository: CallHistoryRepository,
    private val connectionManager: ConnectionManager,
    private val callManager: CallManager
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(CallFilter.ALL)
    val selectedFilter: StateFlow<CallFilter> = _selectedFilter.asStateFlow()

    val calls: StateFlow<List<CallHistoryEntity>> = combine(
        callHistoryRepository.allCalls,
        _selectedFilter
    ) { all, filter ->
        when (filter) {
            CallFilter.ALL -> all
            CallFilter.INCOMING -> all.filter { it.callType == "INCOMING" && it.callStatus != "MISSED" }
            CallFilter.OUTGOING -> all.filter { it.callType == "OUTGOING" }
            CallFilter.MISSED -> all.filter { it.callStatus == "MISSED" || it.callStatus == "REJECTED" }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectedPeerId: StateFlow<String?> = combine(
        connectionManager.connectedPeer,
        connectionManager.connectionState
    ) { peer, state ->
        if (state == ConnectionState.CONNECTED) peer?.id else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectFilter(filter: CallFilter) {
        _selectedFilter.value = filter
    }

    fun callIfConnected(deviceId: String) {
        if (connectedPeerId.value == deviceId) {
            callManager.startOutgoingCall()
        }
    }

    fun deleteCall(id: String) {
        viewModelScope.launch { callHistoryRepository.deleteCall(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { callHistoryRepository.clearAll() }
    }
}
