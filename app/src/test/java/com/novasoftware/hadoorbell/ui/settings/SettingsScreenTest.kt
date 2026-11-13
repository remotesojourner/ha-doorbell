package com.novasoftware.hadoorbell.ui.settings

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantEntitiesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
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

    private lateinit var context: Context
    private lateinit var getSettingsUseCase: GetSettingsUseCase
    private lateinit var saveSettingsUseCase: SaveSettingsUseCase
    private val getHomeAssistantEntities = io.mockk.mockk<GetHomeAssistantEntitiesUseCase>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val initialSettings = Settings(
            url = "",
            token = "",
            streamSource = "",
            instantTwoWayAudio = false,
            webrtcProvider = "frigate"
        )
        getSettingsUseCase = io.mockk.mockk {
            io.mockk.every { this@mockk.invoke() } returns kotlinx.coroutines.flow.flowOf(initialSettings)
        }
        saveSettingsUseCase = io.mockk.mockk(relaxed = true)
    }

    @Test
    fun `settings screen displays input fields`() {
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase, getHomeAssistantEntities)
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
        composeTestRule.onNodeWithText("go2rtc Stream Name").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Stream Buttons").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Add top button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Add bottom button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick Reply Entity ID").assertDoesNotExist()
        composeTestRule.onNodeWithText("Use custom stream buttons").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save & Connect", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `entering text updates the fields and clicking save triggers callback`() {
        var saveClicked = false

        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase, getHomeAssistantEntities)
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

        // Input text
        composeTestRule.onNodeWithText("Home Assistant URL")
            .performTextInput("http://test.local:8123")
        
        composeTestRule.onNodeWithText("Long-Lived Access Token")
            .performScrollTo()
            .performTextInput("my-token-123")
            
        composeTestRule.onNodeWithText("go2rtc Stream Name")
            .performScrollTo()
            .performTextInput("camera1")

        // Click save
        composeTestRule.onNodeWithText("Save & Connect", useUnmergedTree = true)
            .performClick()

        // Wait for coroutine to finish and assert callback was triggered
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue(saveClicked)
    }

    @Test
    fun `instant 2-way audio switch is disabled when quick reply is set`() {
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase, getHomeAssistantEntities)
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
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase, getHomeAssistantEntities)
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
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase, getHomeAssistantEntities)
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
                action = StreamButtonAction.OpenDoor("switch.door_relay", "switch.turn_on"),
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
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase, getHomeAssistantEntities)
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
