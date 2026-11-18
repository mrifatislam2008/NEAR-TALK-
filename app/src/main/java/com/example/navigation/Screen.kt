package com.example.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Splash : Screen

    @Serializable
    data object Welcome : Screen

    @Serializable
    data class ProfileSetup(val isOnboarding: Boolean = true) : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data object Chats : Screen

    @Serializable
    data object Nearby : Screen

    @Serializable
    data object Calls : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object ConnectionRequest : Screen

    @Serializable
    data class ChatDetail(val peerId: String, val peerName: String) : Screen

    @Serializable
    data object IncomingCall : Screen

    @Serializable
    data object ActiveCall : Screen
}
