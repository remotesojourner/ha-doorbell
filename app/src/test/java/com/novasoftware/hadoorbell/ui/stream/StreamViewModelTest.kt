package com.novasoftware.hadoorbell.ui.stream

import android.content.Context
import com.novasoftware.hadoorbell.MainDispatcherRule
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.usecase.CallActionUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSelectOptionsUseCase
import com.novasoftware.hadoorbell.domain.usecase.LockDoorUseCase
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.usecase.OpenDoorUseCase
import com.novasoftware.hadoorbell.domain.usecase.SendQuickReplyUseCase
import com.novasoftware.hadoorbell.domain.usecase.UnlockDoorUseCase
import com.novasoftware.hadoorbell.data.remote.HomeAssistantWebRtcClient
import com.novasoftware.hadoorbell.core.webrtc.WebRtcClientFactory
import com.novasoftware.hadoorbell.core.webrtc.WebRtcManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.webrtc.VideoSink

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
    private lateinit var openDoorUseCase: OpenDoorUseCase
    private lateinit var callActionUseCase: CallActionUseCase
    private lateinit var webRtcManager: WebRtcManager
    private lateinit var videoSink: VideoSink
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
            every { doorEntityIdFlow } returns flowOf("")
            every { doorServiceFlow } returns flowOf("")
            every { answerActionServiceFlow } returns flowOf("")
            every { answerActionEntityIdFlow } returns flowOf("")
            every { hangupActionServiceFlow } returns flowOf("")
            every { hangupActionEntityIdFlow } returns flowOf("")
        }
        
        repository = mockk(relaxed = true) {
            coEvery { getEntityState(any()) } returns Result.success("locked")
            coEvery { callService(any(), any(), any()) } returns Result.success(Unit)
        }
        
        val webRtcClient = mockk<HomeAssistantWebRtcClient>(relaxed = true)
        webRtcClientFactory = mockk(relaxed = true) {
            coEvery { createClient() } returns webRtcClient
        }

        webRtcManager = mockk(relaxed = true) {
            every { disconnect() } returns mockk<Job>(relaxed = true)
        }
        videoSink = mockk(relaxed = true)

        webRtcManagerFactory = mockk(relaxed = true) {
            every { create(any(), any(), any()) } returns webRtcManager
        }

        lockDoorUseCase = mockk(relaxed = true)
        unlockDoorUseCase = mockk(relaxed = true)
        getSelectOptionsUseCase = mockk(relaxed = true)
        sendQuickReplyUseCase = mockk(relaxed = true)
        openDoorUseCase = mockk(relaxed = true)
        callActionUseCase = CallActionUseCase(repository)

        viewModel = StreamViewModel(
            context, appPreferences, repository, webRtcClientFactory, webRtcManagerFactory,
            lockDoorUseCase, unlockDoorUseCase, getSelectOptionsUseCase, sendQuickReplyUseCase,
            openDoorUseCase, callActionUseCase
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
    fun `initializeConnection loads door and call action settings`() = runTest {
        every { appPreferences.doorEntityIdFlow } returns flowOf("switch.door_relay")
        every { appPreferences.doorServiceFlow } returns flowOf("switch.turn_on")
        every { appPreferences.answerActionServiceFlow } returns flowOf("switch.turn_on")
        every { appPreferences.answerActionEntityIdFlow } returns flowOf("switch.doorbell_talk")
        every { appPreferences.hangupActionServiceFlow } returns flowOf("switch.turn_off")
        every { appPreferences.hangupActionEntityIdFlow } returns flowOf("switch.doorbell_talk")

        viewModel.initializeConnection(context)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals("switch.door_relay", uiState.doorEntityId)
        assertEquals("switch.turn_on", uiState.doorService)
        assertEquals("switch.turn_on", uiState.answerActionService)
        assertEquals("switch.doorbell_talk", uiState.answerActionEntityId)
        assertEquals("switch.turn_off", uiState.hangupActionService)
        assertEquals("switch.doorbell_talk", uiState.hangupActionEntityId)
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

    @Test
    fun `answering without a configured action keeps the local only microphone behavior`() = runTest {
        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)

        viewModel.toggleMic(context)
        advanceTimeBy(200)
        runCurrent()
        viewModel.startStream(videoSink)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMicEnabled)
        coVerify(exactly = 0) { repository.callService(any(), any(), any()) }
        verify { webRtcManager.startConnection(any(), true, any()) }
    }

    @Test
    fun `webRTC starts once per mode switch and only when the renderer attaches`() = runTest {
        val firstManager = mockk<WebRtcManager>(relaxed = true) {
            every { disconnect() } returns mockk<Job>(relaxed = true)
        }
        val secondManager = mockk<WebRtcManager>(relaxed = true) {
            every { disconnect() } returns mockk<Job>(relaxed = true)
        }
        every { webRtcManagerFactory.create(any(), any(), any()) } returnsMany
            listOf(firstManager, secondManager)

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)
        verify(exactly = 1) { firstManager.startConnection(any(), false, any()) }

        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()

        verify(exactly = 0) { secondManager.startConnection(any(), any(), any()) }

        viewModel.startStream(videoSink)
        advanceUntilIdle()

        verify(exactly = 1) { secondManager.startConnection(any(), true, any()) }
    }

    @Test
    fun `answer action runs only after the WebRTC connection is ready`() = runTest {
        every { appPreferences.answerActionServiceFlow } returns flowOf("switch.turn_on")
        every { appPreferences.answerActionEntityIdFlow } returns flowOf("switch.doorbell_talk")

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)

        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()

        coVerify(exactly = 0) { repository.callService(any(), any(), any()) }

        viewModel.startStream(videoSink)
        advanceUntilIdle()

        coVerifyOrder {
            webRtcManager.startConnection(any(), true, any())
            repository.callService("switch", "turn_on", "switch.doorbell_talk")
        }
    }

    @Test
    fun `answer action is not sent when the screen is left during the restart`() = runTest {
        every { appPreferences.answerActionServiceFlow } returns flowOf("switch.turn_on")
        every { appPreferences.answerActionEntityIdFlow } returns flowOf("switch.doorbell_talk")

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)

        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()

        viewModel.teardownConnection()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.callService(any(), any(), any()) }
    }

    @Test
    fun `hang up action is sent when leaving an active call`() = runTest {
        every { appPreferences.answerActionServiceFlow } returns flowOf("switch.turn_on")
        every { appPreferences.answerActionEntityIdFlow } returns flowOf("switch.doorbell_talk")
        every { appPreferences.hangupActionServiceFlow } returns flowOf("switch.turn_off")
        every { appPreferences.hangupActionEntityIdFlow } returns flowOf("switch.doorbell_talk")

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)

        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()
        viewModel.startStream(videoSink)
        advanceUntilIdle()

        viewModel.teardownConnection()
        advanceUntilIdle()

        coVerify { repository.callService("switch", "turn_off", "switch.doorbell_talk") }
    }

    @Test
    fun `ending the call mutes the microphone immediately`() = runTest {
        every { appPreferences.answerActionServiceFlow } returns flowOf("switch.turn_on")
        every { appPreferences.answerActionEntityIdFlow } returns flowOf("switch.doorbell_talk")
        every { appPreferences.hangupActionServiceFlow } returns flowOf("switch.turn_off")
        every { appPreferences.hangupActionEntityIdFlow } returns flowOf("switch.doorbell_talk")

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)
        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()
        viewModel.startStream(videoSink)
        advanceUntilIdle()

        viewModel.hangUpCall(context)

        verify(exactly = 1) { webRtcManager.setMicrophoneEnabled(false) }
        coVerify { repository.callService("switch", "turn_off", "switch.doorbell_talk") }
        assertFalse(viewModel.uiState.value.isMicEnabled)
    }

    @Test
    fun `hang up action runs before the audio session is torn down`() = runTest {
        every { appPreferences.answerActionServiceFlow } returns flowOf("switch.turn_on")
        every { appPreferences.answerActionEntityIdFlow } returns flowOf("switch.doorbell_talk")
        every { appPreferences.hangupActionServiceFlow } returns flowOf("switch.turn_off")
        every { appPreferences.hangupActionEntityIdFlow } returns flowOf("switch.doorbell_talk")

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)
        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()
        viewModel.startStream(videoSink)
        advanceUntilIdle()

        viewModel.hangUpCall(context)
        advanceUntilIdle()

        coVerifyOrder {
            webRtcManager.setMicrophoneEnabled(false)
            repository.callService("switch", "turn_off", "switch.doorbell_talk")
            webRtcManager.disconnect()
        }
        assertFalse(viewModel.uiState.value.isMicEnabled)
    }

    @Test
    fun `answer action failures are reported to the user`() = runTest {
        every { appPreferences.answerActionServiceFlow } returns flowOf("switch.turn_on")
        every { appPreferences.answerActionEntityIdFlow } returns flowOf("switch.doorbell_talk")
        coEvery { repository.callService("switch", "turn_on", "switch.doorbell_talk") } returns
            Result.failure(RuntimeException("service unavailable"))

        val events = mutableListOf<UiEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { events.add(it) }
        }

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)
        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()
        viewModel.startStream(videoSink)
        advanceUntilIdle()
        collector.cancel()

        assertTrue(events.any { it is UiEvent.ShowToast && it.message.contains("Answer action failed") })
    }

    @Test
    fun `a call that never connects is cancelled and disconnected after the timeout`() = runTest {
        val pendingConnection = Job()
        every { webRtcManager.startConnection(any(), any(), any()) } returns pendingConnection

        val events = mutableListOf<UiEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { events.add(it) }
        }

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)

        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()
        viewModel.startStream(videoSink)
        runCurrent()

        advanceUntilIdle()

        assertTrue(pendingConnection.isCancelled)
        verify { webRtcManager.setMicrophoneEnabled(false) }
        verify(atLeast = 1) { webRtcManager.disconnect() }
        assertFalse(viewModel.uiState.value.isMicEnabled)
        coVerify(exactly = 0) { repository.callService(any(), any(), any()) }
        assertTrue(events.any { it is UiEvent.ShowToast && it.message.contains("no connection") })
        collector.cancel()
    }

    @Test
    fun `door action requests biometric authentication before opening`() = runTest {
        every { appPreferences.doorEntityIdFlow } returns flowOf("switch.door_relay")

        val events = mutableListOf<UiEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { events.add(it) }
        }

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.handleDoorAction()
        advanceUntilIdle()
        collector.cancel()

        assertTrue(events.any { it is UiEvent.RequestBiometricAuth && it.forDoor })
        coVerify(exactly = 0) { openDoorUseCase(any(), any()) }
    }

    @Test
    fun `open door failures are reported to the user`() = runTest {
        every { appPreferences.doorEntityIdFlow } returns flowOf("switch.door_relay")
        coEvery { openDoorUseCase(any(), any()) } returns Result.failure(RuntimeException("relay offline"))

        val events = mutableListOf<UiEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { events.add(it) }
        }

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.executeOpenDoor()
        advanceUntilIdle()
        collector.cancel()

        assertTrue(events.any { it is UiEvent.ShowToast && it.message.contains("Open door failed") })
    }
}
