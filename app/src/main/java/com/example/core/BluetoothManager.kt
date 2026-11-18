package com.example.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.example.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager
) {
    private val systemBtManager: AndroidBluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager

    val adapter: BluetoothAdapter?
        get() = systemBtManager?.adapter

    private val _isBluetoothSupported = MutableStateFlow(checkBluetoothSupport())
    val isBluetoothSupported: StateFlow<Boolean> = _isBluetoothSupported.asStateFlow()

    private val _isBleSupported = MutableStateFlow(checkBleSupport())
    val isBleSupported: StateFlow<Boolean> = _isBleSupported.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private var receiverRegistered = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val enabled = state == BluetoothAdapter.STATE_ON
                _isBluetoothEnabled.value = enabled
                Logger.i(TAG, "Bluetooth adapter state changed: state=$state enabled=$enabled")
            }
        }
    }

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        runCatching {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(stateReceiver, filter)
            receiverRegistered = true
        }.onFailure {
            Logger.w(TAG, "Unable to register Bluetooth state receiver: ${it.message}")
        }
    }

    fun refreshState() {
        _isBluetoothSupported.value = checkBluetoothSupport()
        _isBleSupported.value = checkBleSupport()
        _isBluetoothEnabled.value = adapter?.isEnabled == true
        permissionManager.refresh()
    }

    private fun checkBluetoothSupport(): Boolean {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        return hasFeature && adapter != null
    }

    private fun checkBleSupport(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    @SuppressLint("MissingPermission")
    fun cancelClassicDiscovery() {
        if (!permissionManager.hasBluetoothScanPermission()) return
        runCatching {
            if (adapter?.isDiscovering == true) {
                adapter?.cancelDiscovery()
            }
        }
    }

    fun buildEnableBluetoothIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    fun buildDiscoverableIntent(durationSeconds: Int = 120): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
        }

    companion object {
        private const val TAG = "BluetoothManager"
    }
}
