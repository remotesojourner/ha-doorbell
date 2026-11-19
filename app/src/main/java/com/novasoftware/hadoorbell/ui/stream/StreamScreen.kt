package com.novasoftware.hadoorbell.ui.stream

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.core.utils.BiometricPromptHelper
import com.novasoftware.hadoorbell.core.webrtc.WebRtcManager
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamBottomItem
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamControl
import com.novasoftware.hadoorbell.ui.icons.StreamButtonIcon
import com.novasoftware.hadoorbell.ui.icons.resolveStreamButtonIcon
import com.novasoftware.hadoorbell.ui.icons.streamButtonFallbackIcon
import com.novasoftware.hadoorbell.ui.theme.StreamColors
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    viewModel: StreamViewModel,
    answerRequest: Long? = null,
    onAnswerRequestHandled: () -> Unit = {}
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val authFailedText = stringResource(R.string.authentication_unavailable)
    val resources by rememberUpdatedState(LocalResources.current)
    val navigateToSettings by rememberUpdatedState(onNavigateToSettings)
    val snackbarHostState = remember { SnackbarHostState() }
    val messageScope = rememberCoroutineScope()
    fun showMessage(message: String, long: Boolean) {
        messageScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = long,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> showMessage(event.message, event.long)
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
                            showMessage(authFailedText, long = true)
                        },
                        onError = { message ->
                            completeAuthentication(false)
                            message?.let { showMessage(it.toString(), long = true) }
                        }
                    )
                }
            }
        }
    }

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

    LaunchedEffect(answerRequest) {
        if (answerRequest != null) {
            viewModel.answerWhenReady(context)
            onAnswerRequestHandled()
        }
    }

    var fillScreen by rememberSaveable { mutableStateOf(false) }

    StreamContent(
        uiState = uiState,
        pulseScale = pulseScale,
        snackbarHostState = snackbarHostState,
        onSettingsClick = viewModel::requestSettingsAuthentication,
        onCloseClick = {
            viewModel.cancelAuthentication()
            onExit()
        },
        onButtonClick = viewModel::handleStreamButton,
        onSoundClick = viewModel::toggleStreamMute,
        onCallClick = { viewModel.toggleMic(context) },
        onRetry = { viewModel.retryConnection(context) },
        onDismissQuickReply = viewModel::dismissQuickReplySheet,
        onSendQuickReply = viewModel::sendQuickReply
    ) { manager ->
        key(manager) {
            StreamVideo(
                manager = manager,
                fillScreen = fillScreen,
                onFillScreenChange = { fillScreen = it },
                onRendererReady = viewModel::startStream
            )
        }
    }
}

/** The stream screen's layout. The live video comes from [video], so the layout can also be shown without a stream. */
@Composable
internal fun StreamContent(
    uiState: StreamUiState,
    pulseScale: Float,
    snackbarHostState: SnackbarHostState,
    onSettingsClick: () -> Unit,
    onCloseClick: () -> Unit,
    onButtonClick: (StreamButton) -> Unit,
    onSoundClick: () -> Unit,
    onCallClick: () -> Unit,
    onRetry: () -> Unit,
    onDismissQuickReply: () -> Unit,
    onSendQuickReply: (String) -> Unit,
    video: @Composable (WebRtcManager) -> Unit
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val manager = uiState.webRtcManager
        when {
            uiState.errorMessage != null -> StreamError(
                message = uiState.errorMessage,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )
            manager != null -> video(manager)
            else -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.stream_connecting),
                    style = MaterialTheme.typography.bodyLarge,
                    color = StreamColors.SecondaryText
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(StreamColors.TopScrim, Color.Transparent)))
        )

        StreamTopControls(
            uiState = uiState,
            onSettingsClick = onSettingsClick,
            onCloseClick = onCloseClick,
            onButtonClick = onButtonClick,
            modifier = Modifier
                .padding(top = topInset + 16.dp)
                .align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, StreamColors.BottomScrim)))
        )

        StreamBottomControls(
            uiState = uiState,
            pulseScale = pulseScale,
            onSoundClick = onSoundClick,
            onCallClick = onCallClick,
            onButtonClick = onButtonClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset + 16.dp + TOP_CONTROL_SIZE + 12.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(24.dp),
                containerColor = StreamColors.MessageBackground,
                contentColor = StreamColors.OnControl,
                dismissActionContentColor = StreamColors.SecondaryText
            )
        }
    }

    uiState.quickReplySheet?.let { sheet ->
        QuickReplySheet(
            sheet = sheet,
            onDismiss = onDismissQuickReply,
            onSelect = onSendQuickReply
        )
    }
}

