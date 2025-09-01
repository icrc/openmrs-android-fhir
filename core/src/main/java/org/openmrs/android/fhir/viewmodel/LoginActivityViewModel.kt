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
package org.openmrs.android.fhir.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import javax.inject.Inject
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.LoginRepository
import org.openmrs.android.fhir.extensions.BiometricUtils
import timber.log.Timber

class LoginActivityViewModel
@Inject
constructor(
  private val applicationContext: Context,
) : ViewModel() {

  private val loginRepository by lazy { LoginRepository.getInstance(applicationContext) }

  var sessionTokenToEncrypt: String? = null

  suspend fun createIntent(): Intent? {
    loginRepository.updateAuthIfConfigurationChanged()
    loginRepository.initializeAppAuth()
    return loginRepository.getAuthIntent()
  }

  fun getLastConfigurationError(): AuthorizationException? {
    return loginRepository.getLastConfigurationError()
  }

  suspend fun handleLoginResponse(response: AuthorizationResponse?, ex: AuthorizationException?) {
    if (response != null || ex != null) {
      loginRepository.updateAfterAuthorization(response, ex)
    }

    when {
      response?.authorizationCode != null -> {
        loginRepository.exchangeCodeForToken(response, ex)
        try {
          val bm = BiometricManager.from(applicationContext)

          val canBioStrong = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
          val canBioOrCred =
            bm.canAuthenticate(
              BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: always use per-use with biometrics or device credential
            if (canBioOrCred == BiometricManager.BIOMETRIC_SUCCESS) {
              BiometricUtils.createBiometricKeyIfNotExists(applicationContext)
            } else {
              // TODO: Add intent for device credential setup
              // handle no-credential/no-biometrics case (fallback UI, PIN setup, etc.)
            }
          } else {
            // API 23–29: pick the flow
            val legacyFlow =
              when {
                // If strong biometrics present, prefer per-use biometrics (CryptoObject path)
                canBioStrong == BiometricManager.BIOMETRIC_SUCCESS ->
                  LegacyAuthFlow.BIOMETRIC_PER_USE

                // If (bio OR device PIN) is available, use validity window so PIN works
                canBioOrCred == BiometricManager.BIOMETRIC_SUCCESS ->
                  LegacyAuthFlow.DEVICE_CRED_VALIDITY_WINDOW
                else -> LegacyAuthFlow.DEVICE_CRED_VALIDITY_WINDOW // conservative fallback
              }

            BiometricUtils.createBiometricKeyIfNotExists(
              applicationContext,
              legacyFlow = legacyFlow,
              validityWindowSeconds = 30,
            )
          }
          val token = loginRepository.getAccessToken()
          if (token.isNotBlank()) {
            sessionTokenToEncrypt = token
          }
        } catch (e: Exception) {
          Timber.e("Biometric key error: ${e.message}")
        }
      }
      ex != null -> Timber.e("Authorization flow failed: ${ex.message}")
      else -> Timber.e("No authorization state retained - reauthorization required")
    }
  }
}
