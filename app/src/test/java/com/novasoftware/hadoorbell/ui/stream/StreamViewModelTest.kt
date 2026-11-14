package com.novasoftware.hadoorbell.ui.stream

import android.content.Context
import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.CallActions
import com.novasoftware.hadoorbell.domain.model.CallServiceSettings
import com.novasoftware.hadoorbell.MainDispatcherRule
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.usecase.CallActionUseCase
import com.novasoftware.hadoorbell.domain.usecase.ExecuteStreamButtonUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSelectOptionsUseCase
import com.novasoftware.hadoorbell.domain.usecase.LockDoorUseCase
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
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
import java.io.IOException

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
    private lateinit var callActionUseCase: CallActionUseCase
    private lateinit var webRtcManager: WebRtcManager
    private lateinit var videoSink: VideoSink
    private lateinit var viewModel: StreamViewModel

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        appPreferences = mockk(relaxed = true) {
            every { streamSourceFlow } returns flowOf("camera1")
            every { instantTwoWayAudioFlow } returns flowOf(false)
            every { webrtcProviderFlow } returns flowOf("webrtc")
            every { callActionsFlow } returns flowOf(CallActions())
            every { streamButtonLayoutFlow } returns flowOf(StreamButtonLayout())
        }
        
        repository = mockk(relaxed = true) {
            every { observeEntityAppearances(any()) } returns flow { awaitCancellation() }
            coEvery { getEntityAppearance(any()) } returns Result.success(HomeAssistantEntityAppearance("locked"))
            coEvery { callService(any(), any(), any()) } returns Result.success(Unit)
            coEvery { callService(any(), any(), any(), any(), any()) } returns Result.success(Unit)
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
        callActionUseCase = CallActionUseCase(repository)
        viewModel = StreamViewModel(
            context, appPreferences, repository, webRtcClientFactory, webRtcManagerFactory,
            lockDoorUseCase, unlockDoorUseCase, getSelectOptionsUseCase, sendQuickReplyUseCase,
            callActionUseCase, ExecuteStreamButtonUseCase(repository)
        )
    }

    @Test
    fun `initializeConnection loads settings and creates WebRtcManager`() = runTest {
        viewModel.initializeConnection(context)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(StreamButtonLayout(), uiState.streamButtonLayout)
        assertEquals(webRtcManager, uiState.webRtcManager)
        assertFalse(uiState.isMicEnabled) // instantTwoWayAudio is false
    }

    @Test
    fun `automatic icons refresh once per visible entity without delaying WebRTC`() = runTest {
        val lamp = StreamButton("lamp", StreamButtonAction.CallService("light", "toggle", "light.porch"))
        val lampAgain = lamp.copy(id = "lamp-copy")
        val override = StreamButton(
            "gate", StreamButtonAction.CallService("button", "press", "button.gate"), icon = "mdi:gate"
        )
        val hidden = StreamButton(
            "hidden", StreamButtonAction.CallService("switch", "toggle", "switch.hidden"), visible = false
        )
        val noIcon = StreamButton("reply", StreamButtonAction.QuickReply("select.reply"), entityIcon = "mdi:chat")
        val event = StreamButton("event", StreamButtonAction.FireEvent("doorbell_pressed"))
        val layout = StreamButtonLayout(
            top = listOf(lamp, override, hidden),
            bottom = listOf(lampAgain, noIcon, event)
        )
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(layout)
        val response = CompletableDeferred<Result<HomeAssistantEntityAppearance>>()
        coEvery { repository.getEntityAppearance("light.porch") } coAnswers { response.await() }
        val replyAppearance = HomeAssistantEntityAppearance("Ready")
        coEvery { repository.getEntityAppearance("select.reply") } returns Result.success(replyAppearance)

        viewModel.initializeConnection(context)
        runCurrent()

        assertEquals(webRtcManager, viewModel.uiState.value.webRtcManager)
        assertEquals(mapOf("select.reply" to replyAppearance), viewModel.uiState.value.entityAppearances)
        coVerify(exactly = 1) { repository.getEntityAppearance("light.porch") }
        coVerify(exactly = 0) { repository.getEntityAppearance("button.gate") }
        coVerify(exactly = 0) { repository.getEntityAppearance("switch.hidden") }
        coVerify(exactly = 0) { repository.getEntityAppearance("") }

        val lampAppearance = HomeAssistantEntityAppearance("on", "mdi:outdoor-lamp")
        response.complete(Result.success(lampAppearance))
        advanceUntilIdle()

        assertEquals(
            mapOf("light.porch" to lampAppearance, "select.reply" to replyAppearance),
            viewModel.uiState.value.entityAppearances
        )
    }

    @Test
    fun `external changes follow the selected entity until the stream closes`() = runTest {
        val entityId = "binary_sensor.front_door"
        val button = StreamButton("gate", StreamButtonAction.RunScript("script.open_gate"), stateEntityId = entityId)
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(StreamButtonLayout(top = listOf(button)))
        val snapshots = MutableSharedFlow<Map<String, HomeAssistantEntityAppearance>>()
        var stopped = false
        every { repository.observeEntityAppearances(setOf(entityId)) } returns flow {
            try {
                snapshots.collect { emit(it) }
            } finally {
                stopped = true
            }
        }
        val stale = CompletableDeferred<Result<HomeAssistantEntityAppearance>>()
        coEvery { repository.getEntityAppearance(entityId) } coAnswers { stale.await() }

        viewModel.initializeConnection(context)
        runCurrent()
        val open = HomeAssistantEntityAppearance("on", deviceClass = "door")
        val closed = HomeAssistantEntityAppearance("off", deviceClass = "door")
        snapshots.emit(mapOf(entityId to open))
        stale.complete(Result.success(closed))
        assertEquals(open, viewModel.uiState.value.entityAppearances[entityId])

        snapshots.emit(mapOf(entityId to closed))
        assertEquals(closed, viewModel.uiState.value.entityAppearances[entityId])
        snapshots.emit(emptyMap())
        assertEquals("unavailable", viewModel.uiState.value.entityAppearances[entityId]?.state)
        coVerify(exactly = 0) { repository.callService(any(), any(), any(), any(), any()) }

        viewModel.teardownConnection()
        assertTrue(stopped)
        snapshots.emit(mapOf(entityId to open))
        assertEquals("unavailable", viewModel.uiState.value.entityAppearances[entityId]?.state)
    }

    @Test
    fun `lock state resyncs on reconnect and external lock removal makes it unknown`() = runTest {
        val entityId = "lock.front"
        val lock = StreamButton("lock", StreamButtonAction.DoorLock(entityId))
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(StreamButtonLayout(top = listOf(lock)))
        val dropConnection = CompletableDeferred<Unit>()
        val unlocked = HomeAssistantEntityAppearance("unlocked")
        val locked = HomeAssistantEntityAppearance("locked")
        val reconnect = MutableSharedFlow<Map<String, HomeAssistantEntityAppearance>>(replay = 1)
        every { repository.observeEntityAppearances(setOf(entityId)) } returnsMany listOf(
            flow {
                emit(mapOf(entityId to locked))
                dropConnection.await()
                throw IOException("Socket closed")
            },
            reconnect
        )
        var restAppearance = locked
        coEvery { repository.getEntityAppearance(entityId) } coAnswers { Result.success(restAppearance) }

        viewModel.initializeConnection(context)
        runCurrent()
        assertEquals(LockState.Locked, viewModel.uiState.value.lockStates[entityId])
        restAppearance = unlocked
        dropConnection.complete(Unit)
        runCurrent()
        assertEquals(LockState.Locked, viewModel.uiState.value.lockStates[entityId])
        advanceTimeBy(5_000)
        runCurrent()
        verify(exactly = 2) { repository.observeEntityAppearances(setOf(entityId)) }
        assertEquals(LockState.Unlocked, viewModel.uiState.value.lockStates[entityId])

        reconnect.emit(mapOf(entityId to locked))
        assertEquals(LockState.Locked, viewModel.uiState.value.lockStates[entityId])
        reconnect.emit(emptyMap())
        assertEquals(LockState.Unknown, viewModel.uiState.value.lockStates[entityId])
        viewModel.teardownConnection()
    }

    @Test
    fun `a live lock update survives an older operation response`() = runTest {
        val entityId = "lock.front"
        val button = StreamButton("front", StreamButtonAction.DoorLock(entityId))
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(StreamButtonLayout(top = listOf(button)))
        val snapshots = MutableSharedFlow<Map<String, HomeAssistantEntityAppearance>>(replay = 1)
        snapshots.emit(mapOf(entityId to HomeAssistantEntityAppearance("unlocked")))
        every { repository.observeEntityAppearances(setOf(entityId)) } returns snapshots
        val finishOperation = CompletableDeferred<Unit>()
        every { lockDoorUseCase(entityId) } returns flow {
            emit(Result.success(LockState.Locking))
            finishOperation.await()
            emit(Result.failure(IOException("Lock request failed")))
        }

        viewModel.initializeConnection(context)
        viewModel.handleStreamButton(button)
        assertEquals(LockState.Locking, viewModel.uiState.value.lockStates[entityId])

        snapshots.emit(mapOf(entityId to HomeAssistantEntityAppearance("locked")))
        finishOperation.complete(Unit)
        runCurrent()

        assertEquals(LockState.Locked, viewModel.uiState.value.lockStates[entityId])
        viewModel.teardownConnection()
    }

    @Test
    fun `confirmed lock completion updates every button while the subscription still reports locking`() = runTest {
        val entityId = "lock.front"
        val lampId = "light.porch"
        val lock = StreamButton("front", StreamButtonAction.DoorLock(entityId))
        val lamp = StreamButton("lamp", StreamButtonAction.CallService("light", "toggle", lampId))
        val script = StreamButton("script", StreamButtonAction.RunScript("script.front"), stateEntityId = entityId)
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(
            StreamButtonLayout(top = listOf(lock, lamp, script))
        )
        val unlocked = HomeAssistantEntityAppearance("unlocked")
        val locking = HomeAssistantEntityAppearance("locking")
        val locked = HomeAssistantEntityAppearance("locked")
        val lampOff = HomeAssistantEntityAppearance("off")
        val lampOn = HomeAssistantEntityAppearance("on")
        val snapshots = MutableSharedFlow<Map<String, HomeAssistantEntityAppearance>>(replay = 1)
        snapshots.emit(mapOf(entityId to unlocked, lampId to lampOff))
        every { repository.observeEntityAppearances(setOf(entityId, lampId)) } returns snapshots
        val finishOperation = CompletableDeferred<Unit>()
        every { lockDoorUseCase(entityId) } returns flow {
            emit(Result.success(LockState.Locking))
            finishOperation.await()
            emit(Result.success(LockState.Locked))
        }

        viewModel.initializeConnection(context)
        viewModel.handleStreamButton(lock)
        snapshots.emit(mapOf(entityId to locking, lampId to lampOff))
        finishOperation.complete(Unit)
        runCurrent()

        assertEquals(LockState.Locked, viewModel.uiState.value.lockStates[entityId])
        assertEquals(locked, viewModel.uiState.value.entityAppearances[script.iconEntityId])
        snapshots.emit(mapOf(entityId to locking, lampId to lampOn))
        assertEquals(lampOn, viewModel.uiState.value.entityAppearances[lampId])
        assertEquals(locked, viewModel.uiState.value.entityAppearances[entityId])

        val changedIcon = locking.copy(icon = "mdi:lock-open")
        snapshots.emit(mapOf(entityId to changedIcon, lampId to lampOn))
        assertEquals(locked.copy(icon = changedIcon.icon), viewModel.uiState.value.entityAppearances[entityId])
        assertEquals(LockState.Locked, viewModel.uiState.value.lockStates[entityId])

        snapshots.emit(mapOf(entityId to locked, lampId to lampOn))
        snapshots.emit(mapOf(entityId to unlocked, lampId to lampOn))
        assertEquals(LockState.Unlocked, viewModel.uiState.value.lockStates[entityId])
        assertEquals(unlocked, viewModel.uiState.value.entityAppearances[entityId])
        viewModel.teardownConnection()
    }

    @Test
    fun `a script can refresh its lock when the subscription has not delivered the new state`() = runTest {
        val entityId = "lock.front"
        val script = StreamButton("script", StreamButtonAction.RunScript("script.front"), stateEntityId = entityId)
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(StreamButtonLayout(top = listOf(script)))
        val unlocked = HomeAssistantEntityAppearance("unlocked")
        val locked = HomeAssistantEntityAppearance("locked")
        every { repository.observeEntityAppearances(setOf(entityId)) } returns flow {
            emit(mapOf(entityId to unlocked))
            awaitCancellation()
        }
        var current = unlocked
        coEvery { repository.getEntityAppearance(entityId) } coAnswers { Result.success(current) }

        viewModel.initializeConnection(context)
        assertEquals(unlocked, viewModel.uiState.value.entityAppearances[entityId])
        viewModel.handleStreamButton(script)
        runCurrent()
        current = locked
        advanceUntilIdle()

        assertEquals(locked, viewModel.uiState.value.entityAppearances[entityId])
        viewModel.teardownConnection()
    }

    @Test
    fun `successful actions refresh the icons of affected buttons after their state changes`() = runTest {
        val lamp = StreamButton("lamp", StreamButtonAction.CallService("light", "toggle", "light.porch"))
        val script = StreamButton("script", StreamButtonAction.RunScript("script.welcome"))
        every { appPreferences.streamButtonLayoutFlow } returns
            flowOf(StreamButtonLayout(top = listOf(lamp), bottom = listOf(script)))
        val off = HomeAssistantEntityAppearance("off", "mdi:lightbulb-off")
        val on = HomeAssistantEntityAppearance("on", "mdi:lightbulb-on")
        coEvery { repository.getEntityAppearance("light.porch") } returnsMany listOf(
            Result.success(off),
            Result.success(off),
            Result.success(on),
            Result.failure(IllegalStateException("offline")),
            Result.failure(IllegalStateException("offline"))
        )

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        assertEquals(off, viewModel.uiState.value.entityAppearances["light.porch"])

        viewModel.handleStreamButton(script)
        runCurrent()
        assertEquals(off, viewModel.uiState.value.entityAppearances["light.porch"])
        assertTrue(viewModel.uiState.value.canRun(script))
        advanceUntilIdle()
        assertEquals(on, viewModel.uiState.value.entityAppearances["light.porch"])

        viewModel.handleStreamButton(lamp)
        advanceUntilIdle()
        assertEquals(on, viewModel.uiState.value.entityAppearances["light.porch"])
    }

    @Test
    fun `a lock uses the selected display entity without changing the lock it controls`() = runTest {
        val lock = StreamButton(
            "lock",
            StreamButtonAction.DoorLock("lock.front"),
            icon = "mdi:lock",
            stateEntityId = "binary_sensor.front_door"
        )
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(StreamButtonLayout(bottom = listOf(lock)))
        val doorOpen = HomeAssistantEntityAppearance("on")
        val doorClosed = HomeAssistantEntityAppearance("off")
        coEvery { repository.getEntityAppearance("binary_sensor.front_door") } returnsMany listOf(
            Result.success(doorOpen),
            Result.success(doorClosed)
        )
        coEvery { repository.getEntityAppearance("lock.front") } returns
            Result.success(HomeAssistantEntityAppearance("unlocked"))
        every { lockDoorUseCase("lock.front") } returns flowOf(Result.success(LockState.Locked))

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        assertEquals(doorOpen, viewModel.uiState.value.entityAppearances["binary_sensor.front_door"])
        assertEquals(LockState.Unlocked, viewModel.uiState.value.lockStates["lock.front"])

        viewModel.handleStreamButton(lock)
        advanceUntilIdle()

        verify(exactly = 1) { lockDoorUseCase("lock.front") }
        assertEquals(doorClosed, viewModel.uiState.value.entityAppearances["binary_sensor.front_door"])
        assertEquals(mapOf("lock.front" to LockState.Locked), viewModel.uiState.value.lockStates)
    }

    @Test
    fun `initializeConnection loads call action settings`() = runTest {
        val callActions = intercomCallActions()
        every { appPreferences.callActionsFlow } returns flowOf(callActions)

        viewModel.initializeConnection(context)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(callActions, uiState.callActions)
    }

    private fun intercomCallActions() = CallActions(
        answer = CallServiceSettings("switch.turn_on", "switch.doorbell_talk"),
        hangup = CallServiceSettings("switch.turn_off", "switch.doorbell_talk")
    )

    @Test
    fun `returning to the stream loads the new layout and enables instant audio once Quick Reply is removed`() = runTest {
        val lock = StreamButton("lock", StreamButtonAction.DoorLock("lock.front"))
        val reply = StreamButton("reply", StreamButtonAction.QuickReply("select.front_reply"))
        val original = StreamButtonLayout(bottom = listOf(lock, reply))
        val revised = StreamButtonLayout(top = listOf(lock))
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(original)
        every { appPreferences.instantTwoWayAudioFlow } returns flowOf(true)

        viewModel.initializeConnection(context)
        advanceUntilIdle()

        assertEquals(original, viewModel.uiState.value.streamButtonLayout)
        assertEquals(LockState.Locked, viewModel.uiState.value.lockStates["lock.front"])
        assertFalse(viewModel.uiState.value.isMicEnabled)
        viewModel.teardownConnection()
        advanceUntilIdle()

        every { appPreferences.streamButtonLayoutFlow } returns flowOf(revised)
        viewModel.initializeConnection(context)
        advanceUntilIdle()

        assertEquals(revised, viewModel.uiState.value.streamButtonLayout)
        assertTrue(viewModel.uiState.value.isMicEnabled)
        coVerify(exactly = 2) { repository.getEntityAppearance("lock.front") }
    }

    @Test
    fun `custom actions require the matching authentication request only when protected`() = runTest {
        val toggle = StreamButton("light", StreamButtonAction.CallService("light", "toggle", "light.porch"))
        val press = StreamButton(
            id = "door",
            action = StreamButtonAction.CallService("button", "press", "button.door"),
            requiresAuthentication = true
        )
        every { appPreferences.streamButtonLayoutFlow } returns
            flowOf(StreamButtonLayout(top = listOf(toggle), bottom = listOf(press)))
        val requests = mutableListOf<UiEvent.AuthenticateStreamButton>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { if (it is UiEvent.AuthenticateStreamButton) requests.add(it) }
        }
        viewModel.initializeConnection(context)
        advanceUntilIdle()

        viewModel.handleStreamButton(toggle)
        viewModel.handleStreamButton(press)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.callService("light", "toggle", "light.porch", JsonObject(), false) }
        coVerify(exactly = 0) { repository.callService("button", "press", "button.door", any(), any()) }
        val firstRequest = requests.single()
        assertEquals(press, firstRequest.button)

        viewModel.cancelAuthentication()
        viewModel.handleStreamButton(press)
        advanceUntilIdle()
        val secondRequest = requests.last()

        viewModel.onStreamButtonAuthentication(firstRequest, true)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.callService("button", "press", "button.door", any(), any()) }

        viewModel.onStreamButtonAuthentication(secondRequest, true)
        viewModel.onStreamButtonAuthentication(secondRequest, true)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.callService("button", "press", "button.door", JsonObject(), false) }
    }

    @Test
    fun `settings opens only for the current successful authentication`() = runTest {
        val protectedButton = StreamButton(
            id = "door",
            action = StreamButtonAction.CallService("button", "press", "button.door"),
            requiresAuthentication = true
        )
        every { appPreferences.streamButtonLayoutFlow } returns
            flowOf(StreamButtonLayout(top = listOf(protectedButton)))
        val requests = mutableListOf<UiEvent.Authentication>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { if (it is UiEvent.Authentication) requests.add(it) }
        }
        viewModel.initializeConnection(context)
        advanceUntilIdle()

        viewModel.requestSettingsAuthentication()
        viewModel.requestSettingsAuthentication()
        viewModel.handleStreamButton(protectedButton)
        advanceUntilIdle()

        val canceled = requests.single() as UiEvent.AuthenticateSettings
        assertFalse(viewModel.onSettingsAuthentication(canceled, succeeded = false))
        viewModel.requestSettingsAuthentication()
        advanceUntilIdle()
        val leftScreen = requests.last() as UiEvent.AuthenticateSettings
        viewModel.cancelAuthentication()
        assertFalse(viewModel.onSettingsAuthentication(leftScreen, succeeded = true))

        viewModel.requestSettingsAuthentication()
        advanceUntilIdle()
        val current = requests.last() as UiEvent.AuthenticateSettings
        // The Android credential screen can stop the stream while authentication is pending.
        viewModel.teardownConnection()
        assertFalse(viewModel.onSettingsAuthentication(canceled, succeeded = true))
        assertTrue(viewModel.onSettingsAuthentication(current, succeeded = true))
        assertFalse(viewModel.onSettingsAuthentication(current, succeeded = true))
    }

    @Test
    fun `toggleStreamMute toggles the mute state`() = runTest {
        assertFalse(viewModel.uiState.value.isStreamMuted)
        
        viewModel.toggleStreamMute()
        
        assertTrue(viewModel.uiState.value.isStreamMuted)
    }

    @Test
    fun `two quick reply buttons load and send options for their own entities`() = runTest {
        val front = StreamButton("front", StreamButtonAction.QuickReply("select.front"))
        val side = StreamButton("side", StreamButtonAction.QuickReply("input_select.side"))
        every { appPreferences.streamButtonLayoutFlow } returns
            flowOf(StreamButtonLayout(top = listOf(front), bottom = listOf(side)))
        coEvery { getSelectOptionsUseCase("select.front") } returns Result.success(listOf("Hello"))
        coEvery { getSelectOptionsUseCase("input_select.side") } returns Result.success(listOf("Wait here"))
        coEvery { sendQuickReplyUseCase("input_select.side", "Wait here") } returns Result.success(Unit)
        viewModel.initializeConnection(context)

        viewModel.handleStreamButton(front)
        advanceUntilIdle()
        assertEquals(QuickReplySheetState(front, listOf("Hello")), viewModel.uiState.value.quickReplySheet)
        viewModel.dismissQuickReplySheet()

        viewModel.handleStreamButton(side)
        advanceUntilIdle()
        assertEquals(QuickReplySheetState(side, listOf("Wait here")), viewModel.uiState.value.quickReplySheet)
        viewModel.sendQuickReply("Hello")
        viewModel.sendQuickReply("Wait here")
        advanceUntilIdle()

        coVerify(exactly = 1) { sendQuickReplyUseCase("input_select.side", "Wait here") }
        coVerify(exactly = 0) { sendQuickReplyUseCase("select.front", any()) }
        coVerify(exactly = 3) { repository.getEntityAppearance("input_select.side") }
        assertEquals(null, viewModel.uiState.value.quickReplySheet)
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
        val data = JsonObject().apply { addProperty("volume_level", 0.25) }
        val action = CallServiceSettings("media_player.volume_set", "media_player.doorbell", data)
        every { appPreferences.callActionsFlow } returns flowOf(CallActions(answer = action))

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.startStream(videoSink)

        viewModel.answerCall(context)
        advanceTimeBy(200)
        runCurrent()

        coVerify(exactly = 0) { repository.callService(any(), any(), any(), any(), any()) }

        viewModel.startStream(videoSink)
        advanceUntilIdle()

        coVerifyOrder {
            webRtcManager.startConnection(any(), true, any())
            repository.callService("media_player", "volume_set", "media_player.doorbell", data, false)
        }
    }

    @Test
    fun `answer action is not sent when the screen is left during the restart`() = runTest {
        every { appPreferences.callActionsFlow } returns flowOf(CallActions(answer = intercomCallActions().answer))

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
        val data = JsonObject().apply { addProperty("message", "Call ended") }
        val action = CallServiceSettings("notify.doorbell", data = data, needsResponse = true)
        every { appPreferences.callActionsFlow } returns flowOf(intercomCallActions().copy(hangup = action))

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

        coVerify { repository.callService("notify", "doorbell", null, data, true) }
    }

    @Test
    fun `ending the call mutes the microphone immediately`() = runTest {
        every { appPreferences.callActionsFlow } returns flowOf(intercomCallActions())

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
        val data = JsonObject().apply { addProperty("message", "Call ended") }
        val action = CallServiceSettings("notify.doorbell", data = data)
        every { appPreferences.callActionsFlow } returns flowOf(intercomCallActions().copy(hangup = action))

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
            repository.callService("notify", "doorbell", null, data, false)
            webRtcManager.disconnect()
        }
        assertFalse(viewModel.uiState.value.isMicEnabled)
    }

    @Test
    fun `answer action failures are reported to the user`() = runTest {
        every { appPreferences.callActionsFlow } returns flowOf(CallActions(answer = intercomCallActions().answer))
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
        val door = StreamButton(
            "door", StreamButtonAction.CallService("switch", "turn_on", "switch.door_relay", doorRelease = true)
        )
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(StreamButtonLayout(bottom = listOf(door)))
        val requests = mutableListOf<UiEvent.AuthenticateStreamButton>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { if (it is UiEvent.AuthenticateStreamButton) requests.add(it) }
        }

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.handleStreamButton(door)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.callService("switch", "turn_on", "switch.door_relay", any(), any()) }
        assertEquals(door, requests.single().button)

        viewModel.onStreamButtonAuthentication(requests.single(), true)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            repository.callService("switch", "turn_on", "switch.door_relay", JsonObject(), false)
        }
    }

    @Test
    fun `lock buttons show each entity state and authenticate unlocking only`() = runTest {
        val front = StreamButton("front", StreamButtonAction.DoorLock("lock.front"))
        val side = StreamButton("side", StreamButtonAction.DoorLock("lock.side"), requiresAuthentication = true)
        every { appPreferences.streamButtonLayoutFlow } returns
            flowOf(StreamButtonLayout(top = listOf(front), bottom = listOf(side)))
        var sideState = "unlocked"
        var frontState = "locked"
        coEvery { repository.getEntityAppearance("lock.side") } coAnswers {
            Result.success(HomeAssistantEntityAppearance(sideState))
        }
        coEvery { repository.getEntityAppearance("lock.front") } coAnswers {
            Result.success(HomeAssistantEntityAppearance(frontState))
        }
        every { lockDoorUseCase("lock.side") } answers {
            sideState = "locked"
            flowOf(Result.success(LockState.Locked))
        }
        every { unlockDoorUseCase("lock.front") } answers {
            frontState = "unlocked"
            flowOf(Result.success(LockState.Unlocked))
        }
        val requests = mutableListOf<UiEvent.AuthenticateStreamButton>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { if (it is UiEvent.AuthenticateStreamButton) requests.add(it) }
        }

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        assertEquals(
            mapOf("lock.front" to LockState.Locked, "lock.side" to LockState.Unlocked),
            viewModel.uiState.value.lockStates
        )

        viewModel.handleStreamButton(side)
        viewModel.handleStreamButton(front)
        advanceUntilIdle()
        verify(exactly = 1) { lockDoorUseCase("lock.side") }
        verify(exactly = 0) { unlockDoorUseCase(any()) }
        assertEquals(front, requests.single().button)

        viewModel.onStreamButtonAuthentication(requests.single(), true)
        advanceUntilIdle()
        verify(exactly = 1) { unlockDoorUseCase("lock.front") }
        assertEquals(
            mapOf("lock.front" to LockState.Unlocked, "lock.side" to LockState.Locked),
            viewModel.uiState.value.lockStates
        )
    }

    @Test
    fun `open door failures are reported using the entity name`() = runTest {
        val door = StreamButton(
            "door",
            StreamButtonAction.CallService("switch", "turn_on", "switch.door_relay", doorRelease = true),
            entityName = "Side door"
        )
        every { appPreferences.streamButtonLayoutFlow } returns flowOf(StreamButtonLayout(bottom = listOf(door)))
        coEvery { repository.callService("switch", "turn_on", "switch.door_relay", JsonObject(), false) } returns
            Result.failure(RuntimeException("relay offline"))
        every { context.getString(R.string.stream_button_failed, "Side door", "relay offline") } returns
            "Side door failed: relay offline"
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvent.collect { events.add(it) }
        }

        viewModel.initializeConnection(context)
        advanceUntilIdle()
        viewModel.handleStreamButton(door)
        val request = events.filterIsInstance<UiEvent.AuthenticateStreamButton>().single()
        viewModel.onStreamButtonAuthentication(request, true)
        advanceUntilIdle()

        assertTrue(events.contains(UiEvent.ShowToast("Side door failed: relay offline", long = true)))
    }
}
