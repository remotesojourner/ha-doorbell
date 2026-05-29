package com.novasoftware.hadoorbell.ui.screens

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
import com.novasoftware.hadoorbell.data.AppPreferences
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
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setup() = kotlinx.coroutines.test.runTest {
        context = ApplicationProvider.getApplicationContext()
        appPreferences = AppPreferences(context)
        // Reset DataStore state to ensure tests are isolated
        appPreferences.saveSettings("", "", "", "", "", false)
    }

    @Test
    fun `settings screen displays input fields`() {
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    appPreferences = appPreferences,
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
        composeTestRule.onNodeWithText("go2rtc Stream Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick Reply Entity ID").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Door Lock Entity ID").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Save & Connect", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `entering text updates the fields and clicking save triggers callback`() {
        var saveClicked = false

        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    appPreferences = appPreferences,
                    onSave = { saveClicked = true },
                    onCancel = {},
                    canCancel = false
                )
            }
        }

        // Input text
        composeTestRule.onNodeWithText("Home Assistant URL")
            .performTextInput("http://test.local:8123")
        
        composeTestRule.onNodeWithText("go2rtc Stream Name")
            .performTextInput("camera1")

        // Click save
        composeTestRule.onNodeWithText("Save & Connect", useUnmergedTree = true)
            .performClick()

        // Wait for coroutine and DataStore I/O to finish and assert callback was triggered
        composeTestRule.waitUntil(timeoutMillis = 5000) { saveClicked }
    }

    @Test
    fun `instant 2-way audio switch is disabled when quick reply is set`() {
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    appPreferences = appPreferences,
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
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                SettingsScreen(
                    appPreferences = appPreferences,
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
}
