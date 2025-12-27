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
import android.os.Build
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import org.openmrs.android.fhir.R

object AuthDialogs {

  @Volatile
  private var composeDialogState: androidx.compose.runtime.MutableState<DialogConfig?>? = null

  fun registerTestHost(state: androidx.compose.runtime.MutableState<DialogConfig?>) {
    composeDialogState = state
  }

  fun clearTestHost() {
    composeDialogState = null
  }

  data class DialogConfig(
    val title: CharSequence,
    val message: CharSequence,
    val positiveText: CharSequence,
    val negativeText: CharSequence,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
  )

  /**
   * Shows an opt-in dialog to enable offline login.
   *
   * Behavior:
   * - Positive ("Enable"): if biometrics/device_credential are enrolled -> calls
   *   [onProceedWithBiometric]. Otherwise shows a toast and calls [navigateToMain].
   * - Negative ("Not now"): calls [navigateToMain].
   *
   * You can override title/message/button text via parameters, or rely on string resources.
   */
  @MainThread
  fun showOfflineLoginOptIn(
    activity: Activity,
    onProceedWithBiometric: () -> Unit,
    navigateToMain: () -> Unit,
    title: CharSequence = activity.getString(R.string.enable_offline_login_title),
    message: CharSequence = activity.getString(R.string.enable_offline_login_body),
    positiveText: CharSequence = activity.getString(R.string.enable_offline_login_positive),
    negativeText: CharSequence = activity.getString(R.string.enable_offline_login_negative),
  ) {
    composeDialogState?.value =
      DialogConfig(
        title = title,
        message = message,
        positiveText = positiveText,
        negativeText = negativeText,
        onConfirm = {
          val bm = BiometricManager.from(activity)
          val canUseBiometric =
            bm.canAuthenticate(
              BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) == BiometricManager.BIOMETRIC_SUCCESS

          if (canUseBiometric || Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            onProceedWithBiometric()
          } else {
            Toast.makeText(
                activity,
                activity.getString(R.string.setup_biometrics_to_enable_offline_login),
                Toast.LENGTH_LONG,
              )
              .show()
            navigateToMain()
          }
        },
        onDismiss = navigateToMain,
      )
    if (composeDialogState != null) {
      return
    }

    AlertDialog.Builder(activity)
      .setTitle(title)
      .setMessage(message)
      .setPositiveButton(positiveText) { _, _ ->
        val bm = BiometricManager.from(activity)
        val canUseBiometric =
          bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
              BiometricManager.Authenticators.DEVICE_CREDENTIAL,
          ) == BiometricManager.BIOMETRIC_SUCCESS

        if (canUseBiometric || Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
          onProceedWithBiometric()
        } else {
          Toast.makeText(
              activity,
              activity.getString(R.string.setup_biometrics_to_enable_offline_login),
              Toast.LENGTH_LONG,
            )
            .show()
          navigateToMain()
        }
      }
      .setNegativeButton(negativeText) { _, _ -> navigateToMain() }
      .setCancelable(false)
      .show()
  }
}
