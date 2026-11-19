package com.novasoftware.hadoorbell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntity
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.HomeAssistantService
import com.novasoftware.hadoorbell.domain.model.HomeAssistantServiceField
import com.novasoftware.hadoorbell.domain.model.StreamBottomItem
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
    fun `a saved lock action can be selected again from the action dropdown`() {
        val lock = StreamButton(
            "front-lock",
            StreamButtonAction.DoorLock("lock.front"),
            requiresAuthentication = true,
            entityName = "Front door"
        )
        val otherEntities = listOf(
            HomeAssistantEntity("lock.front", "Front door", icon = "mdi:lock"),
            HomeAssistantEntity("switch.front", "Front relay")
        )
        var layout by mutableStateOf(StreamButtonLayout(top = listOf(lock)))
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StreamButtonEditor(
                        layout = layout,
                        entities = otherEntities,
                        isLoadingEntities = false,
                        entityLoadError = null,
                        onLoadEntities = {},
                        onLayoutChange = { layout = it },
                        services = listOf(HomeAssistantService("lock", "unlock", "Unlock"))
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Front door").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Home Assistant entity").assertExists()
        composeTestRule.onNode(hasText("Lock or unlock") and hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNode(hasText("Lock or unlock") and isSelected()).assertIsDisplayed()
        composeTestRule.onNodeWithText("Call a service").performClick()
        composeTestRule.onNode(hasText("Choose a service") and hasClickAction()).performClick()
        composeTestRule.onNode(hasText("Lock or unlock") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        composeTestRule.onNode(hasText("Unlock") and inPickerSheet).performClick()
        composeTestRule.onNode(hasText("Call a service") and hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNodeWithText("Lock or unlock").performClick()
        composeTestRule.onNodeWithText("Choose a service").assertDoesNotExist()
        composeTestRule.onNode(hasText("Front door") and hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNode(hasText("lock.front") and inPickerSheet).assertExists()
        composeTestRule.onNodeWithText("Front relay").assertDoesNotExist()
        composeTestRule.onNode(hasText("Front door") and inPickerSheet).performClick()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.runOnIdle {
            assertEquals(lock.copy(entityIcon = "mdi:lock"), layout.top.single())
        }
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
                        onLayoutChange = { layout = it },
                        services = listOf(HomeAssistantService("select", "select_option", "Select option"))
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Add top button").performClick()
        composeTestRule.onNodeWithText("Choose a service").performClick()
        composeTestRule.onNodeWithText("Select option").performClick()
        composeTestRule.onNodeWithText("Choose a Home Assistant entity").performClick()
        composeTestRule.onNodeWithText("Door relay").assertDoesNotExist()
        composeTestRule.onNodeWithText("Search by name or entity ID").performTextInput("doorbell_reply")
        composeTestRule.onNodeWithText("Alarm mode").assertDoesNotExist()
        composeTestRule.onNodeWithText("Doorbell replies").performClick()
        composeTestRule.onNodeWithText("Choose an option").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Leave parcel").performClick()
        composeTestRule.onNodeWithText("Automatic").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Search icon names").performTextInput("mdi:package-variant")
        composeTestRule.onNodeWithText("package variant").performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.runOnIdle {
            assertTrue(requestedEntities)
            assertEquals(
                StreamButtonAction.CallService(
                    "select", "select_option", "select.doorbell_reply",
                    JsonObject().apply { addProperty("option", "Leave parcel") }
                ),
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
    fun `state entity controls the automatic icon independently of the action target`() {
        val script = StreamButton(
            "porch-script",
            StreamButtonAction.RunScript("script.toggle_porch"),
            entityIcon = "mdi:script-text",
            entityName = "Toggle porch"
        )
        var layout by mutableStateOf(StreamButtonLayout(top = listOf(script)))
        val entities = listOf(
            HomeAssistantEntity("script.toggle_porch", "Toggle porch", icon = "mdi:script-text"),
            HomeAssistantEntity("light.porch", "Porch light", icon = "mdi:lightbulb-off", state = "off")
        )
        val liveAppearances = MutableStateFlow(entities.associate { it.entityId to it.appearance })
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StreamButtonEditor(
                        layout = layout,
                        entities = entities,
                        isLoadingEntities = false,
                        entityLoadError = null,
                        onLoadEntities = {},
                        onLayoutChange = { layout = it },
                        onObserveAppearances = { ids ->
                            liveAppearances.map { snapshot -> snapshot.filterKeys { it in ids } }
                        }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Toggle porch").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Follow action target").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Choose a state entity").assertIsDisplayed()
        composeTestRule.onNodeWithText("Porch light").performClick()
        composeTestRule.onNodeWithText("Current state: off").performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle {
            liveAppearances.value += "light.porch" to HomeAssistantEntityAppearance("on", "mdi:lightbulb-on")
        }
        composeTestRule.onNodeWithText("Current state: on").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.runOnIdle {
            assertEquals(
                script.copy(stateEntityId = "light.porch", entityIcon = "mdi:lightbulb-on"),
                layout.top.single()
            )
        }
        composeTestRule.onNodeWithText("Porch light · on").performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle { liveAppearances.value -= "light.porch" }
        composeTestRule.onNodeWithText("Porch light · unavailable").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Toggle porch").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Use action target instead").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.runOnIdle {
            assertEquals(script, layout.top.single())
        }
    }

    @Test
    fun `state colors can be set from entity options and custom states`() {
        val button = StreamButton(
            "entry-mode",
            StreamButtonAction.RunScript("script.toggle_porch"),
            entityName = "Toggle porch",
            stateEntityId = "select.entry_mode"
        )
        var layout by mutableStateOf(StreamButtonLayout(top = listOf(button)))
        val entities = listOf(
            HomeAssistantEntity("select.entry_mode", "Entry mode", options = listOf("Day", "Night"), state = "Day")
        )
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StreamButtonEditor(
                        layout = layout,
                        entities = entities,
                        isLoadingEntities = false,
                        entityLoadError = null,
                        onLoadEntities = {},
                        onLayoutChange = { layout = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Toggle porch").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Choose colors by state").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Night").performClick()
        composeTestRule.onNodeWithContentDescription("Blue").performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithText("Add a custom state").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("state_color_state").performScrollTo().performTextInput("Guest mode")
        composeTestRule.onNodeWithTag("state_color_hex").performScrollTo().performTextInput("802e7d32")
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNode(hasText("Guest mode") and inPickerSheet).assertExists()
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                button.copy(stateColors = mapOf("Night" to "#1565C0", "Guest mode" to "#802E7D32")),
                layout.top.single()
            )
        }

        composeTestRule.onNodeWithText("Toggle porch").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Use action target instead").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Choose colors by state").assertDoesNotExist()
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.runOnIdle {
            assertEquals(button.copy(stateEntityId = null), layout.top.single())
        }
    }

    @Test
    fun `moving a button between rows preserves the button and existing order`() {
        val light = StreamButton("light", StreamButtonAction.CallService("light", "toggle", "light.porch"), entityName = "Porch light")
        val chime = StreamButton("chime", StreamButtonAction.CallService("button", "press", "button.chime"), entityName = "Chime")
        val relay = StreamButton(
            "relay",
            StreamButtonAction.CallService("switch", "toggle", "switch.gate_relay"),
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
        composeTestRule.onNodeWithContentDescription("Move Gate relay up").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(light), layout.top)
            assertEquals(listOf(relay, chime), layout.bottom)
        }
    }

    @Test
    fun `editing a migrated door button retains its service and protection`() {
        val door = StreamButton(
            "legacy-door",
            StreamButtonAction.CallService("switch", "turn_on", "switch.door_relay", doorRelease = true),
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
        composeTestRule.onNodeWithText("switch.turn_on").assertExists()
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

    @Test
    fun `sound and call can be reordered among the bottom buttons`() {
        val chime = StreamButton("chime", StreamButtonAction.CallService("button", "press", "button.chime"), entityName = "Chime")
        var layout by mutableStateOf(StreamButtonLayout(bottom = listOf(chime)))
        showEditor({ layout }, { layout = it })

        composeTestRule.onNodeWithContentDescription("Move Sound up").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Move Call down").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(chime), layout.bottom)
            assertEquals(0, layout.soundPosition)
            assertEquals(2, layout.callPosition)
        }

        composeTestRule.onNodeWithContentDescription("Move Sound down").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, layout.soundPosition)
            assertEquals(2, layout.callPosition)
            assertEquals(chime, (layout.bottomItems.first() as StreamBottomItem.Custom).button)
        }
    }

    @Test
    fun `closing an edited button asks before discarding the changes`() {
        val chime = StreamButton("chime", StreamButtonAction.CallService("button", "press", "button.chime"), entityName = "Chime")
        var layout by mutableStateOf(StreamButtonLayout(top = listOf(chime)))
        showEditor({ layout }, { layout = it })

        composeTestRule.onNodeWithText("Chime").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
        composeTestRule.onNodeWithText("Edit stream button").assertDoesNotExist()

        composeTestRule.onNodeWithText("Chime").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Require device authentication").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep editing").performClick()
        composeTestRule.onNodeWithText("Edit stream button").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.onNodeWithText("Discard").performClick()
        composeTestRule.onNodeWithText("Edit stream button").assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(chime, layout.top.single()) }
    }

    @Test
    fun `service fields can be added to the action data from chips`() {
        var layout by mutableStateOf(StreamButtonLayout())
        val service = HomeAssistantService(
            "light", "turn_on", "Turn on",
            fields = mapOf(
                "brightness" to HomeAssistantServiceField(defaultValue = JsonPrimitive(100)),
                "transition" to HomeAssistantServiceField()
            )
        )
        showEditor({ layout }, { layout = it }, services = listOf(service))

        composeTestRule.onNodeWithText("Add top button").performClick()
        composeTestRule.onNodeWithText("Choose a service").performClick()
        composeTestRule.onNodeWithText("Turn on").performClick()
        composeTestRule.onNodeWithTag("stream_button_data").assertDoesNotExist()
        composeTestRule.onNodeWithText("Show advanced options").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Add the brightness field").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Add the brightness field").assertDoesNotExist()
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.runOnIdle {
            val action = layout.top.single().action as StreamButtonAction.CallService
            assertEquals(100, action.data.get("brightness").asInt)
        }
    }

    @Test
    fun `inserting a field selects its placeholder value`() {
        val withDefault = insertServiceField("", "brightness", HomeAssistantServiceField(defaultValue = JsonPrimitive(100)))!!
        assertEquals("100", withDefault.text.substring(withDefault.selection.start, withDefault.selection.end))

        val blank = insertServiceField("""{"color_name":"red"}""", "transition", HomeAssistantServiceField())!!
        assertTrue(blank.selection.collapsed)
        assertEquals("\"\"", blank.text.substring(blank.selection.start - 1, blank.selection.start + 1))
        assertEquals("red", parseJsonObject(blank.text)?.get("color_name")?.asString)

        assertEquals(null, insertServiceField("[]", "transition", HomeAssistantServiceField()))
    }

    private fun showEditor(
        layout: () -> StreamButtonLayout,
        onLayoutChange: (StreamButtonLayout) -> Unit,
        services: List<HomeAssistantService> = emptyList()
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StreamButtonEditor(
                        layout = layout(),
                        entities = emptyList(),
                        isLoadingEntities = false,
                        entityLoadError = null,
                        onLoadEntities = {},
                        onLayoutChange = onLayoutChange,
                        services = services
                    )
                }
            }
        }
    }

    private val inPickerSheet = hasAnyAncestor(hasTestTag(PICKER_SHEET_TAG))
}