@Composable
private fun StreamVideo(
    manager: WebRtcManager,
    fillScreen: Boolean,
    onFillScreenChange: (Boolean) -> Unit,
    onRendererReady: (SurfaceViewRenderer) -> Unit
) {
    val currentFillScreen by rememberUpdatedState(fillScreen)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }

    fun resetZoom() {
        zoom = 1f
        pan = Offset.Zero
    }
    fun toggleFill() {
        onFillScreenChange(!currentFillScreen)
        resetZoom()
    }
    fun clampPan(offset: Offset, scale: Float, container: IntSize): Offset {
        val maxX = ((videoSize.width * scale - container.width) / 2f).coerceAtLeast(0f)
        val maxY = ((videoSize.height * scale - container.height) / 2f).coerceAtLeast(0f)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    val fillLabel = stringResource(if (fillScreen) R.string.stream_video_fit else R.string.stream_video_fill)
    val resetZoomLabel = stringResource(R.string.stream_video_reset_zoom)

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val renderer = SurfaceViewRenderer(ctx).apply {
                    init(manager.getEglBaseContext(), null)
                    setEnableHardwareScaler(false)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    tag = RendererCommon.ScalingType.SCALE_ASPECT_FIT
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        videoSize = IntSize(view.width, view.height)
                    }
                }
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    addView(
                        renderer,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER
                        )
                    )
                    onRendererReady(renderer)
                }
            },
            update = { frame ->
                val renderer = frame.getChildAt(0) as SurfaceViewRenderer
                val scaling = if (fillScreen) {
                    RendererCommon.ScalingType.SCALE_ASPECT_FILL
                } else {
                    RendererCommon.ScalingType.SCALE_ASPECT_FIT
                }
                if (renderer.tag != scaling) {
                    renderer.setScalingType(scaling)
                    renderer.tag = scaling
                }
                renderer.scaleX = zoom
                renderer.scaleY = zoom
                renderer.translationX = pan.x
                renderer.translationY = pan.y
            },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { if (zoom > 1f) resetZoom() else toggleFill() })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_VIDEO_ZOOM)
                        val focus = centroid - Offset(size.width / 2f, size.height / 2f)
                        val newPan = focus - (focus - pan) * (newZoom / zoom) + panChange
                        zoom = newZoom
                        pan = if (newZoom == 1f) Offset.Zero else clampPan(newPan, newZoom, size)
                    }
                }
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(fillLabel) {
                            toggleFill()
                            true
                        },
                        CustomAccessibilityAction(resetZoomLabel) {
                            resetZoom()
                            true
                        }.takeIf { zoom > 1f }
                    )
                }
        )
    }
}

