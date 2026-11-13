package com.novasoftware.hadoorbell.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.HomeAssistantConnection
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
    fun `connection and stream settings save independently`() = runTest {
        val layout = StreamButtonLayout(top = listOf(
            StreamButton("chime", StreamButtonAction.CallService("button", "press", "button.chime"))
        ))
        appPreferences.saveSettings(
            source = " camera_1 ",
            instantTwoWayAudio = true,
            answerActionService = "switch.turn_on",
            streamButtonLayout = layout
        )
        appPreferences.saveHomeAssistantConnection("  192.168.1.100:8123/  ", " my_token ")

        val connection = HomeAssistantConnection("https://192.168.1.100:8123", "my_token")
        assertEquals(connection, appPreferences.homeAssistantConnectionFlow.first())
        assertEquals("camera_1", appPreferences.streamSourceFlow.first())
        assertEquals(true, appPreferences.instantTwoWayAudioFlow.first())
        assertEquals("switch.turn_on", appPreferences.answerActionServiceFlow.first())
        assertEquals(layout, appPreferences.streamButtonLayoutFlow.first())

        appPreferences.saveSettings(source = "camera_2")

        assertEquals(connection, appPreferences.homeAssistantConnectionFlow.first())
        assertEquals("camera_2", appPreferences.streamSourceFlow.first())
    }

    @Test
    fun `saving the connection replaces http with https`() = runTest {
        appPreferences.saveHomeAssistantConnection("http://192.168.1.100:8123", "token")
        assertEquals("https://192.168.1.100:8123", appPreferences.homeAssistantConnectionFlow.first().url)
    }

    @Test
    fun `saveSettings stores call actions`() = runTest {
        appPreferences.saveSettings(
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
    fun `saved layouts preserve row order protected actions and icon entities`() = runTest {
        val unlock = StreamButton(
            "unlock", StreamButtonAction.CallService("switch", "turn_on", "switch.door_relay"),
            requiresAuthentication = true,
            icon = "mdi:gate",
            entityIcon = "mdi:lock",
            entityName = "Door relay",
            stateEntityId = "lock.front_door"
        )
        val light = StreamButton(
            "light", StreamButtonAction.CallService("light", "toggle", "light.porch"),
            entityIcon = "mdi:outdoor-lamp"
        )
        val reply = StreamButton(
            "reply", StreamButtonAction.CallService(
                "select", "select_option", "select.reply",
                data = JsonObject().apply { addProperty("option", "Leave at door") }
            )
        )
        val layout = StreamButtonLayout(top = listOf(light, unlock), bottom = listOf(reply))

        appPreferences.saveSettings(
            source = "camera",
            streamButtonLayout = layout
        )

        val savedLayout = appPreferences.streamButtonLayoutFlow.first()
        assertEquals(layout, savedLayout)
        assertEquals("lock.front_door", savedLayout.top.last().iconEntityId)
        assertEquals("switch.door_relay", savedLayout.top.last().action.entityId)
    }

    @Test
    fun `deleting all buttons persists empty rows`() = runTest {
        val button = StreamButton("chime", StreamButtonAction.CallService("button", "press", "button.chime"))
        appPreferences.saveSettings(
            source = "camera",
            streamButtonLayout = StreamButtonLayout(bottom = listOf(button))
        )
        appPreferences.saveSettings(source = "camera", streamButtonLayout = StreamButtonLayout())

        assertEquals(StreamButtonLayout(), appPreferences.streamButtonLayoutFlow.first())
    }
}
