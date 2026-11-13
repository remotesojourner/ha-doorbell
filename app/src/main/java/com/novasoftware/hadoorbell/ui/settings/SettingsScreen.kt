package com.novasoftware.hadoorbell.ui.settings

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var invalidTokenQr by rememberSaveable { mutableStateOf(false) }
    var showCallSettings by rememberSaveable { mutableStateOf(false) }
    val tokenScanPrompt = stringResource(R.string.settings_token_scan_prompt)
    val scanToken = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { invalidTokenQr = !viewModel.importTokenFromQr(it) }
    }
    val controlsAuthTitle = stringResource(R.string.settings_controls_auth_title)
    val controlsAuthSubtitle = stringResource(R.string.settings_controls_auth_subtitle)
    val controlsAuthError = stringResource(R.string.settings_controls_auth_error)
    val entityLoadError = homeAssistantErrorMessage(uiState.entityLoadError, R.string.settings_entities_request_error)
    val serviceLoadError = homeAssistantErrorMessage(uiState.serviceLoadError, R.string.settings_services_request_error)

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
                onError = { message ->
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
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
                        onValueChange = {
                            invalidTokenQr = false
                            viewModel.updateToken(it)
                        },
                        label = { Text(stringResource(R.string.settings_ha_token)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = if (hasCamera) {
                            {
                                IconButton(onClick = {
                                    scanToken.launch(
                                        ScanOptions()
                                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            .setPrompt(tokenScanPrompt)
                                            .setBeepEnabled(false)
                                    )
                                }) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = stringResource(R.string.settings_scan_token)
                                    )
                                }
                            }
                        } else null,
                        isError = invalidTokenQr,
                        supportingText = if (invalidTokenQr) {
                            { Text(stringResource(R.string.settings_invalid_token_qr)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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

            // Stream Buttons Card
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
                        stringResource(R.string.settings_stream_buttons),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    StreamButtonEditor(
                        layout = uiState.streamButtonLayout,
                        entities = uiState.entities,
                        isLoadingEntities = uiState.isLoadingEntities,
                        entityLoadError = entityLoadError,
                        onLoadEntities = viewModel::loadEntities,
                        onLayoutChange = viewModel::updateStreamButtonLayout,
                        onEditCall = { showCallSettings = true },
                        services = uiState.services,
                        isLoadingServices = uiState.isLoadingServices,
                        serviceLoadError = serviceLoadError,
                        onLoadServices = viewModel::loadServices
                    )
                }
            }
            
            // Padding for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showCallSettings) {
        CallSettingsDialog(
            state = uiState,
            entityLoadError = entityLoadError,
            viewModel = viewModel,
            onDismiss = { showCallSettings = false }
        )
    }
}

@Composable
private fun homeAssistantErrorMessage(error: HomeAssistantLoadError?, requestFailed: Int): String? =
    when (error) {
        HomeAssistantLoadError.MissingConnection -> stringResource(R.string.settings_entities_missing_connection)
        HomeAssistantLoadError.ChangedServer -> stringResource(R.string.settings_entities_changed_server)
        HomeAssistantLoadError.InvalidConnection -> stringResource(R.string.settings_entities_invalid_connection)
        HomeAssistantLoadError.AuthenticationFailed -> stringResource(R.string.settings_entities_auth_error)
        HomeAssistantLoadError.RequestFailed -> stringResource(requestFailed)
        null -> null
    }

@Composable
private fun CallSettingsDialog(
    state: SettingsUiState,
    entityLoadError: String?,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var answerService by rememberSaveable { mutableStateOf(state.answerActionService) }
    var answerEntity by rememberSaveable { mutableStateOf(state.answerActionEntityId) }
    var hangupService by rememberSaveable { mutableStateOf(state.hangupActionService) }
    var hangupEntity by rememberSaveable { mutableStateOf(state.hangupActionEntityId) }
    var instantAudio by rememberSaveable { mutableStateOf(state.instantTwoWayAudio) }
    val instantAudioAvailable = !state.streamButtonLayout.hasQuickReply
    val instantAudioLabel = stringResource(R.string.settings_instant_2way)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_call_actions)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        instantAudioLabel,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Switch(
                        checked = instantAudio && instantAudioAvailable,
                        onCheckedChange = { instantAudio = it },
                        enabled = instantAudioAvailable,
                        modifier = Modifier.testTag("instant_2way_switch")
                            .semantics { contentDescription = instantAudioLabel }
                    )
                }
                if (!instantAudioAvailable) {
                    Text(
                        stringResource(R.string.settings_instant_audio_quick_reply),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    stringResource(R.string.settings_call_actions_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = answerService,
                    onValueChange = { answerService = it },
                    label = { Text(stringResource(R.string.settings_answer_action)) },
                    placeholder = { Text("switch.turn_on") },
                    modifier = Modifier.fillMaxWidth().testTag("answer_action_service_input"),
                    singleLine = true
                )
                EntityTargetField(
                    value = answerEntity,
                    onValueChange = { answerEntity = it },
                    label = stringResource(R.string.settings_answer_entity),
                    placeholder = "switch.doorbell_talk",
                    entities = state.entities,
                    isLoading = state.isLoadingEntities,
                    loadError = entityLoadError,
                    onLoad = viewModel::loadEntities,
                    modifier = Modifier.testTag("answer_action_entity_input")
                )
                OutlinedTextField(
                    value = hangupService,
                    onValueChange = { hangupService = it },
                    label = { Text(stringResource(R.string.settings_hangup_action)) },
                    placeholder = { Text("switch.turn_off") },
                    modifier = Modifier.fillMaxWidth().testTag("hangup_action_service_input"),
                    singleLine = true
                )
                EntityTargetField(
                    value = hangupEntity,
                    onValueChange = { hangupEntity = it },
                    label = stringResource(R.string.settings_hangup_entity),
                    placeholder = "switch.doorbell_talk",
                    entities = state.entities,
                    isLoading = state.isLoadingEntities,
                    loadError = entityLoadError,
                    onLoad = viewModel::loadEntities,
                    modifier = Modifier.testTag("hangup_action_entity_input")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateCallActions(answerService, answerEntity, hangupService, hangupEntity)
                viewModel.updateInstantTwoWayAudio(instantAudio)
                onDismiss()
            }) {
                Text(stringResource(R.string.settings_call_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
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