@Composable
private fun StreamError(message: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.VideocamOff,
            contentDescription = null,
            tint = StreamColors.Error,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = stringResource(R.string.stream_connection_failed),
            style = MaterialTheme.typography.headlineSmall,
            color = StreamColors.OnControl,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message ?: stringResource(R.string.stream_button_unknown_error),
            color = StreamColors.SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.stream_button_retry))
        }
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
        StreamCircleButton(
            onClick = onSettingsClick,
            color = StreamColors.ControlBackground,
            size = 40.dp
        ) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.stream_settings))
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
                                uiState = uiState,
                                onClick = { onButtonClick(button) },
                                circleSize = TOP_CONTROL_SIZE,
                                iconSize = 24.dp
                            )
                        }
                    }
                }
            }
        }

        StreamCircleButton(
            onClick = onCloseClick,
            color = StreamColors.ControlBackground,
            size = 40.dp
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.stream_close))
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
    val items = uiState.streamButtonLayout.bottomItems.filter { it !is StreamBottomItem.Custom || it.button.visible }
    val buttonCount = items.count { it is StreamBottomItem.Custom }
    val controlSize = 64.dp
    val callContainerSize = 80.dp
    val fixedWidth = controlSize + callContainerSize
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fullWidth = fixedWidth + controlSize * buttonCount
        val horizontalPadding = ((maxWidth - fullWidth) / 2).coerceIn(8.dp, 24.dp)
        val actionSize = if (buttonCount == 0) controlSize else
            ((maxWidth - fixedWidth - horizontalPadding * 2) / buttonCount).coerceIn(48.dp, controlSize)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { item ->
                when (item) {
                    is StreamBottomItem.Control -> key(item.control) {
                        when (item.control) {
                            StreamControl.SOUND -> SoundButton(uiState, onSoundClick, controlSize)
                            StreamControl.CALL -> CallButton(uiState, pulseScale, onCallClick, controlSize, callContainerSize)
                        }
                    }
                    is StreamBottomItem.Custom -> key(item.button.id) {
                        StreamActionButton(
                            button = item.button,
                            uiState = uiState,
                            onClick = { onButtonClick(item.button) },
                            circleSize = actionSize,
                            iconSize = 28.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundButton(uiState: StreamUiState, onClick: () -> Unit, size: Dp) {
    StreamCircleButton(
        onClick = onClick,
        enabled = uiState.isStreamAvailable,
        color = StreamColors.ControlBackground,
        size = size
    ) {
        Icon(
            imageVector = if (uiState.isStreamMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = stringResource(if (uiState.isStreamMuted) R.string.stream_unmute else R.string.stream_mute),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun CallButton(uiState: StreamUiState, pulseScale: Float, onClick: () -> Unit, size: Dp, containerSize: Dp) {
    Box(modifier = Modifier.size(containerSize), contentAlignment = Alignment.Center) {
        if (uiState.isMicEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .background(StreamColors.CallPulse, CircleShape)
            )
        }
        StreamCircleButton(
            onClick = onClick,
            enabled = uiState.isStreamAvailable && !uiState.isSwitchingModes,
            busy = uiState.isSwitchingModes,
            color = if (uiState.isMicEnabled) StreamColors.HangUp else StreamColors.Answer,
            size = size
        ) {
            Icon(
                imageVector = if (uiState.isMicEnabled) Icons.Default.CallEnd else Icons.Default.Call,
                contentDescription = stringResource(if (uiState.isMicEnabled) R.string.stream_end_call else R.string.stream_button_call),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun StreamCircleButton(
    onClick: () -> Unit,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    contentColor: Color = StreamColors.OnControl,
    content: @Composable () -> Unit
) {
    val dimmed = !enabled && !busy
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxSize(),
            shape = CircleShape,
            color = if (dimmed) color.copy(alpha = color.alpha * 0.5f) else color,
            contentColor = contentColor.copy(alpha = if (dimmed) 0.5f else 1f)
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = StreamColors.OnControl,
                strokeWidth = 3.dp
            )
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
    uiState: StreamUiState,
    onClick: () -> Unit,
    circleSize: Dp,
    iconSize: Dp
) {
    val lockState = uiState.lockStates[button.action.entityId] ?: LockState.Unknown
    val entityAppearance = uiState.entityAppearances[button.iconEntityId]
    val running = uiState.isRunning(button)
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
    val defaultBackground = when (displayedLockState) {
        LockState.Unlocked -> StreamColors.LockUnlocked
        LockState.Jammed -> StreamColors.LockJammed
        LockState.Locking, LockState.Unlocking -> StreamColors.LockBusy
        LockState.Unknown -> StreamColors.LockUnknown
        LockState.Locked, null -> StreamColors.ControlBackground
    }
    val stateBackground = button.backgroundForState(entityAppearance)
    val iconColor = if (stateBackground != null && stateBackground.alpha >= 0.75f &&
        stateBackground.luminance() > 0.179f
    ) Color.Black else StreamColors.OnControl
    val hasSeparateStateDescription = if (isLock) {
        usesStateEntity && button.iconEntityId != button.action.entityId
    } else {
        usesStateEntity && stateEntityLockState == null
    }
    val entityDescription = entityAppearance?.state?.takeIf { hasSeparateStateDescription && it.isNotBlank() }
    val progressDescription = if (running) stringResource(R.string.stream_button_running) else null
    val description = listOfNotNull(progressDescription, lockDescription, entityDescription).joinToString()
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

    StreamCircleButton(
        onClick = onClick,
        enabled = uiState.canRun(button),
        busy = running,
        color = stateBackground ?: defaultBackground,
        contentColor = iconColor,
        size = circleSize,
        modifier = stateSemantics
    ) {
        val modifier = Modifier.size(iconSize)
        if (resolvedIcon == null) {
            Icon(fallbackIcon, button.displayName, modifier.rotate(rotation))
        } else {
            StreamButtonIcon(resolvedIcon, button.displayName, modifier)
        }
    }
}

internal fun StreamButton.backgroundForState(appearance: HomeAssistantEntityAppearance?): Color? {
    val hex = colorForState(appearance?.state)?.removePrefix("#") ?: return null
    return Color(if (hex.length == 6) 0xFF000000L or hex.toLong(16) else hex.toLong(16))
}

private val TOP_CONTROL_SIZE = 48.dp
private const val MAX_VIDEO_ZOOM = 4f
