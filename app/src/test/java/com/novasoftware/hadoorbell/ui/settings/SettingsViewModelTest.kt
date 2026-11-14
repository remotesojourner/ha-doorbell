package com.novasoftware.hadoorbell.ui.settings

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.MainDispatcherRule
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantEntitiesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantServicesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveHomeAssistantConnectionUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getSettingsUseCase: GetSettingsUseCase
    private lateinit var saveSettingsUseCase: SaveSettingsUseCase
    private val saveHomeAssistantConnection = mockk<SaveHomeAssistantConnectionUseCase>(relaxed = true)
    private val getHomeAssistantServices = io.mockk.mockk<GetHomeAssistantServicesUseCase>()
    private lateinit var getHomeAssistantEntities: GetHomeAssistantEntitiesUseCase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        val initialSettings = Settings(
            url = "https://test.url",
            token = "token123",
            streamSource = "camera1",
            instantTwoWayAudio = false,
            webrtcProvider = "webrtc",
            answerActionService = "switch.turn_on",
            answerActionEntityId = "switch.doorbell_talk",
            hangupActionService = "switch.turn_off",
            hangupActionEntityId = "switch.doorbell_talk"
        )
        getSettingsUseCase = mockk {
            every { this@mockk.invoke() } returns flowOf(initialSettings)
        }
        saveSettingsUseCase = mockk(relaxed = true)

        getHomeAssistantEntities = mockk {
            coEvery { this@mockk.invoke("https://test.url", "token123") } returns Result.success(emptyList())
        }
        viewModel = SettingsViewModel(
            getSettingsUseCase,
            saveSettingsUseCase,
            getHomeAssistantEntities,
            getHomeAssistantServices,
            saveHomeAssistantConnection,
            mockk<HomeAssistantRepository>()
        )
    }

    @Test
    fun `initialization loads values from use case`() = runTest {
        assertTrue(viewModel.uiState.value.isLoaded)
        assertEquals(HomeAssistantConnectionStatus.Verified, viewModel.uiState.value.connectionStatus)
        coVerify(exactly = 0) { saveHomeAssistantConnection(any(), any()) }
        assertEquals("https://test.url", viewModel.uiState.value.url)
        assertEquals("token123", viewModel.uiState.value.token)
        assertEquals("camera1", viewModel.uiState.value.streamSource)
        assertFalse(viewModel.uiState.value.instantTwoWayAudio)
        assertEquals("webrtc", viewModel.uiState.value.webrtcProvider)
        assertEquals("switch.turn_on", viewModel.uiState.value.answerActionService)
        assertEquals("switch.doorbell_talk", viewModel.uiState.value.answerActionEntityId)
        assertEquals("switch.turn_off", viewModel.uiState.value.hangupActionService)
        assertEquals("switch.doorbell_talk", viewModel.uiState.value.hangupActionEntityId)
    }

    @Test
    fun `updateUrl updates state`() {
        viewModel.updateUrl("https://new.url")
        assertEquals("https://new.url", viewModel.uiState.value.url)
    }

    @Test
    fun `adding a quick reply button disables instant two way audio`() {
        viewModel.updateInstantTwoWayAudio(true)
        assertTrue(viewModel.uiState.value.instantTwoWayAudio)

        val reply = StreamButton("reply", StreamButtonAction.QuickReply("select.reply"))
        viewModel.updateStreamButtonLayout(StreamButtonLayout(top = listOf(reply)))

        assertEquals(listOf(reply), viewModel.uiState.value.streamButtonLayout.top)
        assertFalse(viewModel.uiState.value.instantTwoWayAudio)
        viewModel.updateInstantTwoWayAudio(true)
        assertFalse(viewModel.uiState.value.instantTwoWayAudio)
    }

    @Test
    fun `saveSettings calls use case and onComplete callback`() = runTest {
        var onCompleteCalled = false
        val onComplete = { onCompleteCalled = true }

        viewModel.updateStreamSource("new-camera")

        viewModel.saveSettings(onComplete)

        coVerify {
            saveSettingsUseCase(
                Settings(
                    url = "https://test.url",
                    token = "token123",
                    streamSource = "new-camera",
                    instantTwoWayAudio = false,
                    webrtcProvider = "webrtc",
                    answerActionService = "switch.turn_on",
                    answerActionEntityId = "switch.doorbell_talk",
                    hangupActionService = "switch.turn_off",
                    hangupActionEntityId = "switch.doorbell_talk"
                )
            )
        }
        assertTrue(onCompleteCalled)
        coVerify(exactly = 0) { saveHomeAssistantConnection(any(), any()) }
    }

    @Test
    fun `call actions are unchanged right after loading`() = runTest {
        assertFalse(viewModel.callActionsChanged())
    }

    @Test
    fun `changing the answer action requires authentication`() = runTest {
        viewModel.updateCallActions("lock.unlock", "switch.doorbell_talk", "switch.turn_off", "switch.doorbell_talk")
        assertTrue(viewModel.callActionsChanged())

        viewModel.updateCallActions("switch.turn_on", "switch.doorbell_talk", "switch.turn_off", "switch.doorbell_talk")
        assertFalse(viewModel.callActionsChanged())

        viewModel.updateCallActions("switch.turn_on", "lock.front_door", "switch.turn_off", "switch.doorbell_talk")
        assertTrue(viewModel.callActionsChanged())
    }

    @Test
    fun `changing the hang up action requires authentication`() = runTest {
        viewModel.updateCallActions("switch.turn_on", "switch.doorbell_talk", "script.doorbell_hangup", "switch.doorbell_talk")
        assertTrue(viewModel.callActionsChanged())

        viewModel.updateCallActions("switch.turn_on", "switch.doorbell_talk", "switch.turn_off", "script.doorbell_hangup")
        assertTrue(viewModel.callActionsChanged())
    }

    @Test
    fun `changing only call service data requires authentication`() = runTest {
        viewModel.updateCallActions(
            "switch.turn_on", "switch.doorbell_talk", "switch.turn_off", "switch.doorbell_talk",
            answerData = JsonObject().apply { addProperty("duration", 3) }
        )

        assertTrue(viewModel.callActionsChanged())
        viewModel.saveSettings {}
        assertFalse(viewModel.callActionsChanged())
        coVerify { saveSettingsUseCase(match { it.answerActionData.get("duration")?.asInt == 3 }) }
    }

    @Test
    fun `unrelated settings do not require authentication`() = runTest {
        viewModel.updateUrl("https://other.url")
        viewModel.updateToken("other-token")


        assertFalse(viewModel.callActionsChanged())
    }

    @Test
    fun `saving keeps the saved call actions in sync`() = runTest {
        viewModel.updateCallActions("lock.unlock", "switch.doorbell_talk", "switch.turn_off", "switch.doorbell_talk")
        assertTrue(viewModel.callActionsChanged())

        viewModel.saveSettings {}

        assertFalse(viewModel.callActionsChanged())
    }

    @Test
    fun `changing servers requires a different token before verifying or saving`() = runTest {
        val entities = listOf(HomeAssistantEntity("script.greet_visitor", "Greet visitor"))
        viewModel.updateUrl("https://new.url")
        viewModel.saveConnection()

        assertEquals(
            HomeAssistantConnectionStatus.Failed(HomeAssistantLoadError.ChangedServer),
            viewModel.uiState.value.connectionStatus
        )
        coVerify(exactly = 0) { getHomeAssistantEntities("https://new.url", any()) }
        coVerify(exactly = 0) { saveHomeAssistantConnection(any(), any()) }

        viewModel.updateToken("new-token")
        coEvery { getHomeAssistantEntities("https://new.url", "new-token") } returns Result.success(entities)
        viewModel.saveConnection()

        assertEquals(HomeAssistantConnectionStatus.Verified, viewModel.uiState.value.connectionStatus)
        assertEquals(entities, viewModel.uiState.value.entities)
        coVerify(exactly = 1) { saveHomeAssistantConnection("https://new.url", "new-token") }
        coVerify(exactly = 0) { saveSettingsUseCase(any()) }
        viewModel.updateUrl("https://other.url")
        assertTrue(viewModel.uiState.value.entities.isEmpty())
        assertEquals(HomeAssistantConnectionStatus.Unchecked, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `discovery and saving stream settings require a verified connection`() = runTest {
        val catalog = listOf(HomeAssistantService("light", "turn_on"))
        coEvery { getHomeAssistantServices("https://test.url", "token123") } returns Result.success(catalog)

        viewModel.loadServices()
        assertEquals(catalog, viewModel.uiState.value.services)

        viewModel.updateUrl("https://new.url")
        viewModel.loadServices()
        viewModel.loadEntities()
        var saved = false
        viewModel.saveSettings { saved = true }
        assertTrue(viewModel.uiState.value.services.isEmpty())
        assertFalse(saved)
        coVerify(exactly = 0) { getHomeAssistantServices("https://new.url", any()) }
        coVerify(exactly = 0) { getHomeAssistantEntities("https://new.url", any()) }
        coVerify(exactly = 0) { saveSettingsUseCase(any()) }

        viewModel.updateToken("new-token")
        coEvery { getHomeAssistantEntities("https://new.url", "new-token") } returns Result.success(emptyList())
        viewModel.saveConnection()
        coEvery { getHomeAssistantServices("https://new.url", "new-token") } returns Result.success(catalog)
        viewModel.loadServices()
        assertEquals(catalog, viewModel.uiState.value.services)
        coVerify(exactly = 0) { saveSettingsUseCase(any()) }
    }

    @Test
    fun `connection save waits for Home Assistant and a failed check leaves credentials unsaved`() = runTest {
        val verification = CompletableDeferred<Result<List<HomeAssistantEntity>>>()
        coEvery { getHomeAssistantEntities("https://test.url", "wrong-token") } coAnswers { verification.await() }

        viewModel.updateToken("wrong-token")
        viewModel.saveConnection()
        viewModel.saveConnection()

        assertEquals(HomeAssistantConnectionStatus.Checking, viewModel.uiState.value.connectionStatus)
        coVerify(exactly = 1) { getHomeAssistantEntities("https://test.url", "wrong-token") }
        coVerify(exactly = 0) { saveHomeAssistantConnection(any(), any()) }
        verification.complete(Result.failure(IOException()))
        testScheduler.runCurrent()

        assertEquals(
            HomeAssistantConnectionStatus.Failed(HomeAssistantLoadError.RequestFailed),
            viewModel.uiState.value.connectionStatus
        )
        coVerify(exactly = 0) { saveHomeAssistantConnection(any(), any()) }
    }

    @Test
    fun `successful verification with a failed write leaves controls disabled`() = runTest {
        coEvery { getHomeAssistantEntities("https://test.url", "new-token") } returns Result.success(emptyList())
        coEvery { saveHomeAssistantConnection("https://test.url", "new-token") } throws IOException()

        viewModel.updateToken("new-token")
        viewModel.saveConnection()

        assertEquals(
            HomeAssistantConnectionStatus.Failed(HomeAssistantLoadError.ConnectionSaveFailed),
            viewModel.uiState.value.connectionStatus
        )
    }

    @Test
    fun `scanning a token updates the draft without saving or connecting`() = runTest {
        val exampleToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXN0In0.test-signature"

        assertTrue(viewModel.importTokenFromQr("  $exampleToken\n"))
        assertEquals(exampleToken, viewModel.uiState.value.token)
        coVerify(exactly = 0) { saveSettingsUseCase(any()) }
        coVerify(exactly = 0) { saveHomeAssistantConnection(any(), any()) }
        coVerify(exactly = 0) { getHomeAssistantEntities(any(), exampleToken) }
    }

    @Test
    fun `an unrelated QR code leaves the current token intact`() {
        for (contents in listOf("", "https://home.example.org", "www.example.org")) {
            assertFalse(viewModel.importTokenFromQr(contents))
        }
        assertEquals("token123", viewModel.uiState.value.token)
    }

    @Test
    fun `moving a protected button preserves its action and authentication`() = runTest {
        val door = StreamButton(
            id = "door",
            action = StreamButtonAction.CallService("button", "press", "button.door"),
            requiresAuthentication = true
        )
        val layout = StreamButtonLayout(bottom = listOf(door))
        viewModel.updateStreamButtonLayout(layout)
        viewModel.saveSettings {}

        viewModel.updateStreamButtonLayout(layout.copy(top = listOf(door), bottom = emptyList()))
        assertFalse(viewModel.protectedButtonsChanged())
        viewModel.updateStreamButtonLayout(layout.copy(bottom = listOf(door.copy(requiresAuthentication = false))))
        assertTrue(viewModel.protectedButtonsChanged())
    }
}
