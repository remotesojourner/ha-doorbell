package com.novasoftware.hadoorbell.data.repository

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.novasoftware.hadoorbell.domain.model.StreamButton
import com.novasoftware.hadoorbell.domain.model.StreamButtonAction
import com.novasoftware.hadoorbell.domain.model.StreamButtonLayout
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamButtonLayoutMigrationTest {
    private val migration = StreamButtonLayoutMigration
    private val quickReplyKey = stringPreferencesKey("quick_reply_entity_id")
    private val doorEntityKey = stringPreferencesKey("door_entity_id")
    private val doorServiceKey = stringPreferencesKey("door_service")
    private val lockKey = stringPreferencesKey("lock_entity_id")
    private val answerServiceKey = stringPreferencesKey("answer_action_service")

    @Test
    fun `migrates all existing controls into bottom and preserves service and auth settings`() = runTest {
        val previous = preferencesOf(
            quickReplyKey to " select.reply ",
            doorEntityKey to " switch.door ",
            doorServiceKey to " switch.turn_on ",
            lockKey to " lock.entry ",
            answerServiceKey to "script.start_call"
        )

        assertTrue(migration.shouldMigrate(previous))
        val migrated = migration.migrate(previous)
        val layout = StreamButtonLayoutJson.decode(checkNotNull(migrated[STREAM_BUTTON_LAYOUT]))

        assertEquals(
            StreamButtonLayout(bottom = listOf(
                StreamButton("legacy-quick-reply", "Quick Reply", StreamButtonAction.QuickReply("select.reply")),
                StreamButton("legacy-open-door", "Open Door", StreamButtonAction.OpenDoor("switch.door", "switch.turn_on"), true),
                StreamButton("legacy-door-lock", "Door Lock", StreamButtonAction.DoorLock("lock.entry"), true)
            )),
            layout
        )
        assertEquals("script.start_call", migrated[answerServiceKey])
        assertFalse(quickReplyKey in migrated)
        assertFalse(doorEntityKey in migrated)
        assertFalse(doorServiceKey in migrated)
        assertFalse(lockKey in migrated)
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `enabled prior layout keeps custom positions and adds original controls in open slots`() = runTest {
        val existingDoor = StreamButton("relay", "Open", StreamButtonAction.TurnOn("switch.door"), true)
        val firstCustom = StreamButton("chime", "Chime", StreamButtonAction.Press("button.chime"))
        val secondCustom = StreamButton("porch", "Porch", StreamButtonAction.Toggle("light.porch"))
        val layout = StreamButtonLayout(bottom = listOf(existingDoor, firstCustom, secondCustom))
        val previous = preferencesOf(
            doorEntityKey to "switch.door",
            lockKey to "lock.entry",
            quickReplyKey to "select.reply",
            STREAM_BUTTON_LAYOUT to layout.withPreviousEnabledFlag(true)
        )

        val migrated = migration.migrate(previous)
        val result = checkNotNull(StreamButtonLayoutJson.decode(checkNotNull(migrated[STREAM_BUTTON_LAYOUT])))

        assertEquals(layout.bottom, result.bottom)
        assertEquals(
            listOf(StreamButtonAction.QuickReply("select.reply"), StreamButtonAction.DoorLock("lock.entry")),
            result.top.map(StreamButton::action)
        )
        assertFalse(doorEntityKey in migrated)
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `prior disabled draft keeps bottom order and moves overflow into free top slots`() = runTest {
        val existingTop = StreamButton("top", "Porch light", StreamButtonAction.Toggle("light.porch"))
        val firstDraft = StreamButton("draft1", "Chime", StreamButtonAction.Press("button.chime"))
        val secondDraft = StreamButton("draft2", "Garage", StreamButtonAction.RunScript("script.garage"))
        val thirdDraft = StreamButton("draft3", "Lights", StreamButtonAction.TurnOn("switch.lights"))
        val drafts = StreamButtonLayout(top = listOf(existingTop), bottom = listOf(firstDraft, secondDraft, thirdDraft))
        val previous = preferencesOf(
            doorEntityKey to "switch.door",
            lockKey to "lock.entry",
            STREAM_BUTTON_LAYOUT to drafts.withPreviousEnabledFlag(false)
        )

        val migrated = migration.migrate(previous)
        val layout = checkNotNull(StreamButtonLayoutJson.decode(checkNotNull(migrated[STREAM_BUTTON_LAYOUT])))

        assertEquals(listOf(existingTop, secondDraft, thirdDraft), layout.top)
        assertEquals(
            listOf(StreamButtonAction.OpenDoor("switch.door"), StreamButtonAction.DoorLock("lock.entry"), firstDraft.action),
            layout.bottom.map(StreamButton::action)
        )
        assertFalse(migration.shouldMigrate(migrated))
    }

    private fun StreamButtonLayout.withPreviousEnabledFlag(enabled: Boolean): String =
        """{"enabled":$enabled,${StreamButtonLayoutJson.encode(this).removePrefix("{")}"""
}
