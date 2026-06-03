package com.novasoftware.hadoorbell.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import com.novasoftware.hadoorbell.domain.model.Settings
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val initialSettings = Settings(
            url = "",
            token = "",
            streamSource = "",
            quickReplyEntityId = "",
            lockEntityId = "",
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
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase)
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
        composeTestRule.onNodeWithText("Quick Reply Entity ID").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Door Lock Entity ID").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Save & Connect", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `entering text updates the fields and clicking save triggers callback`() {
        var saveClicked = false

        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase)
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
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase)
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onSave = {}, onCancel = {}, canCancel = false
                )
            }
        }

        // Enter quick reply ID
        composeTestRule.onNodeWithText("Quick Reply Entity ID")
            .performScrollTo()
            .performTextInput("select.some_entity")

        // Wait for recomposition
        composeTestRule.waitForIdle()

        // Assert switch is disabled
        composeTestRule.onNodeWithTag("instant_2way_switch")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun `instant 2-way audio switch can be toggled when quick reply is empty`() {
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase)
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onSave = {}, onCancel = {}, canCancel = false
                )
            }
        }

        // Initially enabled because quick reply is empty
        val switchNode = composeTestRule.onNodeWithTag("instant_2way_switch")
        switchNode.performScrollTo().assertIsEnabled()
        
        // Toggle it
        switchNode.performClick()
        composeTestRule.waitForIdle()
        switchNode.assertIsOn()
    }

    @Test
    fun `webrtc provider options are displayed and can be selected`() {
        val viewModel = SettingsViewModel(getSettingsUseCase, saveSettingsUseCase)
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
