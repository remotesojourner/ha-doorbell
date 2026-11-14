package com.novasoftware.hadoorbell.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private fun viewModel(hasSavedConnection: Boolean = true): SettingsViewModel {
        val initialSettings = if (hasSavedConnection) {
            Settings(url = "https://test.local:8123", token = "my-token-123")
        } else {
            Settings()
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
