package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.UserIdentity
import com.example.ui.components.NearTalkAvatar
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SoftIceBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentIdentity: UserIdentity?,
    isOnboarding: Boolean,
    onSave: (displayName: String, nickname: String, imageUri: String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var displayName by remember(currentIdentity) {
        mutableStateOf(currentIdentity?.displayName.orEmpty())
    }
    var deviceNickname by remember(currentIdentity) {
        mutableStateOf(currentIdentity?.deviceNickname.orEmpty())
    }
    var imageUri by remember(currentIdentity) {
        mutableStateOf(currentIdentity?.profileImageUri)
    }
    var nameError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                imageUri = uri.toString()
            }
        }
    )

    if (!isOnboarding) {
        BackHandler { onBack() }
    }

    Scaffold(
        topBar = {
            if (!isOnboarding) {
                CenterAlignedTopAppBar(
                    title = { Text("Edit Profile", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isOnboarding) stringResource(R.string.profile_setup_title) else "Your Local Identity",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.profile_setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("profile_avatar_picker"),
                contentAlignment = Alignment.BottomEnd
            ) {
                NearTalkAvatar(
                    name = displayName.ifBlank { "NT" },
                    imageUri = imageUri,
                    size = 112.dp
                )
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = RoyalBlue,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Choose profile photo",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap to choose optional photo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    nameError = null
                },
                label = { Text(stringResource(R.string.display_name_label)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                isError = nameError != null,
                supportingText = nameError?.let { err -> { Text(err) } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_display_name_input")
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = deviceNickname,
                onValueChange = { deviceNickname = it },
                label = { Text(stringResource(R.string.device_nickname_label)) },
                leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_nickname_input")
            )

            if (!currentIdentity?.deviceId.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = SoftIceBlue
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Local Anonymous Device ID",
                            style = MaterialTheme.typography.labelMedium,
                            color = RoyalBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentIdentity.deviceId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    val trimmed = displayName.trim()
                    if (trimmed.isEmpty()) {
                        nameError = "Please enter a display name"
                    } else {
                        onSave(trimmed, deviceNickname.trim(), imageUri)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_profile_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
            ) {
                Text(
                    text = stringResource(R.string.save_profile),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
