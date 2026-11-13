package com.novasoftware.hadoorbell.ui.icons

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.novasoftware.hadoorbell.R
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
    currentEntityIcon: String?,
    hasCurrentEntityIcon: Boolean = currentEntityIcon != null
): String? {
    val context = LocalContext.current
    val icons = remember(context) { MaterialDesignIcons.glyphs(context) }
    val entityIcon = if (hasCurrentEntityIcon) currentEntityIcon else button.entityIcon
    val resolved = button.icon?.iconName()?.takeIf(icons::containsKey)
        ?: entityIcon?.iconName()?.takeIf(icons::containsKey)
    if (resolved != null) return resolved

    return when (button.action) {
        is StreamButtonAction.DoorLock, is StreamButtonAction.OpenDoor, is StreamButtonAction.QuickReply -> null
        else -> defaultHomeAssistantIcon(button.action.entityId)?.takeIf(icons::containsKey)
    }
}

internal fun defaultHomeAssistantIcon(entityId: String): String? = when (entityId.substringBefore('.')) {
    "automation" -> "robot"
    "button", "input_button" -> "gesture-tap-button"
    "climate" -> "thermostat"
    "cover" -> "window-shutter"
    "fan" -> "fan"
    "humidifier" -> "air-humidifier"
    "input_boolean" -> "toggle-switch"
    "input_select", "select" -> "format-list-bulleted"
    "light" -> "lightbulb"
    "lock" -> "lock"
    "media_player" -> "cast"
    "remote" -> "remote"
    "scene" -> "palette"
    "script" -> "script-text"
    "siren" -> "bullhorn"
    "switch" -> "toggle-switch"
    "valve" -> "valve"
    else -> null
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
