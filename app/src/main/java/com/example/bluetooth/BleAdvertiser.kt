package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import com.example.core.BluetoothManager
import com.example.core.DeviceIdentityManager
import com.example.core.PermissionManager
import com.example.util.Constants
import com.example.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleAdvertiser @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val permissionManager: PermissionManager,
    private val identityManager: DeviceIdentityManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _advertiseError = MutableStateFlow<String?>(null)
    val advertiseError: StateFlow<String?> = _advertiseError.asStateFlow()

    private var activeAdvertiser: BluetoothLeAdvertiser? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
            _advertiseError.value = null
            Logger.i(TAG, "BLE advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            _advertiseError.value = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Device name too long for BLE beacon."
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "No BLE advertising slots available on device."
                ADVERTISE_FAILED_ALREADY_STARTED -> null
                else -> "Unable to advertise presence (code $errorCode)."
            }
            Logger.w(TAG, "BLE advertising failed: code=$errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        if (!permissionManager.hasBluetoothAdvertisePermission()) {
            _advertiseError.value = "Bluetooth advertise permission is needed for nearby discovery."
            return
        }
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            _advertiseError.value = "Enable Bluetooth to become discoverable to nearby users."
            return
        }
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            _advertiseError.value = "BLE advertising is not supported on this hardware."
            return
        }

        scope.launch {
            val identity = identityManager.getSnapshot()
            val servicePayload = buildServicePayload(
                deviceId = identity.deviceId,
                displayName = identity.displayName.ifBlank { identity.deviceNickname }
            )

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            // Primary adv packet carries ONLY the 128-bit NEAR TALK service UUID (fits 31-byte limit cleanly)
            val advData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(Constants.BLE_SERVICE_UUID))
                .build()

            // Scan response carries compact non-sensitive service data (version + 8-byte ID hash + truncated name)
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData(ParcelUuid(Constants.BLE_SERVICE_UUID), servicePayload)
                .build()

            runCatching {
                if (_isAdvertising.value) {
                    advertiser.stopAdvertising(advertiseCallback)
                }
                activeAdvertiser = advertiser
                advertiser.startAdvertising(settings, advData, scanResponse, advertiseCallback)
            }.onFailure { e ->
                Logger.e(TAG, "Error starting BLE advertiser", e)
                _isAdvertising.value = false
                _advertiseError.value = "Failed to advertise nearby: ${e.message ?: "Unknown error"}"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!_isAdvertising.value) return
        if (permissionManager.hasBluetoothAdvertisePermission()) {
            runCatching {
                activeAdvertiser?.stopAdvertising(advertiseCallback)
            }.onFailure {
                Logger.w(TAG, "Error stopping BLE advertiser: ${it.message}")
            }
        }
        _isAdvertising.value = false
        Logger.i(TAG, "BLE advertising stopped")
    }

    private fun buildServicePayload(deviceId: String, displayName: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray(Charsets.UTF_8))
        val idTag = digest.copyOfRange(0, 8)
        // Keep scan response well within 31-byte BLE limit (18 bytes UUID header + 9 bytes prefix -> max 10 bytes name)
        val rawNameBytes = displayName.toByteArray(Charsets.UTF_8)
        val nameBytes = if (rawNameBytes.size > 10) rawNameBytes.copyOfRange(0, 10) else rawNameBytes
        return byteArrayOf(Constants.PROTOCOL_VERSION) + idTag + nameBytes
    }

    companion object {
        private const val TAG = "BleAdvertiser"
    }
}
