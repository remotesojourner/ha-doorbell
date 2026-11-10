package com.novasoftware.hadoorbell.data.repository

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
class SettingsRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var appPreferences: SettingsRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appPreferences = SettingsRepositoryImpl(context)
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
    fun `saveSettings replaces http with https`() = runTest {
        // Given
        val url = "http://192.168.1.100:8123"

        // When
        appPreferences.saveSettings(url, "", "", "", "", false)

        // Then
        assertEquals("https://192.168.1.100:8123", appPreferences.haUrlFlow.first())
    }

    @Test
    fun `saveSettings stores door and call actions`() = runTest {
        appPreferences.saveSettings(
            url = "https://home.local",
            token = "token",
            source = "camera",
            quickReplyEntityId = "",
            lockEntityId = "",
            instantTwoWayAudio = false,
            provider = "frigate",
            doorEntityId = " switch.door_relay ",
            doorService = " switch.turn_on ",
            answerActionService = " switch.turn_on ",
            answerActionEntityId = " switch.doorbell_talk ",
            hangupActionService = " switch.turn_off ",
            hangupActionEntityId = " switch.doorbell_talk "
        )

        assertEquals("switch.door_relay", appPreferences.doorEntityIdFlow.first())
        assertEquals("switch.turn_on", appPreferences.doorServiceFlow.first())
        assertEquals("switch.turn_on", appPreferences.answerActionServiceFlow.first())
        assertEquals("switch.doorbell_talk", appPreferences.answerActionEntityIdFlow.first())
        assertEquals("switch.turn_off", appPreferences.hangupActionServiceFlow.first())
        assertEquals("switch.doorbell_talk", appPreferences.hangupActionEntityIdFlow.first())
    }

    @Test
    fun `settings saved before the door and call actions existed keep empty defaults`() = runTest {
        appPreferences.saveSettings("https://home.local", "token", "camera", "", "lock.front", false)

        assertEquals("", appPreferences.doorEntityIdFlow.first())
        assertEquals("", appPreferences.doorServiceFlow.first())
        assertEquals("", appPreferences.answerActionServiceFlow.first())
        assertEquals("", appPreferences.hangupActionServiceFlow.first())
    }
}
