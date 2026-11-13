package com.novasoftware.hadoorbell.data.repository

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonParser
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
    fun `migrates all existing controls and keeps the previously hidden lock out of the live row`() = runTest {
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
                StreamButton("legacy-quick-reply", StreamButtonAction.QuickReply("select.reply")),
                StreamButton("legacy-open-door", StreamButtonAction.OpenDoor("switch.door", "switch.turn_on"), true),
                StreamButton("legacy-door-lock", StreamButtonAction.DoorLock("lock.entry"),
                    requiresAuthentication = true, visible = false)
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
        val existingDoor = StreamButton("relay", StreamButtonAction.TurnOn("switch.door"), true)
        val firstCustom = StreamButton("chime", StreamButtonAction.Press("button.chime"))
        val secondCustom = StreamButton("porch", StreamButtonAction.Toggle("light.porch"))
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
        assertFalse(result.top.last().visible)
        assertFalse(doorEntityKey in migrated)
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `prior disabled draft keeps bottom order and moves overflow into free top slots`() = runTest {
        val existingTop = StreamButton("top", StreamButtonAction.Toggle("light.porch"))
        val firstDraft = StreamButton("draft1", StreamButtonAction.Press("button.chime"))
        val secondDraft = StreamButton("draft2", StreamButtonAction.RunScript("script.garage"))
        val thirdDraft = StreamButton("draft3", StreamButtonAction.TurnOn("switch.lights"))
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
        assertFalse(layout.bottom[1].visible)
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `earlier untouched migration restores the hidden lock in a single pass`() = runTest {
        val door = StreamButton("legacy-open-door", StreamButtonAction.OpenDoor("switch.door"), true)
        val lock = StreamButton("legacy-door-lock", StreamButtonAction.DoorLock("lock.entry"), true)
        val reply = StreamButton("legacy-quick-reply", StreamButtonAction.QuickReply("select.reply"))
        val layout = StreamButtonLayout(bottom = listOf(reply, door, lock))
        val previous = preferencesOf(STREAM_BUTTON_LAYOUT to layout.legacyJson())

        assertTrue(migration.shouldMigrate(previous))
        val migrated = migration.migrate(previous)

        assertEquals(
            layout.copy(bottom = listOf(reply, door, lock.copy(visible = false))),
            StreamButtonLayoutJson.decode(checkNotNull(migrated[STREAM_BUTTON_LAYOUT]))
        )
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `earlier layouts with renamed or moved migrated controls keep their chosen visibility`() = runTest {
        val door = StreamButton("legacy-open-door", StreamButtonAction.OpenDoor("switch.door"), true)
        val lock = StreamButton("legacy-door-lock", StreamButtonAction.DoorLock("lock.entry"), true)
        val renamed = StreamButtonLayout(bottom = listOf(door, lock))
        val reordered = StreamButtonLayout(bottom = listOf(lock, door))
        val moved = StreamButtonLayout(top = listOf(lock), bottom = listOf(door))
        val editedLayouts = listOf(
            renamed to renamed.legacyJson(labels = mapOf(lock.id to "Garage lock")),
            reordered to reordered.legacyJson(),
            moved to moved.legacyJson()
        )
        for ((layout, json) in editedLayouts) {
            val previous = preferencesOf(STREAM_BUTTON_LAYOUT to json)
            val migrated = migration.migrate(previous)

            assertEquals(layout, StreamButtonLayoutJson.decode(checkNotNull(migrated[STREAM_BUTTON_LAYOUT])))
            assertFalse(migration.shouldMigrate(migrated))
        }
    }

    @Test
    fun `upgrading a visible layout removes old labels without changing actions or icons`() = runTest {
        val button = StreamButton(
            "porch", StreamButtonAction.Toggle("light.porch"),
            requiresAuthentication = true, icon = "mdi:lightbulb", entityIcon = "mdi:outdoor-lamp"
        )
        val layout = StreamButtonLayout(top = listOf(button))
        val json = JsonParser.parseString(StreamButtonLayoutJson.encode(layout)).asJsonObject.apply {
            getAsJsonArray("top")[0].asJsonObject.addProperty("label", "User label")
        }.toString()
        val previous = preferencesOf(STREAM_BUTTON_LAYOUT to json)

        assertTrue(migration.shouldMigrate(previous))
        val migrated = migration.migrate(previous)
        val persisted = checkNotNull(migrated[STREAM_BUTTON_LAYOUT])
        val decoded = checkNotNull(StreamButtonLayoutJson.decode(persisted))

        assertFalse(StreamButtonLayoutJson.hasLegacyLabels(persisted))
        assertFalse(migration.shouldMigrate(migrated))
        assertEquals(layout, decoded)
        assertEquals("light.porch", decoded.top.single().displayName)
    }

    private fun StreamButtonLayout.withPreviousEnabledFlag(enabled: Boolean): String =
        """{"enabled":$enabled,${legacyJson().removePrefix("{")}"""

    private fun StreamButtonLayout.legacyJson(labels: Map<String, String> = emptyMap()): String =
        JsonParser.parseString(StreamButtonLayoutJson.encode(this)).asJsonObject.apply {
            remove("buttonVisibility")
            for (row in listOf("top", "bottom")) {
                getAsJsonArray(row).forEach { element ->
                    val button = element.asJsonObject
                    button.remove("visible")
                    val id = button.get("id").asString
                    val label = labels[id] ?: when (id) {
                        "legacy-open-door" -> "Open Door"
                        "legacy-door-lock" -> "Door Lock"
                        "legacy-quick-reply" -> "Quick Reply"
                        else -> id
                    }
                    button.addProperty("label", label)
                }
            }
        }.toString()
}
