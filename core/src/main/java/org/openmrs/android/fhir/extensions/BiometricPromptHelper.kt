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
package org.openmrs.android.fhir.extensions

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor
import javax.crypto.Cipher
import org.openmrs.android.fhir.R

object BiometricPromptHelper {

  fun promptBiometricEncryption(
    activity: ComponentActivity,
    biometricPrompt: BiometricPrompt,
    confirmCredLauncher: ActivityResultLauncher<Intent>?,
    navigateToMain: () -> Unit,
    showToast: (String) -> Unit,
  ) {
    val biometricManager = BiometricManager.from(activity)

    val canUseStrongBiometric =
      biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

    val canUseDeviceCredential =
      biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
        BiometricManager.BIOMETRIC_SUCCESS

    val promptBuilder =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.biometric_prompt_title))

    if (canUseStrongBiometric) {
      promptBuilder
        .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText(activity.getString(R.string.cancel))

      val cipher = BiometricUtils.getEncryptionCipher(activity)
      if (cipher != null) {
        biometricPrompt.authenticate(promptBuilder.build(), BiometricPrompt.CryptoObject(cipher))
        return
      } else {
        // TODO: add dialog encryption issue, try setting up biometric auth later in settings.
        showToast("Error encountered while setting offline login")
        navigateToMain()
        return
      }
    } else if (canUseDeviceCredential) {
      promptBuilder
        .setSubtitle(activity.getString(R.string.use_device_credential))
        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)

      val cipher = BiometricUtils.getEncryptionCipher(activity)
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
        if (cipher != null) {
          biometricPrompt.authenticate(promptBuilder.build(), BiometricPrompt.CryptoObject(cipher))
          return
        }
      } else {
        biometricPrompt.authenticate(promptBuilder.build())
        return
      }
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
      val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
      if (!km.isDeviceSecure) {
        showToast(activity.getString(R.string.no_supported_offline_auth_method))
        navigateToMain()
        return
      }
      val intent =
        km.createConfirmDeviceCredentialIntent(
          activity.getString(R.string.biometric_prompt_title),
          activity.getString(R.string.use_device_credential),
        )
      if (intent != null) {
        confirmCredLauncher?.launch(intent)
        return
      }
    }

    showToast(activity.getString(R.string.no_supported_offline_auth_method))
    navigateToMain()
  }

  /**
   * Handles the result of Confirm Device Credentials flow.
   * - If inside the device-credential validity window, tries to get an ENCRYPT cipher and saves
   *   token.
   * - Always calls onNavigate() at the end (your online login already succeeded).
   */
  fun handleConfirmCredentialResult(
    activity: ComponentActivity,
    result: ActivityResult,
    sessionTokenProvider: () -> String?,
    cipherProvider: () -> Cipher?,
    onNavigate: () -> Unit,
    onSaved: (() -> Unit)? = null,
    onFailure: ((String) -> Unit)? = null,
  ) {
    if (result.resultCode == Activity.RESULT_OK) {
      val sessionToken = sessionTokenProvider()
      val enc = cipherProvider()
      if (!sessionToken.isNullOrBlank() && enc != null) {
        BiometricUtils.encryptAndSaveToken(sessionToken, enc, activity.applicationContext)
        onSaved?.invoke()
      } else {
        val msg = activity.getString(R.string.no_supported_offline_auth_method)
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        onFailure?.invoke(msg)
      }
    }
    // Continue regardless (online login already succeeded)
    onNavigate()
  }

  fun createBiometricPrompt(
    activity: FragmentActivity,
    executor: Executor,
    sessionTokenProvider: () -> String?,
    onNavigate: () -> Unit,
  ): BiometricPrompt {
    return BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          val cipher = result.cryptoObject?.cipher
          val token = sessionTokenProvider()
          if (cipher != null && token != null) {
            BiometricUtils.encryptAndSaveToken(token, cipher, activity.applicationContext)
          }
          onNavigate()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          Toast.makeText(
              activity,
              "Auth error: Unable to setup offline login now, please try again later",
              Toast.LENGTH_SHORT,
            )
            .show()
          onNavigate()
        }

        override fun onAuthenticationFailed() {
          Toast.makeText(
              activity,
              "Authentication failed! Unable to setup offline login now",
              Toast.LENGTH_SHORT,
            )
            .show()
          onNavigate()
        }
      },
    )
  }
}
