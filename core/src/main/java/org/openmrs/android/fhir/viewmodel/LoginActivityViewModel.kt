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
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.LoginRepository
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.audit.AuditAction
import org.openmrs.android.fhir.audit.AuditLog
import org.openmrs.android.fhir.audit.AuditOutcome
import org.openmrs.android.fhir.auth.AuthMethod
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ApiResponse
import org.openmrs.android.fhir.data.remote.model.SessionResponse
import org.openmrs.android.fhir.extensions.BiometricUtils
import timber.log.Timber

/**
 * Backs the one login page: signing in with a username and password (basic) and with the single
 * sign-on provider (OpenID). Which of the two the page offers is the `auth_method` build setting.
 */
class LoginActivityViewModel
@Inject
constructor(
  private val applicationContext: Context,
  private val apiManager: ApiManager,
) : ViewModel() {

  private val loginRepository by lazy { LoginRepository.getInstance(applicationContext) }
  private val authStateManager by lazy { AuthStateManager.getInstance(applicationContext) }

  /** The sign-in methods the login page shows. */
  val offeredMethods: Set<AuthMethod> by lazy { authStateManager.getOfferedAuthMethods() }

  private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
  val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  var sessionTokenToEncrypt: String? = null

  private val loginPrefs by lazy {
    applicationContext.getSharedPreferences(LOGIN_PREFS, Context.MODE_PRIVATE)
  }

  /** The username of the last successful sign-in, so only the password has to be typed again. */
  val lastUsername: String
    get() = loginPrefs.getString(KEY_LAST_USERNAME, "").orEmpty()

  // ---- Single sign-on (OpenID) ----------------------------------------------------------------

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
        authStateManager.setActiveAuthMethod(AuthMethod.OPENID)
        loginRepository.exchangeCodeForToken(response, ex)
        try {
          createBiometricKey()
          val token = loginRepository.getAccessToken()
          if (token.isNotBlank()) {
            sessionTokenToEncrypt = token
          }
          AuditLog.setUser(userFromIdToken(authStateManager.current.idToken))
          AuditLog.record(AuditAction.LOGIN, AuditOutcome.SUCCESS, "method" to "sso")
        } catch (e: Exception) {
          Timber.e("Biometric key error: ${e.message}")
        }
      }
      ex != null -> {
        Timber.e("Authorization flow failed: ${ex.message}")
        AuditLog.failure(AuditAction.LOGIN, "method" to "sso", "reason" to ex.errorDescription)
      }
      else -> Timber.e("No authorization state retained - reauthorization required")
    }
  }

  // ---- Username and password (basic) ----------------------------------------------------------

  fun login(username: String, password: String) =
    viewModelScope.launch {
      _uiState.value = LoginUiState.Loading

      if (authStateManager.isLockedOut()) {
        AuditLog.failure(AuditAction.LOGIN, "method" to "basic", "reason" to "locked_out")
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
          authStateManager.incrementFailedAttempts()
          AuditLog.failure(
            AuditAction.LOGIN,
            "method" to "basic",
            "username" to username,
            "reason" to "invalid_credentials",
          )
          _uiState.value = LoginUiState.Failure(R.string.invalid_username_password)
          return
        }

        authStateManager.updateBasicAuthCredentials(username, password)
        loginPrefs.edit().putString(KEY_LAST_USERNAME, username).apply()
        authStateManager.setActiveAuthMethod(AuthMethod.BASIC)
        createBiometricKey()

        sessionTokenToEncrypt = encodedCredentials
        AuditLog.setUser(username)
        AuditLog.record(AuditAction.LOGIN, AuditOutcome.SUCCESS, "method" to "basic")
        _uiState.value = LoginUiState.Success
      }
      is ApiResponse.NoInternetConnection -> {
        AuditLog.failure(
          AuditAction.LOGIN,
          "method" to "basic",
          "username" to username,
          "reason" to "no_internet",
        )
        _uiState.value = LoginUiState.Failure(R.string.no_internet_connection)
      }
      else -> {
        AuditLog.failure(
          AuditAction.LOGIN,
          "method" to "basic",
          "username" to username,
          "reason" to "server_error",
        )
        _uiState.value = LoginUiState.Failure(R.string.something_went_wrong)
      }
    }
  }

  /** The signed-in user's name from the ID token's claims, if the provider sends one. */
  private fun userFromIdToken(idToken: String?): String? =
    try {
      val payload = idToken?.split('.')?.getOrNull(1) ?: return null
      val claims =
        org.json.JSONObject(
          String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)),
        )
      listOf("preferred_username", "email", "name", "sub").firstNotNullOfOrNull {
        claims.optString(it).takeIf { value -> value.isNotBlank() }
      }
    } catch (e: Exception) {
      null
    }

  /** The key that protects the offline login, created once a session exists. */
  private fun createBiometricKey() {
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
        BiometricUtils.createBiometricKeyIfNotExists(
          applicationContext,
          canBioStrong = canBioStrong == BiometricManager.BIOMETRIC_SUCCESS,
        )
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
  }
}

private const val LOGIN_PREFS = "login"
private const val KEY_LAST_USERNAME = "last_username"

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
