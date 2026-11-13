package com.novasoftware.hadoorbell.ui.stream

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        val reply = StreamButton("reply", "Quick Reply", StreamButtonAction.QuickReply("select.reply"))
        val door = StreamButton("door", "Open Door", StreamButtonAction.OpenDoor("button.door"))
        val hiddenLock = StreamButton(
            "lock", "Door Lock", StreamButtonAction.DoorLock("lock.door"), visible = false
        )
        showControls(400.dp, listOf(reply, door, hiddenLock))

        val expectedLeftEdges = mapOf("Mute Stream" to 40f, "Call" to 128f, "Quick Reply" to 216f, "Open Door" to 296f)
        expectedLeftEdges.forEach { (label, expectedLeft) ->
            val bounds = composeTestRule.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            assertEquals(label, 64f, (bounds.right - bounds.left).value, 0.5f)
            assertEquals(label, 64f, (bounds.bottom - bounds.top).value, 0.5f)
            assertEquals(label, expectedLeft, bounds.left.value, 0.5f)
        }
        composeTestRule.onNodeWithContentDescription("Door Lock").assertDoesNotExist()
    }

    @Test
    fun `a narrow row keeps both fixed controls and fits all three custom actions`() {
        val buttons = (1..3).map { index ->
            StreamButton("action$index", "Action $index", StreamButtonAction.Press("button.action$index"))
        }
        var pressed: StreamButton? = null
        showControls(320.dp, buttons, onButtonClick = { pressed = it })

        val labels = listOf("Mute Stream", "Call") + buttons.map(StreamButton::label)
        val bounds = labels.map { composeTestRule.onNodeWithContentDescription(it).getUnclippedBoundsInRoot() }
        bounds.take(2).forEach { assertEquals(64f, (it.right - it.left).value, 0.5f) }
        assertTrue(bounds.last().right <= 320.dp)
        assertTrue(bounds.zipWithNext().all { (first, second) -> first.right <= second.left })

        composeTestRule.onNodeWithContentDescription("Action 3").performClick()
        assertEquals(buttons.last(), pressed)
    }

    private fun showControls(width: Dp, buttons: List<StreamButton>, onButtonClick: (StreamButton) -> Unit = {}) {
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.width(width)) {
                    StreamBottomControls(
                        uiState = StreamUiState(
                            streamButtonLayout = StreamButtonLayout(bottom = buttons),
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
