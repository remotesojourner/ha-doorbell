package com.novasoftware.hadoorbell.domain.usecase

import com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository
import javax.inject.Inject

class GetGo2rtcStreamsUseCase @Inject constructor(
    private val repository: HomeAssistantRepository
) {
    suspend operator fun invoke(url: String, token: String): Result<List<String>> =
        repository.getGo2rtcStreams(url, token)
}
