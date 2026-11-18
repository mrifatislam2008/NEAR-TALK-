package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.NearTalkAvatar
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onEditProfile: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    val trustedDevices by viewModel.trustedDevices.collectAsStateWithLifecycle()
    val blockedDevices by viewModel.blockedDevices.collectAsStateWithLifecycle()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Profile Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditProfile)
                    .testTag("settings_edit_profile_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NearTalkAvatar(
                        name = identity.displayName.ifBlank { "NT" },
                        imageUri = identity.profileImageUri,
                        size = 56.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = identity.displayName.ifBlank { "Set Display Name" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Device: ${identity.deviceNickname}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Edit profile")
                }
            }
        }

        // Bluetooth & Discoverability
        item {
            SectionTitle("Bluetooth & Connectivity")
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsActionRow(
                        icon = Icons.Default.Bluetooth,
                        title = "Bluetooth Radio",
                        subtitle = if (isBluetoothEnabled) "Enabled • Tap to open system Bluetooth" else "Disabled • Tap to enable",
                        trailingText = if (isBluetoothEnabled) "ON" else "OFF",
                        trailingColor = if (isBluetoothEnabled) StatusSuccess else StatusError,
                        onClick = onOpenBluetoothSettings
                    )
                    SettingsActionRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "Make Classic Discoverable",
                        subtitle = "Allow pairing requests for 120 seconds",
                        onClick = onMakeDiscoverable
                    )
                }
            }
        }

        // Privacy & Audio Preferences
        item {
            SectionTitle("Privacy, Audio & Notifications")
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsSwitchRow(
                        icon = Icons.Default.Shield,
                        title = "Auto-Accept Trusted Peers",
                        subtitle = "Unknown devices always require manual approval",
                        checked = identity.autoAcceptTrusted,
                        onCheckedChange = viewModel::setAutoAcceptTrusted
                    )
                    SettingsSwitchRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = "Speakerphone by Default",
                        subtitle = "Start voice calls on speaker instead of earpiece",
                        checked = identity.speakerByDefault,
                        onCheckedChange = viewModel::setSpeakerByDefault
                    )
                    SettingsSwitchRow(
                        icon = Icons.Default.Notifications,
                        title = "Call & Link Notifications",
                        subtitle = "Persistent notifications during calls and links",
                        checked = identity.notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationsEnabled
                    )
                }
            }
        }

        // Trusted Devices
        item {
            SectionTitle("Trusted Devices (${trustedDevices.size})")
        }
        if (trustedDevices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "No trusted peers yet. Mark a peer as trusted from their chat menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(trustedDevices, key = { "trusted_${it.id}" }) { dev ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dev.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(dev.deviceNickname, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { viewModel.removeTrust(dev.id) }) {
                            Text("Remove", color = StatusError)
                        }
                    }
                }
            }
        }

        // Blocked Devices
        if (blockedDevices.isNotEmpty()) {
            item {
                SectionTitle("Blocked Devices (${blockedDevices.size})")
            }
            items(blockedDevices, key = { "blocked_${it.id}" }) { dev ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dev.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Blocked peer", style = MaterialTheme.typography.bodySmall, color = StatusError)
                        }
                        TextButton(onClick = { viewModel.unblockDevice(dev.id) }) {
                            Text("Unblock", color = RoyalBlue)
                        }
                    }
                }
            }
        }

        // About NEAR TALK
        item {
            SectionTitle("About NEAR TALK")
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = RoyalBlue)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.about_neartalk),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = RoyalBlue,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    trailingColor: androidx.compose.ui.graphics.Color = RoyalBlue,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RoyalBlue)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.labelLarge, color = trailingColor, fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RoyalBlue)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = RoyalBlue)
        )
    }
}
