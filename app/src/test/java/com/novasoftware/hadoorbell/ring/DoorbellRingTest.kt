package com.novasoftware.hadoorbell.ring

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class DoorbellRingTest {
    private lateinit var application: Application
    private lateinit var notifications: NotificationManager

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        notifications = application.getSystemService(NotificationManager::class.java)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `ring extras accept companion types and keep snapshots on the Home Assistant server`() {
        val request = RingRequest.from(
            extras("title" to " Front door ", "timeout" to "30", "image" to "/api/camera_proxy/camera.front")
        )
        assertEquals("Front door", request.title)
        assertEquals(30, request.timeoutSeconds)
        assertEquals("/api/camera_proxy/camera.front", request.image)

        assertNull(RingRequest.from(extras("image" to "https://example.com/snap.jpg")).image)
        assertNull(RingRequest.from(extras("image" to "//example.com/snap.jpg")).image)
        assertEquals(RingRequest.MAX_TIMEOUT_SECONDS, RingRequest.from(extras("timeout" to 9_999)).timeoutSeconds)
        assertEquals(RingRequest.MIN_TIMEOUT_SECONDS, RingRequest.from(extras("timeout" to 0)).timeoutSeconds)
        assertEquals(RingRequest.DEFAULT_TIMEOUT_SECONDS, RingRequest.from(null).timeoutSeconds)
    }

    @Test
    fun `the ring broadcast posts an insistent call notification that cancel removes`() {
        val receiver = RingReceiver()
        receiver.onReceive(
            application,
            Intent(DoorbellRing.ACTION_RING).putExtras(extras("title" to "Front door", "timeout" to 20))
        )

        val ring = shadowOf(notifications).getNotification(DoorbellRing.NOTIFICATION_ID)
        assertEquals(Notification.CATEGORY_CALL, ring.category)
        assertTrue(ring.flags and Notification.FLAG_INSISTENT != 0)
        assertTrue(ring.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(20_000L, ring.timeoutAfter)
        assertEquals("Front door", ring.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(DoorbellRing.CHANNEL_ID, ring.channelId)

        receiver.onReceive(application, Intent(DoorbellRing.ACTION_CANCEL_RING))
        assertNull(shadowOf(notifications).getNotification(DoorbellRing.NOTIFICATION_ID))
    }

    private fun extras(vararg values: Pair<String, Any>): Bundle = Bundle().apply {
        values.forEach { (key, value) ->
            when (value) {
                is Int -> putInt(key, value)
                else -> putString(key, value.toString())
            }
        }
    }

    @Test
    fun `nothing rings without notification permission`() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(DoorbellRing.show(application, RingRequest()))
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }
}
