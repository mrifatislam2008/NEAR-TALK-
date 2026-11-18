package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BleAdvertiser
import com.example.bluetooth.BleScanner
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.DiscoveredDevice
import com.example.core.ConnectionManager
import com.example.data.local.entities.DeviceEntity
import com.example.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleAdvertiser: BleAdvertiser,
    private val connectionManager: ConnectionManager,
    deviceRepository: DeviceRepository
) : ViewModel() {

    val isScanning: StateFlow<Boolean> = bleScanner.isScanning
    val isAdvertising: StateFlow<Boolean> = bleAdvertiser.isAdvertising
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleScanner.discoveredDevices
    val scanError: StateFlow<String?> = bleScanner.scanError
    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val connectedPeer: StateFlow<DeviceEntity?> = connectionManager.connectedPeer

    val savedDevices: StateFlow<List<DeviceEntity>> = deviceRepository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startScan() {
        connectionManager.startDiscoveryAndListening()
    }

    fun stopScan() {
        connectionManager.stopDiscovery()
    }

    fun connect(device: DiscoveredDevice) {
        connectionManager.connectToDevice(device)
    }

    fun connectSaved(device: DeviceEntity) {
        connectionManager.connectToSavedDevice(device)
    }
}
