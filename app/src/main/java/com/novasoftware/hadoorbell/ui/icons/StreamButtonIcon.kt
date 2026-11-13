package com.novasoftware.hadoorbell.ui.icons

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction

private object MaterialDesignIcons {
    private val featured = listOf(
        "bell", "bell-ring", "door", "door-open", "lock", "lock-open", "key", "garage",
        "gate", "lightbulb", "lightbulb-on", "toggle-switch", "power", "gesture-tap-button",
        "format-list-bulleted", "message-reply-text", "script-text", "play", "shield-home", "camera"
    )
    private var glyphs: Map<String, String>? = null
    private var choices: List<String>? = null

    @Synchronized
    fun glyphs(context: Context): Map<String, String> = glyphs ?: context.assets
        .open("material-design-icons/icons.txt")
        .bufferedReader()
        .useLines { lines ->
            lines.associate { line ->
                val separator = line.indexOf(' ')
                val name = line.substring(0, separator)
                val codePoint = line.substring(separator + 1).toInt(16)
                name to String(Character.toChars(codePoint))
            }
        }.also { glyphs = it }

    @Synchronized
    fun choices(context: Context): List<String> = choices ?: glyphs(context).keys.let { names ->
        val suggestions = featured.filter { it in names }
        suggestions + (names - suggestions.toSet()).sorted()
    }.also { choices = it }
}

private fun String.iconName(): String = trim().lowercase().removePrefix("mdi:")

@Composable
internal fun rememberStreamButtonIconChoices(): List<String> {
    val context = LocalContext.current
    return remember(context) { MaterialDesignIcons.choices(context) }
}

@Composable
internal fun resolveStreamButtonIcon(
    button: StreamButton,
    entityAppearance: HomeAssistantEntityAppearance?
): String? = resolveStreamButtonIcon(
    action = button.action,
    entityId = button.iconEntityId,
    selectedIcon = button.icon,
    cachedEntityIcon = button.entityIcon,
    entityAppearance = entityAppearance
)

@Composable
internal fun resolveStreamButtonIcon(
    action: StreamButtonAction?,
    entityId: String,
    selectedIcon: String?,
    cachedEntityIcon: String?,
    entityAppearance: HomeAssistantEntityAppearance?
): String? {
    val context = LocalContext.current
    val icons = remember(context) { MaterialDesignIcons.glyphs(context) }
    val entityIcon = if (entityAppearance != null) entityAppearance.icon else cachedEntityIcon
    val resolved = selectedIcon?.iconName()?.takeIf(icons::containsKey)
        ?: entityIcon?.iconName()?.takeIf(icons::containsKey)
    if (resolved != null) return resolved

    val hasActionIcon = when (action) {
        is StreamButtonAction.DoorLock, is StreamButtonAction.QuickReply -> true
        is StreamButtonAction.CallService -> action.doorRelease
        else -> false
    }
    if (hasActionIcon && entityId == action?.entityId) return null

    return defaultHomeAssistantIcon(entityId, entityAppearance)?.takeIf(icons::containsKey)
}

private fun defaultHomeAssistantIcon(
    entityId: String,
    appearance: HomeAssistantEntityAppearance?
): String? = when (entityId.substringBefore('.')) {
    "alarm_control_panel" -> when (appearance?.state) {
        "armed_away" -> "shield-lock"
        "armed_custom_bypass" -> "security"
        "armed_home" -> "shield-home"
        "armed_night" -> "shield-moon"
        "armed_vacation" -> "shield-airplane"
        "disarmed" -> "shield-off"
        "pending" -> "shield-outline"
        "triggered" -> "bell-ring"
        else -> "shield"
    }
    "automation" -> "robot"
    "binary_sensor" -> binarySensorIcon(appearance)
    "button", "input_button" -> "gesture-tap-button"
    "climate" -> "thermostat"
    "cover" -> coverIcon(appearance)
    "fan" -> if (appearance?.state == "off") "fan-off" else "fan"
    "humidifier" -> "air-humidifier"
    "input_boolean" -> if (appearance?.state == "off") "close-circle-outline" else "check-circle-outline"
    "input_select", "select" -> "format-list-bulleted"
    "light" -> if (appearance?.state == "off") "lightbulb-off" else "lightbulb"
    "lock" -> when (appearance?.state) {
        "open", "unlocked" -> "lock-open-variant"
        "opening", "locking", "unlocking" -> "lock-clock"
        "jammed" -> "lock-alert"
        else -> "lock"
    }
    "media_player" -> "cast"
    "remote" -> "remote"
    "scene" -> "palette"
    "script" -> "script-text"
    "siren" -> "bullhorn"
    "switch" -> if (appearance?.deviceClass == "outlet") {
        if (appearance.state == "off") "power-plug-off" else "power-plug"
    } else {
        if (appearance?.state == "off") "toggle-switch-variant-off" else "toggle-switch-variant"
    }
    "valve" -> "valve"
    else -> null
}

