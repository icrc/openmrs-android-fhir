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
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.databinding.ActivityLoginBinding
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
            promptBiometricEncryption() // Prompt biometric after login success
          }
        }
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as FhirApplication).appComponent.inject(this)
    binding = ActivityLoginBinding.inflate(layoutInflater)
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
            val token = viewModel.sessionTokenToEncrypt
            if (cipher != null && token != null) {
              BiometricUtils.encryptAndSaveToken(token, cipher, applicationContext)
            }
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Toast.makeText(
                this@LoginActivity,
                "${getString(R.string.auth_error)} $errString",
                Toast.LENGTH_SHORT,
              )
              .show()
          }

          override fun onAuthenticationFailed() {
            Toast.makeText(
                this@LoginActivity,
                getString(R.string.auth_failed),
                Toast.LENGTH_SHORT,
              )
              .show()
          }
        },
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

      val cipher = BiometricUtils.getEncryptionCipher()
      if (cipher != null) {
        biometricPrompt.authenticate(promptBuilder.build(), BiometricPrompt.CryptoObject(cipher))
      } else {
        // TODO: add dialog encryption issue, try setting up biometric auth later in settings.
        Toast.makeText(this, "Error encountered while setting offline login", Toast.LENGTH_LONG)
          .show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
      }
    } else if (canUseDeviceCredential) {
      promptBuilder
        .setSubtitle(getString(R.string.use_device_credential))
        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
      val cipher = BiometricUtils.getEncryptionCipher() // init with your keystore key
      if (cipher != null) {
        biometricPrompt.authenticate(promptBuilder.build(), BiometricPrompt.CryptoObject(cipher))
      } else {
        biometricPrompt.authenticate(promptBuilder.build())
      }
    } else {
      Toast.makeText(this, getString(R.string.no_supported_offline_auth_method), Toast.LENGTH_LONG)
        .show()
      startActivity(Intent(this, MainActivity::class.java))
      finish()
    }
  }
}
