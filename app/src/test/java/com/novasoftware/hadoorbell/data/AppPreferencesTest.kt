package com.novasoftware.hadoorbell.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest=Config.NONE)
class AppPreferencesTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appPreferences = AppPreferences(context)
    }

    @Test
    fun `saveSettings sanitizes URL and saves preferences`() = runTest {
        // Given
        val url = "  192.168.1.100:8123/  "
        val token = " my_token "
        val source = " camera_1 "
        val quickReply = " select.doorbell "
        val lock = " lock.front "
        val instantTwoWayAudio = true

        // When
        appPreferences.saveSettings(url, token, source, quickReply, lock, instantTwoWayAudio)

        // Then
        assertEquals("https://192.168.1.100:8123", appPreferences.haUrlFlow.first())
        assertEquals("my_token", appPreferences.haTokenFlow.first())
        assertEquals("camera_1", appPreferences.streamSourceFlow.first())
        assertEquals("select.doorbell", appPreferences.quickReplyEntityIdFlow.first())
        assertEquals("lock.front", appPreferences.lockEntityIdFlow.first())
        assertEquals(true, appPreferences.instantTwoWayAudioFlow.first())
    }

    @Test
    fun `saveSettings does not prepend https if http is present`() = runTest {
        // Given
        val url = "http://192.168.1.100:8123"

        // When
        appPreferences.saveSettings(url, "", "", "", "", false)

        // Then
        assertEquals("http://192.168.1.100:8123", appPreferences.haUrlFlow.first())
    }
}
