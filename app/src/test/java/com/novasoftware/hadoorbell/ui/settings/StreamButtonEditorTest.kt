package com.novasoftware.hadoorbell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
    fun `the saved lock action is visible and selected when its dropdown opens`() {
        val lock = StreamButton(
            "front-lock",
            StreamButtonAction.DoorLock("lock.front"),
            requiresAuthentication = true,
            entityName = "Front door"
        )
        val otherEntities = listOf(
            HomeAssistantEntity("lock.front", "Front door"),
            HomeAssistantEntity("switch.front", "Front relay")
        )
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StreamButtonEditor(
                        layout = StreamButtonLayout(top = listOf(lock)),
                        entities = otherEntities,
                        isLoadingEntities = false,
                        entityLoadError = null,
                        onLoadEntities = {},
                        onLayoutChange = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Front door").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Home Assistant entity").assertExists()
        composeTestRule.onNode(hasText("Lock or unlock") and hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNode(hasText("Lock or unlock") and isSelected()).assertIsDisplayed().performClick()
        composeTestRule.onNode(hasText("Front door") and hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNodeWithText("lock.front").assertExists()
        composeTestRule.onNodeWithText("Front relay").assertDoesNotExist()
    }

    @Test
    fun `adding a select button saves the chosen entity and option`() {
        var layout by mutableStateOf(StreamButtonLayout())
        val entities = listOf(
            HomeAssistantEntity(
                "select.doorbell_reply",
                "Doorbell replies",
                listOf("Leave parcel", "Busy"),
                icon = "mdi:message-text"
            ),
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
        composeTestRule.onNodeWithText("Automatic").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Search icon names").performTextInput("mdi:package-variant")
        composeTestRule.onNodeWithText("package variant").performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.runOnIdle {
            assertTrue(requestedEntities)
            assertEquals(
                StreamButtonAction.SelectOption("select.doorbell_reply", "Leave parcel"),
                layout.top.single().action
            )
            assertEquals("Doorbell replies", layout.top.single().entityName)
            assertEquals("package-variant", layout.top.single().icon)
            assertEquals("mdi:message-text", layout.top.single().entityIcon)
        }

        composeTestRule.onNodeWithText("Doorbell replies").performScrollTo().performClick()
        composeTestRule.onNodeWithText("package variant").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Automatic").performClick()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.runOnIdle {
            assertEquals(null, layout.top.single().icon)
            assertEquals("mdi:message-text", layout.top.single().entityIcon)
        }
    }

    @Test
    fun `moving a button between rows preserves the button and existing order`() {
        val light = StreamButton("light", StreamButtonAction.Toggle("light.porch"), entityName = "Porch light")
        val chime = StreamButton("chime", StreamButtonAction.Press("button.chime"), entityName = "Chime")
        val relay = StreamButton(
            "relay",
            StreamButtonAction.Toggle("switch.gate_relay"),
            requiresAuthentication = true,
            entityName = "Gate relay"
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
    fun `editing a migrated door button retains its service and protection`() {
        val door = StreamButton(
            "legacy-door",
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

        composeTestRule.onNodeWithText("switch.door_relay").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Door service").assertExists()
        composeTestRule.onNodeWithText("If this device has a screen lock, it asks you to authenticate before opening the door.")
            .assertExists()
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.onNodeWithText("Hidden on stream").assertExists()
        composeTestRule.runOnIdle {
            assertEquals(door, layout.bottom.single())
        }

        composeTestRule.onNodeWithContentDescription("Options for switch.door_relay")
            .performScrollTo().performClick()
        composeTestRule.onNodeWithText("Show on stream").performClick()
        composeTestRule.onNodeWithText("Hidden on stream").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals(door.copy(visible = true), layout.bottom.single())
        }
    }
}
