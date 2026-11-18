package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.navigation.AppNavigation
import com.example.ui.theme.NearTalkTheme
import com.example.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NearTalkTheme {
                AppNavigation(mainViewModel = mainViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshHardwareAndPermissions()
    }
}
