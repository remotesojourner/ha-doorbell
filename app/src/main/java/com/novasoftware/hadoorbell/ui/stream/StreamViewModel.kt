package com.novasoftware.hadoorbell.ui.stream

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novasoftware.hadoorbell.R
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.model.CallServiceSettings
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.usecase.CallActionUseCase
import com.novasoftware.hadoorbell.domain.usecase.ExecuteStreamButtonUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSelectOptionsUseCase
import com.novasoftware.hadoorbell.domain.usecase.LockDoorUseCase
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.usecase.SendQuickReplyUseCase
import com.novasoftware.hadoorbell.domain.usecase.UnlockDoorUseCase
import com.novasoftware.hadoorbell.core.webrtc.WebRtcClientFactory
import com.novasoftware.hadoorbell.core.webrtc.WebRtcManagerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import java.io.IOException
import javax.inject.Inject

sealed class UiEvent {
    data class ShowMessage(val message: String, val long: Boolean = false) : UiEvent()
    sealed class Authentication : UiEvent()
    data class AuthenticateStreamButton(val button: StreamButton) : Authentication()
    class AuthenticateSettings : Authentication()
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
    private var appearanceRefreshJob: Job? = null
    private var appearanceObserverJob: Job? = null
    private var observedEntityIds = emptySet<String>()
    private var observedLockIds = emptySet<String>()
    private var hasLiveAppearances = false
    private var answerJob: Job? = null
    private var quickReplyLoadJob: Job? = null
    private var pendingAuthentication: UiEvent.Authentication? = null
    private var answerRequested = false

    fun initializeConnection(context: Context) {
        initializeJob?.cancel()
        stopButtonAppearanceUpdates()
        isScreenActive = true
        initializeJob = viewModelScope.launch {
            val layout = appPreferences.streamButtonLayoutFlow.first()
            val callActions = appPreferences.callActionsFlow.first()

            _uiState.update {
                it.copy(
                    lockStates = emptyMap(),
                    entityAppearances = emptyMap(),
                    callActions = callActions,
                    streamButtonLayout = layout,
                    isStreamButtonLayoutReady = true
                )
            }
            (pendingAuthentication as? UiEvent.AuthenticateStreamButton)?.let { request ->
                if (!isConfiguredButton(request.button)) cancelAuthentication()
            }
            startButtonAppearanceUpdates(layout)

            val instantTwoWay = appPreferences.instantTwoWayAudioFlow.first()
            val answerOnStart = answerRequested
            answerRequested = false
            val micEnabled = answerOnStart || instantTwoWay && !layout.hasQuickReply
            _uiState.update { it.copy(isMicEnabled = micEnabled) }
            if (micEnabled) {
                callAnswered = true
            }

            val signalingClient = webRtcClientFactory.createClient()
            val manager = webRtcManagerFactory.create(context, signalingClient, viewModelScope)
            _uiState.update { it.copy(webRtcManager = manager) }

            if (answerOnStart) {
                answerJob = viewModelScope.launch {
                    if (awaitConnectionReady() && isScreenActive) {
                        runCallAction(callActions.answer, R.string.stream_call_action_answer)
                    }
                }
            } else if (answerRequested) {
                answerRequested = false
                answerCall(context)
            }
        }
    }

    /** Starts two-way audio as soon as the stream allows it, for a launch from the Answer action. */
    fun answerWhenReady(context: Context) {
        val state = _uiState.value
        when {
            state.isMicEnabled -> Unit
            state.webRtcManager != null -> answerCall(context)
            else -> answerRequested = true
        }
    }

    private fun startButtonAppearanceUpdates(layout: StreamButtonLayout) {
        val buttons = layout.buttons.filter(StreamButton::visible)
        val lockIds = buttons.mapNotNull { (it.action as? StreamButtonAction.DoorLock)?.entityId }
            .filter(String::isNotBlank).toSet()
        val entityIds = (buttons
            .filter { it.icon.isNullOrBlank() || !it.stateEntityId.isNullOrBlank() || it.iconEntityId.startsWith("lock.") }
            .map(StreamButton::iconEntityId)
            .filter(String::isNotBlank).toSet() + lockIds)
        if (entityIds.isEmpty()) return

        observedEntityIds = entityIds
        observedLockIds = lockIds
        refreshButtonAppearances()
        appearanceObserverJob = viewModelScope.launch {
            var retryDelay = APPEARANCE_RETRY_INITIAL_MS
            while (true) {
                var previousSnapshot: Map<String, HomeAssistantEntityAppearance>? = null
                try {
                    repository.observeEntityAppearances(entityIds).collect { snapshot ->
                        ensureActive()
                        val previous = previousSnapshot
                        previousSnapshot = snapshot
                        hasLiveAppearances = true
                        retryDelay = APPEARANCE_RETRY_INITIAL_MS
                        if (previous == null) appearanceRefreshJob?.cancel()
                        val changes = entityIds.filter { previous == null || snapshot[it] != previous[it] }
                            .associateWith { snapshot[it] ?: UNAVAILABLE_APPEARANCE }
                        _uiState.update { state ->
                            val appearances = changes.mapValues { (entityId, appearance) ->
                                // An attribute-only event can still carry the state from before a confirmed REST update.
                                val current = state.entityAppearances[entityId]
                                if (current != null && appearance.state == previous?.get(entityId)?.state) {
                                    appearance.copy(state = current.state)
                                } else {
                                    appearance
                                }
                            }
                            val lockUpdates = changes.keys.intersect(lockIds)
                                .filter {
                                    changes[it]?.state != (previous ?: state.entityAppearances)[it]?.state ||
                                        previous == null && it !in state.runningLockEntityIds
                                }
                                .associateWith { LockState.fromString(changes[it]?.state) }
                            state.copy(
                                entityAppearances = state.entityAppearances + appearances,
                                lockStates = state.lockStates + lockUpdates
                            )
                        }
                    }
                } catch (_: IOException) {
                    // Keep the last state visible while the socket reconnects.
                }
                hasLiveAppearances = false
                delay(retryDelay)
                refreshButtonAppearances()
                retryDelay = (retryDelay * 2).coerceAtMost(APPEARANCE_RETRY_MAX_MS)
            }
        }
    }

