package com.novasoftware.hadoorbell.ui.settings

import android.widget.Toast
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.core.utils.BiometricPromptHelper
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity

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

    val context = LocalContext.current
    val controlsAuthTitle = stringResource(R.string.settings_controls_auth_title)
    val controlsAuthSubtitle = stringResource(R.string.settings_controls_auth_subtitle)
    val controlsAuthError = stringResource(R.string.settings_controls_auth_error)
    val entityLoadError = when (uiState.entityLoadError) {
        EntityLoadError.MissingConnection -> stringResource(R.string.settings_entities_missing_connection)
        EntityLoadError.ChangedServer -> stringResource(R.string.settings_entities_changed_server)
        EntityLoadError.InvalidConnection -> stringResource(R.string.settings_entities_invalid_connection)
        EntityLoadError.AuthenticationFailed -> stringResource(R.string.settings_entities_auth_error)
        EntityLoadError.RequestFailed -> stringResource(R.string.settings_entities_request_error)
        null -> null
    }

    val saveSettings = {
        if (viewModel.callActionsChanged() || viewModel.protectedButtonsChanged()) {
            BiometricPromptHelper.authenticate(
                context = context,
                title = controlsAuthTitle,
                subtitle = controlsAuthSubtitle,
                onSuccess = { viewModel.saveSettings(onSave) },
                onCryptoFailure = {
                    Toast.makeText(context, controlsAuthError, Toast.LENGTH_LONG).show()
                },
                onError = {
                    Toast.makeText(context, controlsAuthError, Toast.LENGTH_LONG).show()
                }
            )
        } else {
            viewModel.saveSettings(onSave)
        }
    }

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
                onClick = { saveSettings() },
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
                    
                    val customLayout = uiState.streamButtonLayout?.takeIf { it.enabled }
                    val instantAudioAvailable =
                        customLayout != null || uiState.quickReplyEntityId.isBlank()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.settings_instant_2way),
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (instantAudioAvailable) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Switch(
                            checked = uiState.instantTwoWayAudio && instantAudioAvailable,
                            onCheckedChange = { viewModel.updateInstantTwoWayAudio(it) },
                            enabled = instantAudioAvailable,
                            modifier = Modifier.testTag("instant_2way_switch")
                        )
                    }

                    val customButtonsLabel = stringResource(R.string.settings_use_custom_buttons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            customButtonsLabel,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = customLayout != null,
                            onCheckedChange = viewModel::setCustomButtonsEnabled,
                            modifier = Modifier
                                .testTag("custom_buttons_switch")
                                .semantics { contentDescription = customButtonsLabel }
                        )
                    }
                    if (customLayout != null) {
                        StreamButtonEditor(
                            layout = customLayout,
                            entities = uiState.entities,
                            isLoadingEntities = uiState.isLoadingEntities,
                            entityLoadError = entityLoadError,
                            onLoadEntities = viewModel::loadEntities,
                            onLayoutChange = viewModel::updateStreamButtonLayout
                        )
                    } else {
                        LegacyStreamControls(
                            state = uiState,
                            entityLoadError = entityLoadError,
                            viewModel = viewModel
                        )
                    }

                    Text(
                        stringResource(R.string.settings_call_actions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        stringResource(R.string.settings_call_actions_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = uiState.answerActionService,
                        onValueChange = { viewModel.updateAnswerActionService(it) },
                        label = { Text(stringResource(R.string.settings_answer_action)) },
                        placeholder = { Text("switch.turn_on") },
                        modifier = Modifier.fillMaxWidth().testTag("answer_action_service_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    EntityTargetField(
                        value = uiState.answerActionEntityId,
                        onValueChange = viewModel::updateAnswerActionEntityId,
                        label = stringResource(R.string.settings_answer_entity),
                        placeholder = "switch.doorbell_talk",
                        entities = uiState.entities,
                        isLoading = uiState.isLoadingEntities,
                        loadError = entityLoadError,
                        onLoad = viewModel::loadEntities,
                        modifier = Modifier.testTag("answer_action_entity_input")
                    )

                    OutlinedTextField(
                        value = uiState.hangupActionService,
                        onValueChange = { viewModel.updateHangupActionService(it) },
                        label = { Text(stringResource(R.string.settings_hangup_action)) },
                        placeholder = { Text("switch.turn_off") },
                        modifier = Modifier.fillMaxWidth().testTag("hangup_action_service_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    EntityTargetField(
                        value = uiState.hangupActionEntityId,
                        onValueChange = viewModel::updateHangupActionEntityId,
                        label = stringResource(R.string.settings_hangup_entity),
                        placeholder = "switch.doorbell_talk",
                        entities = uiState.entities,
                        isLoading = uiState.isLoadingEntities,
                        loadError = entityLoadError,
                        onLoad = viewModel::loadEntities,
                        modifier = Modifier.testTag("hangup_action_entity_input")
                    )
                }
            }
            
            // Padding for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun LegacyStreamControls(
    state: SettingsUiState,
    entityLoadError: String?,
    viewModel: SettingsViewModel
) {
    EntityTargetField(
        value = state.quickReplyEntityId,
        onValueChange = viewModel::updateQuickReplyEntityId,
        label = stringResource(R.string.settings_quick_reply_id),
        placeholder = "select.doorbell_quick_reply",
        entities = state.entities.filter { it.domain == "select" || it.domain == "input_select" },
        isLoading = state.isLoadingEntities,
        loadError = entityLoadError,
        onLoad = viewModel::loadEntities
    )

    EntityTargetField(
        value = state.lockEntityId,
        onValueChange = viewModel::updateLockEntityId,
        label = stringResource(R.string.settings_door_lock_id),
        placeholder = "lock.front_door",
        entities = state.entities.filter { it.domain == "lock" },
        isLoading = state.isLoadingEntities,
        loadError = entityLoadError,
        onLoad = viewModel::loadEntities
    )

    Text(
        stringResource(R.string.settings_door_open),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        stringResource(R.string.settings_door_open_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    EntityTargetField(
        value = state.doorEntityId,
        onValueChange = viewModel::updateDoorEntityId,
        label = stringResource(R.string.settings_door_entity),
        placeholder = "switch.door_relay",
        entities = state.entities,
        isLoading = state.isLoadingEntities,
        loadError = entityLoadError,
        onLoad = viewModel::loadEntities,
        modifier = Modifier.testTag("door_entity_input")
    )
    OutlinedTextField(
        value = state.doorService,
        onValueChange = viewModel::updateDoorService,
        label = { Text(stringResource(R.string.settings_door_service)) },
        placeholder = { Text("switch.turn_on") },
        modifier = Modifier.fillMaxWidth().testTag("door_service_input"),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
    Text(
        stringResource(R.string.settings_door_service_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EntityTargetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    entities: List<HomeAssistantEntity>,
    isLoading: Boolean,
    loadError: String?,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val browseLabel = stringResource(R.string.settings_browse_entity_target, label)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        trailingIcon = {
            TextButton(
                modifier = Modifier.semantics { contentDescription = browseLabel },
                onClick = {
                    showPicker = true
                    onLoad()
                }
            ) {
                Text(stringResource(R.string.settings_browse_entities))
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
    if (showPicker) {
        HomeAssistantEntityPicker(
            entities = entities,
            isLoading = isLoading,
            loadError = loadError,
            onRetry = onLoad,
            onDismiss = { showPicker = false },
            onSelect = {
                onValueChange(it.entityId)
                showPicker = false
            },
            selectedEntityId = value
        )
    }
}
