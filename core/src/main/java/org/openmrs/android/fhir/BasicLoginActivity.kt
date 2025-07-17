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
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.databinding.ActivityBasicLoginBinding
import org.openmrs.android.fhir.viewmodel.BasicLoginActivityViewModel
import org.openmrs.android.fhir.viewmodel.LoginUiState

class BasicLoginActivity : AppCompatActivity() {

  private lateinit var binding: ActivityBasicLoginBinding

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<BasicLoginActivityViewModel> { viewModelFactory }

  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var executor: Executor

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as FhirApplication).appComponent.inject(this)
    binding = ActivityBasicLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)

    executor = ContextCompat.getMainExecutor(this)

    biometricPrompt =
      BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            val cipher = result.cryptoObject?.cipher
            val sessionToken = viewModel.sessionTokenToEncrypt

            if (cipher != null && sessionToken != null) {
              viewModel.encryptAndSaveToken(sessionToken, cipher)
            }

            navigateToMain()
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Toast.makeText(this@BasicLoginActivity, "Auth error: $errString", Toast.LENGTH_SHORT)
              .show()
          }

          override fun onAuthenticationFailed() {
            Toast.makeText(this@BasicLoginActivity, "Authentication failed", Toast.LENGTH_SHORT)
              .show()
          }
        },
      )

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
          when (state) {
            is LoginUiState.Idle -> Unit
            is LoginUiState.Failure -> {
              binding.progressIndicator.visibility = View.GONE
              showToastMessage(getString(state.resId))
            }
            is LoginUiState.Loading -> {
              binding.progressIndicator.visibility = View.VISIBLE
            }
            is LoginUiState.LockedOut -> {
              binding.progressIndicator.visibility = View.GONE
              showToastMessage(getString(R.string.locked_out_message))
            }
            is LoginUiState.Success -> {
              binding.progressIndicator.visibility = View.GONE
              promptBiometricEncryption()
            }
          }
        }
      }
    }

    binding.basicLoginButton.setOnClickListener {
      val username = binding.usernameInputText.text.toString()
      val password = binding.passwordInputText.text.toString()
      viewModel.login(username, password)
    }
  }

  private fun promptBiometricEncryption() {
    val biometricManager = BiometricManager.from(this)

    val canUseStrongBiometric =
      biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

    val canUseDeviceCredential =
      biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
        BiometricManager.BIOMETRIC_SUCCESS

    val promptBuilder =
      BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.biometric_prompt_title))

    if (canUseStrongBiometric) {
      promptBuilder
        .setSubtitle(getString(R.string.biometric_prompt_subtitle))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText(getString(R.string.cancel))

      val cipher = viewModel.getEncryptionCipher()
      if (cipher != null) {
        biometricPrompt.authenticate(promptBuilder.build(), BiometricPrompt.CryptoObject(cipher))
      } else {
        Toast.makeText(this, "Encryption not available", Toast.LENGTH_LONG).show()
        navigateToMain()
      }
    } else if (canUseDeviceCredential) {
      promptBuilder
        .setSubtitle(getString(R.string.use_device_credential))
        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)

      biometricPrompt.authenticate(promptBuilder.build())
    } else {
      Toast.makeText(this, getString(R.string.no_supported_auth_method), Toast.LENGTH_LONG).show()
      navigateToMain()
    }
  }

  private fun navigateToMain() {
    startActivity(Intent(this@BasicLoginActivity, MainActivity::class.java))
    finish()
  }

  private fun showToastMessage(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }
}
