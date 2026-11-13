package com.novasoftware.hadoorbell.core.utils

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.novasoftware.hadoorbell.R

object BiometricPromptHelper {

    private const val PAYLOAD = "unlock"

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        allowWithoutScreenLock: Boolean = false,
        onSuccess: () -> Unit,
        onCryptoFailure: () -> Unit = {},
        onError: (message: CharSequence?) -> Unit = {}
    ) {
        val fragmentActivity = context as? FragmentActivity ?: run {
            onError(context.getText(R.string.authentication_unavailable))
            return
        }
        val biometricManager = BiometricManager.from(fragmentActivity)
        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val fallback = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val fallbackStatus = biometricManager.canAuthenticate(fallback)
        if (allowWithoutScreenLock &&
            fragmentActivity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == false &&
            (fallbackStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                fallbackStatus == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
        ) {
            onSuccess()
            return
        }
        val cryptoObject = if (biometricManager.canAuthenticate(strong) == BiometricManager.BIOMETRIC_SUCCESS) {
            BiometricHelper.getCryptoObject()
        } else {
            null
        }
        if (cryptoObject == null && fallbackStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            onError(context.getText(R.string.authentication_setup_required))
            return
        }
        val executor = ContextCompat.getMainExecutor(fragmentActivity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(if (cryptoObject != null) strong else fallback)
            .build()

        val biometricPrompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (cryptoObject != null) {
                        try {
                            checkNotNull(result.cryptoObject?.cipher).doFinal(PAYLOAD.toByteArray())
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
                    onError(errorMessage(context, errorCode, errString))
                }
            }
        )

        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun errorMessage(context: Context, code: Int, systemMessage: CharSequence): CharSequence? =
        when (code) {
            BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                context.getText(R.string.authentication_setup_required)
            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> null
            else -> systemMessage.ifBlank { context.getText(R.string.authentication_unavailable) }
        }
}
