package com.novasoftware.hadoorbell.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import com.novasoftware.hadoorbell.data.AppPreferences
import com.novasoftware.hadoorbell.integrations.HomeAssistantApiClient
import com.novasoftware.hadoorbell.integrations.FrigateSignalingClient
import com.novasoftware.hadoorbell.webrtc.WebRtcManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    appPreferences: AppPreferences,
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isMicEnabled by remember { mutableStateOf(false) }
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    var apiClient by remember { mutableStateOf<HomeAssistantApiClient?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var quickReplyEntityId by remember { mutableStateOf("") }
    var lockEntityId by remember { mutableStateOf("") }
    var lockState by remember { mutableStateOf("unknown") }
    var isStreamMuted by remember { mutableStateOf(false) }
    
    // Quick Reply State
    val showQuickReplySheet = remember { mutableStateOf(false) }
    var quickReplyOptions by remember { mutableStateOf<List<String>?>(null) }
    var quickReplyError by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Animation for the pulsing mic
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMicEnabled) 1.15f else 1f,
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
                // Initialize connections
                coroutineScope.launch {
                    val url = appPreferences.haUrlFlow.first() ?: ""
                    val token = appPreferences.haTokenFlow.first() ?: ""
                    val streamSource = appPreferences.streamSourceFlow.first() ?: ""
                    quickReplyEntityId = appPreferences.quickReplyEntityIdFlow.first() ?: ""
                    lockEntityId = appPreferences.lockEntityIdFlow.first() ?: ""

                    apiClient = HomeAssistantApiClient(url, token)
                    
                    if (lockEntityId.isNotBlank()) {
                        try {
                            lockState = apiClient?.getEntityState(lockEntityId) ?: "unknown"
                        } catch (e: Exception) {
                            lockState = "unknown"
                        }
                    }

                    val signalingClient = FrigateSignalingClient(url, token, streamSource)

                    webRtcManager = WebRtcManager(context, signalingClient, coroutineScope)
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                // Disconnect and clean up
                webRtcManager?.disconnect()
                webRtcManager = null
                isMicEnabled = false
                isStreamMuted = false
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webRtcManager?.disconnect()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Video View or Error
        if (errorMessage != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Videocam, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Text(
                    text = "Connection Failed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = errorMessage ?: "Unknown Error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            webRtcManager?.let { manager ->
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
                        
                        manager.startConnection(renderer, isMicEnabled) { error ->
                            errorMessage = error
                        }
                        
                        frameLayout
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } ?: run {
                // Sleek loading overlay
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Connecting to Doorbell...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Top Gradient Overlay (Glassmorphism effect)
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

        // Bottom Gradient Overlay (Glassmorphism effect)
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
                onClick = { 
                    isStreamMuted = !isStreamMuted
                    webRtcManager?.toggleStreamMute(isStreamMuted)
                },
                modifier = Modifier.size(64.dp),
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = if (isStreamMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isStreamMuted) "Unmute Stream" else "Mute Stream",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Call/End Call Button with Pulse Animation
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isMicEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.3f))
                    )
                }
                
                FloatingActionButton(
                    onClick = {
                        isMicEnabled = !isMicEnabled
                        
                        coroutineScope.launch {
                            webRtcManager?.disconnect()
                            webRtcManager = null
                            kotlinx.coroutines.delay(600)
                            
                            val url = appPreferences.haUrlFlow.first() ?: ""
                            val token = appPreferences.haTokenFlow.first() ?: ""
                            val streamSource = appPreferences.streamSourceFlow.first() ?: ""
                            
                            val signalingClient = FrigateSignalingClient(url, token, streamSource)
                            webRtcManager = WebRtcManager(context, signalingClient, coroutineScope)
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    containerColor = if (isMicEnabled) Color.Red else Color(0xFF4CAF50),
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = if (isMicEnabled) Icons.Default.CallEnd else Icons.Default.Call,
                        contentDescription = if (isMicEnabled) "End Call" else "Call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Quick Reply Button
            if (quickReplyEntityId.isNotBlank()) {
                FloatingActionButton(
                    onClick = { showQuickReplySheet.value = true },
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
            if (lockEntityId.isNotBlank()) {
                val (lockIcon, lockColor) = when (lockState) {
                    "unlocked", "open" -> Icons.Default.LockOpen to Color.Red.copy(alpha = 0.8f)
                    "locked" -> Icons.Default.Lock to Color.White.copy(alpha = 0.2f)
                    "jammed" -> Icons.Default.Warning to Color(0xFFFFA500)
                    "locking", "unlocking", "opening" -> Icons.Default.Sync to Color.Gray.copy(alpha = 0.8f)
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
                        if (lockState == "jammed" || lockState == "unavailable" || lockState == "unknown") {
                            Toast.makeText(context, "Cannot operate lock in current state: $lockState", Toast.LENGTH_SHORT).show()
                            return@FloatingActionButton
                        }

                        val fragmentActivity = context as? FragmentActivity
                        if (fragmentActivity != null) {
                            val executor = ContextCompat.getMainExecutor(context)
                            val targetState = if (lockState == "unlocked" || lockState == "open" || lockState == "opening" || lockState == "unlocking") "lock" else "unlock"

                            val executeLockAction = {
                                coroutineScope.launch {
                                    try {
                                        // Re-fetch state just before action
                                        val preState = apiClient?.getEntityState(lockEntityId) ?: "unknown"
                                        lockState = preState
                                        val desiredIsLocked = targetState == "lock"
                                        val currentlyIsLocked = preState == "locked"
                                        val currentlyIsUnlocked = preState == "unlocked" || preState == "open"
                                        
                                        if ((desiredIsLocked && currentlyIsLocked) || (!desiredIsLocked && currentlyIsUnlocked)) {
                                            Toast.makeText(context, "Door is already ${if(desiredIsLocked) "locked" else "unlocked"}", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }

                                        // Call service
                                        apiClient?.callService("lock", targetState, lockEntityId)
                                        lockState = if (targetState == "unlock") "unlocking" else "locking"
                                        
                                        // Start Polling Loop
                                        val startTime = System.currentTimeMillis()
                                        val timeout = 30000L // 30 seconds
                                        while (System.currentTimeMillis() - startTime < timeout) {
                                            kotlinx.coroutines.delay(1500)
                                            val currentState = apiClient?.getEntityState(lockEntityId) ?: "unknown"
                                            lockState = currentState
                                            
                                            if (currentState == "locked" || currentState == "unlocked" || currentState == "jammed" || currentState == "open" || currentState == "unavailable" || currentState == "unknown") {
                                                break
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Lock action failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        lockState = "unknown"
                                    }
                                }
                            }

                            if (targetState == "lock") {
                                // Instantly lock without biometrics
                                executeLockAction()
                            } else {
                                // Require biometrics for unlocking
                                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Unlock Door")
                                    .setSubtitle("Authenticate to unlock")
                                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                    .build()

                                val biometricPrompt = BiometricPrompt(fragmentActivity, executor,
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            super.onAuthenticationSucceeded(result)
                                            executeLockAction()
                                        }
                                    })
                                biometricPrompt.authenticate(promptInfo)
                            }
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    containerColor = lockColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                ) {
                    val isTransitioning = lockState == "locking" || lockState == "unlocking" || lockState == "opening"
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

    if (showQuickReplySheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showQuickReplySheet.value = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Quick Reply",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LaunchedEffect(Unit) {
                    try {
                        quickReplyError = null
                        quickReplyOptions = apiClient?.getSelectOptions(quickReplyEntityId)
                    } catch (e: Exception) {
                        quickReplyError = "Failed to load options: ${e.message}"
                    }
                }

                if (quickReplyError != null) {
                    Text(
                        text = quickReplyError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else if (quickReplyOptions == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (quickReplyOptions!!.isEmpty()) {
                    Text("No quick reply options found for this entity.", modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        items(quickReplyOptions!!) { option ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            try {
                                                apiClient?.setSelectOption(quickReplyEntityId, option)
                                                Toast.makeText(context, "Sent: $option", Toast.LENGTH_SHORT).show()
                                                showQuickReplySheet.value = false
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
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
