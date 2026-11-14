package com.novasoftware.hadoorbell.ui.stream

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.core.utils.BiometricPromptHelper
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.ui.icons.StreamButtonIcon
import com.novasoftware.hadoorbell.ui.icons.resolveStreamButtonIcon
import com.novasoftware.hadoorbell.ui.icons.streamButtonFallbackIcon
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    viewModel: StreamViewModel
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val authFailedText = stringResource(R.string.authentication_unavailable)
    val resources by rememberUpdatedState(LocalResources.current)
    val navigateToSettings by rememberUpdatedState(onNavigateToSettings)

    // Collect one-shot UI events
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(
                        context,
                        event.message,
                        if (event.long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
                is UiEvent.Authentication -> {
                    val button = (event as? UiEvent.AuthenticateStreamButton)?.button
                    val action = button?.action
                    val openingSettings = event is UiEvent.AuthenticateSettings
                    fun completeAuthentication(succeeded: Boolean) {
                        when (event) {
                            is UiEvent.AuthenticateSettings -> {
                                if (viewModel.onSettingsAuthentication(event, succeeded)) navigateToSettings()
                            }
                            is UiEvent.AuthenticateStreamButton ->
                                viewModel.onStreamButtonAuthentication(event, succeeded)
                        }
                    }
                    BiometricPromptHelper.authenticate(
                        context = context,
                        title = when {
                            openingSettings -> resources.getString(R.string.stream_settings_auth_title)
                            action is StreamButtonAction.DoorLock -> resources.getString(R.string.stream_unlock_door)
                            else -> resources.getString(R.string.stream_button_auth_title, button?.displayName)
                        },
                        subtitle = resources.getString(
                            when {
                                openingSettings -> R.string.stream_settings_auth_subtitle
                                action is StreamButtonAction.DoorLock -> R.string.stream_authenticate_unlock
                                else -> R.string.stream_button_auth_subtitle
                            }
                        ),
                        allowWithoutScreenLock = openingSettings || action is StreamButtonAction.DoorLock ||
                            action is StreamButtonAction.CallService && action.doorRelease,
                        onSuccess = { completeAuthentication(true) },
                        onCryptoFailure = {
                            completeAuthentication(false)
                            Toast.makeText(context, authFailedText, Toast.LENGTH_LONG).show()
                        },
                        onError = { message ->
                            completeAuthentication(false)
                            message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        }
                    )
                }
            }
        }
    }

    // Animation for the pulsing mic
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.isMicEnabled) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.initializeConnection(context)
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.teardownConnection()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cancelAuthentication()
            viewModel.teardownConnection()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Video View or Error
        if (uiState.errorMessage != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Videocam, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Text(
                    text = stringResource(R.string.stream_connection_failed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = uiState.errorMessage ?: "Unknown Error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            uiState.webRtcManager?.let { manager ->
                AndroidView(
                    factory = { ctx ->
                        val frameLayout = android.widget.FrameLayout(ctx)
                        frameLayout.layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        val renderer = SurfaceViewRenderer(ctx).apply {
                            init(manager.getEglBaseContext(), null)
                            setEnableHardwareScaler(false)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        }

                        frameLayout.addView(renderer, android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.CENTER
                        ))

                        viewModel.startStream(renderer)

                        frameLayout
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } ?: run {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.stream_connecting),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Top Gradient Overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        StreamTopControls(
            uiState = uiState,
            onSettingsClick = viewModel::requestSettingsAuthentication,
            onCloseClick = {
                viewModel.cancelAuthentication()
                onExit()
            },
            onButtonClick = viewModel::handleStreamButton,
            modifier = Modifier
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp)
                .align(Alignment.TopCenter)
        )

        // Bottom Gradient Overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        StreamBottomControls(
            uiState = uiState,
            pulseScale = pulseScale,
            onSoundClick = viewModel::toggleStreamMute,
            onCallClick = { viewModel.toggleMic(context) },
            onButtonClick = viewModel::handleStreamButton,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp)
        )
    }

    uiState.quickReplySheet?.let { sheet ->
        QuickReplySheet(
            sheet = sheet,
            onDismiss = viewModel::dismissQuickReplySheet,
            onSelect = viewModel::sendQuickReply
        )
    }
}