    private fun stopButtonAppearanceUpdates() {
        appearanceObserverJob?.cancel()
        appearanceObserverJob = null
        appearanceRefreshJob?.cancel()
        appearanceRefreshJob = null
        observedEntityIds = emptySet()
        observedLockIds = emptySet()
        hasLiveAppearances = false
    }

    private fun refreshButtonAppearances(
        afterAction: Boolean = false,
        completedLockId: String? = null,
        serviceLockId: String? = null
    ) {
        val currentState = _uiState.value
        val entityIds = if (hasLiveAppearances) {
            observedEntityIds.filter { it == serviceLockId && it.startsWith("lock.") }
        } else {
            observedEntityIds.filterNot { it == completedLockId }
        }
        val lockIds = observedLockIds
        if (!isScreenActive || entityIds.isEmpty()) return

        appearanceRefreshJob?.cancel()
        appearanceRefreshJob = viewModelScope.launch {
            suspend fun refresh() = coroutineScope {
                entityIds.forEach { entityId ->
                    launch {
                        val before = _uiState.value.entityAppearances[entityId]
                        repository.getEntityAppearance(entityId).onSuccess { appearance ->
                            ensureActive()
                            _uiState.update { state ->
                                if (hasLiveAppearances && state.entityAppearances[entityId] != before) {
                                    return@update state
                                }
                                val lockStates = if (entityId in lockIds &&
                                    entityId !in currentState.runningLockEntityIds &&
                                    entityId !in state.runningLockEntityIds
                                ) {
                                    state.lockStates + (entityId to LockState.fromString(appearance.state))
                                } else {
                                    state.lockStates
                                }
                                state.copy(
                                    entityAppearances = state.entityAppearances + (entityId to appearance),
                                    lockStates = lockStates
                                )
                            }
                        }
                    }
                }
            }
            refresh()
            if (afterAction) {
                delay(APPEARANCE_FOLLOW_UP_MS)
                refresh()
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
        answerRequested = false
        initializeJob?.cancel()
        initializeJob = null
        stopButtonAppearanceUpdates()
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
                    _uiEvent.emit(UiEvent.ShowMessage(appContext.getString(R.string.stream_call_setup_no_connection), long = true))
                    return@launch
                }

                if (!isScreenActive) return@launch

                callAnswered = true
                runCallAction(currentState.callActions.answer, R.string.stream_call_action_answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isMicEnabled = false) }
                _uiEvent.emit(
                    UiEvent.ShowMessage(appContext.getString(R.string.stream_call_setup_failed, errorReason(e)), long = true)
                )
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
                    runCallAction(currentState.callActions.hangup, R.string.stream_call_action_hang_up)
                }
                if (isScreenActive) {
                    restartConnection(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiEvent.emit(
                    UiEvent.ShowMessage(appContext.getString(R.string.stream_hang_up_failed, errorReason(e)), long = true)
                )
            } finally {
                _uiState.update { it.copy(isSwitchingModes = false) }
            }
        }
    }

    private fun sendHangUpAction() {
        val action = _uiState.value.callActions.hangup
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCallAction(action, R.string.stream_call_action_hang_up)
            }
        }
    }

    private suspend fun runCallAction(action: CallServiceSettings, @StringRes labelRes: Int) {
        if (action.service.isBlank()) return
        val label = appContext.getString(labelRes)

        val result = withTimeoutOrNull(CALL_ACTION_TIMEOUT_MS) {
            callActionUseCase(action.service, action.entityId, action.data, action.needsResponse)
        }

        if (result == null) {
            _uiEvent.emit(
                UiEvent.ShowMessage(appContext.getString(R.string.stream_call_action_timed_out, label), long = true)
            )
            return
        }

        result.onSuccess { refreshButtonAppearances(afterAction = true) }.onFailure { error ->
            _uiEvent.emit(
                UiEvent.ShowMessage(
                    appContext.getString(R.string.stream_call_action_failed, label, errorReason(error)),
                    long = true
                )
            )
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

    fun retryConnection(context: Context) {
        val state = _uiState.value
        if (!isScreenActive || state.isSwitchingModes || state.errorMessage == null) return
        viewModelScope.launch { restartConnection(context) }
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
                    _uiEvent.emit(UiEvent.ShowMessage(message))
                }
                LockState.Unlocked, LockState.Unlocking -> executeLockAction(button, action.entityId, unlock = false)
                else -> requestAuthentication(UiEvent.AuthenticateStreamButton(button))
            }
            else -> if (button.requiresAuthentication || action is StreamButtonAction.CallService && action.doorRelease) {
                requestAuthentication(UiEvent.AuthenticateStreamButton(button))
            } else {
                executeStreamButton(button)
            }
        }
    }

    fun requestSettingsAuthentication() = requestAuthentication(UiEvent.AuthenticateSettings())

    private fun requestAuthentication(request: UiEvent.Authentication) {
        if (pendingAuthentication != null) return
        pendingAuthentication = request
        viewModelScope.launch {
            if (pendingAuthentication === request) _uiEvent.emit(request)
        }
    }

    fun onStreamButtonAuthentication(request: UiEvent.AuthenticateStreamButton, succeeded: Boolean) {
        if (consumeAuthentication(request) && succeeded && isConfiguredButton(request.button)) {
            executeStreamButton(request.button)
        }
    }

    fun onSettingsAuthentication(request: UiEvent.AuthenticateSettings, succeeded: Boolean): Boolean =
        consumeAuthentication(request) && succeeded

    private fun consumeAuthentication(request: UiEvent.Authentication): Boolean {
        if (pendingAuthentication !== request) return false
        cancelAuthentication()
        return true
    }

    fun cancelAuthentication() {
        pendingAuthentication = null
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
                executeStreamButtonUseCase(button.action)
                    .onSuccess {
                        refreshButtonAppearances(afterAction = true, serviceLockId = button.iconEntityId)
                    }
                    .onFailure { showButtonFailure(button, it) }
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
                refreshButtonAppearances(afterAction = true)
                _uiEvent.emit(UiEvent.ShowMessage(appContext.getString(R.string.stream_quick_reply_sent, option)))
            }.onFailure { showButtonFailure(sheet.button, it) }
        }
    }

    private fun executeLockAction(button: StreamButton, entityId: String, unlock: Boolean) {
        val initialState = _uiState.value
        if (entityId in initialState.runningLockEntityIds) return
        val initialEntityState = initialState.entityAppearances[entityId]?.state
        _uiState.update { it.copy(runningLockEntityIds = it.runningLockEntityIds + entityId) }
        viewModelScope.launch {
            val expectedState = if (unlock) LockState.Unlocked else LockState.Locked
            var completed = false
            try {
                val updates = if (unlock) unlockDoorUseCase(entityId) else lockDoorUseCase(entityId)
                updates.collect { result ->
                    val state = result.getOrDefault(LockState.Unknown)
                    completed = result.isSuccess && state == expectedState
                    _uiState.update { uiState ->
                        val appearance = uiState.entityAppearances[entityId]
                        if (completed) {
                            val confirmedState = if (unlock) "unlocked" else "locked"
                            val confirmedAppearance = appearance?.copy(state = confirmedState)
                                ?: HomeAssistantEntityAppearance(confirmedState)
                            uiState.copy(
                                lockStates = uiState.lockStates + (entityId to state),
                                entityAppearances = uiState.entityAppearances + (entityId to confirmedAppearance)
                            )
                        } else {
                            val displayedState = if (hasLiveAppearances && appearance != null &&
                                (appearance.state != initialEntityState || result.isFailure)
                            ) {
                                LockState.fromString(appearance.state)
                            } else {
                                state
                            }
                            uiState.copy(lockStates = uiState.lockStates + (entityId to displayedState))
                        }
                    }
                    result.onFailure { showButtonFailure(button, it) }
                }
            } finally {
                _uiState.update { it.copy(runningLockEntityIds = it.runningLockEntityIds - entityId) }
            }
            if (completed) refreshButtonAppearances(afterAction = true, completedLockId = entityId)
        }
    }

    private suspend fun showButtonFailure(button: StreamButton, error: Throwable) {
        val message = appContext.getString(R.string.stream_button_failed, button.displayName, errorReason(error))
        _uiEvent.emit(UiEvent.ShowMessage(message, long = true))
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
        private const val APPEARANCE_FOLLOW_UP_MS = 1_000L
        private const val APPEARANCE_RETRY_INITIAL_MS = 5_000L
        private const val APPEARANCE_RETRY_MAX_MS = 30_000L
        private val UNAVAILABLE_APPEARANCE = HomeAssistantEntityAppearance("unavailable")
    }
}
