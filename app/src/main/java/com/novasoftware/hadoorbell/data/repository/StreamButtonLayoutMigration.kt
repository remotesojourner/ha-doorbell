package com.novasoftware.hadoorbell.data.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout

internal val STREAM_BUTTON_LAYOUT = stringPreferencesKey("stream_button_layout")

internal object StreamButtonLayoutMigration : DataMigration<Preferences> {
    private val quickReplyKey = stringPreferencesKey("quick_reply_entity_id")
    private val doorEntityKey = stringPreferencesKey("door_entity_id")
    private val doorServiceKey = stringPreferencesKey("door_service")
    private val lockKey = stringPreferencesKey("lock_entity_id")
    private val previousKeys = listOf(quickReplyKey, doorEntityKey, doorServiceKey, lockKey)

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        previousKeys.any { it in currentData } ||
            currentData[STREAM_BUTTON_LAYOUT]?.let {
                !StreamButtonLayoutJson.hasVisibilityMarker(it) ||
                    StreamButtonLayoutJson.hasLegacyLabels(it) ||
                    StreamButtonLayoutJson.hasLegacyActions(it)
            } == true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val previousJson = currentData[STREAM_BUTTON_LAYOUT]
        val previousLayout = previousJson?.let(StreamButtonLayoutJson::decode) ?: StreamButtonLayout()
        val previousEnabled = previousJson?.let(StreamButtonLayoutJson::previousEnabledFlag)
        val originalButtons = originalButtons(currentData)
        val layout = when {
            previousEnabled == false -> mergeDrafts(originalButtons, previousLayout)
            originalButtons.isNotEmpty() -> appendOriginalButtons(previousLayout, originalButtons)
            previousJson != null && !StreamButtonLayoutJson.hasVisibilityMarker(previousJson) ->
                restorePreviewVisibility(previousLayout, StreamButtonLayoutJson.legacyBottomLabels(previousJson))
            else -> previousLayout
        }
        return currentData.toMutablePreferences().apply {
            this[STREAM_BUTTON_LAYOUT] = StreamButtonLayoutJson.encode(layout)
            previousKeys.forEach { remove(it) }
        }.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    private fun originalButtons(preferences: Preferences): List<StreamButton> = buildList {
        preferences.entityId(quickReplyKey)?.let { entityId ->
            add(StreamButton("legacy-quick-reply", StreamButtonAction.QuickReply(entityId)))
        }
        val doorEntityId = preferences.entityId(doorEntityKey)
        doorEntityId?.let { entityId ->
            add(StreamButton(
                id = "legacy-open-door",
                action = StreamButtonLayoutJson.legacyDoorRelease(entityId, preferences[doorServiceKey].orEmpty()),
                requiresAuthentication = true
            ))
        }
        preferences.entityId(lockKey)?.let { entityId ->
            add(StreamButton(
                id = "legacy-door-lock",
                action = StreamButtonAction.DoorLock(entityId),
                requiresAuthentication = true,
                visible = doorEntityId == null
            ))
        }
    }

    private fun restorePreviewVisibility(layout: StreamButtonLayout, oldLabels: List<String?>): StreamButtonLayout {
        // The earlier migration put both door buttons in the bottom row even when only Open Door
        // appeared in the legacy app. Recognize that original shape without touching edited rows.
        if (layout.top.isNotEmpty() || layout.bottom.size !in 2..3) return layout
        val expectedLabels = if (layout.bottom.size == 2) listOf("Open Door", "Door Lock")
            else listOf("Quick Reply", "Open Door", "Door Lock")
        if (oldLabels != expectedLabels) return layout

        val (door, lock) = layout.bottom.takeLast(2)
        if (door.id != "legacy-open-door" ||
            (door.action as? StreamButtonAction.CallService)?.doorRelease != true ||
            !door.requiresAuthentication ||
            lock.id != "legacy-door-lock" ||
            lock.action !is StreamButtonAction.DoorLock || !lock.requiresAuthentication
        ) return layout
        val reply = layout.bottom.dropLast(2).singleOrNull()
        if (reply != null &&
            (reply.id != "legacy-quick-reply" ||
                reply.action !is StreamButtonAction.QuickReply || reply.requiresAuthentication)
        ) return layout
        return layout.copy(bottom = layout.bottom.dropLast(1) + lock.copy(visible = false))
    }

    private fun mergeDrafts(originalButtons: List<StreamButton>, drafts: StreamButtonLayout): StreamButtonLayout {
        val topDrafts = drafts.top.filterNot { draft -> originalButtons.any { it.matches(draft) } }
        val bottomDrafts = drafts.bottom.filterNot { draft -> originalButtons.any { it.matches(draft) } }
        val maxButtons = StreamButtonLayout.MAX_BUTTONS_PER_ROW
        val freeBottomSlots = maxButtons - originalButtons.size
        return StreamButtonLayout(
            top = (topDrafts + bottomDrafts.drop(freeBottomSlots)).take(maxButtons),
            bottom = originalButtons + bottomDrafts.take(freeBottomSlots)
        )
    }

    private fun appendOriginalButtons(layout: StreamButtonLayout, originals: List<StreamButton>): StreamButtonLayout {
        val additions = originals.filterNot { original ->
            layout.buttons.any { existing ->
                original.matches(existing) &&
                    (original.id == existing.id || original.requiresAuthentication == existing.requiresAuthentication)
            }
        }
        val maxButtons = StreamButtonLayout.MAX_BUTTONS_PER_ROW
        val freeBottomSlots = maxButtons - layout.bottom.size
        return StreamButtonLayout(
            top = (layout.top + additions.drop(freeBottomSlots)).take(maxButtons),
            bottom = layout.bottom + additions.take(freeBottomSlots)
        )
    }

    private fun StreamButton.matches(other: StreamButton): Boolean {
        if (id == other.id || action == other.action) return true
        val door = action as? StreamButtonAction.CallService ?: return false
        if (!door.doorRelease) return false
        return door.entityId == other.action.entityId &&
            door.service.isNotBlank() && when (val candidate = other.action) {
                is StreamButtonAction.CallService -> door == candidate.copy(doorRelease = true)
                is StreamButtonAction.RunScript ->
                    door.domain == "script" && door.service == "turn_on" && candidate.variables.isEmpty
                else -> false
            }
    }

    private fun Preferences.entityId(key: Preferences.Key<String>): String? =
        get(key)?.trim()?.takeIf { it.isNotEmpty() }
}
