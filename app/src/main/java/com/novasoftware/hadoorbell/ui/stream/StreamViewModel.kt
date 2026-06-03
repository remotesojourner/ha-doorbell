package com.novasoftware.hadoorbell.ui.stream

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.usecase.GetSelectOptionsUseCase
import com.novasoftware.hadoorbell.domain.usecase.LockDoorUseCase
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.usecase.SendQuickReplyUseCase
import com.novasoftware.hadoorbell.domain.usecase.UnlockDoorUseCase
import com.novasoftware.hadoorbell.core.webrtc.WebRtcClientFactory
import com.novasoftware.hadoorbell.core.webrtc.WebRtcManager
import com.novasoftware.hadoorbell.core.webrtc.WebRtcManagerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.VideoSink
import javax.inject.Inject

sealed class UiEvent {
    data class ShowToast(val message: String, val long: Boolean = false) : UiEvent()
    object RequestBiometricAuth : UiEvent()
}

@HiltViewModel
class StreamViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val appPreferences: SettingsRepositoryImpl,
    private val repository: HomeAssistantRepository,
    private val webRtcClientFactory: WebRtcClientFactory,
    private val webRtcManagerFactory: WebRtcManagerFactory,
    private val lockDoorUseCase: LockDoorUseCase,
    private val unlockDoorUseCase: UnlockDoorUseCase,
    private val getSelectOptionsUseCase: GetSelectOptionsUseCase,
    private val sendQuickReplyUseCase: SendQuickReplyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    fun initializeConnection(context: Context) {
        viewModelScope.launch {
            val qrId = appPreferences.quickReplyEntityIdFlow.first() ?: ""
            val lId = appPreferences.lockEntityIdFlow.first() ?: ""
            
            _uiState.update { it.copy(quickReplyEntityId = qrId, lockEntityId = lId) }

            val instantTwoWay = appPreferences.instantTwoWayAudioFlow.first()
            _uiState.update { it.copy(isMicEnabled = instantTwoWay && qrId.isBlank()) }

            if (lId.isNotBlank()) {
                val stateResult = repository.getEntityState(lId)
                _uiState.update { it.copy(lockState = LockState.fromString(stateResult.getOrNull())) }
            }

            val signalingClient = webRtcClientFactory.createClient()
            val manager = webRtcManagerFactory.create(context, signalingClient, viewModelScope)
            _uiState.update { it.copy(webRtcManager = manager) }
        }
    }

    fun startStream(videoSink: VideoSink) {
        val currentState = _uiState.value
        currentState.webRtcManager?.startConnection(videoSink, currentState.isMicEnabled) { error ->
            _uiState.update { it.copy(errorMessage = error) }
        }
    }

    fun teardownConnection() {
        _uiState.value.webRtcManager?.disconnect()
        _uiState.update { 
            it.copy(
                webRtcManager = null,
                isMicEnabled = false,
                isStreamMuted = false
            ) 
        }
    }

    fun toggleMic(context: Context) {
        val currentState = _uiState.value
        if (currentState.webRtcManager == null || currentState.isSwitchingModes) return
        
        _uiState.update { it.copy(isSwitchingModes = true, isMicEnabled = !it.isMicEnabled) }

        viewModelScope.launch {
            _uiState.value.webRtcManager?.disconnect()?.join()
            _uiState.update { it.copy(webRtcManager = null) }
            delay(100)

            val signalingClient = webRtcClientFactory.createClient()
            val manager = WebRtcManager(context, signalingClient, viewModelScope)
            _uiState.update { it.copy(webRtcManager = manager, isSwitchingModes = false) }
        }
    }

    fun toggleStreamMute() {
        _uiState.update { it.copy(isStreamMuted = !it.isStreamMuted) }
        val currentState = _uiState.value
        currentState.webRtcManager?.toggleStreamMute(currentState.isStreamMuted)
    }

    fun openQuickReplySheet() {
        _uiState.update { it.copy(showQuickReplySheet = true) }
    }

    fun dismissQuickReplySheet() {
        _uiState.update { it.copy(showQuickReplySheet = false) }
    }

    fun loadQuickReplyOptions() {
        viewModelScope.launch {
            getSelectOptionsUseCase(_uiState.value.quickReplyEntityId).fold(
                onSuccess = { options ->
                    _uiState.update { it.copy(quickReplyError = null, quickReplyOptions = options) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(quickReplyError = "Failed to load options: ${e.message}") }
                }
            )
        }
    }

    fun sendQuickReply(option: String) {
        viewModelScope.launch {
            sendQuickReplyUseCase(_uiState.value.quickReplyEntityId, option).fold(
                onSuccess = {
                    _uiEvent.emit(UiEvent.ShowToast("Sent: $option"))
                    _uiState.update { it.copy(showQuickReplySheet = false) }
                },
                onFailure = { e ->
                    _uiEvent.emit(UiEvent.ShowToast("Failed: ${e.message}", long = true))
                }
            )
        }
    }

    fun handleLockAction() {
        val currentLockState = _uiState.value.lockState
        if (currentLockState == LockState.Jammed || currentLockState == LockState.Unknown) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowToast(appContext.getString(R.string.stream_cannot_operate_lock, currentLockState.name)))
            }
            return
        }

        val targetState = if (currentLockState == LockState.Unlocked || currentLockState == LockState.Unlocking) LockState.Locked else LockState.Unlocked

        if (targetState == LockState.Locked) {
            executeLockDoor()
        } else {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.RequestBiometricAuth)
            }
        }
    }

    fun executeUnlockDoor() {
        viewModelScope.launch {
            unlockDoorUseCase(_uiState.value.lockEntityId).collect { result ->
                result.fold(
                    onSuccess = { state ->
                        _uiState.update { it.copy(lockState = state) }
                    },
                    onFailure = { e ->
                        _uiEvent.emit(UiEvent.ShowToast("Unlock failed: ${e.message}", long = true))
                        _uiState.update { it.copy(lockState = LockState.Unknown) }
                    }
                )
            }
        }
    }

    private fun executeLockDoor() {
        viewModelScope.launch {
            lockDoorUseCase(_uiState.value.lockEntityId).collect { result ->
                result.fold(
                    onSuccess = { state ->
                        _uiState.update { it.copy(lockState = state) }
                    },
                    onFailure = { e ->
                        _uiEvent.emit(UiEvent.ShowToast("Lock failed: ${e.message}", long = true))
                        _uiState.update { it.copy(lockState = LockState.Unknown) }
                    }
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.webRtcManager?.disconnect()
    }

    companion object {
        // Reserved for future constants if needed
    }
}
