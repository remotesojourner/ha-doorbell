package com.novasoftware.hadoorbell.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.CallActions
import com.novasoftware.hadoorbell.domain.model.CallServiceSettings
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
            callActions = CallActions(answer = CallServiceSettings("switch.turn_on")),
            streamButtonLayout = layout
        )
        appPreferences.saveHomeAssistantConnection("  192.168.1.100:8123/  ", " my_token ")

        val connection = HomeAssistantConnection("https://192.168.1.100:8123", "my_token")
        assertEquals(connection, appPreferences.homeAssistantConnectionFlow.first())
        assertEquals("camera_1", appPreferences.streamSourceFlow.first())
        assertEquals(true, appPreferences.instantTwoWayAudioFlow.first())
        assertEquals("switch.turn_on", appPreferences.callActionsFlow.first().answer.service)
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
        val answerData = JsonObject().apply { addProperty("value", 25) }
        val hangupData = JsonObject().apply { addProperty("value", 0) }
        appPreferences.saveSettings(
            source = "camera",
            instantTwoWayAudio = false,
            provider = "frigate",
            callActions = CallActions(
                answer = CallServiceSettings(" light.turn_on ", " light.doorbell ", answerData),
                hangup = CallServiceSettings(" light.turn_off ", " light.doorbell ", hangupData, true)
            )
        )

        assertEquals(
            CallActions(
                answer = CallServiceSettings("light.turn_on", "light.doorbell", answerData),
                hangup = CallServiceSettings("light.turn_off", "light.doorbell", hangupData, true)
            ),
            appPreferences.callActionsFlow.first()
        )
    }

    @Test
    fun `existing call services and entities load with empty service data`() = runTest {
        context.dataStore.edit {
            it[SettingsRepositoryImpl.ANSWER_ACTION_SERVICE] = "switch.turn_on"
            it[SettingsRepositoryImpl.ANSWER_ACTION_ENTITY_ID] = "switch.doorbell"
            it[SettingsRepositoryImpl.HANGUP_ACTION_SERVICE] = "switch.turn_off"
            it[SettingsRepositoryImpl.HANGUP_ACTION_ENTITY_ID] = "switch.doorbell"
        }

        assertEquals(
            CallActions(
                answer = CallServiceSettings("switch.turn_on", "switch.doorbell"),
                hangup = CallServiceSettings("switch.turn_off", "switch.doorbell")
            ),
            appPreferences.callActionsFlow.first()
        )
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
    fun `saved layouts keep the sound and call positions`() = runTest {
        val chime = StreamButton("chime", StreamButtonAction.CallService("button", "press", "button.chime"))
        val layout = StreamButtonLayout(bottom = listOf(chime), soundPosition = 2, callPosition = 0)

        appPreferences.saveSettings(source = "camera", streamButtonLayout = layout)

        assertEquals(layout, appPreferences.streamButtonLayoutFlow.first())
    }

    @Test
    fun `layouts saved before controls were reorderable keep sound and call first`() {
        val json = """{"buttonVisibility":true,"top":[],"bottom":[{"id":"chime","requiresAuthentication":false,""" +
            """"visible":true,"action":{"type":"callService","entityId":"button.chime","domain":"button","service":"press"}}]}"""

        val layout = StreamButtonLayoutJson.decode(json)

        assertEquals(StreamButtonLayout.DEFAULT_SOUND_POSITION, layout?.soundPosition)
        assertEquals(StreamButtonLayout.DEFAULT_CALL_POSITION, layout?.callPosition)
        assertEquals("chime", layout?.bottom?.single()?.id)
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
