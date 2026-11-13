package com.novasoftware.hadoorbell.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
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
        val instantTwoWayAudio = true

        // When
        appPreferences.saveSettings(url, token, source, instantTwoWayAudio)

        // Then
        assertEquals("https://192.168.1.100:8123", appPreferences.haUrlFlow.first())
        assertEquals("my_token", appPreferences.haTokenFlow.first())
        assertEquals("camera_1", appPreferences.streamSourceFlow.first())
        assertEquals(true, appPreferences.instantTwoWayAudioFlow.first())
    }

    @Test
    fun `saveSettings replaces http with https`() = runTest {
        // Given
        val url = "http://192.168.1.100:8123"

        // When
        appPreferences.saveSettings(url, "", "")

        // Then
        assertEquals("https://192.168.1.100:8123", appPreferences.haUrlFlow.first())
    }

    @Test
    fun `saveSettings stores call actions`() = runTest {
        appPreferences.saveSettings(
            url = "https://home.local",
            token = "token",
            source = "camera",
            instantTwoWayAudio = false,
            provider = "frigate",
            answerActionService = " switch.turn_on ",
            answerActionEntityId = " switch.doorbell_talk ",
            hangupActionService = " switch.turn_off ",
            hangupActionEntityId = " switch.doorbell_talk "
        )

        assertEquals("switch.turn_on", appPreferences.answerActionServiceFlow.first())
        assertEquals("switch.doorbell_talk", appPreferences.answerActionEntityIdFlow.first())
        assertEquals("switch.turn_off", appPreferences.hangupActionServiceFlow.first())
        assertEquals("switch.doorbell_talk", appPreferences.hangupActionEntityIdFlow.first())
    }

    @Test
    fun `settings without a saved layout start with empty rows`() = runTest {
        assertEquals(StreamButtonLayout(), appPreferences.streamButtonLayoutFlow.first())
    }

    @Test
    fun `saved layouts preserve row order protected actions and icons`() = runTest {
        val unlock = StreamButton(
            "unlock", "Unlock", StreamButtonAction.TurnOn("switch.door_relay"),
            requiresAuthentication = true,
            icon = "mdi:gate",
            entityIcon = "mdi:toggle-switch"
        )
        val light = StreamButton(
            "light", "Porch light", StreamButtonAction.Toggle("light.porch"),
            entityIcon = "mdi:outdoor-lamp"
        )
        val reply = StreamButton(
            "reply", "Leave at door", StreamButtonAction.SelectOption("select.reply", "Leave at door")
        )
        val layout = StreamButtonLayout(top = listOf(light, unlock), bottom = listOf(reply))

        appPreferences.saveSettings(
            "https://home.local", "token", "camera",
            streamButtonLayout = layout
        )

        assertEquals(layout, appPreferences.streamButtonLayoutFlow.first())
    }

    @Test
    fun `deleting all buttons persists empty rows`() = runTest {
        val button = StreamButton("chime", "Chime", StreamButtonAction.Press("button.chime"))
        appPreferences.saveSettings(
            "https://home.local", "token", "camera",
            streamButtonLayout = StreamButtonLayout(bottom = listOf(button))
        )
        appPreferences.saveSettings("https://home.local", "token", "camera", streamButtonLayout = StreamButtonLayout())

        assertEquals(StreamButtonLayout(), appPreferences.streamButtonLayoutFlow.first())
    }
}
