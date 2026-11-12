package com.novasoftware.hadoorbell.ui.stream

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.usecase.CallActionUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSelectOptionsUseCase
import com.novasoftware.hadoorbell.domain.usecase.LockDoorUseCase
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.usecase.OpenDoorUseCase
import com.novasoftware.hadoorbell.domain.usecase.SendQuickReplyUseCase
import com.novasoftware.hadoorbell.domain.usecase.UnlockDoorUseCase
import com.novasoftware.hadoorbell.core.webrtc.WebRtcClientFactory
import com.novasoftware.hadoorbell.core.webrtc.WebRtcManagerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.VideoSink
import javax.inject.Inject

sealed class UiEvent {
    data class ShowToast(val message: String, val long: Boolean = false) : UiEvent()
    data class RequestBiometricAuth(val forDoor: Boolean = false) : UiEvent()
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
    private val sendQuickReplyUseCase: SendQuickReplyUseCase,
    private val openDoorUseCase: OpenDoorUseCase,
    private val callActionUseCase: CallActionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val activeConnection = MutableStateFlow<Job?>(null)
    private var isScreenActive = false
    private var callAnswered = false
    private var answerJob: Job? = null

    fun initializeConnection(context: Context) {
        isScreenActive = true
        viewModelScope.launch {
            val qrId = appPreferences.quickReplyEntityIdFlow.first() ?: ""
            val lId = appPreferences.lockEntityIdFlow.first() ?: ""
            val dId = appPreferences.doorEntityIdFlow.first() ?: ""
            val dService = appPreferences.doorServiceFlow.first() ?: ""
            val answerService = appPreferences.answerActionServiceFlow.first() ?: ""
            val answerEntity = appPreferences.answerActionEntityIdFlow.first() ?: ""
            val hangupService = appPreferences.hangupActionServiceFlow.first() ?: ""
            val hangupEntity = appPreferences.hangupActionEntityIdFlow.first() ?: ""

            _uiState.update {
                it.copy(
                    quickReplyEntityId = qrId,
                    lockEntityId = lId,
                    doorEntityId = dId,
                    doorService = dService,
                    answerActionService = answerService,
                    answerActionEntityId = answerEntity,
                    hangupActionService = hangupService,
                    hangupActionEntityId = hangupEntity
                )
            }

            val instantTwoWay = appPreferences.instantTwoWayAudioFlow.first()
            val micEnabled = instantTwoWay && qrId.isBlank()
            _uiState.update { it.copy(isMicEnabled = micEnabled) }
            if (micEnabled) {
                callAnswered = true
            }

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
        val manager = currentState.webRtcManager ?: return
        val connection = manager.startConnection(videoSink, currentState.isMicEnabled) { error ->
            _uiState.update { it.copy(errorMessage = error) }
        }
        activeConnection.value = connection
    }

    fun teardownConnection() {
        isScreenActive = false
        answerJob?.cancel()
        answerJob = null
        val shouldHangUp = callAnswered
        callAnswered = false

        _uiState.value.webRtcManager?.disconnect()
        activeConnection.value = null
        _uiState.update {
            it.copy(
                webRtcManager = null,
                isMicEnabled = false,
                isStreamMuted = false,
                isSwitchingModes = false
            )
        }

        if (shouldHangUp) {
            sendHangUpAction()
        }
    }

    fun toggleMic(context: Context) {
        val currentState = _uiState.value
        if (currentState.webRtcManager == null || currentState.isSwitchingModes) return

        if (currentState.isMicEnabled) {
            hangUpCall(context)
        } else {
            answerCall(context)
        }
    }

    fun answerCall(context: Context) {
        val currentState = _uiState.value
        if (currentState.webRtcManager == null || currentState.isSwitchingModes || currentState.isMicEnabled) return

        _uiState.update { it.copy(isSwitchingModes = true, isMicEnabled = true) }

        answerJob = viewModelScope.launch {
            try {
                restartConnection(context)

                if (!awaitConnectionReady()) {
                    abortFailedCall(context)
                    _uiState.update { it.copy(isMicEnabled = false) }
                    _uiEvent.emit(UiEvent.ShowToast("Call setup failed: no connection", long = true))
                    return@launch
                }

                if (!isScreenActive) return@launch

                callAnswered = true
                runCallAction(
                    service = _uiState.value.answerActionService,
                    entityId = _uiState.value.answerActionEntityId,
                    label = "Answer"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isMicEnabled = false) }
                _uiEvent.emit(UiEvent.ShowToast("Call setup failed: ${e.message}", long = true))
            } finally {
                _uiState.update { it.copy(isSwitchingModes = false) }
            }
        }
    }

