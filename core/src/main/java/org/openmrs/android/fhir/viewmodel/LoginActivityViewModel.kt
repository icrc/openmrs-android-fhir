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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.LoginRepository
import timber.log.Timber

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

  suspend fun handleLoginResponse(response: AuthorizationResponse?, ex: AuthorizationException?) {
    if (response != null || ex != null) {
      loginRepository.updateAfterAuthorization(response, ex)
    }

    when {
      response?.authorizationCode != null -> {
        loginRepository.exchangeCodeForToken(response, ex)

        try {
          createBiometricKeyIfNotExists()
          val sessionToken = loginRepository.getAccessToken()
          if (sessionToken.isNotBlank()) {
            encryptAndSaveToken(sessionToken)
          }
        } catch (e: Exception) {
          Timber.e("Biometric key error: ${e.message}")
        }
      }
      ex != null -> {
        Timber.e("Authorization flow failed: " + ex.message)
      }
      else -> {
        Timber.e("No authorization state retained - reauthorization required")
      }
    }
  }

  private fun createBiometricKeyIfNotExists() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)

    if (!keyStore.containsAlias("biometric_key")) {
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
          .setUserAuthenticationValidityDurationSeconds(-1)
          .setInvalidatedByBiometricEnrollment(true)
          .build()
      keyGenerator.init(keyGenParameterSpec)
      keyGenerator.generateKey()
    }
  }

  private fun getCipher(): Cipher? {
    return try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      val secretKey = keyStore.getKey("biometric_key", null) as? SecretKey ?: return null

      val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      cipher
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
      Timber.e("Key invalidated: ${e.message}")
      deleteBiometricKey()
      null
    } catch (e: Exception) {
      Timber.e("Cipher creation failed: ${e.message}")
      null
    }
  }

  private fun encryptAndSaveToken(token: String) {
    val cipher = getCipher() ?: return

    val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv

    val sharedPreferences = getSharedPrefs()
    sharedPreferences.edit {
      putString("encrypted_token", Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
        .putString("encrypted_iv", Base64.encodeToString(iv, Base64.DEFAULT))
    }
  }

  private fun deleteBiometricKey() {
    try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      keyStore.deleteEntry("biometric_key")

      getSharedPrefs().edit { remove("encrypted_token").remove("encrypted_iv") }
    } catch (e: Exception) {
      Timber.e("Failed to delete biometric key: ${e.message}")
    }
  }

  private fun getSharedPrefs() =
    applicationContext.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
}
