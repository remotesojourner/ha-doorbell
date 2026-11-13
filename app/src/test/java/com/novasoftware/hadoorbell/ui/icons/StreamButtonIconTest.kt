package com.novasoftware.hadoorbell.ui.icons

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
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
    fun `script button can show the state of a separate lock`() {
        val button = StreamButton(
            id = "front-door",
            action = StreamButtonAction.RunScript("script.toggle_front_door"),
            stateEntityId = "lock.front_door"
        )
        val appearance = mutableStateOf(HomeAssistantEntityAppearance("locked"))
        compose.setContent {
            val icon = requireNotNull(resolveStreamButtonIcon(button, appearance.value))
            StreamButtonIcon(icon, contentDescription = icon)
        }

        compose.onNodeWithContentDescription("lock").assertIsDisplayed()
        compose.runOnIdle { appearance.value = HomeAssistantEntityAppearance("unlocked") }
        compose.onNodeWithContentDescription("lock-open-variant").assertIsDisplayed()
    }
}
