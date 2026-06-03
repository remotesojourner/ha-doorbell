package com.novasoftware.hadoorbell.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import com.novasoftware.hadoorbell.domain.model.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = getSettingsUseCase().first()
            _uiState.update {
                it.copy(
                    url = settings.url,
                    token = settings.token,
                    streamSource = settings.streamSource,
                    quickReplyEntityId = settings.quickReplyEntityId,
                    lockEntityId = settings.lockEntityId,
                    instantTwoWayAudio = settings.instantTwoWayAudio,
                    webrtcProvider = settings.webrtcProvider,
                    isLoaded = true
                )
            }
        }
    }

    fun updateUrl(value: String) { _uiState.update { it.copy(url = value) } }
    fun updateToken(value: String) { _uiState.update { it.copy(token = value) } }
    fun updateStreamSource(value: String) { _uiState.update { it.copy(streamSource = value) } }
    fun updateQuickReplyEntityId(value: String) {
        _uiState.update { 
            it.copy(
                quickReplyEntityId = value,
                instantTwoWayAudio = if (value.isNotBlank()) false else it.instantTwoWayAudio
            )
        }
    }
    fun updateLockEntityId(value: String) { _uiState.update { it.copy(lockEntityId = value) } }
    fun updateInstantTwoWayAudio(value: Boolean) { _uiState.update { it.copy(instantTwoWayAudio = value) } }
    fun updateWebrtcProvider(value: String) { _uiState.update { it.copy(webrtcProvider = value) } }

    fun saveSettings(onComplete: () -> Unit) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val settings = Settings(
                url = currentState.url,
                token = currentState.token,
                streamSource = currentState.streamSource,
                quickReplyEntityId = currentState.quickReplyEntityId,
                lockEntityId = currentState.lockEntityId,
                instantTwoWayAudio = currentState.instantTwoWayAudio,
                webrtcProvider = currentState.webrtcProvider
            )
            saveSettingsUseCase(settings)
            onComplete()
        }
    }
}
