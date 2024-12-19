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
package org.openmrs.android.fhir.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openmrs.android.fhir.LoginRepository
import org.openmrs.android.fhir.auth.AuthStateManager
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ApiResponse
import org.openmrs.android.fhir.data.remote.model.SessionResponse

class BasicLoginActivityViewModel @Inject constructor(private val applicationContext: Context, private val apiManager: ApiManager) :
  ViewModel() {
  private val authStateManager by lazy { AuthStateManager.getInstance(applicationContext) }

  private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
  val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  fun login(username: String, password: String) = viewModelScope.launch {
    _uiState.value = LoginUiState.Loading
    if (isLockedOut()) {
      _uiState.value = LoginUiState.LockedOut
      return@launch
    }

    validateCredentials(username, password)
  }

  private suspend fun validateCredentials(username: String, password: String) {
    if(username.isEmpty() || password.isEmpty()) {
      _uiState.value = LoginUiState.Failure("Either username or password is empty")
      return
    }

    val credentials = "$username:$password"
    val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
    when (val response = apiManager.validateSession("Basic $encodedCredentials")) {
      is ApiResponse.Success<SessionResponse> -> {
        val authenticated = response.data?.authenticated == true
        if(!authenticated) {
          incrementFailedAttempts()
          _uiState.value = LoginUiState.Failure("Invalid Credentials")
          return
        }
        authStateManager.updateBasicAuthCredentials(username, password)
        _uiState.value = LoginUiState.Success
        return
      }
      is ApiResponse.NoInternetConnection -> {
        _uiState.value = LoginUiState.Failure("No Internet Connection")
      }
      else -> _uiState.value = LoginUiState.Failure("Internal Error")
    }
  }

  private suspend fun incrementFailedAttempts() {
    authStateManager.incrementFailedAttempts()
  }

  private suspend fun isLockedOut(): Boolean {
    return authStateManager.isLockedOut()
  }
}

sealed class LoginUiState {
  object Idle : LoginUiState()
  object LockedOut : LoginUiState()
  object Loading : LoginUiState()
  object Success : LoginUiState()
  data class Failure(val errorMessage: String) : LoginUiState()
}
