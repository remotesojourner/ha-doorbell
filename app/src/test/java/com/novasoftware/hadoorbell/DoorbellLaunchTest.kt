package com.novasoftware.hadoorbell

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class DoorbellLaunchTest {
    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun `answer and stream links open the stream with or without talking`() {
        assertEquals(true, DoorbellLaunch.from(view("hadoorbell://answer"))?.answer)
        assertEquals(false, DoorbellLaunch.from(view("hadoorbell://stream"))?.answer)
        assertEquals(true, DoorbellLaunch.from(view("HADOORBELL://ANSWER"))?.answer)
    }

    @Test
    fun `other intents are not doorbell launches`() {
        assertNull(DoorbellLaunch.from(view("hadoorbell://settings")))
        assertNull(DoorbellLaunch.from(view("https://answer")))
        assertNull(DoorbellLaunch.from(Intent(Intent.ACTION_MAIN)))
        assertNull(DoorbellLaunch.from(null))
    }

    @Test
    fun `each launch gets its own id so repeated links are handled`() {
        val first = DoorbellLaunch.from(view("hadoorbell://answer"))!!
        val second = DoorbellLaunch.from(view("hadoorbell://answer"))!!
        assert(first.id != second.id)
    }
}
