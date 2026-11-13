package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class GetSettingsUseCaseTest {

    private lateinit var repository: SettingsRepository
    private lateinit var getSettingsUseCase: GetSettingsUseCase

    @Before
    fun setup() {
        repository = mockk {
            every { haUrlFlow } returns flowOf("https://home.local")
            every { haTokenFlow } returns flowOf("token_abc")
            every { streamSourceFlow } returns flowOf("front_cam")
            every { quickReplyEntityIdFlow } returns flowOf("select.qr")
            every { lockEntityIdFlow } returns flowOf("lock.front")
            every { instantTwoWayAudioFlow } returns flowOf(false)
            every { webrtcProviderFlow } returns flowOf("webrtc")
            every { doorEntityIdFlow } returns flowOf("switch.door_relay")
            every { doorServiceFlow } returns flowOf("switch.turn_on")
            every { answerActionServiceFlow } returns flowOf("switch.turn_on")
            every { answerActionEntityIdFlow } returns flowOf("switch.doorbell_talk")
            every { hangupActionServiceFlow } returns flowOf("switch.turn_off")
            every { hangupActionEntityIdFlow } returns flowOf("switch.doorbell_talk")
        }
        getSettingsUseCase = GetSettingsUseCase(repository)
    }

    @Test
    fun `invoke combines all flows into Settings model`() = runTest {
        val result = getSettingsUseCase().first()

        assertEquals("https://home.local", result.url)
        assertEquals("token_abc", result.token)
        assertEquals("front_cam", result.streamSource)
        assertEquals("select.qr", result.quickReplyEntityId)
        assertEquals("lock.front", result.lockEntityId)
        assertFalse(result.instantTwoWayAudio)
        assertEquals("webrtc", result.webrtcProvider)
        assertEquals("switch.door_relay", result.doorEntityId)
        assertEquals("switch.turn_on", result.doorService)
        assertEquals("switch.turn_on", result.answerActionService)
        assertEquals("switch.doorbell_talk", result.answerActionEntityId)
        assertEquals("switch.turn_off", result.hangupActionService)
        assertEquals("switch.doorbell_talk", result.hangupActionEntityId)
    }
}
