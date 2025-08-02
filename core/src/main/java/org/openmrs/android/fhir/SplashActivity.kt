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
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.AuthMethod
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.extensions.BiometricUtils

class SplashActivity : AppCompatActivity() {

  private lateinit var authStateManager: AuthStateManager
  private lateinit var executor: Executor
  private lateinit var biometricPrompt: BiometricPrompt

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_splash)

    authStateManager = AuthStateManager.getInstance(applicationContext)
    executor = ContextCompat.getMainExecutor(this)

    resetBiometricKeyIfNeeded()
    setupBiometricPrompt()

    lifecycleScope.launch { handleAuthenticationFlow() }
  }

  private suspend fun handleAuthenticationFlow() {
    val userUuid = getUserUuid()

    if (userUuid == null) {
      if (isInternetAvailable()) {
        redirectToAuthFlow()
      } else {
        showNoInternetDialog()
      }
    } else {
      if (authStateManager.isAuthenticated()) {
        promptBiometricAuthentication()
      } else if (isInternetAvailable()) {
        redirectToAuthFlow()
      } else {
        promptBiometricAuthentication()
      }
    }
  }

  private suspend fun getUserUuid(): String? {
    return try {
      applicationContext.dataStore.data.first()[PreferenceKeys.USER_UUID]
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

    val canUseBiometric =
      biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG,
      ) == BiometricManager.BIOMETRIC_SUCCESS

    val canUseCredential =
      biometricManager.canAuthenticate(
        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
      ) == BiometricManager.BIOMETRIC_SUCCESS

    val promptBuilder =
      BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.biometric_prompt_title))

    if (canUseBiometric) {
      val cipher = BiometricUtils.getDecryptionCipher(this)
      if (cipher != null) {
        promptBuilder
          .setSubtitle(getString(R.string.biometric_prompt_subtitle))
          .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
          .setNegativeButtonText(getString(R.string.cancel))

        biometricPrompt.authenticate(promptBuilder.build(), BiometricPrompt.CryptoObject(cipher))
        return
      }
    }

    if (canUseCredential) {
      promptBuilder
        .setSubtitle(getString(R.string.use_device_credential))
        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)

      biometricPrompt.authenticate(promptBuilder.build())
    } else {
      showToast(R.string.no_supported_auth_method)
      navigateToMainActivity()
    }
  }

  private fun setupBiometricPrompt() {
    biometricPrompt =
      BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            showToast(R.string.auth_error, errString.toString())
            finish()
          }

          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            val cipher = result.cryptoObject?.cipher
            if (cipher != null) {
              val token = BiometricUtils.decryptToken(cipher, this@SplashActivity)
              if (token != null) {
                val prefs = BiometricUtils.getSharedPrefs(this@SplashActivity)
                if (!prefs.contains("encrypted_token")) {
                  val currentToken = authStateManager.current.accessToken
                  if (!currentToken.isNullOrBlank()) {
                    BiometricUtils.saveToken(cipher, currentToken, this@SplashActivity)
                  }
                }
                navigateToMainActivity()
              } else {
                showToast(R.string.invalid_session)
                finish()
              }
            } else {
              navigateToMainActivity()
            }
          }

          override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            showToast(R.string.auth_failed)
            finish()
          }
        },
      )
  }

  private fun navigateToMainActivity() {
    startActivity(Intent(this, MainActivity::class.java))
    finish()
  }

  private fun redirectToAuthFlow() {
    val intent =
      when (authStateManager.getAuthMethod()) {
        AuthMethod.BASIC -> Intent(this, BasicLoginActivity::class.java)
        AuthMethod.OPENID -> Intent(this, LoginActivity::class.java)
      }
    startActivity(intent)
    finish()
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

  private fun resetBiometricKeyIfNeeded() {
    if (BiometricUtils.hasBiometricStateChanged(this)) {
      BiometricUtils.deleteBiometricKey(this)
      BiometricUtils.updateBiometricEnrollmentState(this)
    }
  }

  private fun showToast(resId: Int, arg: String? = null) {
    val msg = arg?.let { getString(resId, it) } ?: getString(resId)
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
  }
}
