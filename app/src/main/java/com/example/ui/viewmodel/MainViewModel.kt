package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ActiveCallInfo
import com.example.audio.CallManager
import com.example.audio.CallQuality
import com.example.audio.CallState
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.PendingConnectionRequest
import com.example.core.BluetoothManager
import com.example.core.ConnectionManager
import com.example.core.DeviceIdentityManager
import com.example.core.PermissionManager
import com.example.core.PermissionStatus
import com.example.core.UserIdentity
import com.example.data.local.entities.DeviceEntity
import com.example.service.BluetoothService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityManager: DeviceIdentityManager,
    private val bluetoothManager: BluetoothManager,
    private val permissionManager: PermissionManager,
    private val connectionManager: ConnectionManager,
    private val callManager: CallManager
) : ViewModel() {

    val identity: StateFlow<UserIdentity?> = identityManager.identityFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val connectedPeer: StateFlow<DeviceEntity?> = connectionManager.connectedPeer
    val pendingRequest: StateFlow<PendingConnectionRequest?> = connectionManager.pendingRequest
    val connectionError: StateFlow<String?> = connectionManager.errorMessage

    val callState: StateFlow<CallState> = callManager.callState
    val activeCallInfo: StateFlow<ActiveCallInfo> = callManager.activeCallInfo
    val isMuted: StateFlow<Boolean> = callManager.isMuted
    val isSpeakerOn: StateFlow<Boolean> = callManager.isSpeakerOn
    val callQuality: StateFlow<CallQuality> = callManager.callQuality

    val permissionStatus: StateFlow<PermissionStatus> = permissionManager.status
    val isBluetoothSupported: StateFlow<Boolean> = bluetoothManager.isBluetoothSupported
    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothManager.isBluetoothEnabled

    fun refreshHardwareAndPermissions() {
        bluetoothManager.refreshState()
        if (permissionManager.evaluateStatus().allBluetoothGranted &&
            bluetoothManager.adapter?.isEnabled == true
        ) {
            connectionManager.ensureServerListening()
            startBluetoothLinkService()
        }
    }

    fun startBluetoothLinkService() {
        if (!permissionManager.evaluateStatus().allBluetoothGranted) return
        if (bluetoothManager.adapter?.isEnabled != true) return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BluetoothService::class.java)
            )
        }
    }

    fun requiredBluetoothPermissions(): Array<String> = permissionManager.requiredBluetoothPermissions()
    fun requiredAllPermissions(): Array<String> = permissionManager.requiredAllPermissions()
    fun buildEnableBluetoothIntent(): Intent = bluetoothManager.buildEnableBluetoothIntent()
    fun buildDiscoverableIntent(): Intent = bluetoothManager.buildDiscoverableIntent()

    fun saveProfile(displayName: String, nickname: String, imageUri: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            identityManager.saveProfile(displayName, nickname, imageUri)
            onDone()
        }
    }

    fun acceptConnectionRequest() = connectionManager.acceptIncomingConnection()
    fun rejectConnectionRequest() = connectionManager.rejectIncomingConnection()
    fun disconnectPeer() = connectionManager.disconnect()
    fun clearConnectionError() = connectionManager.clearError()

    fun startOutgoingCall() = callManager.startOutgoingCall()
    fun acceptIncomingCall() = callManager.acceptIncomingCall()
    fun rejectIncomingCall() = callManager.rejectIncomingCall()
    fun endCall() = callManager.endCall()
    fun toggleMute() = callManager.toggleMute()
    fun toggleSpeaker() = callManager.toggleSpeaker()
    fun resetCallState() = callManager.resetCallState()
}
