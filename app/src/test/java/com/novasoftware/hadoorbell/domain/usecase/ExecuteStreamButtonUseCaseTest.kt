package com.novasoftware.hadoorbell.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    fun `a service receives the exact target data and response preference`() = runTest {
        val data = json("""{"brightness":80,"transition":0.5,"flash":"short"}""")
        val action = StreamButtonAction.CallService("light", "turn_on", "light.porch", data, needsResponse = true)
        coEvery { repository.callService("light", "turn_on", "light.porch", data, true) } returns Result.success(Unit)

        execute(action).getOrThrow()

        coVerify(exactly = 1) { repository.callService("light", "turn_on", "light.porch", data, true) }
    }

    @Test
    fun `a service may provide its payload without a selected entity`() = runTest {
        val data = json("""{"message":"Someone at the door","data":{"urgent":true}}""")
        val action = StreamButtonAction.CallService("notify", "mobile_app_phone", data = data)
        coEvery { repository.callService("notify", "mobile_app_phone", null, data, false) } returns Result.success(Unit)

        execute(action).getOrThrow()

        coVerify(exactly = 1) { repository.callService("notify", "mobile_app_phone", null, data, false) }
    }

    @Test
    fun `a script receives nested variables with their JSON types intact`() = runTest {
        val variables = json("""{"message":"Open gate","count":2,"rooms":["porch","hall"],"enabled":true}""")
        val data = JsonObject().apply { add("variables", variables) }
        coEvery { repository.callService("script", "turn_on", "script.gate", data, false) } returns Result.success(Unit)

        execute(StreamButtonAction.RunScript("script.gate", variables)).getOrThrow()

        coVerify(exactly = 1) { repository.callService("script", "turn_on", "script.gate", data, false) }
    }

    @Test
    fun `automation triggers pass the configured condition behavior explicitly`() = runTest {
        val honorConditions = json("""{"skip_condition":false}""")
        val skipConditions = json("""{"skip_condition":true}""")
        coEvery { repository.callService("automation", "trigger", "automation.bell", any(), false) } returns
            Result.success(Unit)

        execute(StreamButtonAction.TriggerAutomation("automation.bell")).getOrThrow()
        execute(StreamButtonAction.TriggerAutomation("automation.bell", skipConditions = true)).getOrThrow()

        coVerify(exactly = 1) { repository.callService("automation", "trigger", "automation.bell", honorConditions, false) }
        coVerify(exactly = 1) { repository.callService("automation", "trigger", "automation.bell", skipConditions, false) }
    }

    @Test
    fun `an event is sent with its exact payload and no service call`() = runTest {
        val data = json("""{"visitor":true,"priority":3,"zones":["front","drive"]}""")
        coEvery { repository.fireEvent("doorbell_pressed", data) } returns Result.success(Unit)

        execute(StreamButtonAction.FireEvent("doorbell_pressed", data)).getOrThrow()

        coVerify(exactly = 1) { repository.fireEvent("doorbell_pressed", data) }
        coVerify(exactly = 0) { repository.callService(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `invalid actions do not send requests to Home Assistant`() = runTest {
        val invalidActions = listOf(
            StreamButtonAction.CallService("switch", "turn/on", "switch.porch"),
            StreamButtonAction.CallService("switch", "turn_on", "switch."),
            StreamButtonAction.CallService("switch", "turn_on", doorRelease = true),
            StreamButtonAction.RunScript("button.gate"),
            StreamButtonAction.TriggerAutomation("switch.bell"),
            StreamButtonAction.FireEvent("doorbell/pressed")
        )

        for (action in invalidActions) {
            assertTrue(execute(action).exceptionOrNull() is IllegalArgumentException)
        }
        coVerify(exactly = 0) { repository.callService(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { repository.fireEvent(any(), any()) }
    }

    private fun json(source: String): JsonObject = JsonParser.parseString(source).asJsonObject
}
