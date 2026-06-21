package com.climapilot.free

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * EN: Thin wrapper around [BiometricPrompt] used to gate the app behind the device biometric or, as a
 *     fallback, the device PIN/pattern/password — so a flatmate or anyone who picks up the phone can't
 *     control the AC. Weak biometrics are accepted (face/fingerprint) and DEVICE_CREDENTIAL is allowed
 *     so the lock still works on phones with no enrolled biometric.
 * DE: Dünne Hülle um [BiometricPrompt], um die App hinter der Geräte-Biometrie oder ersatzweise der
 *     Geräte-PIN/-Muster/-Passwort zu sperren — damit ein Mitbewohner oder wer auch immer das Handy in
 *     die Hand nimmt, die Klima nicht steuern kann. Schwache Biometrie (Gesicht/Fingerabdruck) wird
 *     akzeptiert und DEVICE_CREDENTIAL erlaubt, sodass die Sperre auch ohne hinterlegte Biometrie greift.
 */
object BiometricLock {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** EN: True if the device can authenticate (biometric enrolled or a screen lock is set). DE: True, wenn das Gerät authentifizieren kann (Biometrie hinterlegt oder Bildschirmsperre gesetzt). */
    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** EN: Show the unlock prompt. DE: Den Entsperr-Dialog zeigen. */
    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_title))
            .setSubtitle(activity.getString(R.string.lock_subtitle))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
