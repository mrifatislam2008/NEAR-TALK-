package com.example.navigation

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.audio.CallState
import com.example.ui.components.NearTalkBottomBar
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ConnectionRequestScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.screens.NearbyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.viewmodel.ChatListViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.NearbyViewModel

@Composable
fun AppNavigation(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val identity by mainViewModel.identity.collectAsStateWithLifecycle()
    val connectionState by mainViewModel.connectionState.collectAsStateWithLifecycle()
    val connectedPeer by mainViewModel.connectedPeer.collectAsStateWithLifecycle()
    val pendingRequest by mainViewModel.pendingRequest.collectAsStateWithLifecycle()
    val connectionError by mainViewModel.connectionError.collectAsStateWithLifecycle()
    val callState by mainViewModel.callState.collectAsStateWithLifecycle()
    val activeCallInfo by mainViewModel.activeCallInfo.collectAsStateWithLifecycle()
    val isMuted by mainViewModel.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by mainViewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val callQuality by mainViewModel.callQuality.collectAsStateWithLifecycle()
    val permissionStatus by mainViewModel.permissionStatus.collectAsStateWithLifecycle()
    val isBluetoothEnabled by mainViewModel.isBluetoothEnabled.collectAsStateWithLifecycle()

    val chatListViewModel: ChatListViewModel = hiltViewModel()
    val recentConversations by chatListViewModel.conversations.collectAsStateWithLifecycle()

    val nearbyViewModel: NearbyViewModel = hiltViewModel()
    val isScanning by nearbyViewModel.isScanning.collectAsStateWithLifecycle()
    val isAdvertising by nearbyViewModel.isAdvertising.collectAsStateWithLifecycle()
    val discoveredDevices by nearbyViewModel.discoveredDevices.collectAsStateWithLifecycle()
    val savedDevices by nearbyViewModel.savedDevices.collectAsStateWithLifecycle()
    val scanError by nearbyViewModel.scanError.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mainViewModel.refreshHardwareAndPermissions()
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        mainViewModel.refreshHardwareAndPermissions()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val activeBottomTab: Screen? = when {
        currentDest?.hasRoute<Screen.Home>() == true -> Screen.Home
        currentDest?.hasRoute<Screen.Chats>() == true -> Screen.Chats
        currentDest?.hasRoute<Screen.Nearby>() == true -> Screen.Nearby
        currentDest?.hasRoute<Screen.Calls>() == true -> Screen.Calls
        currentDest?.hasRoute<Screen.Settings>() == true -> Screen.Settings
        else -> null
    }

    // Automatically surface ConnectionRequest screen when a remote peer asks to pair
    LaunchedEffect(pendingRequest) {
        if (pendingRequest != null && currentDest?.hasRoute<Screen.ConnectionRequest>() != true) {
            navController.navigate(Screen.ConnectionRequest) { launchSingleTop = true }
        } else if (pendingRequest == null && currentDest?.hasRoute<Screen.ConnectionRequest>() == true) {
            navController.popBackStack()
        }
    }

    // Automatically route to IncomingCall / ActiveCall screens synchronized with CallManager
    LaunchedEffect(callState) {
        when (callState) {
            CallState.INCOMING -> {
                if (currentDest?.hasRoute<Screen.IncomingCall>() != true) {
                    navController.navigate(Screen.IncomingCall) { launchSingleTop = true }
                }
            }
            CallState.OUTGOING,
            CallState.CONNECTING,
            CallState.ACTIVE,
            CallState.MUTED -> {
                if (currentDest?.hasRoute<Screen.ActiveCall>() != true) {
                    navController.navigate(Screen.ActiveCall) {
                        popUpTo(Screen.IncomingCall) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            CallState.ENDED, CallState.FAILED -> {
                val err = activeCallInfo.errorMessage
                if (currentDest?.hasRoute<Screen.IncomingCall>() == true ||
                    currentDest?.hasRoute<Screen.ActiveCall>() == true
                ) {
                    navController.popBackStack()
                }
                if (!err.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(err)
                }
                mainViewModel.resetCallState()
            }
            else -> Unit
        }
    }

    // Surface understandable connection errors via Snackbar
    LaunchedEffect(connectionError) {
        val msg = connectionError
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearConnectionError()
        }
    }

    val showBottomBar = activeBottomTab != null

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NearTalkBottomBar(
                    currentScreen = activeBottomTab,
                    onNavigate = { target ->
                        navController.navigate(target) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash
            ) {
                composable<Screen.Splash> {
                    SplashScreen(
                        identity = identity,
                        onFinished = { configured ->
                            val target: Screen = if (configured) Screen.Home else Screen.Welcome
                            navController.navigate(target) {
                                popUpTo(Screen.Splash) { inclusive = true }
                            }
                        }
                    )
                }

                composable<Screen.Welcome> {
                    WelcomeScreen(
                        onGetStarted = {
                            navController.navigate(Screen.ProfileSetup(isOnboarding = true)) {
                                popUpTo(Screen.Welcome) { inclusive = true }
                            }
                        }
                    )
                }

                composable<Screen.ProfileSetup> { backStackEntry ->
                    val args = backStackEntry.toRoute<Screen.ProfileSetup>()
                    ProfileScreen(
                        currentIdentity = identity,
                        isOnboarding = args.isOnboarding,
                        onSave = { name, nickname, uri ->
                            mainViewModel.saveProfile(name, nickname, uri) {
                                if (args.isOnboarding) {
                                    permissionLauncher.launch(mainViewModel.requiredAllPermissions())
                                    navController.navigate(Screen.Home) {
                                        popUpTo(Screen.ProfileSetup(isOnboarding = true)) { inclusive = true }
                                    }
                                } else {
                                    navController.popBackStack()
                                }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<Screen.Home> {
                    HomeScreen(
                        identity = identity,
                        connectionState = connectionState,
                        connectedPeer = connectedPeer,
                        isBluetoothEnabled = isBluetoothEnabled,
                        permissionStatus = permissionStatus,
                        recentConversations = recentConversations,
                        onEnableBluetooth = {
                            enableBluetoothLauncher.launch(mainViewModel.buildEnableBluetoothIntent())
                        },
                        onGrantPermissions = {
                            permissionLauncher.launch(mainViewModel.requiredAllPermissions())
                        },
                        onOpenNearby = {
                            navController.navigate(Screen.Nearby) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenChat = { peerId, peerName ->
                            navController.navigate(Screen.ChatDetail(peerId, peerName))
                        },
                        onStartVoiceCall = { mainViewModel.startOutgoingCall() },
                        onDisconnect = { mainViewModel.disconnectPeer() }
                    )
                }

                composable<Screen.Chats> {
                    ChatListScreen(
                        conversations = recentConversations,
                        onOpenChat = { peerId, peerName ->
                            navController.navigate(Screen.ChatDetail(peerId, peerName))
                        },
                        onOpenNearby = {
                            navController.navigate(Screen.Nearby) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable<Screen.Nearby> {
                    NearbyScreen(
                        isScanning = isScanning,
                        isAdvertising = isAdvertising,
                        discoveredDevices = discoveredDevices,
                        savedDevices = savedDevices,
                        scanError = scanError,
                        connectionState = connectionState,
                        connectedPeer = connectedPeer,
                        isBluetoothEnabled = isBluetoothEnabled,
                        permissionStatus = permissionStatus,
                        onStartScan = {
                            if (!permissionStatus.allBluetoothGranted) {
                                permissionLauncher.launch(mainViewModel.requiredBluetoothPermissions())
                            } else {
                                mainViewModel.startBluetoothLinkService()
                                nearbyViewModel.startScan()
                            }
                        },
                        onStopScan = { nearbyViewModel.stopScan() },
                        onConnectDevice = { device -> nearbyViewModel.connect(device) },
                        onConnectSaved = { device -> nearbyViewModel.connectSaved(device) },
                        onEnableBluetooth = {
                            enableBluetoothLauncher.launch(mainViewModel.buildEnableBluetoothIntent())
                        },
                        onGrantPermissions = {
                            permissionLauncher.launch(mainViewModel.requiredBluetoothPermissions())
                        }
                    )
                }

                composable<Screen.Calls> {
                    CallHistoryScreen()
                }

                composable<Screen.Settings> {
                    SettingsScreen(
                        onEditProfile = {
                            navController.navigate(Screen.ProfileSetup(isOnboarding = false))
                        },
                        onMakeDiscoverable = {
                            enableBluetoothLauncher.launch(mainViewModel.buildDiscoverableIntent())
                        },
                        onOpenBluetoothSettings = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        }
                    )
                }

                composable<Screen.ConnectionRequest> {
                    ConnectionRequestScreen(
                        request = pendingRequest,
                        onAccept = { mainViewModel.acceptConnectionRequest() },
                        onReject = { mainViewModel.rejectConnectionRequest() }
                    )
                }

                composable<Screen.ChatDetail> { backStackEntry ->
                    val args = backStackEntry.toRoute<Screen.ChatDetail>()
                    ChatScreen(
                        peerId = args.peerId,
                        initialPeerName = args.peerName,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<Screen.IncomingCall> {
                    IncomingCallScreen(
                        callInfo = activeCallInfo,
                        onAccept = { mainViewModel.acceptIncomingCall() },
                        onReject = { mainViewModel.rejectIncomingCall() }
                    )
                }

                composable<Screen.ActiveCall> {
                    ActiveCallScreen(
                        callState = callState,
                        callInfo = activeCallInfo,
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn,
                        callQuality = callQuality,
                        onToggleMute = { mainViewModel.toggleMute() },
                        onToggleSpeaker = { mainViewModel.toggleSpeaker() },
                        onEndCall = { mainViewModel.endCall() }
                    )
                }
            }
        }
    }
}
