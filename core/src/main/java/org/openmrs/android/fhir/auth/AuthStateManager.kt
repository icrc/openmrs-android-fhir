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

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse

/**
 * An example persistence mechanism for an [AuthState] instance. This stores the instance in a
 * shared preferences file, and provides thread-safe access and mutation.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "AUTH_STATE")

class AuthStateManager private constructor(private val context: Context) {
  private val key by lazy { stringPreferencesKey("STATE") }
  val current: AuthState
    get() {
      val serializedJson = runBlocking { context.dataStore.data.first()[key] } ?: return AuthState()
      return AuthState.jsonDeserialize(serializedJson)
    }

  suspend fun replace(state: AuthState) {
    context.dataStore.edit { pref -> pref[key] = state.jsonSerializeString() }
  }

  suspend fun updateAfterAuthorization(
    response: AuthorizationResponse?,
    ex: AuthorizationException?,
  ) {
    val current = current
    current.update(response, ex)
    replace(current)
  }

  suspend fun updateAfterTokenResponse(
    response: TokenResponse?,
    ex: AuthorizationException?,
  ) {
    val current = current
    current.update(response, ex)
    replace(current)
  }

  companion object {
    @SuppressLint("StaticFieldLeak") private var INSTANCE: AuthStateManager? = null

    @Synchronized
    fun getInstance(context: Context): AuthStateManager {
      if (INSTANCE == null) {
        INSTANCE = AuthStateManager(context.applicationContext)
      }
      return INSTANCE as AuthStateManager
    }
  }
}
