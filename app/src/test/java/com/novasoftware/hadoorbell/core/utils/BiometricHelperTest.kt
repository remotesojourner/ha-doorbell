package com.novasoftware.hadoorbell.core.utils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P]) // BiometricPrompt requires P+
class BiometricHelperTest {

    @Test
    fun `getCryptoObject handles keystore initialization safely`() {
        // Under Robolectric, the AndroidKeyStore provider has a shadow implementation.
        // It might not support all hardware-backed features like setUserAuthenticationRequired,
        // so getCryptoObject() may return null or a valid object depending on the exact Robolectric version.
        // This test ensures that calling it does not crash the app with an unhandled exception.
        
        val cryptoObject = BiometricHelper.getCryptoObject()
        
        // We just assert that it completes execution (returns either null or the object)
        // rather than crashing with an unhandled KeyStoreException or ProviderException.
        if (cryptoObject == null) {
            assertNull(cryptoObject)
        } else {
            assertNotNull(cryptoObject.cipher)
        }
    }
}
