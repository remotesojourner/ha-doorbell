package com.novasoftware.hadoorbell.ui.settings

import com.novasoftware.hadoorbell.MainDispatcherRule
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import com.novasoftware.hadoorbell.domain.model.Settings
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getSettingsUseCase: GetSettingsUseCase
    private lateinit var saveSettingsUseCase: SaveSettingsUseCase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        val initialSettings = Settings(
            url = "https://test.url",
            token = "token123",
            streamSource = "camera1",
            quickReplyEntityId = "input_select.qr",
            lockEntityId = "lock.front",
            instantTwoWayAudio = false,
            webrtcProvider = "webrtc"
        )
        getSettingsUseCase = mockk {
            every { this@mockk.invoke() } returns flowOf(initialSettings)
        }
        saveSettingsUseCase = mockk(relaxed = true)

        viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase)
    }

    @Test
    fun `initialization loads values from use case`() = runTest {
        assertTrue(viewModel.uiState.value.isLoaded)
        assertEquals("https://test.url", viewModel.uiState.value.url)
        assertEquals("token123", viewModel.uiState.value.token)
        assertEquals("camera1", viewModel.uiState.value.streamSource)
        assertEquals("input_select.qr", viewModel.uiState.value.quickReplyEntityId)
        assertEquals("lock.front", viewModel.uiState.value.lockEntityId)
        assertFalse(viewModel.uiState.value.instantTwoWayAudio)
        assertEquals("webrtc", viewModel.uiState.value.webrtcProvider)
    }

    @Test
    fun `updateUrl updates state`() {
        viewModel.updateUrl("https://new.url")
        assertEquals("https://new.url", viewModel.uiState.value.url)
    }

    @Test
    fun `updateQuickReplyEntityId disables instant two way audio if not blank`() {
        viewModel.updateInstantTwoWayAudio(true)
        assertTrue(viewModel.uiState.value.instantTwoWayAudio)
        
        viewModel.updateQuickReplyEntityId("new.entity")
        
        assertEquals("new.entity", viewModel.uiState.value.quickReplyEntityId)
        assertFalse(viewModel.uiState.value.instantTwoWayAudio)
    }

    @Test
    fun `saveSettings calls use case and onComplete callback`() = runTest {
        var onCompleteCalled = false
        val onComplete = { onCompleteCalled = true }

        viewModel.updateUrl("https://updated.url")
        
        viewModel.saveSettings(onComplete)

        coVerify { 
            saveSettingsUseCase(
                Settings(
                    url = "https://updated.url",
                    token = "token123",
                    streamSource = "camera1",
                    quickReplyEntityId = "input_select.qr",
                    lockEntityId = "lock.front",
                    instantTwoWayAudio = false,
                    webrtcProvider = "webrtc"
                )
            )
        }
        assertTrue(onCompleteCalled)
    }
}
