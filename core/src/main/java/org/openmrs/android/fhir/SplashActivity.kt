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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.Executor
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
            navigateToMainActivity()
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
          BiometricManager.Authenticators.BIOMETRIC_WEAK or
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

    // Check for biometric + device credential (PIN/pattern/password)
    when (
      biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL,
      )
    ) {
      BiometricManager.BIOMETRIC_SUCCESS -> {
        biometricPrompt.authenticate(promptInfo)
      }
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
        // Check if device credential is available as fallback
        if (
          biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
          biometricPrompt.authenticate(promptInfo)
        } else {
          Toast.makeText(this, "No authentication method available", Toast.LENGTH_SHORT).show()
          navigateToMainActivity()
        }
      }
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
        // Check if device credential is available as fallback
        if (
          biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
          biometricPrompt.authenticate(promptInfo)
        } else {
          Toast.makeText(
              this,
              "Authentication features are currently unavailable",
              Toast.LENGTH_SHORT,
            )
            .show()
          navigateToMainActivity()
        }
      }
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
        // Check if device credential is available as fallback
        if (
          biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
          biometricPrompt.authenticate(promptInfo)
        } else {
          Toast.makeText(
              this,
              "No authentication method set up. Please set up biometric or screen lock",
              Toast.LENGTH_SHORT,
            )
            .show()
          navigateToMainActivity()
        }
      }
      BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
        Toast.makeText(this, "Security update required for authentication", Toast.LENGTH_SHORT)
          .show()
        navigateToMainActivity()
      }
      BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
        Toast.makeText(this, "Authentication not supported on this device", Toast.LENGTH_SHORT)
          .show()
        navigateToMainActivity()
      }
      BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
        Toast.makeText(this, "Authentication status unknown", Toast.LENGTH_SHORT).show()
        navigateToMainActivity()
      }
      else -> {
        Toast.makeText(this, "Authentication not available", Toast.LENGTH_SHORT).show()
        navigateToMainActivity()
      }
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
}
