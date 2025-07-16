package org.openmrs.android.fhir.viewmodel

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.lifecycle.ViewModel
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.LoginRepository
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

class LoginActivityViewModel @Inject constructor(private val applicationContext: Context) :
  ViewModel() {

  private val loginRepository by lazy { LoginRepository.getInstance(applicationContext) }

  suspend fun createIntent(): Intent? {
    loginRepository.updateAuthIfConfigurationChanged()
    loginRepository.initializeAppAuth()
    return loginRepository.getAuthIntent()
  }

  fun getLastConfigurationError(): AuthorizationException? {
    return loginRepository.getLastConfigurationError()
  }

  suspend fun isAuthAlreadyEstablished() = loginRepository.isAuthEstablished()

  suspend fun handleLoginResponse(response: AuthorizationResponse?, ex: AuthorizationException?) {
    if (response != null || ex != null) {
      loginRepository.updateAfterAuthorization(response, ex)
    }

    when {
      response?.authorizationCode != null -> {
        loginRepository.exchangeCodeForToken(response, ex)
        createBiometricKey()
        val sessionToken = loginRepository.getAccessToken() // Or wherever token is stored
        encryptAndSaveToken(sessionToken)
      }
      ex != null -> {
        Timber.e("Authorization flow failed: " + ex.message)
      }
      else -> {
        Timber.e("No authorization state retained - reauthorization required")
      }
    }
  }

  fun createBiometricKey() {
    val keyGenerator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      "AndroidKeyStore"
    )
    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
      "biometric_key", // alias
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
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
