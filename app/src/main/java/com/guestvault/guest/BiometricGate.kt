package com.guestvault.guest

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper around [BiometricPrompt].
 *
 * The prompt is only ever used to *leave* Guest Mode. It never gates normal app
 * usage, and no biometric data is stored by GuestVault — Android's Keystore
 * handles the comparison.
 */
class BiometricGate(private val activity: FragmentActivity) {

    private var activePrompt: BiometricPrompt? = null

    fun isAvailable(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isAvailable()) {
            onError("No fingerprint or face is enrolled on this device.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> Unit // user chose the PIN instead
                    else -> onError(errString.toString())
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        activePrompt = prompt

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Return to Owner")
            .setSubtitle("Confirm your fingerprint or face")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }

    fun cancel() {
        activePrompt?.cancelAuthentication()
        activePrompt = null
    }
}
