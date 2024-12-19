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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.databinding.ActivityBasicLoginBinding
import org.openmrs.android.fhir.viewmodel.BasicLoginActivityViewModel
import org.openmrs.android.fhir.viewmodel.LoginUiState

class BasicLoginActivity : AppCompatActivity() {

  private lateinit var binding: ActivityBasicLoginBinding

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
  private val viewModel by viewModels<BasicLoginActivityViewModel> { viewModelFactory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (this.application as FhirApplication).appComponent.inject(this)
    binding = ActivityBasicLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
          when (state) {
            is LoginUiState.Idle -> {}
            is LoginUiState.Failure -> {
              Toast.makeText(this@BasicLoginActivity, state.errorMessage, Toast.LENGTH_SHORT).show()
            }

            LoginUiState.Loading -> {

            }
            LoginUiState.LockedOut -> {
              Toast.makeText(this@BasicLoginActivity, "Locked Out", Toast.LENGTH_SHORT).show()
            }
            is LoginUiState.Success -> {
              startActivity(Intent(this@BasicLoginActivity, MainActivity::class.java))
              finish()
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
}
