package com.novasoftware.hadoorbell.ui.screens

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appPreferences = AppPreferences(context)
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
        composeTestRule.onNodeWithText("Quick Reply Entity ID").assertIsDisplayed()
        composeTestRule.onNodeWithText("Door Lock Entity ID").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save & Connect").assertIsDisplayed()
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
        composeTestRule.onNodeWithText("Save & Connect")
            .performClick()

        // Wait for coroutine to finish
        composeTestRule.waitForIdle()

        // Assert callback was triggered
        assert(saveClicked)
    }
}
