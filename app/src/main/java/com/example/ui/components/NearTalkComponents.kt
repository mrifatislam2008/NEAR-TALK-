package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bluetooth.ConnectionState
import com.example.navigation.Screen
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueDark
import com.example.ui.theme.SoftIceBlue
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg

@Composable
fun NearTalkAvatar(
    name: String,
    imageUri: String?,
    size: Dp = 48.dp,
    isOnline: Boolean = false
) {
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "NT" }

    Box(modifier = Modifier.size(size)) {
        if (!imageUri.isNullOrBlank()) {
            AsyncImage(
                model = imageUri,
                contentDescription = "$name avatar",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(RoyalBlue, RoyalBlueDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(StatusSuccess)
            )
        }
    }
}

@Composable
fun ConnectionStatusBadge(
    state: ConnectionState,
    peerName: String? = null,
    modifier: Modifier = Modifier
) {
    val (bg, fg, label) = when (state) {
        ConnectionState.CONNECTED -> Triple(
            StatusSuccessBg,
            StatusSuccess,
            if (!peerName.isNullOrBlank()) "Connected • $peerName" else "Connected"
        )
        ConnectionState.SCANNING -> Triple(SoftIceBlue, RoyalBlue, "Scanning Nearby")
        ConnectionState.DEVICE_FOUND -> Triple(SoftIceBlue, RoyalBlue, "Devices Found")
        ConnectionState.REQUESTING -> Triple(StatusWarningBg, StatusWarning, "Connection Request")
        ConnectionState.CONNECTING,
        ConnectionState.AUTHENTICATING,
        ConnectionState.RECONNECTING -> Triple(SoftIceBlue, RoyalBlue, state.name.lowercase().replaceFirstChar { it.uppercase() })
        ConnectionState.ERROR -> Triple(StatusErrorBg, StatusError, "Connection Issue")
        ConnectionState.DISCONNECTED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Disconnected")
        ConnectionState.IDLE -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Offline Ready")
    }

    val animatedBg by animateColorAsState(bg, label = "badgeBg")
    Surface(
        modifier = modifier,
        color = animatedBg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BluetoothDisabledBanner(
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StatusErrorBg),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BluetoothDisabled,
                    contentDescription = "Bluetooth turned off",
                    tint = StatusError
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bluetooth is Turned Off",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Turn on Bluetooth to discover phones, message, and call offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onEnableClick,
                modifier = Modifier.testTag("enable_bluetooth_button"),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Turn On", maxLines = 1)
            }
        }
    }
}

@Composable
fun PermissionRationaleCard(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SoftIceBlue),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Permissions required",
                    tint = RoyalBlue
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Permissions Needed for Offline Talk",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "NEAR TALK uses Bluetooth to find & connect directly to nearby phones, Microphone only during active voice calls, and Notifications for incoming calls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_permissions_button"),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Grant Bluetooth & Audio Access")
            }
        }
    }
}

@Composable
fun RadarPulseRings(
    isActive: Boolean,
    color: Color = RoyalBlue,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radarPulse")
    val p1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, delayMillis = 650, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse2"
    )
    val p3 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, delayMillis = 1300, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse3"
    )

    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2f
        if (isActive) {
            listOf(p1, p2, p3).forEach { progress ->
                val alpha = (1f - progress) * 0.45f
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = maxRadius * progress,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        } else {
            drawCircle(
                color = color.copy(alpha = 0.16f),
                radius = maxRadius * 0.55f,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = color.copy(alpha = 0.10f),
                radius = maxRadius * 0.85f,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

private data class BottomNavTab(
    val route: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun NearTalkBottomBar(
    currentScreen: Screen?,
    onNavigate: (Screen) -> Unit
) {
    val tabs = listOf(
        BottomNavTab(Screen.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        BottomNavTab(Screen.Chats, "Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chats"),
        BottomNavTab(Screen.Nearby, "Nearby", Icons.Filled.Radar, Icons.Outlined.Radar, "nav_nearby"),
        BottomNavTab(Screen.Calls, "Calls", Icons.Filled.Call, Icons.Outlined.Call, "nav_calls"),
        BottomNavTab(Screen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        tabs.forEach { tab ->
            val selected = currentScreen == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(tab.route) },
                modifier = Modifier.testTag(tab.testTag),
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RoyalBlue,
                    selectedTextColor = RoyalBlue,
                    indicatorColor = SoftIceBlue,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
