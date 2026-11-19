package com.novasoftware.hadoorbell.ring

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Doorbell
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.novasoftware.hadoorbell.DoorbellLaunch
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.ui.theme.HaDoorbellTheme
import com.novasoftware.hadoorbell.ui.theme.StreamColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The full-screen ring shown over the lock screen. Answering or viewing the camera asks the user to
 * unlock first, so the stream and its buttons stay behind the device lock.
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {
    @Inject
    lateinit var repository: HomeAssistantRepository

    private var request by mutableStateOf(RingRequest())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        request = RingRequest.from(intent.extras)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                DoorbellRing.closeRequests.collect { finish() }
            }
        }
        setContent {
            HaDoorbellTheme(darkTheme = true, dynamicColor = false) {
                RingScreen(
                    request = request,
                    loadSnapshot = repository::getImage,
                    onAnswer = { openStream(answer = true) },
                    onWatch = { openStream(answer = false) },
                    onDecline = {
                        DoorbellRing.silence(this)
                        finish()
                    },
                    onTimeout = ::finish
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = RingRequest.from(intent.extras)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun openStream(answer: Boolean) {
        DoorbellRing.silence(this)
        val launch = {
            startActivity(DoorbellLaunch.intent(this, answer))
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launch()
            })
        } else {
            launch()
        }
    }

    companion object {
        fun intent(context: Context, request: RingRequest): Intent =
            Intent(context, RingActivity::class.java)
                .putExtras(request.toBundle())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
internal fun RingScreen(
    request: RingRequest,
    loadSnapshot: suspend (String) -> Result<ByteArray>,
    onAnswer: () -> Unit,
    onWatch: () -> Unit,
    onDecline: () -> Unit,
    onTimeout: () -> Unit
) {
    LaunchedEffect(request) {
        delay(request.timeoutSeconds * 1_000L)
        onTimeout()
    }
    val snapshot by produceState<ImageBitmap?>(null, request.image) {
        value = request.image?.let { path ->
            loadSnapshot(path).getOrNull()?.let { bytes -> withContext(Dispatchers.Default) { decodeSnapshot(bytes) } }
        }
    }
    val title = request.title ?: stringResource(R.string.ring_default_title)
    val message = request.message ?: stringResource(R.string.ring_default_message)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        snapshot?.let { image ->
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.ring_snapshot),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.8f),
                            0.4f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Icon(
                    Icons.Default.Doorbell,
                    contentDescription = null,
                    tint = StreamColors.OnControl,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = StreamColors.OnControl,
                    textAlign = TextAlign.Center
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = StreamColors.SecondaryText,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RingAction(stringResource(R.string.ring_decline), Icons.Default.CallEnd, StreamColors.HangUp, onDecline)
                    RingAction(stringResource(R.string.ring_answer), Icons.Default.Call, StreamColors.Answer, onAnswer)
                }
                TextButton(onClick = onWatch) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = StreamColors.OnControl)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ring_watch), color = StreamColors.OnControl)
                }
            }
        }
    }
}

@Composable
private fun RingAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = color,
            contentColor = StreamColors.OnControl,
            modifier = Modifier.size(72.dp).semantics { contentDescription = label }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Text(
            label,
            color = StreamColors.OnControl,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

private const val MAX_SNAPSHOT_SIZE = 1440

private fun decodeSnapshot(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= MAX_SNAPSHOT_SIZE || bounds.outHeight / (sampleSize * 2) >= MAX_SNAPSHOT_SIZE) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
