package com.novasoftware.hadoorbell.domain.usecase

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
        val settings = Settings(
            url = "https://home.local",
            token = "token_abc",
            streamSource = "front_cam",
            instantTwoWayAudio = true,
            webrtcProvider = "webrtc",
            streamButtonLayout = StreamButtonLayout(bottom = listOf(
                StreamButton("chime", "Chime", StreamButtonAction.Press("button.chime"))
            ))
        )

        saveSettingsUseCase(settings)

        coVerify {
            repository.saveSettings(
                url = "https://home.local",
                token = "token_abc",
                source = "front_cam",
                instantTwoWayAudio = true,
                provider = "webrtc",
                streamButtonLayout = settings.streamButtonLayout
            )
        }
    }
}
