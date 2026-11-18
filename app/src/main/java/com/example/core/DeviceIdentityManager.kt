package com.example.core

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nearTalkDataStore: DataStore<Preferences> by preferencesDataStore(name = "neartalk_identity")

data class UserIdentity(
    val deviceId: String = "",
    val displayName: String = "",
    val deviceNickname: String = Build.MODEL ?: "Android Phone",
    val profileImageUri: String? = null,
    val isProfileConfigured: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val autoAcceptTrusted: Boolean = false,
    val speakerByDefault: Boolean = false
)

@Singleton
class DeviceIdentityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val DEVICE_NICKNAME = stringPreferencesKey("device_nickname")
        val PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
        val PROFILE_CONFIGURED = booleanPreferencesKey("profile_configured")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_ACCEPT_TRUSTED = booleanPreferencesKey("auto_accept_trusted")
        val SPEAKER_DEFAULT = booleanPreferencesKey("speaker_default")
    }

    val identityFlow: Flow<UserIdentity> = context.nearTalkDataStore.data.map { prefs ->
        val storedId = prefs[Keys.DEVICE_ID] ?: ""
        UserIdentity(
            deviceId = storedId,
            displayName = prefs[Keys.DISPLAY_NAME] ?: "",
            deviceNickname = prefs[Keys.DEVICE_NICKNAME] ?: (Build.MODEL ?: "Android Phone"),
            profileImageUri = prefs[Keys.PROFILE_IMAGE_URI],
            isProfileConfigured = prefs[Keys.PROFILE_CONFIGURED] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            autoAcceptTrusted = prefs[Keys.AUTO_ACCEPT_TRUSTED] ?: false,
            speakerByDefault = prefs[Keys.SPEAKER_DEFAULT] ?: false
        )
    }

    suspend fun ensureDeviceId(): String {
        val current = context.nearTalkDataStore.data.first()[Keys.DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val generated = UUID.randomUUID().toString()
        context.nearTalkDataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = generated
            }
        }
        return context.nearTalkDataStore.data.first()[Keys.DEVICE_ID] ?: generated
    }

    suspend fun getSnapshot(): UserIdentity {
        ensureDeviceId()
        return identityFlow.first()
    }

    suspend fun saveProfile(
        displayName: String,
        deviceNickname: String,
        profileImageUri: String?
    ) {
        ensureDeviceId()
        context.nearTalkDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = displayName.trim()
            prefs[Keys.DEVICE_NICKNAME] = deviceNickname.trim().ifBlank { Build.MODEL ?: "Android Phone" }
            if (profileImageUri.isNullOrBlank()) {
                prefs.remove(Keys.PROFILE_IMAGE_URI)
            } else {
                prefs[Keys.PROFILE_IMAGE_URI] = profileImageUri
            }
            prefs[Keys.PROFILE_CONFIGURED] = displayName.trim().isNotEmpty()
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.nearTalkDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun updateAutoAcceptTrusted(enabled: Boolean) {
        context.nearTalkDataStore.edit { prefs ->
            prefs[Keys.AUTO_ACCEPT_TRUSTED] = enabled
        }
    }

    suspend fun updateSpeakerDefault(enabled: Boolean) {
        context.nearTalkDataStore.edit { prefs ->
            prefs[Keys.SPEAKER_DEFAULT] = enabled
        }
    }
}
