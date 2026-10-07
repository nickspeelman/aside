package com.nickspeelman.localjournal.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AuthenticationCoordinator(private val activity: FragmentActivity) {
    private var onSuccess: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onCancelled: (() -> Unit)? = null

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val callback = onSuccess
                clearCallbacks()
                callback?.invoke()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                val errorCallback = onError
                val cancelCallback = onCancelled
                clearCallbacks()
                if (cancelled) cancelCallback?.invoke() else errorCallback?.invoke(errString.toString())
            }
        }
    )

    fun canAuthenticate(): Boolean {
        return BiometricManager.from(activity).canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        if (!canAuthenticate()) {
            onError("Set up a screen lock, fingerprint, or supported face unlock in Android settings first.")
            return
        }

        this.onSuccess = onSuccess
        this.onError = onError
        this.onCancelled = onCancelled

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported on API 28-29. AndroidX
            // Biometric emulates the compatible weak-biometric/device-credential flow on older OSes.
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    private fun clearCallbacks() {
        onSuccess = null
        onError = null
        onCancelled = null
    }
}
