package com.novasoftware.hadoorbell.ui.stream

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
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
@Config(manifest = Config.NONE, qualifiers = "w480dp-h800dp-mdpi")
class StreamBottomControlsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `migrated buttons keep the original size and placement`() {
        val reply = StreamButton("reply", StreamButtonAction.QuickReply("select.reply"))
        val door = StreamButton(
            "door", StreamButtonAction.CallService("button", "press", "button.door", doorRelease = true)
        )
        val hiddenLock = StreamButton(
            "lock", StreamButtonAction.DoorLock("lock.door"), visible = false
        )
        showControls(400.dp, listOf(reply, door, hiddenLock))

        val expectedLeftEdges = mapOf(
            "Mute Stream" to 40f,
            "Call" to 128f,
            reply.displayName to 216f,
            door.displayName to 296f
        )
        expectedLeftEdges.forEach { (label, expectedLeft) ->
            val bounds = composeTestRule.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            assertEquals(label, 64f, (bounds.right - bounds.left).value, 0.5f)
            assertEquals(label, 64f, (bounds.bottom - bounds.top).value, 0.5f)
            assertEquals(label, expectedLeft, bounds.left.value, 0.5f)
        }
        composeTestRule.onNodeWithContentDescription(hiddenLock.displayName).assertDoesNotExist()
    }

    @Test
    fun `a narrow row keeps both fixed controls and fits all three custom actions`() {
        val buttons = (1..3).map { index ->
            StreamButton(
                "action$index",
                StreamButtonAction.CallService("button", "press", "button.action$index"),
                entityName = "Action $index"
            )
        }
        var pressed: StreamButton? = null
        showControls(320.dp, buttons, onButtonClick = { pressed = it })

        val labels = listOf("Mute Stream", "Call") + buttons.map(StreamButton::displayName)
        val bounds = labels.map { composeTestRule.onNodeWithContentDescription(it).getUnclippedBoundsInRoot() }
        bounds.take(2).forEach { assertEquals(64f, (it.right - it.left).value, 0.5f) }
        assertTrue(bounds.last().right <= 320.dp)
        assertTrue(bounds.zipWithNext().all { (first, second) -> first.right <= second.left })

        buttons.forEach {
            composeTestRule.onNodeWithText(it.displayName, useUnmergedTree = true).assertDoesNotExist()
        }
        composeTestRule.onNodeWithContentDescription("Action 3").performClick()
        assertEquals(buttons.last(), pressed)
    }

    @Test
    fun `a stream button exposes the selected entity state without adding a label`() {
        val button = StreamButton(
            "script",
            StreamButtonAction.RunScript("script.porch"),
            stateEntityId = "light.porch"
        )
        showControls(
            400.dp,
            listOf(button),
            entityAppearances = mapOf(
                "script.porch" to HomeAssistantEntityAppearance("on"),
                "light.porch" to HomeAssistantEntityAppearance("off")
            )
        )

        composeTestRule.onNodeWithContentDescription(button.displayName)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "off"))
        composeTestRule.onNodeWithText("off", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a script with a fixed icon still shows the selected lock status`() {
        val button = StreamButton(
            "script",
            StreamButtonAction.RunScript("script.front_door"),
            stateEntityId = "lock.front_door",
            icon = "mdi:key"
        )
        val layout = StreamButtonLayout(bottom = listOf(button))
        val appearance = mutableStateOf(HomeAssistantEntityAppearance("locked"))
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    StreamBottomControls(
                        uiState = StreamUiState(
                            streamButtonLayout = layout,
                            entityAppearances = mapOf(button.iconEntityId to appearance.value),
                            isStreamButtonLayoutReady = true
                        ),
                        pulseScale = 1f,
                        onSoundClick = {},
                        onCallClick = {},
                        onButtonClick = {}
                    )
                }
            }
        }

        val control = composeTestRule.onNodeWithContentDescription(button.displayName)
        control.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Locked"))
        composeTestRule.runOnIdle { appearance.value = HomeAssistantEntityAppearance("unlocked") }
        control.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unlocked"))
    }

    private fun showControls(
        width: Dp,
        buttons: List<StreamButton>,
        entityAppearances: Map<String, HomeAssistantEntityAppearance> = emptyMap(),
        onButtonClick: (StreamButton) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.width(width)) {
                    StreamBottomControls(
                        uiState = StreamUiState(
                            streamButtonLayout = StreamButtonLayout(bottom = buttons),
                            entityAppearances = entityAppearances,
                            isStreamButtonLayoutReady = true
                        ),
                        pulseScale = 1f,
                        onSoundClick = {},
                        onCallClick = {},
                        onButtonClick = onButtonClick
                    )
                }
            }
        }
    }
}
