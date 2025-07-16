package org.openmrs.android.fhir.viewmodel

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.lifecycle.ViewModel
import android.util.Base64
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ApiResponse
import org.openmrs.android.fhir.data.remote.model.SessionResponse
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

class BasicLoginActivityViewModel
@Inject
constructor(private val applicationContext: Context, private val apiManager: ApiManager) :
  ViewModel() {

  private val authStateManager by lazy { AuthStateManager.getInstance(applicationContext) }

  private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
  val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  fun login(username: String, password: String) = viewModelScope.launch {
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
        createBiometricKey()
        encryptAndSaveToken(encodedCredentials) // Store Basic token securely
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

  // ---- Security Functions for Biometric Key ----

  private fun createBiometricKey() {
    val keyGenerator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      "AndroidKeyStore"
    )
    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
      "biometric_key",
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
      .setUserAuthenticationRequired(false)
      .setInvalidatedByBiometricEnrollment(true)
      .setUserAuthenticationValidityDurationSeconds(-1)
      .build()
    keyGenerator.init(keyGenParameterSpec)
    keyGenerator.generateKey()
  }

  private fun getCipher(): Cipher {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val secretKey = keyStore.getKey("biometric_key", null) as SecretKey
    val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    return cipher
  }

  private fun encryptAndSaveToken(token: String) {
    val cipher = getCipher()
    val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv

    val sharedPreferences = getSharedPrefs()
    sharedPreferences.edit()
      .putString("encrypted_token", Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
      .putString("encrypted_iv", Base64.encodeToString(iv, Base64.DEFAULT))
      .apply()
  }

  private fun getSharedPrefs() =
    applicationContext.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
}

sealed class LoginUiState {
  object Idle : LoginUiState()
  object LockedOut : LoginUiState()
  object Loading : LoginUiState()
  object Success : LoginUiState()
  data class Failure(val resId: Int) : LoginUiState()
}
