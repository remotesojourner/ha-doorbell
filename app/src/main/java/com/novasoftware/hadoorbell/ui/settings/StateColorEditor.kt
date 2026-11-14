package com.novasoftware.hadoorbell.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.domain.model.normalizeStreamButtonColor

@Composable
internal fun StateColorEditor(
    entityId: String,
    currentState: String?,
    entityOptions: List<String>,
    stateColors: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var editingState by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedState = editingState
    if (selectedState != null) {
        StateColorDialog(
            selectedState = selectedState,
            selectedColor = stateColors[selectedState].orEmpty(),
            existingStates = stateColors.keys,
            onSave = { state, color ->
                onChange(stateColors + (state to color))
                editingState = null
            },
            onRemove = {
                onChange(stateColors - selectedState)
                editingState = null
            },
            onDismiss = { editingState = null }
        )
        return
    }

    val states = buildList {
        currentState?.takeIf(String::isNotBlank)?.let(::add)
        addAll(entityOptions.filter(String::isNotBlank))
        addAll(suggestedStates(entityId.substringBefore('.')))
        addAll(stateColors.keys)
        add("unavailable")
        add("unknown")
    }.distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stream_button_state_colors_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.stream_button_state_colors_description),
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(states, key = { it }) { state ->
                        val color = stateColors[state]
                        Surface(onClick = { editingState = state }) {
                            ListItem(
                                headlineContent = { Text(state) },
                                supportingContent = {
                                    Text(color ?: stringResource(R.string.stream_button_state_color_default))
                                },
                                trailingContent = {
                                    color?.let { ColorSwatch(Color(it.toColorInt())) }
                                }
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { editingState = "" }) {
                Text(stringResource(R.string.stream_button_state_color_add_custom))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.stream_button_done)) }
        }
    )
}

@Composable
private fun StateColorDialog(
    selectedState: String,
    selectedColor: String,
    existingStates: Set<String>,
    onSave: (String, String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var state by rememberSaveable { mutableStateOf(selectedState) }
    var colorText by rememberSaveable { mutableStateOf(selectedColor) }
    val color = normalizeStreamButtonColor(colorText)
    val isCustom = selectedState.isEmpty()
    val isDuplicate = isCustom && state in existingStates
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isCustom) stringResource(R.string.stream_button_state_color_add_custom)
                else stringResource(R.string.stream_button_state_color_for, selectedState)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCustom) {
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        modifier = Modifier.fillMaxWidth().testTag("state_color_state"),
                        label = { Text(stringResource(R.string.stream_button_state_color_state)) },
                        isError = isDuplicate,
                        supportingText = {
                            Text(
                                stringResource(
                                    if (isDuplicate) R.string.stream_button_state_color_exists
                                    else R.string.stream_button_state_color_exact
                                )
                            )
                        },
                        singleLine = true
                    )
                }
                colorPresets.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { preset ->
                            val presetColor = Color(preset.hex.toColorInt())
                            val label = stringResource(preset.name)
                            val isSelected = preset.hex == color
                            IconButton(
                                onClick = { colorText = preset.hex },
                                modifier = Modifier.semantics {
                                    contentDescription = label
                                    selected = isSelected
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    ColorSwatch(presetColor)
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (presetColor.luminance() > 0.5f) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = colorText,
                    onValueChange = { colorText = it },
                    modifier = Modifier.fillMaxWidth().testTag("state_color_hex"),
                    label = { Text(stringResource(R.string.stream_button_state_color_hex)) },
                    supportingText = { Text(stringResource(R.string.stream_button_state_color_format)) },
                    trailingIcon = { color?.let { ColorSwatch(Color(it.toColorInt())) } },
                    isError = colorText.isNotBlank() && color == null,
                    singleLine = true
                )
                if (selectedColor.isNotEmpty()) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.stream_button_state_color_remove))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        confirmButton = {
            TextButton(
                enabled = state.isNotBlank() && color != null && !isDuplicate,
                onClick = { color?.let { onSave(state, it) } }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun ColorSwatch(color: Color) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.background(color))
    }
}

private data class StateColorPreset(@param:StringRes val name: Int, val hex: String)

private val colorPresets = listOf(
    StateColorPreset(R.string.stream_button_state_color_red, "#C62828"),
    StateColorPreset(R.string.stream_button_state_color_orange, "#EF6C00"),
    StateColorPreset(R.string.stream_button_state_color_yellow, "#F9A825"),
    StateColorPreset(R.string.stream_button_state_color_green, "#2E7D32"),
    StateColorPreset(R.string.stream_button_state_color_blue, "#1565C0"),
    StateColorPreset(R.string.stream_button_state_color_purple, "#6A1B9A"),
    StateColorPreset(R.string.stream_button_state_color_white, "#FFFFFF"),
    StateColorPreset(R.string.stream_button_state_color_black, "#212121")
)

private fun suggestedStates(domain: String): List<String> = when (domain) {
    "automation", "binary_sensor", "fan", "humidifier", "input_boolean", "light",
    "remote", "script", "siren", "switch" -> listOf("on", "off")
    "alarm_control_panel" -> listOf(
        "disarmed", "armed_home", "armed_away", "armed_night", "armed_vacation",
        "armed_custom_bypass", "arming", "pending", "triggered"
    )
    "climate" -> listOf("off", "heat", "cool", "heat_cool", "auto", "dry", "fan_only")
    "cover", "valve" -> listOf("closed", "open", "closing", "opening")
    "device_tracker", "person" -> listOf("home", "not_home")
    "lock" -> listOf("locked", "unlocked", "locking", "unlocking", "open", "opening", "jammed")
    "media_player" -> listOf("off", "on", "idle", "playing", "paused", "buffering", "standby")
    else -> emptyList()
}
