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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
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
import timber.log.Timber

class BasicLoginActivityViewModel
@Inject
constructor(private val applicationContext: Context, private val apiManager: ApiManager) :
  ViewModel() {

  private val authStateManager by lazy { AuthStateManager.getInstance(applicationContext) }

  private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
  val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  // Temporary store for token after login success
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
        createBiometricKeyIfPossible()
        sessionTokenToEncrypt = encodedCredentials // wait for biometric to encrypt
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

  // ---- Biometric Key Setup and Secure Token Storage ----

  fun createBiometricKeyIfPossible() {
    val biometricManager = BiometricManager.from(applicationContext)
    val canUseBiometric =
      biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

    if (canUseBiometric != BiometricManager.BIOMETRIC_SUCCESS) {
      // Skip key creation if no biometric enrolled
      Timber.w("Biometric not enrolled or not available, skipping key creation")
      return
    }

    val keyGenerator =
      KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore",
      )

    val keyGenParameterSpec =
      KeyGenParameterSpec.Builder(
          "biometric_key",
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .setUserAuthenticationValidityDurationSeconds(-1)
        .build()

    keyGenerator.init(keyGenParameterSpec)
    keyGenerator.generateKey()
  }

  fun getEncryptionCipher(): Cipher {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val secretKey = keyStore.getKey("biometric_key", null) as SecretKey
    val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    return cipher
  }

  fun encryptAndSaveToken(token: String, cipher: Cipher) {
    val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv

    val sharedPreferences =
      applicationContext.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    sharedPreferences.edit {
      putString("encrypted_token", Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
        .putString("encrypted_iv", Base64.encodeToString(iv, Base64.DEFAULT))
    }
  }
}

sealed class LoginUiState {
  object Idle : LoginUiState()

  object LockedOut : LoginUiState()

  object Loading : LoginUiState()

  object Success : LoginUiState()

  data class Failure(val resId: Int) : LoginUiState()
}
