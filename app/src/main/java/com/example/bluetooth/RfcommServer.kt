package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.example.core.BluetoothManager
import com.example.core.PermissionManager
import com.example.util.Constants
import com.example.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RfcommServer @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val permissionManager: PermissionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    var isListening: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun startListening(onClientConnected: suspend (BluetoothSocket) -> Unit, onError: (String) -> Unit = {}) {
        if (isListening) return
        if (!permissionManager.hasBluetoothConnectPermission()) {
            onError("Bluetooth connect permission is required to accept incoming connections.")
            return
        }
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            onError("Bluetooth is turned off.")
            return
        }

        acceptJob = scope.launch {
            try {
                val socket = adapter.listenUsingRfcommWithServiceRecord(
                    Constants.RFCOMM_SERVICE_NAME,
                    Constants.RFCOMM_SERVICE_UUID
                )
                serverSocket = socket
                isListening = true
                Logger.i(TAG, "RFCOMM ServerSocket listening for NEAR TALK connections")

                while (isActive && isListening) {
                    val accepted: BluetoothSocket = try {
                        socket.accept()
                    } catch (e: IOException) {
                        if (isListening) {
                            Logger.w(TAG, "RFCOMM accept interrupted: ${e.message}")
                        }
                        break
                    }
                    Logger.i(TAG, "RFCOMM ServerSocket accepted incoming socket")
                    onClientConnected(accepted)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "RFCOMM server socket failure", e)
                onError("Unable to open Bluetooth listening socket: ${e.message ?: "Unknown error"}")
            } finally {
                stopListening()
            }
        }
    }

    fun stopListening() {
        isListening = false
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        Logger.i(TAG, "RFCOMM ServerSocket stopped")
    }

    companion object {
        private const val TAG = "RfcommServer"
    }
}
