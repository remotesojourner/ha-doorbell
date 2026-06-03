package com.novasoftware.hadoorbell.ui.stream

import android.content.Context
import com.novasoftware.hadoorbell.MainDispatcherRule
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.usecase.GetSelectOptionsUseCase
import com.novasoftware.hadoorbell.domain.usecase.LockDoorUseCase
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.usecase.SendQuickReplyUseCase
import com.novasoftware.hadoorbell.domain.usecase.UnlockDoorUseCase
import com.novasoftware.hadoorbell.data.remote.HomeAssistantWebRtcClient
import com.novasoftware.hadoorbell.core.webrtc.WebRtcClientFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var appPreferences: SettingsRepositoryImpl
    private lateinit var repository: HomeAssistantRepository
    private lateinit var webRtcClientFactory: WebRtcClientFactory
    private lateinit var webRtcManagerFactory: com.novasoftware.hadoorbell.core.webrtc.WebRtcManagerFactory
    private lateinit var lockDoorUseCase: LockDoorUseCase
    private lateinit var unlockDoorUseCase: UnlockDoorUseCase
    private lateinit var getSelectOptionsUseCase: GetSelectOptionsUseCase
    private lateinit var sendQuickReplyUseCase: SendQuickReplyUseCase
    private lateinit var viewModel: StreamViewModel

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        appPreferences = mockk(relaxed = true) {
            every { haUrlFlow } returns flowOf("https://test.url")
            every { haTokenFlow } returns flowOf("token123")
            every { streamSourceFlow } returns flowOf("camera1")
            every { quickReplyEntityIdFlow } returns flowOf("input_select.qr")
            every { lockEntityIdFlow } returns flowOf("lock.front")
            every { instantTwoWayAudioFlow } returns flowOf(false)
            every { webrtcProviderFlow } returns flowOf("webrtc")
        }
        
        repository = mockk(relaxed = true) {
            coEvery { getEntityState(any()) } returns Result.success("locked")
        }
        
        val webRtcClient = mockk<HomeAssistantWebRtcClient>(relaxed = true)
        webRtcClientFactory = mockk(relaxed = true) {
            coEvery { createClient() } returns webRtcClient
        }
        
        webRtcManagerFactory = mockk(relaxed = true) {
            every { create(any(), any(), any()) } returns mockk(relaxed = true)
        }

        lockDoorUseCase = mockk(relaxed = true)
        unlockDoorUseCase = mockk(relaxed = true)
        getSelectOptionsUseCase = mockk(relaxed = true)
        sendQuickReplyUseCase = mockk(relaxed = true)

        viewModel = StreamViewModel(
            context, appPreferences, repository, webRtcClientFactory, webRtcManagerFactory, 
            lockDoorUseCase, unlockDoorUseCase, getSelectOptionsUseCase, sendQuickReplyUseCase
        )
    }

    @Test
    fun `initializeConnection loads settings and creates WebRtcManager`() = runTest {
        viewModel.initializeConnection(context)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals("input_select.qr", uiState.quickReplyEntityId)
        assertEquals("lock.front", uiState.lockEntityId)
        assertEquals(LockState.Locked, uiState.lockState)
        assertFalse(uiState.isMicEnabled) // instantTwoWayAudio is false
    }

    @Test
    fun `toggleStreamMute toggles the mute state`() = runTest {
        assertFalse(viewModel.uiState.value.isStreamMuted)
        
        viewModel.toggleStreamMute()
        
        assertTrue(viewModel.uiState.value.isStreamMuted)
    }

    @Test
    fun `quick reply sheet toggling updates state`() = runTest {
        assertFalse(viewModel.uiState.value.showQuickReplySheet)
        
        viewModel.openQuickReplySheet()
        assertTrue(viewModel.uiState.value.showQuickReplySheet)
        
        viewModel.dismissQuickReplySheet()
        assertFalse(viewModel.uiState.value.showQuickReplySheet)
    }
}
