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
import com.example.audio.CallManager
import com.example.util.Constants
import com.example.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ActiveCallService : Service() {

    @Inject
    lateinit var callManager: CallManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_MUTE -> {
                callManager.toggleMute()
                updateNotification(intent.getStringExtra(EXTRA_PEER_NAME) ?: callManager.activeCallInfo.value.remoteDisplayName)
                return START_NOT_STICKY
            }
            ACTION_END_CALL -> {
                callManager.endCall("NOTIFICATION_HANGUP")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME)
            ?: callManager.activeCallInfo.value.remoteDisplayName
            .ifBlank { "NEAR TALK Peer" }

        val notification = buildCallNotification(peerName)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    Constants.NOTIFICATION_ID_ACTIVE_CALL,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceCompat.startForeground(
                    this,
                    Constants.NOTIFICATION_ID_ACTIVE_CALL,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(Constants.NOTIFICATION_ID_ACTIVE_CALL, notification)
            }
        }.onFailure { e ->
            Logger.e(TAG, "Failed to start ActiveCallService foreground", e)
            callManager.endCall("SERVICE_START_FAILED")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Safety guarantee: never leave microphone active if service terminates unexpectedly
        if (callManager.callState.value !in setOf(
                com.example.audio.CallState.IDLE,
                com.example.audio.CallState.ENDED
            )
        ) {
            callManager.endCall("SERVICE_DESTROYED")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(peerName: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(Constants.NOTIFICATION_ID_ACTIVE_CALL, buildCallNotification(peerName))
    }

    private fun buildCallNotification(peerName: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val muteIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ActiveCallService::class.java).apply {
                action = ACTION_TOGGLE_MUTE
                putExtra(EXTRA_PEER_NAME, peerName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ActiveCallService::class.java).apply {
                action = ACTION_END_CALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isMuted = callManager.isMuted.value
        return NotificationCompat.Builder(this, Constants.CHANNEL_CALLS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Voice Call • $peerName")
            .setContentText(if (isMuted) "Encrypted Bluetooth call (Muted)" else "Encrypted Bluetooth audio active")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(0, if (isMuted) "Unmute" else "Mute", muteIntent)
            .addAction(0, "End Call", endIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            Constants.CHANNEL_CALLS_ID,
            getString(R.string.call_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Persistent notification for active encrypted Bluetooth voice calls"
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ActiveCallService"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val ACTION_TOGGLE_MUTE = "com.example.service.ACTION_TOGGLE_MUTE"
        const val ACTION_END_CALL = "com.example.service.ACTION_END_CALL"
    }
}