@Composable
private fun StreamTopControls(
    uiState: StreamUiState,
    onSettingsClick: () -> Unit,
    onCloseClick: () -> Unit,
    onButtonClick: (StreamButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttons = uiState.streamButtonLayout.top.filter(StreamButton::visible)
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }

        if (buttons.isNotEmpty()) {
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                buttons.forEach { button ->
                    key(button.id) {
                        Box(
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            StreamActionButton(
                                button = button,
                                enabled = uiState.canRun(button),
                                lockState = uiState.lockStates[button.action.entityId] ?: LockState.Unknown,
                                entityAppearance = uiState.entityAppearances[button.iconEntityId],
                                onClick = { onButtonClick(button) },
                                circleSize = 48.dp,
                                iconSize = 24.dp
                            )
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onCloseClick,
            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
internal fun StreamBottomControls(
    uiState: StreamUiState,
    pulseScale: Float,
    onSoundClick: () -> Unit,
    onCallClick: () -> Unit,
    onButtonClick: (StreamButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttons = uiState.streamButtonLayout.bottom.filter(StreamButton::visible)
    val controlSize = 64.dp
    val callContainerSize = 80.dp
    val fixedWidth = controlSize + callContainerSize
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fullWidth = fixedWidth + controlSize * buttons.size
        val horizontalPadding = ((maxWidth - fullWidth) / 2).coerceIn(8.dp, 24.dp)
        val actionSize = if (buttons.isEmpty()) controlSize else
            ((maxWidth - fixedWidth - horizontalPadding * 2) / buttons.size).coerceIn(48.dp, controlSize)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FloatingActionButton(
                onClick = onSoundClick,
                modifier = Modifier.size(controlSize),
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isStreamMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (uiState.isStreamMuted) "Unmute Stream" else "Mute Stream",
                    modifier = Modifier.size(28.dp)
                )
            }

            Box(
                modifier = Modifier.size(callContainerSize),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isMicEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.3f))
                    )
                }

                FloatingActionButton(
                    onClick = onCallClick,
                    modifier = Modifier.size(controlSize),
                    containerColor = if (uiState.isMicEnabled) Color.Red else Color(0xFF4CAF50),
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isMicEnabled) Icons.Default.CallEnd else Icons.Default.Call,
                        contentDescription = if (uiState.isMicEnabled) "End Call" else "Call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            buttons.forEach { button ->
                key(button.id) {
                    StreamActionButton(
                        button = button,
                        enabled = uiState.canRun(button),
                        lockState = uiState.lockStates[button.action.entityId] ?: LockState.Unknown,
                        entityAppearance = uiState.entityAppearances[button.iconEntityId],
                        onClick = { onButtonClick(button) },
                        circleSize = actionSize,
                        iconSize = 28.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplySheet(
    sheet: QuickReplySheetState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.stream_quick_reply),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when {
                sheet.error != null -> Text(
                    text = sheet.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                sheet.options == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                sheet.options.isEmpty() -> Text(
                    text = stringResource(R.string.stream_quick_reply_no_options),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    items(sheet.options) { option ->
                        Surface(
                            onClick = { onSelect(option) },
                            enabled = !sheet.isSending,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = option,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamActionButton(
    button: StreamButton,
    enabled: Boolean,
    lockState: LockState,
    entityAppearance: HomeAssistantEntityAppearance?,
    onClick: () -> Unit,
    circleSize: Dp,
    iconSize: Dp
) {
    val isLock = button.action is StreamButtonAction.DoorLock
    val fallbackIcon = streamButtonFallbackIcon(button.action, lockState)
    val usesStateEntity = !button.stateEntityId.isNullOrBlank()
    val stateEntityLockState = if (button.iconEntityId.startsWith("lock.")) {
        LockState.fromString(entityAppearance?.state)
    } else null
    val displayedLockState = if (isLock) lockState else stateEntityLockState
    val lockDescription = displayedLockState?.let { state ->
        stringResource(
            when (state) {
                LockState.Locked -> R.string.stream_lock_locked
                LockState.Unlocked -> R.string.stream_lock_unlocked
                LockState.Locking -> R.string.stream_lock_locking
                LockState.Unlocking -> R.string.stream_lock_unlocking
                LockState.Jammed -> R.string.stream_lock_jammed
                LockState.Unknown -> R.string.stream_lock_unknown
            }
        )
    }
    val background = when (displayedLockState) {
        LockState.Unlocked -> Color.Red.copy(alpha = 0.8f)
        LockState.Jammed -> Color(0xFFFFA500)
        LockState.Locking, LockState.Unlocking -> Color.Gray.copy(alpha = 0.8f)
        LockState.Unknown -> Color.DarkGray.copy(alpha = 0.8f)
        LockState.Locked, null -> Color.White.copy(alpha = 0.2f)
    }
    val hasSeparateStateDescription = if (isLock) {
        usesStateEntity && button.iconEntityId != button.action.entityId
    } else {
        usesStateEntity && stateEntityLockState == null
    }
    val entityDescription = entityAppearance?.state?.takeIf { hasSeparateStateDescription && it.isNotBlank() }
    val description = listOfNotNull(lockDescription, entityDescription).joinToString()
    val stateSemantics = if (description.isEmpty()) Modifier else Modifier.semantics {
        stateDescription = description
    }
    val resolvedIcon = resolveStreamButtonIcon(button, entityAppearance)
    val rotation = if (resolvedIcon == null && isLock &&
        (lockState == LockState.Locking || lockState == LockState.Unlocking)
    ) {
        val transition = rememberInfiniteTransition(label = "lock")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "lock status"
        )
        angle
    } else 0f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(circleSize).then(stateSemantics),
        shape = CircleShape,
        color = background,
        contentColor = Color.White.copy(alpha = if (enabled || isLock) 1f else 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val modifier = Modifier.size(iconSize)
            if (resolvedIcon == null) {
                Icon(fallbackIcon, button.displayName, modifier.rotate(rotation))
            } else {
                StreamButtonIcon(resolvedIcon, button.displayName, modifier)
            }
        }
    }
}
