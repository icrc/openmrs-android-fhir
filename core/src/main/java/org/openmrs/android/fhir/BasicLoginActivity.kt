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
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.auth.OfflineAuthMethod
import org.openmrs.android.fhir.extensions.AuthDialogs
import org.openmrs.android.fhir.extensions.BiometricPromptHelper
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.ui.screens.BasicLoginScreen
import org.openmrs.android.fhir.viewmodel.BasicLoginActivityViewModel
import org.openmrs.android.fhir.viewmodel.LoginUiState

class BasicLoginActivity : AppCompatActivity() {

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<BasicLoginActivityViewModel> { viewModelFactory }

  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var executor: Executor
  private var offlineAuthMethod: OfflineAuthMethod? = null

  private var loginUiState: LoginUiState by mutableStateOf(LoginUiState.Idle)
  val dialogStateForTest: MutableState<AuthDialogs.DialogConfig?> = mutableStateOf(null)

  private val confirmCredLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
      BiometricPromptHelper.handleConfirmCredentialResult(
        activity = this,
        result = res,
        sessionTokenProvider = { viewModel.sessionTokenToEncrypt },
        cipherProvider = { BiometricUtils.getEncryptionCipher(this) },
        onNavigate = { navigateToMain() },
        onSaved = { offlineAuthMethod?.let { BiometricUtils.setOfflineAuthMethod(this, it) } },
      )
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as FhirApplication).appComponent.inject(this)
    setContent {
      val navController = rememberNavController()
      NavHost(navController = navController, startDestination = "basic_login") {
        composable("basic_login") {
          BasicLoginScreen(
            uiState = loginUiState,
            onLoginClick = { username, password -> viewModel.login(username, password) },
          )
        }
      }
      LaunchedEffect(loginUiState) {
        when (loginUiState) {
          is LoginUiState.Failure ->
            showToastMessage(getString((loginUiState as LoginUiState.Failure).resId))
          is LoginUiState.LockedOut -> showToastMessage(getString(R.string.locked_out_message))
          is LoginUiState.Success ->
            AuthDialogs.showOfflineLoginOptIn(
              activity = this@BasicLoginActivity,
              onProceedWithBiometric = { promptBiometricEncryption() },
              navigateToMain = { navigateToMain() },
            )
          else -> Unit
        }
      }
      val dialogConfig = dialogStateForTest.value
      if (dialogConfig != null) {
        androidx.compose.material3.AlertDialog(
          onDismissRequest = dialogConfig.onDismiss,
          confirmButton = {
            androidx.compose.material3.TextButton(onClick = dialogConfig.onConfirm) {
              androidx.compose.material3.Text(text = dialogConfig.positiveText.toString())
            }
          },
          dismissButton = {
            androidx.compose.material3.TextButton(onClick = dialogConfig.onDismiss) {
              androidx.compose.material3.Text(text = dialogConfig.negativeText.toString())
            }
          },
          title = { androidx.compose.material3.Text(text = dialogConfig.title.toString()) },
          text = { androidx.compose.material3.Text(text = dialogConfig.message.toString()) },
        )
      }
    }

    executor = ContextCompat.getMainExecutor(this)

    biometricPrompt =
      BiometricPromptHelper.createBiometricPrompt(
        activity = this,
        executor = executor,
        sessionTokenProvider = { viewModel.sessionTokenToEncrypt },
        onNavigate = { navigateToMain() },
        onSaved = { offlineAuthMethod?.let { BiometricUtils.setOfflineAuthMethod(this, it) } },
      )

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state -> loginUiState = state }
      }
    }
  }

  private fun promptBiometricEncryption() {
    BiometricPromptHelper.promptBiometricEncryption(
      activity = this,
      biometricPrompt = biometricPrompt,
      confirmCredLauncher = confirmCredLauncher,
      navigateToMain = { navigateToMain() },
      showToast = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() },
      onMethodSelected = { method -> offlineAuthMethod = method },
    )
  }

  private fun navigateToMain() {
    startActivity(Intent(this@BasicLoginActivity, MainActivity::class.java))
    finish()
  }

  private fun showToastMessage(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }

  @VisibleForTesting
  fun setLoginUiStateForTest(state: LoginUiState) {
    loginUiState = state
  }
}
