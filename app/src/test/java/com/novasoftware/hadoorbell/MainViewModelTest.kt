package com.novasoftware.hadoorbell

import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.ui.Navigation
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var appPreferences: SettingsRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        appPreferences = mockk()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when preferences are set, start destination is stream`() = runTest {
        // Arrange
        every { appPreferences.haUrlFlow } returns flowOf("http://ha.local")
        every { appPreferences.haTokenFlow } returns flowOf("token")
        every { appPreferences.streamSourceFlow } returns flowOf("camera.front")

        // Act
        val viewModel = MainViewModel(appPreferences)
        advanceUntilIdle()

        // Assert
        assertEquals(Navigation.ROUTE_STREAM, viewModel.startDestination.value)
    }

    @Test
    fun `when preferences are missing, start destination is settings`() = runTest {
        // Arrange
        every { appPreferences.haUrlFlow } returns flowOf("")
        every { appPreferences.haTokenFlow } returns flowOf("token")
        every { appPreferences.streamSourceFlow } returns flowOf("camera.front")

        // Act
        val viewModel = MainViewModel(appPreferences)
        advanceUntilIdle()

        // Assert
        assertEquals(Navigation.ROUTE_SETTINGS, viewModel.startDestination.value)
    }
}
