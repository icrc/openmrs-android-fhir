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
import android.os.Build
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ApiResponse
import org.openmrs.android.fhir.data.remote.model.SessionResponse
import org.openmrs.android.fhir.extensions.BiometricUtils

class BasicLoginActivityViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val apiManager: ApiManager,
) : ViewModel() {

  private val authStateManager by lazy { AuthStateManager.getInstance(applicationContext) }

  private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
  val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  var sessionTokenToEncrypt: String? = null

  fun login(username: String, password: String) =
    viewModelScope.launch {
      _uiState.value = LoginUiState.Loading

      if (isLockedOut()) {
        _uiState.value = LoginUiState.LockedOut
        return@launch
      }

      validateCredentials(username, password)
    }

  private suspend fun validateCredentials(username: String, password: String) {
    if (username.isEmpty() || password.isEmpty()) {
      _uiState.value = LoginUiState.Failure(R.string.username_password_empty)
      return
    }

    val credentials = "$username:$password"
    val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

    when (val response = apiManager.validateSession("Basic $encodedCredentials")) {
      is ApiResponse.Success<SessionResponse> -> {
        val authenticated = response.data?.authenticated == true
        if (!authenticated) {
          incrementFailedAttempts()
          _uiState.value = LoginUiState.Failure(R.string.invalid_username_password)
          return
        }

        authStateManager.updateBasicAuthCredentials(username, password)

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
              canBioStrong == BiometricManager.BIOMETRIC_SUCCESS -> LegacyAuthFlow.BIOMETRIC_PER_USE

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

        sessionTokenToEncrypt = encodedCredentials
        _uiState.value = LoginUiState.Success
      }
      is ApiResponse.NoInternetConnection -> {
        _uiState.value = LoginUiState.Failure(R.string.no_internet_connection)
      }
      else -> {
        _uiState.value = LoginUiState.Failure(R.string.something_went_wrong)
      }
    }
  }

  private suspend fun incrementFailedAttempts() {
    authStateManager.incrementFailedAttempts()
  }

  private suspend fun isLockedOut(): Boolean {
    return authStateManager.isLockedOut()
  }
}

sealed class LoginUiState {
  object Idle : LoginUiState()

  object LockedOut : LoginUiState()

  object Loading : LoginUiState()

  object Success : LoginUiState()

  data class Failure(val resId: Int) : LoginUiState()
}

enum class LegacyAuthFlow {
  BIOMETRIC_PER_USE, // per-use auth via CryptoObject (biometrics only)
  DEVICE_CRED_VALIDITY_WINDOW, // allow device credential by using a validity window
}
