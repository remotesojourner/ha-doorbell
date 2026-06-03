package com.novasoftware.hadoorbell.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.novasoftware.hadoorbell.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    canCancel: Boolean,
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isLoaded) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (canCancel) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.saveSettings(onSave) },
                icon = { },
                text = { Text(stringResource(R.string.settings_save), fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                shape = RoundedCornerShape(16.dp)
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server Connection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_server_connection),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    OutlinedTextField(
                        value = uiState.url,
                        onValueChange = { viewModel.updateUrl(it) },
                        label = { Text(stringResource(R.string.settings_ha_url)) },
                        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = "Server URL") },
                        placeholder = { Text("https://192.168.1.10:8123") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.token,
                        onValueChange = { viewModel.updateToken(it) },
                        label = { Text(stringResource(R.string.settings_ha_token)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = "Token") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )


                }
            }

            // WebRTC Provider Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_stream_config),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.settings_ha_integration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("frigate" to "Frigate", "webrtc" to "AlexxIT/WebRTC").forEach { (value, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = uiState.webrtcProvider == value,
                                onClick = { viewModel.updateWebrtcProvider(value) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("provider_chip_$value")
                            )
                        }
                    }
                    
                    Text(
                        stringResource(R.string.settings_stream_source_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = uiState.streamSource,
                        onValueChange = { viewModel.updateStreamSource(it) },
                        label = { Text(stringResource(R.string.settings_stream_source)) },
                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = "Stream") },
                        placeholder = { Text("front_door_camera") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Intercom & Features Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_integrations_optional),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        stringResource(R.string.settings_integrations_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.settings_instant_2way),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.quickReplyEntityId.isBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Switch(
                            checked = uiState.instantTwoWayAudio,
                            onCheckedChange = { viewModel.updateInstantTwoWayAudio(it) },
                            enabled = uiState.quickReplyEntityId.isBlank(),
                            modifier = Modifier.testTag("instant_2way_switch")
                        )
                    }

                    OutlinedTextField(
                        value = uiState.quickReplyEntityId,
                        onValueChange = { viewModel.updateQuickReplyEntityId(it) },
                        label = { Text(stringResource(R.string.settings_quick_reply_id)) },
                        placeholder = { Text("select.doorbell_quick_reply") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.lockEntityId,
                        onValueChange = { viewModel.updateLockEntityId(it) },
                        label = { Text(stringResource(R.string.settings_door_lock_id)) },
                        placeholder = { Text("lock.front_door") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
            
            // Padding for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
