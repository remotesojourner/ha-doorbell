package com.novasoftware.hadoorbell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class StreamButtonEditorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `adding a select button saves the chosen entity and option`() {
        var layout by mutableStateOf(StreamButtonLayout())
        val entities = listOf(
            HomeAssistantEntity("select.doorbell_reply", "Doorbell replies", listOf("Leave parcel", "Busy")),
            HomeAssistantEntity("select.alarm_mode", "Alarm mode", listOf("Home")),
            HomeAssistantEntity("switch.door_relay", "Door relay")
        )
        var requestedEntities = false
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StreamButtonEditor(
                        layout = layout,
                        entities = entities,
                        isLoadingEntities = false,
                        entityLoadError = null,
                        onLoadEntities = { requestedEntities = true },
                        onLayoutChange = { layout = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Add top button").performClick()
        composeTestRule.onNodeWithText("Toggle").performClick()
        composeTestRule.onNodeWithText("Select option").performClick()
        composeTestRule.onNodeWithText("Choose a Home Assistant entity").performClick()
        composeTestRule.onNodeWithText("Door relay").assertDoesNotExist()
        composeTestRule.onNodeWithText("Search by name or entity ID").performTextInput("doorbell_reply")
        composeTestRule.onNodeWithText("Alarm mode").assertDoesNotExist()
        composeTestRule.onNodeWithText("Doorbell replies").performClick()
        composeTestRule.onNodeWithText("Choose an option").performClick()
        composeTestRule.onNodeWithText("Leave parcel").performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.runOnIdle {
            assertTrue(requestedEntities)
            assertEquals(
                StreamButtonAction.SelectOption("select.doorbell_reply", "Leave parcel"),
                layout.top.single().action
            )
            assertEquals("Leave parcel", layout.top.single().label)
        }
    }

    @Test
    fun `moving a button between rows preserves the button and existing order`() {
        val light = StreamButton("light", "Porch light", StreamButtonAction.Toggle("light.porch"))
        val chime = StreamButton("chime", "Chime", StreamButtonAction.Press("button.chime"))
        val relay = StreamButton(
            "relay",
            "Gate relay",
            StreamButtonAction.Toggle("switch.gate_relay"),
            requiresAuthentication = true
        )
        var layout by mutableStateOf(StreamButtonLayout(top = listOf(light, relay), bottom = listOf(chime)))
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StreamButtonEditor(
                        layout = layout,
                        entities = emptyList(),
                        isLoadingEntities = false,
                        entityLoadError = null,
                        onLoadEntities = {},
                        onLayoutChange = { layout = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Options for Gate relay").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Move to bottom row").performClick()
        composeTestRule.onNodeWithContentDescription("Options for Gate relay").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Move up").performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(light), layout.top)
            assertEquals(listOf(relay, chime), layout.bottom)
        }
    }

    @Test
    fun `renaming a migrated door button retains its service and protection`() {
        val door = StreamButton(
            "legacy-door",
            "Door release",
            StreamButtonAction.OpenDoor("switch.door_relay", "switch.turn_on"),
            requiresAuthentication = true,
            visible = false
        )
        var layout by mutableStateOf(StreamButtonLayout(bottom = listOf(door)))
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StreamButtonEditor(
                        layout = layout,
                        entities = emptyList(),
                        isLoadingEntities = false,
                        entityLoadError = null,
                        onLoadEntities = {},
                        onLayoutChange = { layout = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Door release").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Door service").assertExists()
        composeTestRule.onNodeWithText("If this device has a screen lock, it asks you to authenticate before opening the door.")
            .assertExists()
        composeTestRule.onNodeWithText("Button label").performScrollTo().performTextReplacement("Open side door")
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.onNodeWithText("Hidden on stream").assertExists()
        composeTestRule.runOnIdle {
            assertEquals(door.copy(label = "Open side door"), layout.bottom.single())
        }

        composeTestRule.onNodeWithContentDescription("Options for Open side door")
            .performScrollTo().performClick()
        composeTestRule.onNodeWithText("Show on stream").performClick()
        composeTestRule.onNodeWithText("Hidden on stream").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals(door.copy(label = "Open side door", visible = true), layout.bottom.single())
        }
    }
}
