package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteStreamButtonUseCaseTest {
    private val repository = mockk<HomeAssistantRepository>()
    private val execute = ExecuteStreamButtonUseCase(repository)

    @Test
    fun `toggle press script and relay actions call their Home Assistant services`() = runTest {
        val actions = listOf(
            StreamButtonAction.Toggle("light.porch") to Triple("light", "toggle", "light.porch"),
            StreamButtonAction.Press("input_button.bell") to Triple("input_button", "press", "input_button.bell"),
            StreamButtonAction.RunScript("script.gate") to Triple("script", "turn_on", "script.gate"),
            StreamButtonAction.TurnOn("switch.relay") to
                Triple("switch", "turn_on", "switch.relay")
        )

        for ((action, service) in actions) {
            val (domain, name, target) = service
            coEvery { repository.callService(domain, name, target) } returns Result.success(Unit)

            execute(action).getOrThrow()

            coVerify(exactly = 1) { repository.callService(domain, name, target) }
        }
    }

    @Test
    fun `select passes the configured option to Home Assistant without changing it`() = runTest {
        val action = StreamButtonAction.SelectOption("select.quick_reply", "Leave at door")
        coEvery { repository.setSelectOption(action.entityId, action.option) } returns Result.success(Unit)

        execute(action).getOrThrow()

        coVerify(exactly = 1) { repository.setSelectOption("select.quick_reply", "Leave at door") }
    }

    @Test
    fun `incompatible or missing entity IDs cannot call a service`() = runTest {
        assertTrue(execute(StreamButtonAction.Toggle("sensor.temperature")).isFailure)
        assertTrue(execute(StreamButtonAction.Press("button.")).isFailure)
        coVerify(exactly = 0) { repository.callService(any(), any(), any()) }
    }
}
