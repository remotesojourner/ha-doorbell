package com.novasoftware.hadoorbell.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.ring.DoorbellRing
import com.novasoftware.hadoorbell.ring.RingRequest

@Composable
internal fun DoorbellAlertsSection() {
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(DoorbellRing.canPostNotifications(context)) }
    var fullScreenAllowed by remember { mutableStateOf(DoorbellRing.canUseFullScreenIntent(context)) }
    fun refresh() {
        notificationsAllowed = DoorbellRing.canPostNotifications(context)
        fullScreenAllowed = DoorbellRing.canUseFullScreenIntent(context)
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose {}
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
        if (!notificationsAllowed) context.startActivity(appNotificationSettings(context))
    }

    Text(
        stringResource(R.string.settings_alerts_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AlertPermissionRow(
        title = stringResource(R.string.settings_alerts_notifications),
        description = null,
        allowed = notificationsAllowed,
        onAllow = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(appNotificationSettings(context))
            }
        }
    )
    AlertPermissionRow(
        title = stringResource(R.string.settings_alerts_full_screen),
        description = stringResource(R.string.settings_alerts_full_screen_description),
        allowed = fullScreenAllowed,
        onAllow = { context.startActivity(fullScreenSettings(context)) }
    )
    val testTitle = stringResource(R.string.ring_test_title)
    val testMessage = stringResource(R.string.ring_test_message)
    OutlinedButton(
        onClick = {
            DoorbellRing.show(
                context,
                RingRequest(title = testTitle, message = testMessage, timeoutSeconds = TEST_RING_SECONDS)
            )
        },
        enabled = notificationsAllowed
    ) {
        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.settings_alerts_test))
    }
}

@Composable
private fun AlertPermissionRow(title: String, description: String?, allowed: Boolean, onAllow: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (allowed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_alerts_allowed), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            TextButton(onClick = onAllow) { Text(stringResource(R.string.settings_alerts_allow)) }
        }
    }
}

private const val TEST_RING_SECONDS = 15

private fun appNotificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun fullScreenSettings(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        appNotificationSettings(context)
    }
