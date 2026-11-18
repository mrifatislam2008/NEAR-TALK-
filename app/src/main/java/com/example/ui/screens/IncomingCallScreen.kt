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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.ActiveCallInfo
import com.example.ui.components.NearTalkAvatar
import com.example.ui.components.RadarPulseRings
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueDark
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess

@Composable
fun IncomingCallScreen(
    callInfo: ActiveCallInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    BackHandler { onReject() }

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = stringResource(R.string.incoming_call_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RadarPulseRings(
                        isActive = true,
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Direct Peer-to-Peer Voice",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = StatusError,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(72.dp)
                    ) {
                        IconButton(
                            onClick = onReject,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("reject_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Decline", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = StatusSuccess,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(72.dp)
                    ) {
                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("accept_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Accept call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Accept", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
