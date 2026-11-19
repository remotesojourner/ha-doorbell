package com.novasoftware.hadoorbell.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.model.normalizeHomeAssistantUrl
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import com.novasoftware.hadoorbell.domain.usecase.GetGo2rtcStreamsUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantEntitiesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantServicesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveHomeAssistantConnectionUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.io.IOException
import java.util.Base64
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase,
    private val getHomeAssistantEntities: GetHomeAssistantEntitiesUseCase,
    private val getHomeAssistantServices: GetHomeAssistantServicesUseCase,
    private val getGo2rtcStreams: GetGo2rtcStreamsUseCase,
    private val saveHomeAssistantConnection: SaveHomeAssistantConnectionUseCase,
    private val homeAssistantRepository: HomeAssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var savedSettings = Settings()
    private var entityLoadJob: Job? = null
    private var serviceLoadJob: Job? = null
    private var streamLoadJob: Job? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = getSettingsUseCase().first()
            savedSettings = settings
            _uiState.update {
                it.copy(
                    url = settings.url,
                    token = settings.token,
                    streamSource = settings.streamSource,
                    instantTwoWayAudio = settings.instantTwoWayAudio && !settings.streamButtonLayout.hasQuickReply,
                    webrtcProvider = settings.webrtcProvider,
                    answerActionService = settings.answerActionService,
                    answerActionEntityId = settings.answerActionEntityId,
                    answerActionData = settings.answerActionData,
                    answerActionNeedsResponse = settings.answerActionNeedsResponse,
                    hangupActionService = settings.hangupActionService,
                    hangupActionEntityId = settings.hangupActionEntityId,
                    hangupActionData = settings.hangupActionData,
                    hangupActionNeedsResponse = settings.hangupActionNeedsResponse,
                    streamButtonLayout = settings.streamButtonLayout,
                    isLoaded = true
                )
            }
            if (settings.url.isNotBlank() && settings.token.isNotBlank()) {
                verifyConnection(save = false)
            }
        }
    }

    fun updateUrl(value: String) = updateConnection { it.copy(url = value) }
    fun updateToken(value: String) = updateConnection { it.copy(token = value) }

    fun importTokenFromQr(contents: String): Boolean {
        val token = contents.trim()
        if (!looksLikeHomeAssistantToken(token)) return false
        updateToken(token)
        return true
    }

    private fun looksLikeHomeAssistantToken(token: String): Boolean {
        if (!TOKEN_QR_PATTERN.matches(token)) return false
        return try {
            val header = Base64.getUrlDecoder().decode(token.substringBefore('.')).decodeToString()
            val algorithm = JsonParser.parseString(header).takeIf { it.isJsonObject }
                ?.asJsonObject?.get("alg")
            algorithm?.isJsonPrimitive == true && algorithm.asString == "HS256"
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: JsonParseException) {
            false
        }
    }

    private fun updateConnection(update: (SettingsUiState) -> SettingsUiState) {
        val state = _uiState.value
        if (state.connectionStatus == HomeAssistantConnectionStatus.Checking) return
        val draft = update(state)
        if (draft == state) return
        entityLoadJob?.cancel()
        serviceLoadJob?.cancel()
        streamLoadJob?.cancel()
        _uiState.value = draft.copy(
            connectionStatus = HomeAssistantConnectionStatus.Unchecked,
            entities = emptyList(),
            isLoadingEntities = false,
            entityLoadError = null,
            services = emptyList(),
            isLoadingServices = false,
            serviceLoadError = null,
            streams = emptyList(),
            isLoadingStreams = false,
            streamLoadError = null
        )
    }

    fun saveConnection() = verifyConnection(save = true)

    private fun verifyConnection(save: Boolean) {
        val state = _uiState.value
        if (!state.isLoaded || state.connectionStatus == HomeAssistantConnectionStatus.Checking ||
            state.connectionStatus == HomeAssistantConnectionStatus.Verified
        ) return

        val error = connectionError(state)
        if (error != null) {
            _uiState.update { it.copy(connectionStatus = HomeAssistantConnectionStatus.Failed(error)) }
            return
        }

        val url = normalizeHomeAssistantUrl(state.url)
        val token = state.token.trim()
        _uiState.update { it.copy(connectionStatus = HomeAssistantConnectionStatus.Checking) }
        viewModelScope.launch {
            val entities = getHomeAssistantEntities(url, token).getOrElse { failure ->
                _uiState.update {
                    it.copy(connectionStatus = HomeAssistantConnectionStatus.Failed(loadError(failure)))
                }
                return@launch
            }
            if (save) {
                try {
                    saveHomeAssistantConnection(url, token)
                } catch (_: IOException) {
                    _uiState.update {
                        it.copy(
                            connectionStatus = HomeAssistantConnectionStatus.Failed(HomeAssistantLoadError.ConnectionSaveFailed)
                        )
                    }
                    return@launch
                }
                savedSettings = savedSettings.copy(url = url, token = token)
            }
            _uiState.update {
                it.copy(
                    url = url,
                    token = token,
                    connectionStatus = HomeAssistantConnectionStatus.Verified,
                    entities = entities
                )
            }
        }
    }

    fun loadEntities() {
        val state = _uiState.value
        if (state.connectionStatus != HomeAssistantConnectionStatus.Verified || state.isLoadingEntities) return

        _uiState.update { it.copy(isLoadingEntities = true, entityLoadError = null) }
        entityLoadJob = viewModelScope.launch {
            getHomeAssistantEntities(state.url, state.token).fold(
                onSuccess = { entities ->
                    _uiState.update { it.copy(entities = entities, isLoadingEntities = false) }
                },
                onFailure = { failure ->
                    _uiState.update { it.copy(isLoadingEntities = false, entityLoadError = loadError(failure)) }
                }
            )
        }
    }

    internal fun observeEntityAppearances(
        entityIds: Set<String>
    ): Flow<Map<String, HomeAssistantEntityAppearance>> {
        if (entityIds.isEmpty() || _uiState.value.connectionStatus != HomeAssistantConnectionStatus.Verified) {
            return emptyFlow()
        }
        return homeAssistantRepository.observeEntityAppearances(entityIds).retryWhen { failure, _ ->
            if (failure !is IOException) return@retryWhen false
            delay(APPEARANCE_RETRY_DELAY_MS)
            true
        }
    }

    fun loadServices() {
        val state = _uiState.value
        if (state.connectionStatus != HomeAssistantConnectionStatus.Verified || state.isLoadingServices) return

        _uiState.update { it.copy(isLoadingServices = true, serviceLoadError = null) }
        serviceLoadJob = viewModelScope.launch {
            getHomeAssistantServices(state.url, state.token).fold(
                onSuccess = { services ->
                    _uiState.update { it.copy(services = services, isLoadingServices = false) }
                },
                onFailure = { failure ->
                    _uiState.update { it.copy(isLoadingServices = false, serviceLoadError = loadError(failure)) }
                }
            )
        }
    }

    fun loadStreams() {
        val state = _uiState.value
        if (state.connectionStatus != HomeAssistantConnectionStatus.Verified || state.isLoadingStreams) return

        _uiState.update { it.copy(isLoadingStreams = true, streamLoadError = null) }
        streamLoadJob = viewModelScope.launch {
            getGo2rtcStreams(state.url, state.token).fold(
                onSuccess = { streams ->
                    _uiState.update { it.copy(streams = streams, isLoadingStreams = false) }
                },
                onFailure = { failure ->
                    _uiState.update { it.copy(isLoadingStreams = false, streamLoadError = loadError(failure)) }
                }
            )
        }
    }

    private fun loadError(failure: Throwable): HomeAssistantLoadError = when {
        failure is IllegalArgumentException -> HomeAssistantLoadError.InvalidConnection
        failure is HttpException && (failure.code() == 401 || failure.code() == 403) ->
            HomeAssistantLoadError.AuthenticationFailed
        else -> HomeAssistantLoadError.RequestFailed
    }

    private fun connectionError(state: SettingsUiState): HomeAssistantLoadError? {
        if (state.url.isBlank() || state.token.isBlank()) return HomeAssistantLoadError.MissingConnection
        val draftUrl = normalizeHomeAssistantUrl(state.url).toHttpUrlOrNull()
            ?: return HomeAssistantLoadError.InvalidConnection
        val savedUrl = normalizeHomeAssistantUrl(savedSettings.url).toHttpUrlOrNull()
        if (savedUrl != null && state.token.trim() == savedSettings.token.trim() &&
            draftUrl.origin() != savedUrl.origin()
        ) {
            return HomeAssistantLoadError.ChangedServer
        }
        return null
    }

    private fun HttpUrl.origin(): String = newBuilder().username("").password("")
        .encodedPath("/").query(null).fragment(null).build().toString()

    fun updateStreamButtonLayout(layout: StreamButtonLayout) {
        _uiState.update {
            it.copy(
                streamButtonLayout = layout,
                instantTwoWayAudio = it.instantTwoWayAudio && !layout.hasQuickReply
            )
        }
    }

    fun updateStreamSource(value: String) { _uiState.update { it.copy(streamSource = value) } }
    fun updateInstantTwoWayAudio(value: Boolean) {
        _uiState.update { it.copy(instantTwoWayAudio = value && !it.streamButtonLayout.hasQuickReply) }
    }
    fun updateWebrtcProvider(value: String) { _uiState.update { it.copy(webrtcProvider = value) } }

    fun updateCallActions(
        answerService: String,
        answerEntity: String,
        hangupService: String,
        hangupEntity: String,
        answerData: JsonObject = JsonObject(),
        hangupData: JsonObject = JsonObject(),
        answerNeedsResponse: Boolean = false,
        hangupNeedsResponse: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                answerActionService = answerService.trim(),
                answerActionEntityId = answerEntity.trim(),
                answerActionData = answerData.deepCopy(),
                answerActionNeedsResponse = answerNeedsResponse,
                hangupActionService = hangupService.trim(),
                hangupActionEntityId = hangupEntity.trim(),
                hangupActionData = hangupData.deepCopy(),
                hangupActionNeedsResponse = hangupNeedsResponse
            )
        }
    }

    fun callActionsChanged(): Boolean {
        val currentState = _uiState.value
        return currentState.answerActionService != savedSettings.answerActionService ||
            currentState.answerActionEntityId != savedSettings.answerActionEntityId ||
            currentState.answerActionData != savedSettings.answerActionData ||
            currentState.answerActionNeedsResponse != savedSettings.answerActionNeedsResponse ||
            currentState.hangupActionService != savedSettings.hangupActionService ||
            currentState.hangupActionEntityId != savedSettings.hangupActionEntityId ||
            currentState.hangupActionData != savedSettings.hangupActionData ||
            currentState.hangupActionNeedsResponse != savedSettings.hangupActionNeedsResponse
    }

    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        if (!state.isLoaded) return false
        val saved = savedSettings
        val savedInstantAudio = saved.instantTwoWayAudio && !saved.streamButtonLayout.hasQuickReply
        return normalizeHomeAssistantUrl(state.url) != normalizeHomeAssistantUrl(saved.url) ||
            state.token.trim() != saved.token.trim() ||
            state.streamSource != saved.streamSource ||
            state.instantTwoWayAudio != savedInstantAudio ||
            state.webrtcProvider != saved.webrtcProvider ||
            state.streamButtonLayout != saved.streamButtonLayout ||
            callActionsChanged()
    }

    fun protectedButtonsChanged(): Boolean {
        val original = savedSettings.streamButtonLayout.buttons
        val current = _uiState.value.streamButtonLayout.buttons
        return original.filter(StreamButton::requiresAuthentication).any { saved ->
            current.none { it.id == saved.id && it.action == saved.action && it.requiresAuthentication } ||
                current.any { it.action == saved.action && !it.requiresAuthentication && it !in original }
        }
    }

    fun saveSettings(onComplete: () -> Unit) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.connectionStatus != HomeAssistantConnectionStatus.Verified) return@launch
            val settings = savedSettings.copy(
                streamSource = currentState.streamSource,
                instantTwoWayAudio = currentState.instantTwoWayAudio,
                webrtcProvider = currentState.webrtcProvider,
                answerActionService = currentState.answerActionService,
                answerActionEntityId = currentState.answerActionEntityId,
                answerActionData = currentState.answerActionData,
                answerActionNeedsResponse = currentState.answerActionNeedsResponse,
                hangupActionService = currentState.hangupActionService,
                hangupActionEntityId = currentState.hangupActionEntityId,
                hangupActionData = currentState.hangupActionData,
                hangupActionNeedsResponse = currentState.hangupActionNeedsResponse,
                streamButtonLayout = currentState.streamButtonLayout
            )
            saveSettingsUseCase(settings)
            savedSettings = settings.copy(url = savedSettings.url, token = savedSettings.token)
            onComplete()
        }
    }

    private companion object {
        const val APPEARANCE_RETRY_DELAY_MS = 5_000L
        // Home Assistant encodes access tokens as compact, URL-safe JWTs.
        val TOKEN_QR_PATTERN = Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
    }
}
