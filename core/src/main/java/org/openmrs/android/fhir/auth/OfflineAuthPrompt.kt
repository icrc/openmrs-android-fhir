/*
* BSD 3-Clause License
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* 3. Neither the name of the copyright holder nor the names of its
*    contributors may be used to endorse or promote products derived from
*    this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.openmrs.android.fhir.auth

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.openmrs.android.fhir.R

/**
 * Asks the user to prove who they are with what the offline login uses: the fingerprint or face
 * when that was chosen, otherwise (or when it is no longer available) the screen lock.
 */
object OfflineAuthPrompt {

  /** The kinds of check this device can do for [method], best first; `null`: none. */
  private fun authenticators(activity: FragmentActivity, method: OfflineAuthMethod?): Int? {
    val manager = BiometricManager.from(activity)
    val candidates = buildList {
      if (method == OfflineAuthMethod.BIOMETRIC) add(BIOMETRIC_STRONG)
      // The screen lock alone is supported from Android 11; before, it comes with the biometrics.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        add(DEVICE_CREDENTIAL)
      } else {
        add(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
      }
    }
    return candidates.firstOrNull {
      manager.canAuthenticate(it) == BiometricManager.BIOMETRIC_SUCCESS
    }
  }

  /** Whether this device can ask at all (a fingerprint, a face or a screen lock is set up). */
  fun isAvailable(activity: FragmentActivity): Boolean =
    authenticators(activity, BiometricUtils.getOfflineAuthMethod(activity)) != null

  /** Shows the prompt. [onFailed] is called when the user cancels or the check is not possible. */
  fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String?,
    onSuccess: () -> Unit,
    onFailed: (cancelled: Boolean) -> Unit,
  ) {
    val allowed = authenticators(activity, BiometricUtils.getOfflineAuthMethod(activity))
    if (allowed == null) {
      onFailed(false)
      return
    }
    val prompt =
      BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
            onSuccess()

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
            onFailed(true)
        },
      )
    val info =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(allowed)
        .apply {
          // With only biometrics the system needs a way out of its own.
          if (allowed and DEVICE_CREDENTIAL == 0) {
            setNegativeButtonText(activity.getString(R.string.cancel))
          }
        }
        .build()
    prompt.authenticate(info)
  }

  /**
   * Before something that cannot be undone: the user confirms with the same check. A device with no
   * fingerprint, face or screen lock cannot ask, and the action goes ahead (the dialog that
   * preceded this one was the confirmation).
   */
  fun confirm(
    activity: FragmentActivity,
    title: String,
    subtitle: String?,
    onConfirmed: () -> Unit,
  ) {
    if (!isAvailable(activity)) {
      onConfirmed()
      return
    }
    authenticate(activity, title, subtitle, onSuccess = onConfirmed, onFailed = {})
  }
}
