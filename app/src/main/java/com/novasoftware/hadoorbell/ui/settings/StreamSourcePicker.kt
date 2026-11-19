package com.novasoftware.hadoorbell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.novasoftware.hadoorbell.R

@Composable
internal fun Go2rtcStreamPicker(
    streams: List<String>,
    isLoading: Boolean,
    loadError: String?,
    selectedStream: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(streams, query) {
        val search = query.trim()
        streams.filter { it.contains(search, ignoreCase = true) }
    }

    PickerSheet(title = stringResource(R.string.settings_choose_stream), onDismiss = onDismiss) {
        SearchField(query, { query = it }, R.string.settings_search_streams)
        when {
            isLoading -> LoadingRow(R.string.settings_loading_streams)
            loadError != null -> Column {
                Text(loadError, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.stream_button_retry)) }
            }
            streams.isEmpty() -> Column {
                Text(stringResource(R.string.settings_no_streams))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.stream_button_refresh)) }
            }
            matches.isEmpty() -> Text(stringResource(R.string.settings_no_stream_matches))
            else -> LazyColumn(modifier = PickerListModifier) {
                items(matches, key = { it }) { stream ->
                    PickerChoice(stream, null, stream == selectedStream) { onSelect(stream) }
                }
            }
        }
    }
}
