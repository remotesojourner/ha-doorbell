package com.novasoftware.hadoorbell.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.novasoftware.hadoorbell.R
import java.io.StringReader

@Composable
internal fun ServiceDataField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    availableFields: Set<String> = emptySet()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        supportingText = {
            Text(stringResource(if (isError) R.string.stream_button_invalid_json else R.string.stream_button_json_object))
        },
        isError = isError,
        modifier = modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5
    )
    if (availableFields.isNotEmpty()) {
        Text(
            stringResource(R.string.stream_button_service_fields, availableFields.sorted().joinToString()),
            style = MaterialTheme.typography.bodySmall
        )
    }
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
