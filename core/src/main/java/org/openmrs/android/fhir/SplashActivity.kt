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
package org.openmrs.android.fhir

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import java.security.KeyStore
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.AuthMethod
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys

class SplashActivity : AppCompatActivity() {

  private lateinit var authStateManager: AuthStateManager
  private lateinit var executor: Executor
  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var promptInfo: BiometricPrompt.PromptInfo

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_splash)

    resetBiometricKeyIfNeeded() // Reset key if biometric state changed

    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }

    authStateManager = AuthStateManager.getInstance(applicationContext)
    setupBiometricPrompt()

    lifecycleScope.launch { handleAuthenticationFlow() }
  }

  private fun setupBiometricPrompt() {
    executor = ContextCompat.getMainExecutor(this)
    biometricPrompt =
      BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            Toast.makeText(
                applicationContext,
                "Authentication error: $errString",
                Toast.LENGTH_SHORT,
              )
              .show()
            finish()
          }

          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)

            val cipher = result.cryptoObject?.cipher
            if (cipher == null) {
              // Device credential only — fallback login
              navigateToMainActivity()
              return
            }

            try {
              val decryptedToken = decryptSessionToken(cipher)

              if (decryptedToken != null) {
                // ✅ Check if secure_prefs is empty — user added biometric later
                val prefs = getSharedPreferences("secure_prefs", MODE_PRIVATE)
                if (!prefs.contains("encrypted_token")) {
                  // First time biometric available, store token again
                  val currentToken = authStateManager.current.accessToken
                  if (!currentToken.isNullOrBlank()) {
                    encryptAndSaveToken(currentToken, cipher)
                  }
                }

                navigateToMainActivity()
              } else {
                Toast.makeText(applicationContext, "Invalid session", Toast.LENGTH_SHORT).show()
                finish()
              }
            } catch (e: Exception) {
              e.printStackTrace()
              Toast.makeText(applicationContext, "Decryption failed", Toast.LENGTH_SHORT).show()
              finish()
            }
          }

          override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
            finish()
          }
        },
      )

    promptInfo =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle("Offline Device Authentication")
        .setSubtitle("Use your biometric credential or device PIN/pattern/password")
        .setAllowedAuthenticators(
          BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()
  }

  private suspend fun handleAuthenticationFlow() {
    val userUuid = getUserUuid()

    if (userUuid == null) {
      // No USER_UUID found, always go for auth flow
      if (isInternetAvailable()) {
        redirectToAuthFlow()
      } else {
        showNoInternetDialog()
      }
    } else {
      // USER_UUID exists, check authentication state
      if (authStateManager.isAuthenticated()) {
        // User is authenticated, ask for biometric only
        promptBiometricAuthentication()
      } else {
        // User is not authenticated
        if (isInternetAvailable()) {
          redirectToAuthFlow()
        } else {
          // No internet, opt for biometric
          promptBiometricAuthentication()
        }
      }
    }
  }

  private suspend fun getUserUuid(): String? {
    return try {
      val preferences = applicationContext.dataStore.data.first()
      preferences[PreferenceKeys.USER_UUID]
    } catch (e: Exception) {
      null
    }
  }

  private fun isInternetAvailable(): Boolean {
    val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
  }

  private fun promptBiometricAuthentication() {
    val biometricManager = BiometricManager.from(this)

    val canUseStrongBiometric =
      biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

    val canUseDeviceCredential =
      biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
        BiometricManager.BIOMETRIC_SUCCESS

    val promptBuilder =
      BiometricPrompt.PromptInfo.Builder().setTitle("Offline Device Authentication")

    if (canUseStrongBiometric) {
      val cipher = getDecryptionCipher()

      if (cipher != null) {
        promptBuilder
          .setSubtitle("Use your biometric credential")
          .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
          .setNegativeButtonText("Cancel") // ✅ OK for strong biometric only

        biometricPrompt.authenticate(promptBuilder.build(), BiometricPrompt.CryptoObject(cipher))
        return
      }
    }

    if (canUseDeviceCredential) {
      promptBuilder
        .setSubtitle("Use your device PIN/pattern/password")
        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)

      biometricPrompt.authenticate(promptBuilder.build())
      return
    }

    Toast.makeText(
        this,
        "No supported authentication method available",
        Toast.LENGTH_LONG,
      )
      .show()
    navigateToMainActivity()
  }

  fun getDecryptionCipher(): Cipher? {
    return try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      val secretKey = keyStore.getKey("biometric_key", null) as? SecretKey ?: return null

      val sharedPreferences = getSharedPreferences("secure_prefs", MODE_PRIVATE)
      val ivBase64 = sharedPreferences.getString("encrypted_iv", null) ?: return null
      val iv = Base64.decode(ivBase64, Base64.DEFAULT)

      val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
      cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
      cipher
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
      e.printStackTrace()
      deleteBiometricKey()
      null
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  private fun showNoInternetDialog() {
    AlertDialog.Builder(this)
      .setTitle("No Internet Connection")
      .setMessage("Please connect to the internet to continue with authentication.")
      .setPositiveButton("Retry") { _, _ -> lifecycleScope.launch { handleAuthenticationFlow() } }
      .setNegativeButton("Exit") { _, _ -> finish() }
      .setCancelable(false)
      .show()
  }

  private fun redirectToAuthFlow() {
    when (authStateManager.getAuthMethod()) {
      AuthMethod.BASIC -> startActivity(Intent(this@SplashActivity, BasicLoginActivity::class.java))
      AuthMethod.OPENID -> startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
    }
    finish()
  }

  private fun navigateToMainActivity() {
    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
    finish()
  }

  fun decryptSessionToken(cipher: Cipher): String? {
    return try {
      val sharedPreferences = getSharedPreferences("secure_prefs", MODE_PRIVATE)
      val tokenBase64 = sharedPreferences.getString("encrypted_token", null) ?: return null
      val encryptedToken = Base64.decode(tokenBase64, Base64.DEFAULT)
      val decryptedBytes = cipher.doFinal(encryptedToken)
      String(decryptedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  fun encryptAndSaveToken(token: String, cipher: Cipher) {
    val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv

    val sharedPreferences = getSharedPreferences("secure_prefs", MODE_PRIVATE)
    sharedPreferences.edit {
      putString("encrypted_token", Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
        .putString("encrypted_iv", Base64.encodeToString(iv, Base64.DEFAULT))
    }
  }

  private fun isBiometricEnrolled(): Boolean {
    val biometricManager = BiometricManager.from(this)
    return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
      BiometricManager.BIOMETRIC_SUCCESS
  }

  private fun hasBiometricStateChanged(): Boolean {
    val prefs = getSharedPreferences("secure_prefs", MODE_PRIVATE)
    val previousState = prefs.getBoolean("biometric_enrolled", false)
    val currentState = isBiometricEnrolled()
    return previousState != currentState
  }

  private fun updateBiometricEnrollmentState(current: Boolean) {
    val prefs = getSharedPreferences("secure_prefs", MODE_PRIVATE)
    prefs.edit { putBoolean("biometric_enrolled", current) }
  }

  private fun resetBiometricKeyIfNeeded() {
    if (hasBiometricStateChanged()) {
      try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry("biometric_key")
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        updateBiometricEnrollmentState(isBiometricEnrolled())
      }
    }
  }

  fun deleteBiometricKey() {
    try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      keyStore.deleteEntry("biometric_key")

      // Optional: Clear encrypted data if needed
      getSharedPreferences("secure_prefs", MODE_PRIVATE).edit {
        remove("encrypted_token").remove("encrypted_iv")
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
