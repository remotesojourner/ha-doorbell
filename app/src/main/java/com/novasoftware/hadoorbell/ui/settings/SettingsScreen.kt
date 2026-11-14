package com.novasoftware.hadoorbell.ui.settings

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService

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

    val connectionStatus = uiState.connectionStatus
    val isCheckingConnection = connectionStatus == HomeAssistantConnectionStatus.Checking
    val canEditStreamSettings = connectionStatus == HomeAssistantConnectionStatus.Verified
    val canSaveConnection = uiState.url.isNotBlank() && uiState.token.isNotBlank() &&
        !canEditStreamSettings && !isCheckingConnection

    val context = LocalContext.current
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var invalidTokenQr by rememberSaveable { mutableStateOf(false) }
    var showCallSettings by rememberSaveable(canEditStreamSettings) { mutableStateOf(false) }
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
            Button(
                onClick = saveSettings,
                enabled = canEditStreamSettings,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.settings_save), fontWeight = FontWeight.Bold)
            }
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
                        enabled = !isCheckingConnection,
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
                        enabled = !isCheckingConnection,
                        label = { Text(stringResource(R.string.settings_ha_token)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = if (hasCamera) {
                            {
                                IconButton(
                                    enabled = !isCheckingConnection,
                                    onClick = {
                                        scanToken.launch(
                                            ScanOptions()
                                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                                .setPrompt(tokenScanPrompt)
                                                .setBeepEnabled(false)
                                        )
                                    }
                                ) {
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
                    val connectionMessage = when (connectionStatus) {
                        HomeAssistantConnectionStatus.Unchecked -> stringResource(R.string.settings_connection_required)
                        HomeAssistantConnectionStatus.Checking -> stringResource(R.string.settings_connection_checking)
                        HomeAssistantConnectionStatus.Verified -> stringResource(R.string.settings_connection_verified)
                        is HomeAssistantConnectionStatus.Failed -> homeAssistantErrorMessage(
                            connectionStatus.error,
                            R.string.settings_connection_request_error
                        ).orEmpty()
                    }
                    Text(
                        text = connectionMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connectionStatus is HomeAssistantConnectionStatus.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    Button(
                        onClick = viewModel::saveConnection,
                        enabled = canSaveConnection,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.settings_connection_save))
                    }
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
                                enabled = canEditStreamSettings,
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
                        enabled = canEditStreamSettings,
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
                        onLoadServices = viewModel::loadServices,
                        enabled = canEditStreamSettings,
                        onObserveAppearances = viewModel::observeEntityAppearances
                    )
                }
            }
            
            // Padding for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (canEditStreamSettings && showCallSettings) {
        CallSettingsDialog(
            state = uiState,
            entityLoadError = entityLoadError,
            serviceLoadError = serviceLoadError,
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
        HomeAssistantLoadError.ConnectionSaveFailed -> stringResource(R.string.settings_connection_save_error)
        null -> null
    }

@Composable
private fun CallSettingsDialog(
    state: SettingsUiState,
    entityLoadError: String?,
    serviceLoadError: String?,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var answerService by rememberSaveable { mutableStateOf(state.answerActionService) }
    var answerEntity by rememberSaveable { mutableStateOf(state.answerActionEntityId) }
    var answerJson by rememberSaveable { mutableStateOf(state.answerActionData.takeUnless { it.isEmpty }?.toString().orEmpty()) }
    var hangupService by rememberSaveable { mutableStateOf(state.hangupActionService) }
    var hangupEntity by rememberSaveable { mutableStateOf(state.hangupActionEntityId) }
    var hangupJson by rememberSaveable { mutableStateOf(state.hangupActionData.takeUnless { it.isEmpty }?.toString().orEmpty()) }
    var instantAudio by rememberSaveable { mutableStateOf(state.instantTwoWayAudio) }
    var picker by rememberSaveable { mutableStateOf<CallPicker?>(null) }
    val instantAudioAvailable = !state.streamButtonLayout.hasQuickReply
    val instantAudioLabel = stringResource(R.string.settings_instant_2way)
    fun serviceDescriptor(id: String): HomeAssistantService? = state.services.find { serviceId(it) == id }

    picker?.let { activePicker ->
        val isAnswer = activePicker == CallPicker.ANSWER_SERVICE || activePicker == CallPicker.ANSWER_ENTITY
        val service = if (isAnswer) answerService else hangupService
        val entityId = if (isAnswer) answerEntity else hangupEntity
        val dismissPicker = { picker = null }
        if (activePicker == CallPicker.ANSWER_SERVICE || activePicker == CallPicker.HANGUP_SERVICE) {
            HomeAssistantServicePicker(
                services = state.services,
                isLoading = state.isLoadingServices,
                loadError = serviceLoadError,
                onRetry = viewModel::loadServices,
                onDismiss = dismissPicker,
                serviceId = service,
                onSelect = { selected ->
                    val descriptor = serviceDescriptor(selected)
                    val domains = serviceTargetDomains(selected, descriptor)
                    val compatibleEntity = entityId.takeIf {
                        domains.isEmpty() || it.substringBefore('.') in domains
                    }.orEmpty()
                    if (isAnswer) {
                        if (answerService != selected) answerJson = ""
                        answerService = selected
                        answerEntity = compatibleEntity
                    } else {
                        if (hangupService != selected) hangupJson = ""
                        hangupService = selected
                        hangupEntity = compatibleEntity
                    }
                    dismissPicker()
                }
            )
        } else {
            val descriptor = serviceDescriptor(service)
            val domains = serviceTargetDomains(service, descriptor)
            HomeAssistantEntityPicker(
                entities = filterEntitiesByDomain(state.entities, domains),
                isLoading = state.isLoadingEntities,
                loadError = entityLoadError,
                onRetry = viewModel::loadEntities,
                onDismiss = dismissPicker,
                selectedEntityId = entityId,
                onSelect = { selected ->
                    if (isAnswer) answerEntity = selected.entityId else hangupEntity = selected.entityId
                    dismissPicker()
                }
            )
        }
        return
    }

    val servicePlaceholder = stringResource(R.string.stream_button_choose_service)
    val entityPlaceholder = stringResource(R.string.stream_button_choose_entity)
    val dialogScroll = rememberScrollState()
    val answerData = remember(answerJson) { parseJsonObject(answerJson) }
    val hangupData = remember(hangupJson) { parseJsonObject(hangupJson) }
    val answerDescriptor = serviceDescriptor(answerService)
    val hangupDescriptor = serviceDescriptor(hangupService)
    fun serviceName(id: String): String =
        serviceDescriptor(id)?.name?.takeIf(String::isNotBlank) ?: id.ifBlank { servicePlaceholder }
    fun entityName(id: String): String =
        state.entities.find { it.entityId == id }?.name ?: id.ifBlank { entityPlaceholder }
    fun openPicker(next: CallPicker) {
        when (next) {
            CallPicker.ANSWER_SERVICE, CallPicker.HANGUP_SERVICE -> viewModel.loadServices()
            CallPicker.ANSWER_ENTITY, CallPicker.HANGUP_ENTITY -> {
                if (state.services.isEmpty()) viewModel.loadServices()
                viewModel.loadEntities()
            }
        }
        picker = next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_call_actions)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(dialogScroll),
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
                CallPickerField(
                    label = R.string.settings_answer_action,
                    value = serviceName(answerService),
                    tag = "answer_action_service_picker",
                    onChoose = { openPicker(CallPicker.ANSWER_SERVICE) },
                    clearLabel = R.string.settings_clear_answer_service,
                    canClear = answerService.isNotBlank(),
                    onClear = {
                        answerService = ""
                        answerEntity = ""
                        answerJson = ""
                    }
                )
                CallPickerField(
                    label = R.string.settings_answer_entity,
                    value = entityName(answerEntity),
                    tag = "answer_action_entity_picker",
                    enabled = answerService.isNotBlank(),
                    onChoose = { openPicker(CallPicker.ANSWER_ENTITY) },
                    clearLabel = R.string.settings_clear_answer_target,
                    canClear = answerEntity.isNotBlank(),
                    onClear = { answerEntity = "" }
                )
                if (answerService.isNotBlank()) {
                    ServiceDataField(
                        value = answerJson,
                        onValueChange = { answerJson = it },
                        isError = answerData == null,
                        label = R.string.settings_answer_data,
                        modifier = Modifier.testTag("answer_action_data"),
                        availableFields = answerDescriptor?.fields.orEmpty().keys - "entity_id"
                    )
                }
                CallPickerField(
                    label = R.string.settings_hangup_action,
                    value = serviceName(hangupService),
                    tag = "hangup_action_service_picker",
                    onChoose = { openPicker(CallPicker.HANGUP_SERVICE) },
                    clearLabel = R.string.settings_clear_hangup_service,
                    canClear = hangupService.isNotBlank(),
                    onClear = {
                        hangupService = ""
                        hangupEntity = ""
                        hangupJson = ""
                    }
                )
                CallPickerField(
                    label = R.string.settings_hangup_entity,
                    value = entityName(hangupEntity),
                    tag = "hangup_action_entity_picker",
                    enabled = hangupService.isNotBlank(),
                    onChoose = { openPicker(CallPicker.HANGUP_ENTITY) },
                    clearLabel = R.string.settings_clear_hangup_target,
                    canClear = hangupEntity.isNotBlank(),
                    onClear = { hangupEntity = "" }
                )
                if (hangupService.isNotBlank()) {
                    ServiceDataField(
                        value = hangupJson,
                        onValueChange = { hangupJson = it },
                        isError = hangupData == null,
                        label = R.string.settings_hangup_data,
                        modifier = Modifier.testTag("hangup_action_data"),
                        availableFields = hangupDescriptor?.fields.orEmpty().keys - "entity_id"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = answerData != null && hangupData != null,
                onClick = {
                    if (answerData == null || hangupData == null) return@TextButton
                    viewModel.updateCallActions(
                        answerService = answerService,
                        answerEntity = answerEntity,
                        hangupService = hangupService,
                        hangupEntity = hangupEntity,
                        answerData = answerData,
                        hangupData = hangupData,
                        answerNeedsResponse = callActionNeedsResponse(
                            descriptor = answerDescriptor,
                            isSavedService = answerService == state.answerActionService,
                            isSavedData = answerData == state.answerActionData,
                            savedNeedsResponse = state.answerActionNeedsResponse
                        ),
                        hangupNeedsResponse = callActionNeedsResponse(
                            descriptor = hangupDescriptor,
                            isSavedService = hangupService == state.hangupActionService,
                            isSavedData = hangupData == state.hangupActionData,
                            savedNeedsResponse = state.hangupActionNeedsResponse
                        )
                    )
                    viewModel.updateInstantTwoWayAudio(instantAudio)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.settings_call_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun CallPickerField(
    @StringRes label: Int,
    value: String,
    tag: String,
    onChoose: () -> Unit,
    @StringRes clearLabel: Int,
    canClear: Boolean,
    onClear: () -> Unit,
    enabled: Boolean = true
) {
    Row(verticalAlignment = Alignment.Bottom) {
        EditorPickerButton(
            label = stringResource(label),
            value = value,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            buttonModifier = Modifier.testTag(tag),
            onClick = onChoose
        )
        if (canClear) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(clearLabel))
            }
        }
    }
}

private enum class CallPicker { ANSWER_SERVICE, ANSWER_ENTITY, HANGUP_SERVICE, HANGUP_ENTITY }

private fun callActionNeedsResponse(
    descriptor: HomeAssistantService?,
    isSavedService: Boolean,
    isSavedData: Boolean,
    savedNeedsResponse: Boolean
): Boolean = if (isSavedService && isSavedData) {
    savedNeedsResponse
} else {
    descriptor?.needsResponse ?: (isSavedService && savedNeedsResponse)
}
