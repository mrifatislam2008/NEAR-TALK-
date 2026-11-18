package com.example

import android.app.Application
import com.example.core.DeviceIdentityManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NearTalkApplication : Application() {

    @Inject
    lateinit var deviceIdentityManager: DeviceIdentityManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            deviceIdentityManager.ensureDeviceId()
        }
    }
}
