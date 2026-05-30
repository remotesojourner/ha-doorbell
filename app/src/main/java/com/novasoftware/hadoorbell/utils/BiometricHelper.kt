package com.novasoftware.hadoorbell.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object BiometricHelper {
    private const val KEY_NAME = "doorbell_unlock_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    fun getCryptoObject(): androidx.biometric.BiometricPrompt.CryptoObject? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_NAME)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }

            val secretKey = keyStore.getKey(KEY_NAME, null) as SecretKey
            val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            androidx.biometric.BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
