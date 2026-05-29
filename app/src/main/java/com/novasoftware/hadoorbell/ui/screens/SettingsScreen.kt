package com.novasoftware.hadoorbell.ui.screens

import androidx.compose.foundation.layout.*
import com.novasoftware.hadoorbell.data.AppPreferences
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    canCancel: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var streamSource by remember { mutableStateOf("") }
    var quickReplyEntityId by remember { mutableStateOf("") }
    var lockEntityId by remember { mutableStateOf("") }

    // Load initial values
    LaunchedEffect(Unit) {
        appPreferences.haUrlFlow.collect { url = it ?: "" }
    }
    LaunchedEffect(Unit) {
        appPreferences.haTokenFlow.collect { token = it ?: "" }
    }
    LaunchedEffect(Unit) {
        appPreferences.streamSourceFlow.collect { streamSource = it ?: "" }
    }
    LaunchedEffect(Unit) {
        appPreferences.quickReplyEntityIdFlow.collect { quickReplyEntityId = it ?: "" }
    }
    LaunchedEffect(Unit) {
        appPreferences.lockEntityIdFlow.collect { lockEntityId = it ?: "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doorbell Setup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (canCancel) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        appPreferences.saveSettings(url, token, streamSource, quickReplyEntityId, lockEntityId)
                        onSave()
                    }
                },
                icon = { Icon(Icons.Filled.Videocam, contentDescription = "Save & Connect") },
                text = { Text("Save & Connect") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp)
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Server Connection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Server Connection",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Home Assistant URL") },
                        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = "Server URL") },
                        placeholder = { Text("http://192.168.1.10:8123") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Long-Lived Access Token") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = "Token") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Stream Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Stream Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        "Go2rtc stream name that supports 2 way audio from Frigate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = streamSource,
                        onValueChange = { streamSource = it },
                        label = { Text("go2rtc Stream Name") },
                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = "Camera") },
                        placeholder = { Text("doorbell_camera_2way") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Quick Reply Features Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Integrations (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        "If you have a select entity for quick replies or a lock entity to open your door, enter their IDs below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = quickReplyEntityId,
                        onValueChange = { quickReplyEntityId = it },
                        label = { Text("Quick Reply Entity ID") },
                        placeholder = { Text("select.doorbell_quick_reply") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = lockEntityId,
                        onValueChange = { lockEntityId = it },
                        label = { Text("Door Lock Entity ID") },
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
