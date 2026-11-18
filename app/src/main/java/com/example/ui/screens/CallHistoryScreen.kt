package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SoftIceBlue
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.CallFilter
import com.example.ui.viewmodel.CallHistoryViewModel
import com.example.util.DateTimeUtils

@Composable
fun CallHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: CallHistoryViewModel = hiltViewModel()
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val connectedPeerId by viewModel.connectedPeerId.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Call History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (calls.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearHistory() },
                    modifier = Modifier.testTag("clear_call_history_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CallFilter.entries) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { viewModel.selectFilter(filter) },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SoftIceBlue,
                        selectedLabelColor = RoyalBlue
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (calls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SoftIceBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = RoyalBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "No Call Records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Placed, received, and missed Bluetooth voice calls appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(calls, key = { it.id }) { call ->
                    val isMissedOrRejected = call.callStatus == "MISSED" || call.callStatus == "REJECTED"
                    val isOutgoing = call.callType == "OUTGOING"
                    val (icon, tint) = when {
                        isMissedOrRejected -> Icons.AutoMirrored.Filled.CallMissed to StatusError
                        isOutgoing -> Icons.AutoMirrored.Filled.CallMade to RoyalBlue
                        else -> Icons.AutoMirrored.Filled.CallReceived to StatusSuccess
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = icon, contentDescription = call.callType, tint = tint)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = call.remoteDisplayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = buildString {
                                        append(if (isOutgoing) "Outgoing" else "Incoming")
                                        append(" • ")
                                        if (call.duration > 0) {
                                            append(DateTimeUtils.formatCallDuration(call.duration))
                                        } else {
                                            append(call.callStatus.lowercase().replaceFirstChar { it.uppercase() })
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isMissedOrRejected) StatusError else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = DateTimeUtils.formatConversationTime(call.startTime),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (connectedPeerId == call.remoteDeviceId) {
                                IconButton(onClick = { viewModel.callIfConnected(call.remoteDeviceId) }) {
                                    Icon(Icons.Default.Call, contentDescription = "Redial", tint = RoyalBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