private fun binarySensorIcon(appearance: HomeAssistantEntityAppearance?): String {
    val (off, on) = when (appearance?.deviceClass) {
        "door" -> "door-closed" to "door-open"
        "garage_door" -> "garage" to "garage-open"
        "lock" -> "lock" to "lock-open"
        "motion" -> "motion-sensor-off" to "motion-sensor"
        "occupancy", "presence" -> "home-outline" to "home"
        "opening" -> "square" to "square-outline"
        "window" -> "window-closed" to "window-open"
        else -> "radiobox-blank" to "checkbox-marked-circle"
    }
    return if (appearance?.state == "on") on else off
}

private fun coverIcon(appearance: HomeAssistantEntityAppearance?): String {
    if (appearance == null) return "window-shutter"
    val (open, closed) = when (appearance.deviceClass) {
        "blind" -> "blinds-horizontal" to "blinds-horizontal-closed"
        "curtain" -> "curtains" to "curtains-closed"
        "damper" -> "circle" to "circle-slice-8"
        "door" -> "door-open" to "door-closed"
        "garage" -> "garage-open" to "garage"
        "gate" -> "gate-open" to "gate"
        "shade" -> "roller-shade" to "roller-shade-closed"
        "shutter" -> "window-shutter-open" to "window-shutter"
        else -> "window-open" to "window-closed"
    }
    return when (appearance.state) {
        "closed" -> closed
        "opening", "closing" -> when (appearance.deviceClass) {
            "curtain" -> if (appearance.state == "opening") "arrow-split-vertical" else "arrow-collapse-horizontal"
            "door", "damper" -> open
            "gate" -> "arrow-right"
            else -> if (appearance.state == "opening") "arrow-up-box" else "arrow-down-box"
        }
        else -> open
    }
}

internal fun streamButtonFallbackIcon(
    action: StreamButtonAction,
    lockState: LockState = LockState.Locked
): ImageVector = when (action) {
    is StreamButtonAction.CallService -> when {
        action.doorRelease -> Icons.Default.LockOpen
        action.service == "toggle" -> Icons.Default.ToggleOn
        action.service == "press" -> Icons.Default.TouchApp
        action.service == "select_option" -> Icons.AutoMirrored.Filled.List
        action.service == "turn_on" -> Icons.Default.PowerSettingsNew
        else -> Icons.Default.Bolt
    }
    is StreamButtonAction.RunScript -> Icons.Default.PlayArrow
    is StreamButtonAction.TriggerAutomation -> Icons.Default.Bolt
    is StreamButtonAction.FireEvent -> Icons.Default.Notifications
    is StreamButtonAction.QuickReply -> Icons.AutoMirrored.Filled.Chat
    is StreamButtonAction.DoorLock -> when (lockState) {
        LockState.Locked -> Icons.Default.Lock
        LockState.Unlocked -> Icons.Default.LockOpen
        LockState.Locking, LockState.Unlocking -> Icons.Default.Sync
        LockState.Jammed -> Icons.Default.Warning
        LockState.Unknown -> Icons.Default.QuestionMark
    }
}

@Composable
internal fun StreamButtonIcon(
    icon: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val context = LocalContext.current
    val icons = remember(context) { MaterialDesignIcons.glyphs(context) }
    val glyph = icons[icon.iconName()] ?: icons.getValue("help-circle-outline")
    val typeface = remember(context) { ResourcesCompat.getFont(context, R.font.material_design_icons) }
    val paint = remember(typeface, tint) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = tint.toArgb()
        }
    }
    val bounds = remember { Rect() }
    val semantics = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
    }
    Canvas(modifier.size(24.dp).then(semantics)) {
        paint.textSize = size.minDimension
        paint.getTextBounds(glyph, 0, glyph.length, bounds)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                glyph,
                center.x - bounds.exactCenterX(),
                center.y - bounds.exactCenterY(),
                paint
            )
        }
    }
}
