package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.BluetoothManager
import com.example.core.DeviceIdentityManager
import com.example.core.UserIdentity
import com.example.data.local.entities.DeviceEntity
import com.example.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val identityManager: DeviceIdentityManager,
    private val deviceRepository: DeviceRepository,
    bluetoothManager: BluetoothManager
) : ViewModel() {

    val identity: StateFlow<UserIdentity> = identityManager.identityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserIdentity())

    val trustedDevices: StateFlow<List<DeviceEntity>> = deviceRepository.trustedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedDevices: StateFlow<List<DeviceEntity>> = deviceRepository.blockedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothManager.isBluetoothEnabled

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { identityManager.updateNotificationsEnabled(enabled) }
    }

    fun setAutoAcceptTrusted(enabled: Boolean) {
        viewModelScope.launch { identityManager.updateAutoAcceptTrusted(enabled) }
    }

    fun setSpeakerByDefault(enabled: Boolean) {
        viewModelScope.launch { identityManager.updateSpeakerDefault(enabled) }
    }

    fun removeTrust(deviceId: String) {
        viewModelScope.launch { deviceRepository.setTrusted(deviceId, false) }
    }

    fun unblockDevice(deviceId: String) {
        viewModelScope.launch { deviceRepository.setBlocked(deviceId, false) }
    }
}
