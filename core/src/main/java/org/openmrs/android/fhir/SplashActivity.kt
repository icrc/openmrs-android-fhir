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

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.AuthMethod
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.auth.OfflineAuthMethod
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ServerConnectivityState
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.extensions.getServerConnectivityState
import org.openmrs.android.fhir.ui.screens.SplashScreen

class SplashActivity : AppCompatActivity() {
  companion object {
    @VisibleForTesting const val EXTRA_SKIP_AUTH_FLOW = "skip_auth_flow"
  }

  private lateinit var authStateManager: AuthStateManager
  private lateinit var executor: Executor
  private lateinit var biometricPrompt: BiometricPrompt
  private var isBiometricReset = false
  private val apiManager by lazy { ApiManager(applicationContext) }

  private var isProgressVisible by mutableStateOf(false)
  private var statusText by mutableStateOf<String?>(null)

  private val confirmCredLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
      if (res.resultCode == RESULT_OK) {
        val dec = BiometricUtils.getDecryptionCipher(this)
        if (dec != null) {
          val token = BiometricUtils.decryptToken(dec, this)
          if (token != null) {
            navigateToMainActivity()
            return@registerForActivityResult
          }
        }
      }

      lifecycleScope.launch { handleConnectivityWhenOfflineLoginUnavailable() }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val navController = rememberNavController()
      NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
          SplashScreen(isProgressVisible = isProgressVisible, statusText = statusText)
        }
      }
    }

    authStateManager = AuthStateManager.getInstance(applicationContext)
    executor = ContextCompat.getMainExecutor(this)

    if (!intent.getBooleanExtra(EXTRA_SKIP_AUTH_FLOW, false)) {
      lifecycleScope.launch {
        val userUuid = getUserUuid()
        if (userUuid != null && BiometricUtils.getOfflineAuthMethod(this@SplashActivity) != null) {
          resetBiometricKeyIfNeeded()
        }
        setupBiometricPrompt()
        handleAuthenticationFlow()
      }
    }
  }

  private suspend fun handleAuthenticationFlow() {
    val userUuid = getUserUuid()
    val offlineAuthMethod = BiometricUtils.getOfflineAuthMethod(this)
    if (userUuid == null || isBiometricReset || offlineAuthMethod == null) {
      handleConnectivityForAuth()
    } else {
      if (authStateManager.isAuthenticated()) {
        promptBiometricAuthentication(offlineAuthMethod)
      } else {
        when (getConnectivityState()) {
          ServerConnectivityState.ServerConnected -> redirectToAuthFlow()
          ServerConnectivityState.InternetOnly -> promptBiometricAuthentication(offlineAuthMethod)
          ServerConnectivityState.Offline -> promptBiometricAuthentication(offlineAuthMethod)
        }
      }
    }
  }

  private suspend fun getUserUuid(): String? {
    return try {
      applicationContext.dataStore.data.first()[PreferenceKeys.USER_UUID]
    } catch (_: Exception) {
      null
    }
  }

  private suspend fun getConnectivityState(): ServerConnectivityState {
    showConnectivityCheckInProgress()
    val state = applicationContext.getServerConnectivityState(apiManager)
    when (state) {
      ServerConnectivityState.ServerConnected -> hideConnectivityStatus()
      ServerConnectivityState.InternetOnly ->
        showConnectivityFailureMessage(R.string.splash_status_server_unreachable)
      ServerConnectivityState.Offline ->
        showConnectivityFailureMessage(R.string.splash_status_offline)
    }
    return state
  }

  private fun showConnectivityCheckInProgress() {
    isProgressVisible = true
    statusText = getString(R.string.splash_status_checking_connectivity)
  }

  private fun showConnectivityFailureMessage(@StringRes messageId: Int) {
    isProgressVisible = false
    statusText = getString(messageId)
  }

  private fun hideConnectivityStatus() {
    isProgressVisible = false
    statusText = null
  }

  private suspend fun handleConnectivityForAuth() {
    when (getConnectivityState()) {
      ServerConnectivityState.ServerConnected -> redirectToAuthFlow()
      ServerConnectivityState.InternetOnly -> showServerUnavailableDialog()
      ServerConnectivityState.Offline -> showNoInternetDialog()
    }
  }

  private suspend fun handleConnectivityWhenOfflineLoginUnavailable() {
    when (getConnectivityState()) {
      ServerConnectivityState.ServerConnected -> redirectToAuthFlow()
      ServerConnectivityState.InternetOnly -> showServerUnavailableDialog()
      ServerConnectivityState.Offline -> loginWithInternetOrExit()
    }
  }

  private fun promptBiometricAuthentication(method: OfflineAuthMethod) {
    val biometricManager = BiometricManager.from(this)
    val promptBuilder =
      BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.biometric_prompt_title))

    when (method) {
      OfflineAuthMethod.BIOMETRIC -> {
        if (
          biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
          val cipher = BiometricUtils.getEncryptionCipher(this)
          if (cipher != null) {
            promptBuilder
              .setSubtitle(getString(R.string.biometric_prompt_subtitle))
              .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
              .setNegativeButtonText(getString(R.string.cancel))
            biometricPrompt.authenticate(
              promptBuilder.build(),
              BiometricPrompt.CryptoObject(cipher),
            )
            return
          }
        }
      }
      OfflineAuthMethod.DEVICE_CREDENTIAL -> {
        if (
          biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
          promptBuilder
            .setSubtitle(getString(R.string.use_device_credential))
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)

          val cipher = BiometricUtils.getEncryptionCipher(this)
          if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            if (cipher != null) {
              biometricPrompt.authenticate(
                promptBuilder.build(),
                BiometricPrompt.CryptoObject(cipher),
              )
              return
            }
          } else {
            biometricPrompt.authenticate(promptBuilder.build())
            return
          }
          return
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
          val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
          if (km.isDeviceSecure) {
            val intent =
              km.createConfirmDeviceCredentialIntent(
                getString(R.string.biometric_prompt_title),
                getString(R.string.use_device_credential),
              )
            if (intent != null) {
              confirmCredLauncher.launch(intent)
              return
            }
          }
        }
      }
    }

    lifecycleScope.launch { handleConnectivityWhenOfflineLoginUnavailable() }
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
            lifecycleScope.launch { handleConnectivityWhenOfflineLoginUnavailable() }
          }

          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            val cipher = result.cryptoObject?.cipher
            if (cipher != null) {
              val token = BiometricUtils.decryptToken(cipher, this@SplashActivity)
              if (token != null) {
                navigateToMainActivity()
                return
              }
            }
            lifecycleScope.launch { handleConnectivityWhenOfflineLoginUnavailable() }
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
      .setTitle(getString(R.string.no_internet_connection))
      .setMessage(
        getString(R.string.please_connect_to_the_internet_to_continue_with_authentication),
      )
      .setPositiveButton(getString(R.string.retry)) { _, _ ->
        lifecycleScope.launch { handleAuthenticationFlow() }
      }
      .setNegativeButton(getString(R.string.exit)) { _, _ -> finish() }
      .setCancelable(false)
      .show()
  }

  private fun showServerUnavailableDialog() {
    AlertDialog.Builder(this)
      .setTitle(getString(R.string.server_unreachable_title))
      .setMessage(getString(R.string.server_unreachable_dialog_message))
      .setPositiveButton(getString(R.string.retry)) { _, _ ->
        lifecycleScope.launch { handleConnectivityForAuth() }
      }
      .setNegativeButton(getString(R.string.exit)) { _, _ -> finish() }
      .setCancelable(false)
      .show()
  }

  private fun loginWithInternetOrExit() {
    AlertDialog.Builder(this)
      .setTitle(getString(R.string.offline_login_unavailable))
      .setMessage(
        getString(R.string.try_logging_in_with_active_internet_connection),
      )
      .setPositiveButton(R.string.button_login) { _, _ ->
        lifecycleScope.launch { redirectToAuthFlow() }
      }
      .setNegativeButton(getString(R.string.exit)) { _, _ -> finish() }
      .setCancelable(false)
      .show()
  }

  private fun resetBiometricKeyIfNeeded() {
    if (BiometricUtils.hasBiometricStateChanged(this)) {
      BiometricUtils.deleteBiometricKey(this)
      BiometricUtils.updateBiometricEnrollmentState(this)
      isBiometricReset = true
      showToast(R.string.password_changed, length = Toast.LENGTH_LONG)
    }
  }

  private fun showToast(resId: Int, arg: String? = null, length: Int = Toast.LENGTH_SHORT) {
    val msg = arg?.let { getString(resId, it) } ?: getString(resId)
    Toast.makeText(this, msg, length).show()
  }

  @VisibleForTesting
  fun updateSplashStatusForTest(progressVisible: Boolean, statusText: String?) {
    isProgressVisible = progressVisible
    this.statusText = statusText
  }
}
