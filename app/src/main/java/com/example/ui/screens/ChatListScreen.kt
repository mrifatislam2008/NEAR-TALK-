package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.messaging.MessageStatus
import com.example.ui.components.NearTalkAvatar
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SoftIceBlue
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.ConversationPreviewUi
import com.example.util.DateTimeUtils

@Composable
fun ChatListScreen(
    conversations: List<ConversationPreviewUi>,
    onOpenChat: (peerId: String, peerName: String) -> Unit,
    onOpenNearby: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Messages",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_chats),
                    contentDescription = "No conversations illustration",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.no_chats_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_chats_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onOpenNearby,
                    modifier = Modifier.testTag("chatlist_scan_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Find Nearby Devices")
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(conversations, key = { it.peerId }) { conv ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(conv.peerId, conv.peerName) }
                            .testTag("chat_row_${conv.peerId}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NearTalkAvatar(
                                name = conv.peerName,
                                imageUri = conv.peerAvatarUri,
                                size = 52.dp,
                                isOnline = conv.isConnected
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = conv.peerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = DateTimeUtils.formatConversationTime(conv.lastTimestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (conv.unreadCount > 0) RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!conv.lastIsIncoming) {
                                        Icon(
                                            imageVector = when (conv.lastStatus) {
                                                MessageStatus.PENDING, MessageStatus.FAILED -> Icons.Default.Schedule
                                                MessageStatus.SENT -> Icons.Default.Done
                                                MessageStatus.DELIVERED, MessageStatus.READ -> Icons.Default.DoneAll
                                            },
                                            contentDescription = conv.lastStatus.value,
                                            tint = if (conv.lastStatus == MessageStatus.READ) RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = conv.lastMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (conv.unreadCount > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(shape = CircleShape, color = RoyalBlue) {
                                            Text(
                                                text = conv.unreadCount.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (conv.isConnected) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(shape = RoundedCornerShape(999.dp), color = SoftIceBlue) {
                                            Text(
                                                text = "Live",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusSuccess,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
