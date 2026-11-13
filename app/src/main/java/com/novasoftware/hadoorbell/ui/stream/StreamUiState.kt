package com.novasoftware.hadoorbell.ui.stream

import com.novasoftware.hadoorbell.core.webrtc.WebRtcManager
import com.novasoftware.hadoorbell.domain.model.HomeAssistantEntityAppearance
import com.novasoftware.hadoorbell.domain.model.LockState
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout

data class StreamUiState(
    val isMicEnabled: Boolean = false,
    val webRtcManager: WebRtcManager? = null,
    val errorMessage: String? = null,
    val lockStates: Map<String, LockState> = emptyMap(),
    val entityAppearances: Map<String, HomeAssistantEntityAppearance> = emptyMap(),
    val isStreamMuted: Boolean = false,
    val isSwitchingModes: Boolean = false,
    val quickReplySheet: QuickReplySheetState? = null,
    val answerActionService: String = "",
    val answerActionEntityId: String = "",
    val hangupActionService: String = "",
    val hangupActionEntityId: String = "",
    val streamButtonLayout: StreamButtonLayout = StreamButtonLayout(),
    val isStreamButtonLayoutReady: Boolean = false,
    val runningStreamButtonIds: Set<String> = emptySet(),
    val runningLockEntityIds: Set<String> = emptySet()
) {
    fun canRun(button: StreamButton): Boolean {
        if (!isStreamButtonLayoutReady || button.id in runningStreamButtonIds) return false
        val lock = button.action as? StreamButtonAction.DoorLock
        return lock == null || lock.entityId !in runningLockEntityIds
    }
}

data class QuickReplySheetState(
    val button: StreamButton,
    val options: List<String>? = null,
    val error: String? = null,
    val isSending: Boolean = false
)
