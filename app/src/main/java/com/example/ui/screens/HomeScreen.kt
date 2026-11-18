package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bluetooth.ConnectionState
import com.example.core.PermissionStatus
import com.example.core.UserIdentity
import com.example.data.local.entities.DeviceEntity
import com.example.ui.components.BluetoothDisabledBanner
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.NearTalkAvatar
import com.example.ui.components.PermissionRationaleCard
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueDark
import com.example.ui.theme.SoftIceBlue
import com.example.ui.viewmodel.ConversationPreviewUi
import com.example.util.DateTimeUtils

@Composable
fun HomeScreen(
    identity: UserIdentity?,
    connectionState: ConnectionState,
    connectedPeer: DeviceEntity?,
    isBluetoothEnabled: Boolean,
    permissionStatus: PermissionStatus,
    recentConversations: List<ConversationPreviewUi>,
    onEnableBluetooth: () -> Unit,
    onGrantPermissions: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenChat: (peerId: String, peerName: String) -> Unit,
    onStartVoiceCall: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NEAR TALK",
                        style = MaterialTheme.typography.headlineMedium,
                        color = RoyalBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hi, ${identity?.displayName?.ifBlank { "there" } ?: "there"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConnectionStatusBadge(
                    state = connectionState,
                    peerName = connectedPeer?.displayName
                )
            }
        }

        if (!permissionStatus.allBluetoothGranted || !permissionStatus.recordAudioGranted) {
            item {
                PermissionRationaleCard(onGrantClick = onGrantPermissions)
            }
        }

        if (!isBluetoothEnabled) {
            item {
                BluetoothDisabledBanner(onEnableClick = onEnableBluetooth)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(RoyalBlue, RoyalBlueDark)
                            )
                        )
                        .padding(20.dp)
                ) {
                    if (connectionState == ConnectionState.CONNECTED && connectedPeer != null) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NearTalkAvatar(
                                    name = connectedPeer.displayName,
                                    imageUri = connectedPeer.profileImage,
                                    size = 54.dp,
                                    isOnline = true
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Encrypted Link Active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = connectedPeer.displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onOpenChat(connectedPeer.id, connectedPeer.displayName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("home_open_connected_chat"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = RoyalBlue
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Message", maxLines = 1)
                                }
                                Button(
                                    onClick = onStartVoiceCall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("home_call_connected_peer"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.22f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Voice Call", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onDisconnect,
                                    modifier = Modifier.testTag("home_disconnect_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = "Disconnect", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Ready for Offline Talk",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Discover nearby NEAR TALK phones over Bluetooth.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onOpenNearby,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("home_nearby_shortcut"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = RoyalBlue
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Radar, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Find Nearby Devices", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Conversations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalButton(
                    onClick = onOpenNearby,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SoftIceBlue,
                        contentColor = RoyalBlue
                    )
                ) {
                    Text("Scan Nearby")
                }
            }
        }

        if (recentConversations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No offline conversations yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tap Find Nearby Devices to pair directly with another phone running NEAR TALK.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentConversations.take(5), key = { it.peerId }) { conv ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(conv.peerId, conv.peerName) }
                        .testTag("home_conversation_${conv.peerId}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NearTalkAvatar(
                            name = conv.peerName,
                            imageUri = conv.peerAvatarUri,
                            size = 48.dp,
                            isOnline = conv.isConnected
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = conv.peerName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = DateTimeUtils.formatConversationTime(conv.lastTimestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = conv.lastMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (conv.unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = RoyalBlue
                            ) {
                                Text(
                                    text = conv.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
