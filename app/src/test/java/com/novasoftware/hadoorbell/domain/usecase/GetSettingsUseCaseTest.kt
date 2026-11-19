package com.novasoftware.hadoorbell.domain.usecase

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.CallActions
import com.novasoftware.hadoorbell.domain.model.CallServiceSettings
import com.novasoftware.hadoorbell.domain.model.HomeAssistantConnection
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetSettingsUseCaseTest {

    private lateinit var repository: SettingsRepository
    private lateinit var getSettingsUseCase: GetSettingsUseCase
    private val answerData = JsonObject().apply { addProperty("message", "Hello") }
    private val hangupData = JsonObject().apply { addProperty("message", "Goodbye") }

    @Before
    fun setup() {
        repository = mockk {
            every { homeAssistantConnectionFlow } returns
                flowOf(HomeAssistantConnection("https://home.local", "token_abc"))
            every { streamSourceFlow } returns flowOf("front_cam")
            every { instantTwoWayAudioFlow } returns flowOf(false)
            every { webrtcProviderFlow } returns flowOf("webrtc")
            every { callActionsFlow } returns flowOf(
                CallActions(
                    answer = CallServiceSettings("switch.turn_on", "switch.doorbell_talk", answerData),
                    hangup = CallServiceSettings("switch.turn_off", "switch.doorbell_talk", hangupData, true)
                )
            )
            every { streamButtonLayoutFlow } returns flowOf(StreamButtonLayout())
        }
        getSettingsUseCase = GetSettingsUseCase(repository)
    }

    @Test
    fun `invoke combines all flows into Settings model`() = runTest {
        val result = getSettingsUseCase().first()

        assertEquals("https://home.local", result.url)
        assertEquals("token_abc", result.token)
        assertEquals("front_cam", result.streamSource)
        assertFalse(result.instantTwoWayAudio)
        assertEquals("webrtc", result.webrtcProvider)
        assertEquals("switch.turn_on", result.answerActionService)
        assertEquals("switch.doorbell_talk", result.answerActionEntityId)
        assertEquals("switch.turn_off", result.hangupActionService)
        assertEquals("switch.doorbell_talk", result.hangupActionEntityId)
        assertEquals(answerData, result.answerActionData)
        assertEquals(hangupData, result.hangupActionData)
        assertFalse(result.answerActionNeedsResponse)
        assertTrue(result.hangupActionNeedsResponse)
        assertEquals(StreamButtonLayout(), result.streamButtonLayout)
    }
}
