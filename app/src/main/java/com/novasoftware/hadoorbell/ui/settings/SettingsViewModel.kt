package com.novasoftware.hadoorbell.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.model.normalizeHomeAssistantUrl
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantEntitiesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetHomeAssistantServicesUseCase
import com.novasoftware.hadoorbell.domain.usecase.GetSettingsUseCase
import com.novasoftware.hadoorbell.domain.usecase.SaveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.util.Base64
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase,
    private val getHomeAssistantEntities: GetHomeAssistantEntitiesUseCase,
    private val getHomeAssistantServices: GetHomeAssistantServicesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var savedSettings = Settings()
    private var entityLoadJob: Job? = null
    private var serviceLoadJob: Job? = null

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
                    hangupActionService = settings.hangupActionService,
                    hangupActionEntityId = settings.hangupActionEntityId,
                    streamButtonLayout = settings.streamButtonLayout,
                    isLoaded = true
                )
            }
            if (settings.streamButtonLayout.buttons.any { it.icon.isNullOrBlank() && it.action.entityId.isNotBlank() }) {
                loadEntities()
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
        entityLoadJob?.cancel()
        serviceLoadJob?.cancel()
        _uiState.update {
            update(it).copy(
                entities = emptyList(),
                isLoadingEntities = false,
                entityLoadError = null,
                services = emptyList(),
                isLoadingServices = false,
                serviceLoadError = null
            )
        }
    }

    fun loadEntities() {
        val state = _uiState.value
        if (state.isLoadingEntities) return

        val error = connectionError(state)
        if (error != null) {
            _uiState.update { it.copy(entityLoadError = error) }
            return
        }

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

    fun loadServices() {
        val state = _uiState.value
        if (state.isLoadingServices) return

        val error = connectionError(state)
        if (error != null) {
            _uiState.update { it.copy(serviceLoadError = error) }
            return
        }

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

    fun updateCallActions(answerService: String, answerEntity: String, hangupService: String, hangupEntity: String) {
        _uiState.update {
            it.copy(
                answerActionService = answerService.trim(),
                answerActionEntityId = answerEntity.trim(),
                hangupActionService = hangupService.trim(),
                hangupActionEntityId = hangupEntity.trim()
            )
        }
    }

    fun callActionsChanged(): Boolean {
        val currentState = _uiState.value
        return currentState.answerActionService != savedSettings.answerActionService ||
            currentState.answerActionEntityId != savedSettings.answerActionEntityId ||
            currentState.hangupActionService != savedSettings.hangupActionService ||
            currentState.hangupActionEntityId != savedSettings.hangupActionEntityId
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
            val settings = Settings(
                url = currentState.url,
                token = currentState.token,
                streamSource = currentState.streamSource,
                instantTwoWayAudio = currentState.instantTwoWayAudio,
                webrtcProvider = currentState.webrtcProvider,
                answerActionService = currentState.answerActionService,
                answerActionEntityId = currentState.answerActionEntityId,
                hangupActionService = currentState.hangupActionService,
                hangupActionEntityId = currentState.hangupActionEntityId,
                streamButtonLayout = currentState.streamButtonLayout
            )
            saveSettingsUseCase(settings)
            savedSettings = settings
            onComplete()
        }
    }

    private companion object {
        // Home Assistant encodes access tokens as compact, URL-safe JWTs.
        val TOKEN_QR_PATTERN = Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
    }
}
