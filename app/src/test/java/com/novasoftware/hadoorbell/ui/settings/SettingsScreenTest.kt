package com.novasoftware.hadoorbell.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantEntitiesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantServicesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveHomeAssistantConnectionUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest=Config.NONE)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val getSettingsUseCase = mockk<GetSettingsUseCase>()
    private val saveSettingsUseCase = mockk<SaveSettingsUseCase>(relaxed = true)
    private val saveHomeAssistantConnection = mockk<SaveHomeAssistantConnectionUseCase>(relaxed = true)
    private val getHomeAssistantServices = mockk<GetHomeAssistantServicesUseCase>()
    private val getHomeAssistantEntities = mockk<GetHomeAssistantEntitiesUseCase>()
    private val homeAssistantRepository = mockk<HomeAssistantRepository> {
        every { observeEntityAppearances(any()) } returns emptyFlow()
    }

    @Before
    fun setup() {
        coEvery { getHomeAssistantEntities(any(), any()) } returns Result.success(emptyList())
    }

    private fun viewModel(
        hasSavedConnection: Boolean = true,
        settings: Settings = Settings()
    ): SettingsViewModel {
        val initialSettings = if (hasSavedConnection) {
            settings.copy(url = "https://test.local:8123", token = "my-token-123")
        } else {
            settings
        }
        every { getSettingsUseCase() } returns flowOf(initialSettings)
        return SettingsViewModel(
            getSettingsUseCase,
            saveSettingsUseCase,
            getHomeAssistantEntities,
            getHomeAssistantServices,
            saveHomeAssistantConnection,
            homeAssistantRepository
        )
    }

    @Test
    fun `stream controls are disabled before saving a Home Assistant connection`() {
        val viewModel = viewModel(hasSavedConnection = false)
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onSave = {},
                    onCancel = {},
                    canCancel = true
                )
            }
        }
        
        composeTestRule.waitForIdle()

        // Verify elements exist
        composeTestRule.onNodeWithText("Doorbell Setup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Home Assistant URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Long-Lived Access Token").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save connection").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText("go2rtc Stream Name").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Stream Buttons").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Add top button").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Add bottom button").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Call").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Quick Reply Entity ID").assertDoesNotExist()
        composeTestRule.onNodeWithText("Use custom stream buttons").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save & Connect").assertIsNotEnabled()
    }

    @Test
    fun `connection save verifies credentials separately before stream settings can be saved`() {
        var saveClicked = false
        val finishVerification = CompletableDeferred<Unit>()
        coEvery { getHomeAssistantEntities(any(), any()) } coAnswers {
            finishVerification.await()
            Result.success(emptyList())
        }

        val viewModel = viewModel(hasSavedConnection = false)
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onSave = { saveClicked = true },
                    onCancel = {},
                    canCancel = false
                )
            }
        }

        composeTestRule.onNodeWithText("Home Assistant URL")
            .performTextInput("https://test.local:8123")
        
        composeTestRule.onNodeWithText("Long-Lived Access Token")
            .performScrollTo()
            .performTextInput("my-token-123")

        composeTestRule.onNodeWithText("Save connection").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Checking the Home Assistant connection…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save connection").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Save & Connect").assertIsNotEnabled()
        coVerify(exactly = 0) { saveHomeAssistantConnection(any(), any()) }

        finishVerification.complete(Unit)
        composeTestRule.onNodeWithText("Home Assistant connection verified").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save & Connect").assertIsEnabled()
        coVerify(exactly = 1) { saveHomeAssistantConnection("https://test.local:8123", "my-token-123") }
        coVerify(exactly = 0) { saveSettingsUseCase(any()) }
        assertFalse(saveClicked)

        composeTestRule.onNodeWithText("go2rtc Stream Name")
            .performScrollTo()
            .performTextInput("camera1")

        composeTestRule.onNodeWithText("Save & Connect").performClick()
        composeTestRule.waitForIdle()
        assertTrue(saveClicked)
    }

    @Test
    fun `instant 2-way audio switch is disabled when quick reply is set`() {
        val viewModel = viewModel()
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onSave = {}, onCancel = {}, canCancel = false
                )
            }
        }

        composeTestRule.runOnIdle {
            val reply = StreamButton("reply", StreamButtonAction.QuickReply("select.reply"))
            viewModel.updateStreamButtonLayout(StreamButtonLayout(bottom = listOf(reply)))
        }
        composeTestRule.onNodeWithText("Call").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("Call Button").assertIsDisplayed()

        // Wait for recomposition
        composeTestRule.waitForIdle()

        // Assert switch is disabled
        composeTestRule.onNodeWithTag("instant_2way_switch", useUnmergedTree = true)
            .performScrollTo()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText(
            "A Quick Reply button is enabled, so instant two-way audio is disabled. " +
                "The stream starts in listen-only mode so replies can play."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `instant 2-way audio switch can be toggled when quick reply is empty`() {
        val viewModel = viewModel()
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onSave = {}, onCancel = {}, canCancel = false
                )
            }
        }

        composeTestRule.onNodeWithText("Call").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("Call Button").assertIsDisplayed()
        val switchNode = composeTestRule.onNodeWithTag("instant_2way_switch", useUnmergedTree = true)
        switchNode.performScrollTo().assertIsEnabled()
        
        // Toggle it
        switchNode.performClick()
        composeTestRule.waitForIdle()
        switchNode.assertIsOn()
    }

    @Test
    fun `door and call actions are available through the button list`() {
        val viewModel = viewModel()
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onSave = {}, onCancel = {}, canCancel = false
                )
            }
        }

        composeTestRule.runOnIdle {
            val door = StreamButton(
                id = "legacy_open_door",
                action = StreamButtonAction.CallService(
                    "switch", "turn_on", "switch.door_relay", doorRelease = true
                ),
                requiresAuthentication = true,
                entityName = "Front Door"
            )
            viewModel.updateStreamButtonLayout(StreamButtonLayout(bottom = listOf(door)))
        }
        composeTestRule.onNodeWithText("Front Door").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Door Open Entity ID").assertDoesNotExist()
        composeTestRule.onNodeWithText("Answer Service").assertDoesNotExist()
        composeTestRule.onNodeWithText("Call").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Call Button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Answer Service").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Hang Up Service").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `answer and hang up use independent service and entity selections`() {
        val entities = listOf(
            HomeAssistantEntity("switch.porch", "Porch intercom"),
            HomeAssistantEntity("switch.side", "Side intercom"),
            HomeAssistantEntity("script.end_call", "Finish the call")
        )
        coEvery { getHomeAssistantEntities(any(), any()) } returns Result.success(entities)
        coEvery { getHomeAssistantServices(any(), any()) } returns Result.success(
            listOf(
                HomeAssistantService("switch", "toggle", "Toggle switch", target = entityTarget("switch")),
                HomeAssistantService("script", "turn_on", "Run script", target = entityTarget("script"))
            )
        )
        val viewModel = viewModel(settings = Settings(
            answerActionService = "switch.turn_on",
            answerActionEntityId = "switch.porch"
        ))
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(viewModel = viewModel, onSave = {}, onCancel = {}, canCancel = false)
            }
        }
        val answerService = composeTestRule.onNodeWithTag("answer_action_service_picker")
        val answerEntity = composeTestRule.onNodeWithTag("answer_action_entity_picker")
        val hangUpService = composeTestRule.onNodeWithTag("hangup_action_service_picker")
        val hangUpEntity = composeTestRule.onNodeWithTag("hangup_action_entity_picker")

        composeTestRule.onNodeWithText("Call").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("Call Button").assertIsDisplayed()
        answerService.performScrollTo().assertTextContains("switch.turn_on").performClick()
        composeTestRule.onNodeWithText("Search by name or domain.service").assertIsDisplayed()
        composeTestRule.onNodeWithText("Toggle switch").performClick()
        answerEntity.performScrollTo().assertTextContains("Porch intercom").performClick()
        composeTestRule.onNodeWithText("Choose a Home Assistant entity").assertIsDisplayed()
        composeTestRule.onNodeWithText("Finish the call").assertDoesNotExist()
        composeTestRule.onNodeWithText("Side intercom").performClick()
        val answerData = composeTestRule.onNodeWithTag("answer_action_data")
        answerData.performScrollTo().performTextInput("[]")
        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()
        answerData.performTextClearance()
        answerData.performTextInput("""{"level": 3}""")

        hangUpService.performScrollTo().performClick()
        composeTestRule.onNodeWithText("Run script").performClick()
        hangUpEntity.performScrollTo().performClick()
        composeTestRule.onNodeWithText("Side intercom").assertDoesNotExist()
        composeTestRule.onNodeWithText("Finish the call").performClick()
        composeTestRule.onNodeWithTag("hangup_action_data").performScrollTo()
            .performTextInput("""{"variables": {"end": true}}""")
        composeTestRule.runOnIdle { assertFalse(viewModel.callActionsChanged()) }
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.runOnIdle {
            val state = viewModel.uiState.value
            assertEquals("switch.toggle", state.answerActionService)
            assertEquals("switch.side", state.answerActionEntityId)
            assertEquals(3, state.answerActionData.get("level").asInt)
            assertEquals("script.turn_on", state.hangupActionService)
            assertEquals("script.end_call", state.hangupActionEntityId)
            assertTrue(state.hangupActionData.getAsJsonObject("variables").get("end").asBoolean)
        }

        composeTestRule.onNodeWithText("Call").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithContentDescription("Clear Answer target").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Clear Hang Up service").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.runOnIdle {
            val state = viewModel.uiState.value
            assertEquals("switch.toggle", state.answerActionService)
            assertEquals("", state.answerActionEntityId)
            assertEquals(3, state.answerActionData.get("level").asInt)
            assertEquals("", state.hangupActionService)
            assertEquals("", state.hangupActionEntityId)
            assertTrue(state.hangupActionData.isEmpty)
        }
    }

    private fun entityTarget(domain: String) = JsonObject().apply {
        add("entity", JsonObject().apply { addProperty("domain", domain) })
    }

    @Test
    fun `webrtc provider options are displayed and can be selected`() {
        val viewModel = viewModel()
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onSave = {}, onCancel = {}, canCancel = false
                )
            }
        }

        // Verify elements exist
        composeTestRule.onNodeWithText("Home Assistant Integration").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Frigate").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("AlexxIT/WebRTC").performScrollTo().assertIsDisplayed()

        // Select AlexxIT/WebRTC
        composeTestRule.onNodeWithText("AlexxIT/WebRTC").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }
}
