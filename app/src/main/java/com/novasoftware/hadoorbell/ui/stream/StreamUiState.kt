package com.novasoftware.hadoorbell.ui.stream

import com.novasoftware.hadoorbell.core.webrtc.WebRtcManager
import com.novasoftware.hadoorbell.domain.model.LockState

data class StreamUiState(
    val isMicEnabled: Boolean = false,
    val webRtcManager: WebRtcManager? = null,
    val errorMessage: String? = null,
    val quickReplyEntityId: String = "",
    val lockEntityId: String = "",
    val lockState: LockState = LockState.Unknown,
    val isStreamMuted: Boolean = false,
    val isSwitchingModes: Boolean = false,
    val showQuickReplySheet: Boolean = false,
    val quickReplyOptions: List<String>? = null,
    val quickReplyError: String? = null
)
