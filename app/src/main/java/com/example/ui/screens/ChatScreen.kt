package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messaging.Message
import com.example.messaging.MessageStatus
import com.example.ui.components.NearTalkAvatar
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SoftIceBlue
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerId: String,
    initialPeerName: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val peerDevice by viewModel.peerDevice.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val isPeerConnected by viewModel.isPeerConnected.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(peerId) {
        viewModel.openChat(peerId)
    }
    DisposableEffect(peerId) {
        onDispose { viewModel.closeChat() }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    BackHandler { onBack() }

    val displayName = peerDevice?.displayName?.ifBlank { initialPeerName } ?: initialPeerName

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NearTalkAvatar(
                            name = displayName,
                            imageUri = peerDevice?.profileImage,
                            size = 40.dp,
                            isOnline = isPeerConnected
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isPeerConnected) "Bluetooth Connected" else "Offline • Messages queue as Pending",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPeerConnected) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.startVoiceCall() },
                        enabled = isPeerConnected,
                        modifier = Modifier.testTag("chat_voice_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Start voice call",
                            tint = if (isPeerConnected) RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (peerDevice?.isTrusted == true) "Remove Trust" else "Mark as Trusted") },
                                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                                onClick = {
                                    viewModel.toggleTrusted()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block Device", color = StatusError) },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = StatusError) },
                                onClick = {
                                    viewModel.blockPeer()
                                    showMenu = false
                                    onBack()
                                }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (!isPeerConnected) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StatusWarningBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Peer offline. Sent messages stay Pending until reconnected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.reconnectPeer() }) {
                            Text("Reconnect", color = RoyalBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = composerText,
                        onValueChange = viewModel::onComposerChanged,
                        placeholder = { Text("Write an offline message…") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedBorderColor = RoyalBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = { viewModel.sendMessage() },
                        enabled = composerText.isNotBlank(),
                        shape = CircleShape,
                        color = if (composerText.isNotBlank()) RoyalBlue else SoftIceBlue,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (composerText.isNotBlank()) Color.White else RoyalBlue.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isMine = !message.isIncoming
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isMine) RoyalBlue else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            tonalElevation = if (isMine) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = DateTimeUtils.formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMine) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMine) {
                        Spacer(Modifier.width(6.dp))
                        val (icon, label, tint) = when (message.status) {
                            MessageStatus.PENDING -> Triple(Icons.Default.Schedule, "Pending", Color.White.copy(alpha = 0.8f))
                            MessageStatus.SENT -> Triple(Icons.Default.Done, "Sent", Color.White.copy(alpha = 0.85f))
                            MessageStatus.DELIVERED -> Triple(Icons.Default.DoneAll, "Delivered", Color.White)
                            MessageStatus.READ -> Triple(Icons.Default.DoneAll, "Read", Color(0xFF98F5D4))
                            MessageStatus.FAILED -> Triple(Icons.Default.ErrorOutline, "Failed", Color(0xFFFFD5D5))
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
