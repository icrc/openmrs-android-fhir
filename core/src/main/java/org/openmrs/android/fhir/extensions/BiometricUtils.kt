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

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.openmrs.android.fhir.EncryptionHelper
import timber.log.Timber

object BiometricUtils {

  private const val KEY_ALIAS = "biometric_key"
  private const val PREFS_NAME = "secure_prefs"
  private const val TOKEN_KEY = "encrypted_token"
  private const val IV_KEY = "encrypted_iv"
  private const val BIOMETRIC_ENROLLED_KEY = "biometric_enrolled"

  /** Creates a biometric key in the AndroidKeyStore if it does not already exist. */
  fun createBiometricKeyIfNotExists() {
    try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      if (!keyStore.containsAlias(KEY_ALIAS)) {
        val keyGenerator =
          KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
          )
        val keyGenParameterSpec =
          KeyGenParameterSpec.Builder(
              KEY_ALIAS,
              KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
      }
    } catch (e: Exception) {
      Timber.e("Error creating biometric key: ${e.message}")
    }
  }

  fun getSecretKey(): SecretKey? {
    return try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    } catch (e: Exception) {
      Timber.e("Failed to get secret key: ${e.message}")
      null
    }
  }

  fun deleteBiometricKey(context: Context) {
    try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      keyStore.deleteEntry(KEY_ALIAS)
      getSharedPrefs(context).edit {
        remove(TOKEN_KEY)
        remove(IV_KEY)
      }
    } catch (e: Exception) {
      Timber.e("Failed to delete biometric key: ${e.message}")
    }
  }

  fun getSharedPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun isBiometricEnrolled(context: Context): Boolean {
    val biometricManager = BiometricManager.from(context)
    return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
      BiometricManager.BIOMETRIC_SUCCESS
  }

  fun hasBiometricStateChanged(context: Context): Boolean {
    val prefs = getSharedPrefs(context)
    val previous = prefs.getBoolean(BIOMETRIC_ENROLLED_KEY, false)
    val current = isBiometricEnrolled(context)
    return previous != current
  }

  fun updateBiometricEnrollmentState(context: Context) {
    val current = isBiometricEnrolled(context)
    getSharedPrefs(context).edit { putBoolean(BIOMETRIC_ENROLLED_KEY, current) }
  }

  fun saveToken(cipher: Cipher, token: String, context: Context) {
    val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv
    getSharedPrefs(context).edit {
      putString(TOKEN_KEY, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
      putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
    }
  }

  fun decryptToken(cipher: Cipher, context: Context): String? {
    return try {
      val prefs = getSharedPrefs(context)
      val tokenBase64 = prefs.getString(TOKEN_KEY, null) ?: return null
      val encryptedToken = Base64.decode(tokenBase64, Base64.DEFAULT)
      val decryptedBytes = cipher.doFinal(encryptedToken)
      String(decryptedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
      Timber.e("Decryption failed: ${e.message}")
      null
    }
  }

  fun getDecryptionCipher(context: Context): Cipher? {
    return try {
      val secretKey = getSecretKey() ?: return null
      val prefs = getSharedPrefs(context)
      val ivBase64 = prefs.getString(IV_KEY, null) ?: return null
      val iv = Base64.decode(ivBase64, Base64.DEFAULT)
      EncryptionHelper.getDecryptionCipher(secretKey, iv)
    } catch (e: Exception) {
      Timber.e("Cipher init failed: ${e.message}")
      null
    }
  }

  fun getEncryptionCipher(): Cipher? {
    return try {
      val secretKey = getSecretKey() ?: return null
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      cipher
    } catch (e: Exception) {
      Timber.e("Encryption cipher init failed: ${e.message}")
      null
    }
  }

  fun encryptAndSaveToken(token: String, cipher: Cipher, context: Context) {
    try {
      val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
      val iv = cipher.iv
      getSharedPrefs(context).edit {
        putString(TOKEN_KEY, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
        putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
      }
    } catch (e: Exception) {
      Timber.e("Token encryption failed: ${e.message}")
    }
  }
}
