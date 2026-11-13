package com.novasoftware.hadoorbell.core.utils

import android.app.KeyguardManager
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novasoftware.hadoorbell.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class BiometricPromptHelperTest {
    @Test
    fun `door action proceeds when the device has no configured authentication`() {
        val result = authenticateWithoutEnrollment(secure = false, allowWithoutScreenLock = true)

        assertTrue(result.succeeded)
        assertNull(result.error)
    }

    @Test
    fun `explicit authentication and secured devices never use the no screen lock fallback`() {
        for ((secure, allowWithoutScreenLock) in listOf(false to false, true to true)) {
            val result = authenticateWithoutEnrollment(secure, allowWithoutScreenLock)

            assertFalse(result.succeeded)
            assertEquals(result.setupMessage, result.error)
        }
    }

    private fun authenticateWithoutEnrollment(
        secure: Boolean,
        allowWithoutScreenLock: Boolean
    ): Attempt {
        Robolectric.buildActivity(FragmentActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            shadowOf(activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(secure)
            val manager = mockk<BiometricManager> {
                every { canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            }
            var succeeded = false
            var error: CharSequence? = null
            mockkStatic(BiometricManager::class) {
                every { BiometricManager.from(activity) } returns manager
                BiometricPromptHelper.authenticate(
                    context = activity,
                    title = "Unlock",
                    subtitle = "Authenticate",
                    allowWithoutScreenLock = allowWithoutScreenLock,
                    onSuccess = { succeeded = true },
                    onError = { error = it }
                )
            }
            return Attempt(succeeded, error, activity.getText(R.string.authentication_setup_required))
        }
    }

    private data class Attempt(val succeeded: Boolean, val error: CharSequence?, val setupMessage: CharSequence)
}
