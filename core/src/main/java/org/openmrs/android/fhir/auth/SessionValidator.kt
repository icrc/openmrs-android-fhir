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
package org.openmrs.android.fhir.auth

import android.content.Context
import org.openmrs.android.fhir.LoginRepository
import org.openmrs.android.fhir.data.remote.ApiManager
import org.openmrs.android.fhir.data.remote.ApiResponse
import org.openmrs.android.fhir.data.remote.model.SessionResponse
import timber.log.Timber

/** Whether the server still accepts the credentials the app holds. */
enum class SessionStatus {
  /** The server confirmed the session. */
  Valid,

  /** The server (or the stored credentials) say the user has to sign in again. */
  Invalid,

  /** Could not tell, e.g. the server did not answer; nothing to ask of the user. */
  Unknown,
}

/**
 * Checks, right before an automatic sync, that the session is still good: a password that changed
 * or a token that expired on the server must not turn into a sync that fails in the background.
 * Read only: it asks the server who the current user is.
 */
class SessionValidator(context: Context, private val apiManager: ApiManager) {
  private val appContext = context.applicationContext
  private val authStateManager = AuthStateManager.getInstance(appContext)
  private val loginRepository = LoginRepository.getInstance(appContext)

  suspend fun check(): SessionStatus =
    try {
      when (authStateManager.getAuthMethod()) {
        AuthMethod.BASIC -> checkBasic()
        AuthMethod.OPENID -> checkOpenId()
      }
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.w(e, "Could not check the session")
      SessionStatus.Unknown
    }

  private suspend fun checkBasic(): SessionStatus {
    val credentials = loginRepository.getBasicAuthEncodedString()
    if (credentials.isEmpty()) return SessionStatus.Invalid
    return interpret(apiManager.validateSession("Basic $credentials"))
  }

  private suspend fun checkOpenId(): SessionStatus {
    if (!authStateManager.current.isAuthorized) return SessionStatus.Invalid
    if (authStateManager.current.needsTokenRefresh) {
      // A refresh token renews the session without bothering the user.
      runCatching { loginRepository.refreshAccessToken() }
        .onFailure { Timber.w(it, "Token refresh failed") }
    }
    val state = authStateManager.current
    val token = state.accessToken
    if (!state.isAuthorized || state.needsTokenRefresh || token.isNullOrEmpty()) {
      return SessionStatus.Invalid
    }
    return interpret(apiManager.validateSession("Bearer $token"))
  }

  private fun interpret(response: ApiResponse<SessionResponse>): SessionStatus =
    when (response) {
      is ApiResponse.Success ->
        if (response.data?.authenticated == true) SessionStatus.Valid else SessionStatus.Invalid
      is ApiResponse.UnauthorizedAccess,
      is ApiResponse.ApiError, -> SessionStatus.Invalid
      else -> SessionStatus.Unknown
    }
}
