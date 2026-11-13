package com.novasoftware.hadoorbell.core.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricPromptHelper {

    private const val PAYLOAD = "unlock"

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onCryptoFailure: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val fragmentActivity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(fragmentActivity)
        val cryptoObject = BiometricHelper.getCryptoObject()

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (cryptoObject != null) {
            promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } else {
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        }

        val biometricPrompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (cryptoObject != null) {
                        try {
                            result.cryptoObject?.cipher?.doFinal(PAYLOAD.toByteArray())
                            onSuccess()
                        } catch (_: Exception) {
                            onCryptoFailure()
                        }
                    } else {
                        onSuccess()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError()
                }
            }
        )

        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfoBuilder.build(), cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfoBuilder.build())
        }
    }
}
