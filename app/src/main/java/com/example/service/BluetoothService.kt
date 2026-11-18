package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.core.ConnectionManager
import com.example.util.Constants
import com.example.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothService : Service() {

    @Inject
    lateinit var connectionManager: ConnectionManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            connectionManager.stopDiscovery()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(
            peerName = connectionManager.connectedPeer.value?.displayName
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    Constants.NOTIFICATION_ID_BLUETOOTH,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(Constants.NOTIFICATION_ID_BLUETOOTH, notification)
            }
            connectionManager.ensureServerListening()
        }.onFailure { e ->
            Logger.w(TAG, "Unable to enter foreground for BluetoothService: ${e.message}")
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(peerName: String?): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BluetoothService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (!peerName.isNullOrBlank()) {
            "Connected offline with $peerName"
        } else {
            "Listening for nearby NEAR TALK peers"
        }

        return NotificationCompat.Builder(this, Constants.CHANNEL_BLUETOOTH_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("NEAR TALK Offline Link")
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Pause Link", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            Constants.CHANNEL_BLUETOOTH_ID,
            getString(R.string.bluetooth_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps direct Bluetooth messaging link active"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "BluetoothService"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_BLUETOOTH"
    }
}
