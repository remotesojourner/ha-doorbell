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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.domain.model.LockState
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

    val sheetState = rememberModalBottomSheetState()

    val streamUnlockDoorText = stringResource(R.string.stream_unlock_door)
    val streamAuthenticateUnlockText = stringResource(R.string.stream_authenticate_unlock)
    val streamAuthBypassedText = stringResource(R.string.stream_auth_bypassed)

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
                is UiEvent.RequestBiometricAuth -> {
                    val fragmentActivity = context as? FragmentActivity
                    if (fragmentActivity != null) {
                        val executor = androidx.core.content.ContextCompat.getMainExecutor(fragmentActivity)
                        val cryptoObject = com.novasoftware.hadoorbell.core.utils.BiometricHelper.getCryptoObject()

                        val promptInfoBuilder = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                            .setTitle(streamUnlockDoorText)
                            .setSubtitle(streamAuthenticateUnlockText)

                        if (cryptoObject != null) {
                            promptInfoBuilder.setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        } else {
                            promptInfoBuilder.setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        }
                        val promptInfo = promptInfoBuilder.build()

                        val biometricPrompt = androidx.biometric.BiometricPrompt(fragmentActivity, executor,
                            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    if (cryptoObject != null) {
                                        try {
                                            result.cryptoObject?.cipher?.doFinal("unlock".toByteArray())
                                            viewModel.executeUnlockDoor()
                                        } catch (_: Exception) {
                                            Toast.makeText(context, streamAuthBypassedText, Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        viewModel.executeUnlockDoor()
                                    }
                                }
                            })

                        if (cryptoObject != null) {
                            biometricPrompt.authenticate(promptInfo, cryptoObject)
                        } else {
                            biometricPrompt.authenticate(promptInfo)
                        }
                    }
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

        // Overlay UI (Top Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp)
                .padding(horizontal = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }

            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

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

        // Bottom Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mute Stream Button
            FloatingActionButton(
                onClick = { viewModel.toggleStreamMute() },
                modifier = Modifier.size(64.dp),
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

            // Call/End Call Button with Pulse Animation
            Box(
                modifier = Modifier.size(80.dp),
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
                    onClick = { viewModel.toggleMic(context) },
                    modifier = Modifier.size(64.dp),
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

            // Quick Reply Button
            if (uiState.quickReplyEntityId.isNotBlank()) {
                FloatingActionButton(
                    onClick = { viewModel.openQuickReplySheet() },
                    modifier = Modifier.size(64.dp),
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Quick Reply", modifier = Modifier.size(28.dp))
                }
            }

            // Lock/Unlock Button
            if (uiState.lockEntityId.isNotBlank()) {
                val (lockIcon, lockColor) = when (uiState.lockState) {
                    LockState.Unlocked -> Icons.Default.LockOpen to Color.Red.copy(alpha = 0.8f)
                    LockState.Locked -> Icons.Default.Lock to Color.White.copy(alpha = 0.2f)
                    LockState.Jammed -> Icons.Default.Warning to Color(0xFFFFA500)
                    LockState.Locking, LockState.Unlocking -> Icons.Default.Sync to Color.Gray.copy(alpha = 0.8f)
                    else -> Icons.Default.QuestionMark to Color.DarkGray.copy(alpha = 0.8f)
                }

                val infiniteLockTransition = rememberInfiniteTransition(label = "lock_sync")
                val syncRotation by infiniteLockTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "syncRotation"
                )

                FloatingActionButton(
                    onClick = {
                        viewModel.handleLockAction()
                    },
                    modifier = Modifier.size(64.dp),
                    containerColor = lockColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    val isTransitioning = uiState.lockState == LockState.Locking || uiState.lockState == LockState.Unlocking
                    Icon(
                        imageVector = lockIcon,
                        contentDescription = "Lock Status",
                        modifier = Modifier
                            .size(28.dp)
                            .then(if (isTransitioning) Modifier.rotate(syncRotation) else Modifier)
                    )
                }
            }
        }
    }

    if (uiState.showQuickReplySheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissQuickReplySheet() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.stream_quick_reply),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LaunchedEffect(Unit) {
                    viewModel.loadQuickReplyOptions()
                }

                if (uiState.quickReplyError != null) {
                    Text(
                        text = uiState.quickReplyError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else if (uiState.quickReplyOptions == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.quickReplyOptions!!.isEmpty()) {
                    Text("No quick reply options found for this entity.", modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        items(uiState.quickReplyOptions!!) { option ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.sendQuickReply(option) },
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
}
