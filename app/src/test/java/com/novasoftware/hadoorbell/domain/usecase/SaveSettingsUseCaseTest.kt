package com.novasoftware.hadoorbell.domain.usecase

import com.google.gson.JsonObject
import com.novasoftware.hadoorbell.domain.model.CallActions
import com.novasoftware.hadoorbell.domain.model.CallServiceSettings
import com.novasoftware.hadoorbell.domain.model.Settings
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SaveSettingsUseCaseTest {

    private lateinit var repository: SettingsRepository
    private lateinit var saveSettingsUseCase: SaveSettingsUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        saveSettingsUseCase = SaveSettingsUseCase(repository)
    }

    @Test
    fun `invoke passes settings model fields to repository`() = runTest {
        val answerData = JsonObject().apply { addProperty("message", "Answering") }
        val hangupData = JsonObject().apply { addProperty("message", "Ending") }
        val settings = Settings(
            url = "https://home.local",
            token = "token_abc",
            streamSource = "front_cam",
            instantTwoWayAudio = true,
            webrtcProvider = "webrtc",
            answerActionService = "notify.answer",
            hangupActionService = "notify.hangup",
            answerActionData = answerData,
            hangupActionData = hangupData,
            hangupActionNeedsResponse = true,
            streamButtonLayout = StreamButtonLayout(bottom = listOf(
                StreamButton("chime", StreamButtonAction.CallService("button", "press", "button.chime"))
            ))
        )

        saveSettingsUseCase(settings)

        coVerify {
            repository.saveSettings(
                source = "front_cam",
                instantTwoWayAudio = true,
                provider = "webrtc",
                callActions = CallActions(
                    answer = CallServiceSettings("notify.answer", data = answerData),
                    hangup = CallServiceSettings("notify.hangup", data = hangupData, needsResponse = true)
                ),
                streamButtonLayout = settings.streamButtonLayout
            )
        }
    }
}
