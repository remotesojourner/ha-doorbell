package com.novasoftware.hadoorbell.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.domain.model.HomeAssistantServiceField
import java.io.StringReader

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServiceDataField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    fields: Map<String, HomeAssistantServiceField> = emptyMap()
) {
    var editorValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val fieldValue = if (editorValue.text == value) editorValue else TextFieldValue(value, TextRange(value.length))
    val parsed = parseJsonObject(value)
    val suggestions = fields.entries
        .filter { (name, _) -> parsed?.has(name) != true }
        .sortedWith(compareByDescending<Map.Entry<String, HomeAssistantServiceField>> { it.value.required }.thenBy { it.key })

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                editorValue = it
                if (it.text != value) onValueChange(it.text)
            },
            label = { Text(stringResource(label)) },
            supportingText = {
                Text(stringResource(if (isError) R.string.stream_button_invalid_json else R.string.stream_button_json_object))
            },
            isError = isError,
            modifier = modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6
        )
        if (suggestions.isNotEmpty()) {
            Text(stringResource(R.string.stream_button_add_field), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { (name, field) ->
                    val description = stringResource(R.string.stream_button_add_field_description, name)
                    SuggestionChip(
                        onClick = {
                            insertServiceField(value, name, field)?.let { inserted ->
                                editorValue = inserted
                                onValueChange(inserted.text)
                            }
                        },
                        enabled = parsed != null,
                        label = { Text(if (field.required) "$name *" else name) },
                        modifier = Modifier.semantics { contentDescription = description }
                    )
                }
            }
        }
    }
}

private val prettyJson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

/** Adds [name] to the JSON object in [json] and selects its placeholder value so typing replaces it. */
internal fun insertServiceField(json: String, name: String, field: HomeAssistantServiceField): TextFieldValue? {
    val data = parseJsonObject(json) ?: return null
    if (data.has(name)) return null
    val placeholder = field.defaultValue?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: JsonPrimitive("")
    data.add(name, placeholder)
    val text = prettyJson.toJson(data)
    val valueText = prettyJson.toJson(placeholder)
    val entry = "${prettyJson.toJson(name)}: $valueText"
    val entryStart = text.lastIndexOf(entry)
    if (entryStart < 0) return TextFieldValue(text, TextRange(text.length))
    val valueStart = entryStart + entry.length - valueText.length
    val selection = if (placeholder.isString) {
        TextRange(valueStart + 1, valueStart + valueText.length - 1)
    } else {
        TextRange(valueStart, valueStart + valueText.length)
    }
    return TextFieldValue(text, selection)
}

internal fun parseJsonObject(value: String): JsonObject? {
    if (value.isBlank()) return JsonObject()
    return runCatching {
        JsonReader(StringReader(value)).use { reader ->
            reader.strictness = Strictness.STRICT
            val parsed = JsonParser.parseReader(reader)
            parsed.asJsonObject.takeIf { reader.peek() == JsonToken.END_DOCUMENT }
        }
    }.getOrNull()
}
