package com.novasoftware.hadoorbell.ring

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.novasoftware.hadoorbell.DoorbellLaunch
import com.novasoftware.hadoorbell.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Rings the phone like an incoming call when Home Assistant reports a doorbell press. */
object DoorbellRing {
    const val ACTION_RING = "com.novasoftware.hadoorbell.RING"
    const val ACTION_CANCEL_RING = "com.novasoftware.hadoorbell.CANCEL_RING"
    const val CHANNEL_ID = "doorbell_ring"
    const val NOTIFICATION_ID = 1001

    private const val REQUEST_OPEN = 1
    private const val REQUEST_ANSWER = 2
    private const val REQUEST_DECLINE = 3
    private const val REQUEST_RING_SCREEN = 4

    private val _closeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the ring was cancelled from outside the ring screen, so the screen can close. */
    val closeRequests: SharedFlow<Unit> = _closeRequests.asSharedFlow()

    fun canPostNotifications(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canUseFullScreenIntent(context: Context): Boolean =
        NotificationManagerCompat.from(context).canUseFullScreenIntent()

    /** Posts the ring notification. Returns false when the app is not allowed to post notifications. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, request: RingRequest): Boolean {
        if (!canPostNotifications(context)) return false
        ensureChannel(context)

        val title = request.title ?: context.getString(R.string.ring_default_title)
        val message = request.message ?: context.getString(R.string.ring_default_message)
        val openStream = activityIntent(context, REQUEST_OPEN, DoorbellLaunch.intent(context, answer = false))
        val answer = activityIntent(context, REQUEST_ANSWER, DoorbellLaunch.intent(context, answer = true))
        val decline = PendingIntent.getBroadcast(
            context,
            REQUEST_DECLINE,
            Intent(context, RingReceiver::class.java).setAction(ACTION_CANCEL_RING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_doorbell)
            .setContentTitle(title)
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(openStream)
            .setTimeoutAfter(request.timeoutSeconds * 1_000L)

        if (canUseFullScreenIntent(context)) {
            val ringScreen = activityIntent(context, REQUEST_RING_SCREEN, RingActivity.intent(context, request))
            val caller = Person.Builder().setName(title).setImportant(true).build()
            builder.setFullScreenIntent(ringScreen, true)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
        } else {
            builder.addAction(0, context.getString(R.string.ring_decline), decline)
                .addAction(0, context.getString(R.string.ring_answer), answer)
        }

        val notification = builder.build().apply { flags = flags or Notification.FLAG_INSISTENT }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    /** Stops the ring without closing the ring screen, for example while the user unlocks the phone. */
    fun silence(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun cancel(context: Context) {
        silence(context)
        _closeRequests.tryEmit(Unit)
    }

    private fun activityIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun ensureChannel(context: Context) {
        val ringtone = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.ring_channel_name))
            .setDescription(context.getString(R.string.ring_channel_description))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), ringtone)
            .setVibrationEnabled(true)
            .setVibrationPattern(longArrayOf(0, 1_000, 800, 1_000, 800))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}
