package com.novasoftware.hadoorbell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.ui.Navigation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appPreferences: SettingsRepositoryImpl
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        determineStartDestination()
    }

    private fun determineStartDestination() {
        viewModelScope.launch {
            val url = appPreferences.haUrlFlow.first()
            val token = appPreferences.haTokenFlow.first()
            val source = appPreferences.streamSourceFlow.first()

            _startDestination.value = if (!url.isNullOrBlank() && !token.isNullOrBlank() && !source.isNullOrBlank()) {
                Navigation.ROUTE_STREAM
            } else {
                Navigation.ROUTE_SETTINGS
            }
        }
    }
}