    fun hangUpCall(context: Context) {
        val currentState = _uiState.value
        if (currentState.webRtcManager == null || currentState.isSwitchingModes || !currentState.isMicEnabled) return

        // Stop transmitting audio before the Home Assistant call, which can take seconds.
        currentState.webRtcManager.setMicrophoneEnabled(false)
        callAnswered = false
        _uiState.update { it.copy(isSwitchingModes = true, isMicEnabled = false) }

        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    runCallAction(
                        service = _uiState.value.hangupActionService,
                        entityId = _uiState.value.hangupActionEntityId,
                        label = "Hang up"
                    )
                }
                if (isScreenActive) {
                    restartConnection(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("Hang up failed: ${e.message}", long = true))
            } finally {
                _uiState.update { it.copy(isSwitchingModes = false) }
            }
        }
    }

    private fun sendHangUpAction() {
        val state = _uiState.value
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCallAction(
                    service = state.hangupActionService,
                    entityId = state.hangupActionEntityId,
                    label = "Hang up"
                )
            }
        }
    }

    private suspend fun runCallAction(service: String, entityId: String, label: String) {
        if (service.isBlank()) return

        val result = withTimeoutOrNull(CALL_ACTION_TIMEOUT_MS) {
            callActionUseCase(service, entityId)
        }

        if (result == null) {
            _uiEvent.emit(UiEvent.ShowToast("$label action timed out", long = true))
            return
        }

        result.onFailure { error ->
            _uiEvent.emit(UiEvent.ShowToast("$label action failed: ${error.message}", long = true))
        }
    }

    private suspend fun restartConnection(context: Context) {
        _uiState.value.webRtcManager?.disconnect()?.join()
        activeConnection.value = null
        _uiState.update { it.copy(webRtcManager = null, errorMessage = null) }
        delay(100)

        val signalingClient = webRtcClientFactory.createClient()
        val manager = webRtcManagerFactory.create(context, signalingClient, viewModelScope)
        _uiState.update { it.copy(webRtcManager = manager) }
    }

    private suspend fun awaitConnectionReady(): Boolean {
        val connection = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            activeConnection.filterNotNull().first()
        } ?: return false

        val completed = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) { connection.join() }
        return completed != null && _uiState.value.errorMessage == null
    }

    private suspend fun abortFailedCall(context: Context) {
        val manager = _uiState.value.webRtcManager
        // Mute first: the local track stays alive until the asynchronous teardown runs.
        manager?.setMicrophoneEnabled(false)
        activeConnection.value?.cancel()
        activeConnection.value = null

        if (isScreenActive) {
            // Recreate a listen-only session so the preview recovers and the next Call starts clean.
            restartConnection(context)
        } else {
            manager?.disconnect()
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
                _uiEvent.emit(UiEvent.RequestBiometricAuth())
            }
        }
    }

    fun handleDoorAction() {
        if (_uiState.value.doorEntityId.isBlank()) return

        viewModelScope.launch {
            _uiEvent.emit(UiEvent.RequestBiometricAuth(forDoor = true))
        }
    }

    fun executeOpenDoor() {
        viewModelScope.launch {
            openDoorUseCase(
                entityId = _uiState.value.doorEntityId,
                service = _uiState.value.doorService
            ).onFailure { error ->
                _uiEvent.emit(UiEvent.ShowToast("Open door failed: ${error.message}", long = true))
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
        answerJob?.cancel()
        _uiState.value.webRtcManager?.disconnect()
    }

    companion object {
        private const val CALL_ACTION_TIMEOUT_MS = 10_000L
        private const val CONNECTION_TIMEOUT_MS = 15_000L
    }
}
