package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.ActiveCallInfo
import com.example.audio.CallQuality
import com.example.audio.CallState
import com.example.ui.components.NearTalkAvatar
import com.example.ui.components.RadarPulseRings
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueDark
import com.example.ui.theme.StatusError
import com.example.util.DateTimeUtils

@Composable
fun ActiveCallScreen(
    callState: CallState,
    callInfo: ActiveCallInfo,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    callQuality: CallQuality,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }

    val statusText = when (callState) {
        CallState.OUTGOING -> "Calling…"
        CallState.CONNECTING -> "Establishing Encrypted Audio…"
        CallState.ACTIVE -> DateTimeUtils.formatCallDuration(callInfo.durationSeconds)
        CallState.MUTED -> "${DateTimeUtils.formatCallDuration(callInfo.durationSeconds)} • Muted"
        CallState.ENDING -> "Ending Call…"
        CallState.FAILED -> callInfo.errorMessage ?: "Call Failed"
        else -> "Ended"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(RoyalBlue, RoyalBlueDark)
                )
            )
            .systemBarsPadding()
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Connection quality",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Bluetooth Voice • ${callQuality.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(176.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RadarPulseRings(
                        isActive = callState in setOf(CallState.OUTGOING, CallState.ACTIVE),
                        color = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                    NearTalkAvatar(
                        name = callInfo.remoteDisplayName.ifBlank { "NEAR TALK Peer" },
                        imageUri = null,
                        size = 104.dp
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = callInfo.remoteDisplayName.ifBlank { "NEAR TALK Peer" },
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = statusText,
                    style = if (callState in setOf(CallState.ACTIVE, CallState.MUTED)) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallControlTile(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "Unmute" else "Mute",
                        isActive = isMuted,
                        testTag = "call_mute_button",
                        onClick = onToggleMute
                    )
                    CallControlTile(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = "Speaker",
                        isActive = isSpeakerOn,
                        testTag = "call_speaker_button",
                        onClick = onToggleSpeaker
                    )
                }

                Spacer(Modifier.height(28.dp))

                Surface(
                    shape = CircleShape,
                    color = StatusError,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "End Call",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CallControlTile(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.18f),
            modifier = Modifier.size(64.dp)
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(testTag)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) RoyalBlue else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}
