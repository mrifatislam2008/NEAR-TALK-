package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.example.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android AudioManager focus, MODE_IN_COMMUNICATION routing, and earpiece/speakerphone switching.
 */
@Singleton
class AudioSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var focusRequest: AudioFocusRequest? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    fun acquireCallAudioSession(speakerDefault: Boolean = false) {
        previousAudioMode = audioManager.mode
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { /* handled gracefully */ }
            .build()

        focusRequest = request
        audioManager.requestAudioFocus(request)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphone(speakerDefault)
        Logger.i(TAG, "Acquired MODE_IN_COMMUNICATION audio session (speaker=$speakerDefault)")
    }

    fun setSpeakerphone(enabled: Boolean) {
        _isSpeakerOn.value = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = if (enabled) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val devices = audioManager.availableCommunicationDevices
            val targetDevice = devices.firstOrNull { it.type == targetType }
            if (targetDevice != null) {
                audioManager.setCommunicationDevice(targetDevice)
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enabled
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
    }

    fun releaseCallAudioSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
        @Suppress("DEPRECATION")
        runCatching { audioManager.isSpeakerphoneOn = false }
        focusRequest?.let { req ->
            runCatching { audioManager.abandonAudioFocusRequest(req) }
        }
        focusRequest = null
        runCatching { audioManager.mode = previousAudioMode }
        _isSpeakerOn.value = false
        Logger.i(TAG, "Released audio session and restored previous AudioManager mode")
    }

    companion object {
        private const val TAG = "AudioSessionManager"
    }
}
