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
package org.openmrs.android.fhir.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.openmrs.android.fhir.LoginRepository
import timber.log.Timber
import javax.inject.Inject

class LoginActivityViewModel @Inject constructor(private val applicationContext: Context) : ViewModel() {
  private val loginRepository by lazy {
    LoginRepository.getInstance(applicationContext)
  }

  suspend fun createIntent(): Intent? {
    loginRepository.updateAuthIfConfigurationChanged()
    loginRepository.initializeAppAuth()
    return loginRepository.getAuthIntent()
  }

  fun getLastConfigurationError(): AuthorizationException? {
    return loginRepository.getLastConfigurationError()
  }

  suspend fun isAuthAlreadyEstablished() = loginRepository.isAuthEstablished()

  suspend fun handleLoginResponse(response: AuthorizationResponse?, ex: AuthorizationException?) {
    if (response != null || ex != null) {
      loginRepository.updateAfterAuthorization(response, ex)
    }
    when {
      response?.authorizationCode != null -> {
        loginRepository.exchangeCodeForToken(response, ex)
      }
      ex != null -> {
        Timber.e("Authorization flow failed: " + ex.message)
      }
      else -> {
        Timber.e("No authorization state retained - reauthorization required")
      }
    }
  }
}
