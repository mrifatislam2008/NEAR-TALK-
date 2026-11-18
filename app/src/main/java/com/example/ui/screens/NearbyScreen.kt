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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.DiscoveredDevice
import com.example.core.PermissionStatus
import com.example.data.local.entities.DeviceEntity
import com.example.ui.components.BluetoothDisabledBanner
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.NearTalkAvatar
import com.example.ui.components.PermissionRationaleCard
import com.example.ui.components.RadarPulseRings
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SoftIceBlue
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusSuccess

@Composable
fun NearbyScreen(
    isScanning: Boolean,
    isAdvertising: Boolean,
    discoveredDevices: List<DiscoveredDevice>,
    savedDevices: List<DeviceEntity>,
    scanError: String?,
    connectionState: ConnectionState,
    connectedPeer: DeviceEntity?,
    isBluetoothEnabled: Boolean,
    permissionStatus: PermissionStatus,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (DiscoveredDevice) -> Unit,
    onConnectSaved: (DeviceEntity) -> Unit,
    onEnableBluetooth: () -> Unit,
    onGrantPermissions: () -> Unit,
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
                Text(
                    text = "Nearby Devices",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                ConnectionStatusBadge(
                    state = connectionState,
                    peerName = connectedPeer?.displayName
                )
            }
        }

        if (!permissionStatus.allBluetoothGranted) {
            item { PermissionRationaleCard(onGrantClick = onGrantPermissions) }
        }

        if (!isBluetoothEnabled) {
            item { BluetoothDisabledBanner(onEnableClick = onEnableBluetooth) }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(136.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RadarPulseRings(
                            isActive = isScanning,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(SoftIceBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Radar,
                                contentDescription = "Nearby Bluetooth radar",
                                tint = RoyalBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (isScanning) "Scanning for NEAR TALK phones…" else "Ready to Discover Nearby Users",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isAdvertising) {
                            "Your device is visible to nearby NEAR TALK phones."
                        } else {
                            "Tap Scan to search via Bluetooth Low Energy."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    if (isScanning) {
                        OutlinedButton(
                            onClick = onStopScan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("stop_scan_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop Scanning")
                        }
                    } else {
                        Button(
                            onClick = onStartScan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_scan_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Nearby Devices", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!scanError.isNullOrBlank()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = StatusErrorBg
                ) {
                    Text(
                        text = scanError,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusError,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "Discovered Nearby (${discoveredDevices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (discoveredDevices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = if (isScanning) {
                            "Looking for nearby smartphones running NEAR TALK…"
                        } else {
                            "No nearby devices listed yet. Keep NEAR TALK open on both phones and tap Scan."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(discoveredDevices, key = { it.deviceId }) { device ->
                val isThisConnected = connectionState == ConnectionState.CONNECTED &&
                    connectedPeer?.lastBluetoothAddress == device.bluetoothAddress
                val isBusyConnecting = connectionState in setOf(
                    ConnectionState.CONNECTING,
                    ConnectionState.AUTHENTICATING
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discovered_device_${device.deviceId}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NearTalkAvatar(
                            name = device.displayName,
                            imageUri = null,
                            size = 48.dp,
                            isOnline = isThisConnected
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = device.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (device.isTrusted) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "Trusted device",
                                        tint = RoyalBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${device.deviceNickname} • Signal ${device.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        when {
                            isThisConnected -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Connected",
                                        tint = StatusSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Connected",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            isBusyConnecting -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = RoyalBlue
                                )
                            }
                            else -> {
                                Button(
                                    onClick = { onConnectDevice(device) },
                                    modifier = Modifier.testTag("connect_device_${device.deviceId}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (savedDevices.isNotEmpty()) {
            item {
                Text(
                    text = "Previously Paired Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(savedDevices, key = { it.id }) { saved ->
                val isConnected = connectionState == ConnectionState.CONNECTED && connectedPeer?.id == saved.id
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
                        NearTalkAvatar(
                            name = saved.displayName,
                            imageUri = saved.profileImage,
                            size = 44.dp,
                            isOnline = isConnected
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = saved.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (saved.isTrusted) "Trusted peer" else "Known peer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isConnected && !saved.lastBluetoothAddress.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { onConnectSaved(saved) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Reconnect")
                            }
                        }
                    }
                }
            }
        }
    }
}
