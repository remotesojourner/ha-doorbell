package com.novasoftware.hadoorbell.ui.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class StreamButtonIconTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `automatic icon updates with the entity and a custom icon takes precedence`() {
        val button = mutableStateOf(
            StreamButton(
                id = "porch",
                action = StreamButtonAction.CallService("light", "toggle", "light.porch"),
                entityIcon = "mdi:outdoor-lamp"
            )
        )
        val appearance = mutableStateOf<HomeAssistantEntityAppearance?>(null)
        compose.setContent {
            val icon = requireNotNull(resolveStreamButtonIcon(button.value, appearance.value))
            StreamButtonIcon(icon, contentDescription = icon)
        }

        compose.onNodeWithContentDescription("outdoor-lamp").assertIsDisplayed()
        compose.runOnIdle { appearance.value = HomeAssistantEntityAppearance("off") }
        compose.onNodeWithContentDescription("lightbulb-off").assertIsDisplayed()
        compose.runOnIdle { appearance.value = HomeAssistantEntityAppearance("on") }
        compose.onNodeWithContentDescription("lightbulb").assertIsDisplayed()
        compose.runOnIdle { appearance.value = HomeAssistantEntityAppearance("on", "mdi:coach-lamp") }
        compose.onNodeWithContentDescription("coach-lamp").assertIsDisplayed()
        compose.runOnIdle { button.value = button.value.copy(icon = "mdi:star") }
        compose.onNodeWithContentDescription("star").assertIsDisplayed()
    }

    @Test
    fun `script button shows the state of a separate lock with a static Home Assistant icon`() {
        val button = StreamButton(
            id = "front-door",
            action = StreamButtonAction.RunScript("script.toggle_front_door"),
            stateEntityId = "lock.front_door"
        )
        val appearance = mutableStateOf(HomeAssistantEntityAppearance("unlocked", "mdi:lock-open"))
        compose.setContent {
            val icon = requireNotNull(resolveStreamButtonIcon(button, appearance.value))
            StreamButtonIcon(icon, contentDescription = icon)
        }

        compose.onNodeWithContentDescription("lock-open-variant").assertIsDisplayed()
        compose.runOnIdle { appearance.value = appearance.value.copy(state = "locked") }
        compose.onNodeWithContentDescription("lock").assertIsDisplayed()
        compose.runOnIdle { appearance.value = appearance.value.copy(state = "unavailable") }
        compose.onNodeWithContentDescription("help-circle-outline").assertIsDisplayed()
    }

    @Test
    fun `dedicated lock uses its state glyph in Automatic and respects a manual icon`() {
        val action = StreamButtonAction.DoorLock("lock.front_door")
        val button = mutableStateOf(StreamButton("front-door", action, entityIcon = "mdi:lock-open"))
        val appearance = mutableStateOf(HomeAssistantEntityAppearance("unlocked", "mdi:lock-open"))
        compose.setContent {
            val icon = resolveStreamButtonIcon(button.value, appearance.value)
            if (icon == null) {
                val fallback = streamButtonFallbackIcon(action, LockState.fromString(appearance.value.state))
                Icon(fallback, contentDescription = fallback.name)
            } else {
                StreamButtonIcon(icon, contentDescription = icon)
            }
        }

        compose.onNodeWithContentDescription(streamButtonFallbackIcon(action, LockState.Unlocked).name)
            .assertIsDisplayed()
        compose.runOnIdle { appearance.value = appearance.value.copy(state = "locked") }
        compose.onNodeWithContentDescription(streamButtonFallbackIcon(action, LockState.Locked).name)
            .assertIsDisplayed()
        compose.runOnIdle { button.value = button.value.copy(icon = "mdi:key") }
        compose.onNodeWithContentDescription("key").assertIsDisplayed()
    }
}
