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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.databinding.ActivityLoginBinding
import org.openmrs.android.fhir.extensions.AuthDialogs
import org.openmrs.android.fhir.extensions.BiometricPromptHelper
import org.openmrs.android.fhir.extensions.BiometricUtils
import org.openmrs.android.fhir.viewmodel.LoginActivityViewModel
import timber.log.Timber

class LoginActivity : AppCompatActivity() {

  private lateinit var binding: ActivityLoginBinding

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<LoginActivityViewModel> { viewModelFactory }

  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var executor: Executor

  private val getContent =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
          if (result.resultCode == RESULT_OK) {
            Timber.i("Exchange for token")
            val response = result.data?.let { AuthorizationResponse.fromIntent(it) }
            val ex = AuthorizationException.fromIntent(result.data)
            viewModel.handleLoginResponse(response, ex)
            AuthDialogs.showOfflineLoginOptIn(
              activity = this@LoginActivity,
              onProceedWithBiometric = { promptBiometricEncryption() },
              navigateToMain = { navigateToMain() },
            )
          }
        }
      }
    }

  private val confirmCredLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
      BiometricPromptHelper.handleConfirmCredentialResult(
        activity = this,
        result = res,
        sessionTokenProvider = { viewModel.sessionTokenToEncrypt },
        cipherProvider = { BiometricUtils.getEncryptionCipher(this) },
        onNavigate = { navigateToMain() },
      )
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as FhirApplication).appComponent.inject(this)
    binding = ActivityLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)

    executor = ContextCompat.getMainExecutor(this)

    biometricPrompt =
      BiometricPromptHelper.createBiometricPrompt(
        activity = this,
        executor = executor,
        sessionTokenProvider = { viewModel.sessionTokenToEncrypt },
        onNavigate = { navigateToMain() },
      )

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        val loginIntent = viewModel.createIntent()
        val lastConfigurationError = viewModel.getLastConfigurationError()
        if (lastConfigurationError != null) {
          Toast.makeText(
              this@LoginActivity,
              lastConfigurationError.cause?.localizedMessage
                ?: lastConfigurationError.localizedMessage,
              Toast.LENGTH_LONG,
            )
            .show()
          binding.buttonLogin.setOnClickListener {
            Timber.i("restart current login activity as configuration can't be retrieved")
            val intent = this@LoginActivity.intent
            finish()
            startActivity(intent)
          }
        } else {
          binding.buttonLogin.setOnClickListener { loginIntent?.let { getContent.launch(it) } }
        }
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
    )
  }

  private fun navigateToMain() {
    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
    finish()
  }
}
