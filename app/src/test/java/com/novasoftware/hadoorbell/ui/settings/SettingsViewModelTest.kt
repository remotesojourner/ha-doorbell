package com.novasoftware.hadoorbell.ui.settings

import com.novasoftware.hadoorbell.MainDispatcherRule
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantEntitiesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import com.novasoftware.hadoorbell.domain.model.Settings
import io.mockk.coEvery
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

        getHomeAssistantEntities = mockk()
        viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase, getHomeAssistantEntities)
    }

    @Test
    fun `initialization loads values from use case`() = runTest {
        assertTrue(viewModel.uiState.value.isLoaded)
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

        viewModel.updateUrl("https://updated.url")

        viewModel.saveSettings(onComplete)

        coVerify {
            saveSettingsUseCase(
                Settings(
                    url = "https://updated.url",
                    token = "token123",
                    streamSource = "camera1",
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
    fun `entity lookup can use new credentials without saving them`() = runTest {
        val entities = listOf(HomeAssistantEntity("script.greet_visitor", "Greet visitor"))
        viewModel.updateUrl("https://new.url")
        viewModel.loadEntities()

        assertEquals(EntityLoadError.ChangedServer, viewModel.uiState.value.entityLoadError)
        coVerify(exactly = 0) { getHomeAssistantEntities(any(), any()) }

        viewModel.updateToken("new-token")
        coEvery { getHomeAssistantEntities("https://new.url", "new-token") } returns Result.success(entities)
        viewModel.loadEntities()

        assertEquals(entities, viewModel.uiState.value.entities)
        coVerify(exactly = 0) { saveSettingsUseCase(any()) }
        viewModel.updateUrl("https://other.url")
        assertTrue(viewModel.uiState.value.entities.isEmpty())
    }

    @Test
    fun `scanning a token updates the draft without saving or connecting`() = runTest {
        val exampleToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXN0In0.test-signature"

        assertTrue(viewModel.importTokenFromQr("  $exampleToken\n"))
        assertEquals(exampleToken, viewModel.uiState.value.token)
        coVerify(exactly = 0) { saveSettingsUseCase(any()) }
        coVerify(exactly = 0) { getHomeAssistantEntities(any(), any()) }
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
            action = StreamButtonAction.Press("button.door"),
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
