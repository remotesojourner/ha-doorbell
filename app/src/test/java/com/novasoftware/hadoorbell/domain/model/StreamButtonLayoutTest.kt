package com.novasoftware.hadoorbell.domain.model

import com.novasoftware.hadoorbell.domain.model.StreamBottomItem.Control
import com.novasoftware.hadoorbell.domain.model.StreamBottomItem.Custom
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamButtonLayoutTest {
    private val chime = StreamButton("chime", StreamButtonAction.CallService("button", "press", "button.chime"))
    private val light = StreamButton("light", StreamButtonAction.CallService("light", "toggle", "light.porch"))
    private val sound = Control(StreamControl.SOUND)
    private val call = Control(StreamControl.CALL)

    @Test
    fun `sound and call lead the bottom row by default`() {
        val layout = StreamButtonLayout(bottom = listOf(chime, light))

        assertEquals(listOf(sound, call, Custom(chime), Custom(light)), layout.bottomItems)
    }

    @Test
    fun `reordered controls round trip through the bottom items`() {
        val items = listOf(Custom(chime), call, Custom(light), sound)
        val layout = StreamButtonLayout().withBottomItems(items)

        assertEquals(listOf(chime, light), layout.bottom)
        assertEquals(3, layout.soundPosition)
        assertEquals(1, layout.callPosition)
        assertEquals(items, layout.bottomItems)
    }

    @Test
    fun `controls beyond the end of a shorter row keep their relative order`() {
        val layout = StreamButtonLayout(bottom = listOf(chime), soundPosition = 4, callPosition = 3)

        assertEquals(listOf(Custom(chime), call, sound), layout.bottomItems)
    }
}
