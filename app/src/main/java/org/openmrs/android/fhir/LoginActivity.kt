/*
 * Copyright 2022-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openmrs.android.fhir

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.databinding.ActivityLoginBinding
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.viewmodel.LoginActivityViewModel
import timber.log.Timber

class LoginActivity : AppCompatActivity() {

  private lateinit var binding: ActivityLoginBinding
  private val viewModel: LoginActivityViewModel by viewModels()

  private val getContent =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
          if (result.resultCode == RESULT_OK) {
            Timber.i("Exchange for token")
            val response = result.data?.let { AuthorizationResponse.fromIntent(it) }
            val ex = AuthorizationException.fromIntent(result.data)
            viewModel.handleLoginResponse(response, ex)
            val mainActivityIntent = Intent(this@LoginActivity, MainActivity::class.java)
            startActivity(mainActivityIntent)
          }
        }
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)
    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
//        if (viewModel.isAuthAlreadyEstablished()) {
//          Timber.i("Auth already established do not show login again")
//          val mainActivityIntent = Intent(this@LoginActivity, MainActivity::class.java)
//          startActivity(mainActivityIntent)
//        } else {
        val loginIntent = viewModel.createIntent()
        val lastConfigurationError = viewModel.getLastConfigurationError()
        if (lastConfigurationError != null) {
          Toast.makeText(
            this@LoginActivity,
            lastConfigurationError.cause?.localizedMessage ?: lastConfigurationError.localizedMessage,
            Toast.LENGTH_LONG
          ).show()
          binding.buttonLogin.setOnClickListener {
            Timber.i("restart current login activity as configuration can't be retrieved")
            val intent = this@LoginActivity.intent
            this@LoginActivity.finish()
            startActivity(intent)
          }
        } else
          binding.buttonLogin.setOnClickListener { getContent.launch(loginIntent) }
      }
//      }
    }
  }
}
