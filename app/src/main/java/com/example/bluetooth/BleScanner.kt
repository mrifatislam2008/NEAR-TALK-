package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.example.core.BluetoothManager
import com.example.core.PermissionManager
import com.example.data.repository.DeviceRepository
import com.example.util.Constants
import com.example.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val permissionManager: PermissionManager,
    private val deviceRepository: DeviceRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val deviceMap = LinkedHashMap<String, DiscoveredDevice>()
    private var scanTimeoutJob: Job? = null
    private var activeScanner: BluetoothLeScanner? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Logger.e(TAG, "BLE scan failed with code=$errorCode")
            _isScanning.value = false
            _scanError.value = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Bluetooth scan is already running."
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Bluetooth scanner registration failed. Try toggling Bluetooth."
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE discovery is unsupported on this device."
                else -> "Unable to scan nearby devices (code $errorCode)."
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = Constants.BLE_SCAN_TIMEOUT_MS) {
        _scanError.value = null
        if (!permissionManager.hasBluetoothScanPermission()) {
            _scanError.value = "Bluetooth scan permission is required to find nearby users."
            return
        }
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            _scanError.value = "Turn on Bluetooth to scan for nearby NEAR TALK devices."
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _scanError.value = "BLE scanner is unavailable on this hardware."
            return
        }

        if (_isScanning.value) {
            stopScan()
        }

        deviceMap.clear()
        _discoveredDevices.value = emptyList()

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(Constants.BLE_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        runCatching {
            activeScanner = scanner
            scanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Logger.i(TAG, "BLE scan started for NEAR TALK UUID")
            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(timeoutMs)
                if (_isScanning.value) {
                    Logger.i(TAG, "BLE scan timeout reached (${timeoutMs}ms), stopping scan")
                    stopScan()
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Failed to start BLE scan", e)
            _isScanning.value = false
            _scanError.value = "Could not start Bluetooth discovery: ${e.message ?: "Unknown error"}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (!_isScanning.value) return
        if (permissionManager.hasBluetoothScanPermission()) {
            runCatching {
                activeScanner?.stopScan(scanCallback)
            }.onFailure {
                Logger.w(TAG, "Error stopping BLE scan: ${it.message}")
            }
        }
        _isScanning.value = false
        Logger.i(TAG, "BLE scan stopped")
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val btDevice = result.device ?: return
        val address = btDevice.address ?: return
        val record = result.scanRecord

        val serviceData = record?.getServiceData(ParcelUuid(Constants.BLE_SERVICE_UUID))
        val parsed = parseAdvertisementMetadata(serviceData, record?.deviceName, btDevice.name, address)

        scope.launch {
            val saved = deviceRepository.getDevice(parsed.id)
            if (saved?.isBlocked == true) {
                return@launch
            }
            val entry = DiscoveredDevice(
                deviceId = parsed.id,
                displayName = saved?.displayName?.ifBlank { parsed.name } ?: parsed.name,
                deviceNickname = parsed.nickname,
                bluetoothAddress = address,
                rssi = result.rssi,
                lastSeenAt = System.currentTimeMillis(),
                bluetoothDevice = btDevice,
                isTrusted = saved?.isTrusted == true,
                isBlocked = false
            )
            synchronized(deviceMap) {
                deviceMap[parsed.id] = entry
                _discoveredDevices.value = deviceMap.values.sortedByDescending { it.rssi }
            }
        }
    }

    private data class ParsedAdv(val id: String, val name: String, val nickname: String)

    private fun parseAdvertisementMetadata(
        serviceData: ByteArray?,
        recordName: String?,
        deviceName: String?,
        fallbackAddress: String
    ): ParsedAdv {
        // Service data layout: [1 byte version][8 bytes id prefix hash][remaining UTF-8 display name]
        var shortTag = fallbackAddress
        var advName = ""
        if (serviceData != null && serviceData.size >= 9) {
            val idBytes = serviceData.copyOfRange(1, 9)
            val hexId = idBytes.joinToString("") { "%02x".format(it) }
            shortTag = "nt-$hexId"
            if (serviceData.size > 9) {
                advName = String(serviceData, 9, serviceData.size - 9, Charsets.UTF_8).trim()
            }
        }
        val cleanName = advName
            .ifBlank { recordName.orEmpty().trim() }
            .ifBlank { deviceName.orEmpty().trim() }
            .ifBlank { "NEAR TALK User" }
        val nickname = recordName?.trim().takeIf { !it.isNullOrBlank() } ?: cleanName
        return ParsedAdv(id = shortTag, name = cleanName, nickname = nickname)
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}
