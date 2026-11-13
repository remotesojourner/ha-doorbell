package com.novasoftware.hadoorbell.ui.stream

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.usecase.CallActionUseCase
import com.novasoftware.hadoorbell.domain.usecase.ExecuteStreamButtonUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSelectOptionsUseCase
import com.novasoftware.hadoorbell.domain.usecase.LockDoorUseCase
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
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
    data class AuthenticateStreamButton(val button: StreamButton) : UiEvent()
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
    private val callActionUseCase: CallActionUseCase,
    private val executeStreamButtonUseCase: ExecuteStreamButtonUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val activeConnection = MutableStateFlow<Job?>(null)
    private var isScreenActive = false
    private var callAnswered = false
    private var initializeJob: Job? = null
    private var answerJob: Job? = null
    private var quickReplyLoadJob: Job? = null
    private var pendingButtonAuthentication: UiEvent.AuthenticateStreamButton? = null

    fun initializeConnection(context: Context) {
        initializeJob?.cancel()
        isScreenActive = true
        initializeJob = viewModelScope.launch {
            val layout = appPreferences.streamButtonLayoutFlow.first()
            val answerService = appPreferences.answerActionServiceFlow.first() ?: ""
            val answerEntity = appPreferences.answerActionEntityIdFlow.first() ?: ""
            val hangupService = appPreferences.hangupActionServiceFlow.first() ?: ""
            val hangupEntity = appPreferences.hangupActionEntityIdFlow.first() ?: ""

            _uiState.update {
                it.copy(
                    lockStates = emptyMap(),
                    entityIcons = emptyMap(),
                    answerActionService = answerService,
                    answerActionEntityId = answerEntity,
                    hangupActionService = hangupService,
                    hangupActionEntityId = hangupEntity,
                    streamButtonLayout = layout,
                    isStreamButtonLayoutReady = true
                )
            }
            pendingButtonAuthentication?.let { request ->
                if (!isConfiguredButton(request.button)) cancelStreamButtonAuthentication()
            }

            val instantTwoWay = appPreferences.instantTwoWayAudioFlow.first()
            val micEnabled = instantTwoWay && !layout.hasQuickReply
            _uiState.update { it.copy(isMicEnabled = micEnabled) }
            if (micEnabled) {
                callAnswered = true
            }

            val signalingClient = webRtcClientFactory.createClient()
            val manager = webRtcManagerFactory.create(context, signalingClient, viewModelScope)
            _uiState.update { it.copy(webRtcManager = manager) }

            val visibleButtons = layout.buttons.filter(StreamButton::visible)
            visibleButtons.asSequence()
                .filter { it.icon.isNullOrBlank() }
                .map { it.action.entityId }
                .filter(String::isNotBlank)
                .distinct()
                .forEach { entityId ->
                    launch {
                        repository.getEntityIcon(entityId).onSuccess { icon ->
                            _uiState.update { it.copy(entityIcons = it.entityIcons + (entityId to icon)) }
                        }
                    }
                }

            visibleButtons
                .mapNotNull { (it.action as? StreamButtonAction.DoorLock)?.entityId }
                .distinct()
                .forEach { entityId ->
                    launch {
                        val result = repository.getEntityState(entityId)
                        if (entityId !in _uiState.value.runningLockEntityIds) {
                            updateLockState(entityId, LockState.fromString(result.getOrNull()))
                        }
                    }
                }
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
        initializeJob?.cancel()
        initializeJob = null
        dismissQuickReplySheet()
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
                isSwitchingModes = false,
                isStreamButtonLayoutReady = false
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

    fun handleStreamButton(button: StreamButton) {
        val state = _uiState.value
        if (!state.canRun(button) || !isConfiguredButton(button)) return
        when (val action = button.action) {
            is StreamButtonAction.DoorLock -> when (val lockState = state.lockStates[action.entityId] ?: LockState.Unknown) {
                LockState.Jammed, LockState.Unknown -> viewModelScope.launch {
                    val message = appContext.getString(R.string.stream_cannot_operate_lock, lockState.name)
                    _uiEvent.emit(UiEvent.ShowToast(message))
                }
                LockState.Unlocked, LockState.Unlocking -> executeLockAction(button, action.entityId, unlock = false)
                else -> requestStreamButtonAuthentication(button)
            }
            else -> if (button.requiresAuthentication || action is StreamButtonAction.CallService && action.doorRelease) {
                requestStreamButtonAuthentication(button)
            } else {
                executeStreamButton(button)
            }
        }
    }

    private fun requestStreamButtonAuthentication(button: StreamButton) {
        if (pendingButtonAuthentication != null) return
        val request = UiEvent.AuthenticateStreamButton(button)
        pendingButtonAuthentication = request
        viewModelScope.launch { _uiEvent.emit(request) }
    }

    fun onStreamButtonAuthentication(request: UiEvent.AuthenticateStreamButton, succeeded: Boolean) {
        if (pendingButtonAuthentication !== request) return
        cancelStreamButtonAuthentication()
        if (succeeded && isConfiguredButton(request.button)) {
            executeStreamButton(request.button)
        }
    }

    fun cancelStreamButtonAuthentication() {
        pendingButtonAuthentication = null
    }

    private fun isConfiguredButton(button: StreamButton): Boolean {
        return button.visible && button in _uiState.value.streamButtonLayout.buttons
    }

    private fun executeStreamButton(button: StreamButton) {
        when (val action = button.action) {
            is StreamButtonAction.QuickReply -> openQuickReplySheet(button, action.entityId)
            is StreamButtonAction.DoorLock -> executeLockAction(button, action.entityId, unlock = true)
            else -> executeServiceButton(button)
        }
    }

    private fun executeServiceButton(button: StreamButton) {
        if (button.id in _uiState.value.runningStreamButtonIds) return
        _uiState.update { it.copy(runningStreamButtonIds = it.runningStreamButtonIds + button.id) }
        viewModelScope.launch {
            try {
                executeStreamButtonUseCase(button.action).onFailure { showButtonFailure(button, it) }
            } finally {
                _uiState.update { it.copy(runningStreamButtonIds = it.runningStreamButtonIds - button.id) }
            }
        }
    }

    private fun openQuickReplySheet(button: StreamButton, entityId: String) {
        quickReplyLoadJob?.cancel()
        val sheet = QuickReplySheetState(button)
        _uiState.update { it.copy(quickReplySheet = sheet) }
        quickReplyLoadJob = viewModelScope.launch {
            val result = getSelectOptionsUseCase(entityId)
            _uiState.update { state ->
                if (state.quickReplySheet !== sheet) return@update state
                val error = result.exceptionOrNull()?.let {
                    appContext.getString(R.string.stream_button_failed, button.displayName, errorReason(it))
                }
                state.copy(quickReplySheet = sheet.copy(options = result.getOrNull(), error = error))
            }
        }
    }

    fun dismissQuickReplySheet() {
        quickReplyLoadJob?.cancel()
        quickReplyLoadJob = null
        _uiState.update { it.copy(quickReplySheet = null) }
    }

    fun sendQuickReply(option: String) {
        val sheet = _uiState.value.quickReplySheet ?: return
        if (sheet.isSending || option !in sheet.options.orEmpty()) return
        val sending = sheet.copy(isSending = true)
        _uiState.update { it.copy(quickReplySheet = sending) }
        viewModelScope.launch {
            val result = sendQuickReplyUseCase(sheet.button.action.entityId, option)
            _uiState.update { state ->
                if (state.quickReplySheet !== sending) return@update state
                state.copy(quickReplySheet = sheet.takeIf { result.isFailure })
            }
            result.onSuccess {
                _uiEvent.emit(UiEvent.ShowToast(appContext.getString(R.string.stream_quick_reply_sent, option)))
            }.onFailure { showButtonFailure(sheet.button, it) }
        }
    }

    private fun executeLockAction(button: StreamButton, entityId: String, unlock: Boolean) {
        if (entityId in _uiState.value.runningLockEntityIds) return
        _uiState.update { it.copy(runningLockEntityIds = it.runningLockEntityIds + entityId) }
        viewModelScope.launch {
            try {
                val updates = if (unlock) unlockDoorUseCase(entityId) else lockDoorUseCase(entityId)
                updates.collect { result ->
                    updateLockState(entityId, result.getOrDefault(LockState.Unknown))
                    result.onFailure { showButtonFailure(button, it) }
                }
            } finally {
                _uiState.update { it.copy(runningLockEntityIds = it.runningLockEntityIds - entityId) }
            }
        }
    }

    private fun updateLockState(entityId: String, lockState: LockState) {
        _uiState.update { it.copy(lockStates = it.lockStates + (entityId to lockState)) }
    }

    private suspend fun showButtonFailure(button: StreamButton, error: Throwable) {
        val message = appContext.getString(R.string.stream_button_failed, button.displayName, errorReason(error))
        _uiEvent.emit(UiEvent.ShowToast(message, long = true))
    }

    private fun errorReason(error: Throwable): String =
        error.localizedMessage ?: appContext.getString(R.string.stream_button_unknown_error)

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
