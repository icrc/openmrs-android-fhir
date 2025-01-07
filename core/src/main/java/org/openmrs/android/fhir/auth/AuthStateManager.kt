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
import android.app.PendingIntent
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.TokenResponse
import org.openmrs.android.fhir.EncryptionHelper
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.KeystoreHelper
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.model.BasicAuthState
import org.openmrs.android.fhir.extensions.decodeToByteArray
import org.openmrs.android.fhir.extensions.encodeToString
import org.openmrs.android.fhir.extensions.fromJson
import org.openmrs.android.fhir.extensions.hoursInMillis
import org.openmrs.android.fhir.extensions.minutesInMillis
import org.openmrs.android.fhir.extensions.toJson

/**
 * An example persistence mechanism for an [AuthState] instance. This stores the instance in a
 * shared preferences file, and provides thread-safe access and mutation.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "AUTH_STATE")

class AuthStateManager private constructor(private val context: Context) {

  private val key by lazy { stringPreferencesKey("STATE") }
  private val basicAuthStateKey by lazy { stringPreferencesKey("BASIC_AUTH_STATE") }
  private val usernameIV by lazy { stringPreferencesKey("USERNAME_IV") }
  private val passwordIV by lazy { stringPreferencesKey("PASSWORD_IV") }
  private val failedAttemptsKey by lazy { intPreferencesKey("FAILED_ATTEMPTS") }
  private val lockOutDurationKey by lazy { longPreferencesKey("LOCK_OUT_DURATION") }

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

  fun endSessionRequest(pendingIntentSuccess: PendingIntent, pendingIntentCancel: PendingIntent) {
    val authConfig = AuthConfiguration.getInstance(context)
    current.authorizationServiceConfiguration?.let {
      val endSessionRequest =
        EndSessionRequest.Builder(it)
          .setIdTokenHint(current.idToken)
          .setPostLogoutRedirectUri(authConfig.redirectUri)
          .build()
      val authService = AuthorizationService(context)
      authService.performEndSessionRequest(
        endSessionRequest,
        pendingIntentSuccess,
        pendingIntentCancel,
      )
    }
  }

  fun getAuthMethod(): AuthMethod {
    val authMethod = FhirApplication.authMethod(context)
    return AuthMethod.fromValue(authMethod)
  }

  suspend fun isAuthenticated(): Boolean {
    val authMethod = getAuthMethod()
    return when (authMethod) {
      AuthMethod.BASIC -> {
        val basicAuthState = getBasicAuthState()
        val isAuthenticated =
          basicAuthState.authenticated && basicAuthState.expiryEpoch > System.currentTimeMillis()
        if (isAuthenticated) return true
        resetBasicAuthCredentials()
        return false
      }
      AuthMethod.OPENID -> {
        // TODO: fix that point for real offline mode
        // by removing needsTokenRefresh we have an exception later on
        // net.openid.appauth.AuthState.mRefreshToken being null
        // in this method:
        // net.openid.appauth.AuthState.createTokenRefreshRequest(java.util.Map<java.lang.String,java.lang.String>)
        current.isAuthorized && !current.needsTokenRefresh
      }
    }
  }

  suspend fun updateBasicAuthCredentials(username: String, password: String) {
    val key = KeystoreHelper.getKey()
    val (encryptedUsername, uIV) = EncryptionHelper.encrypt(username, key)
    val (encryptedPassword, pIV) = EncryptionHelper.encrypt(password, key)
    val basicAuthState =
      BasicAuthState(
        username = encryptedUsername,
        password = encryptedPassword,
        authenticated = true,
        expiryEpoch = System.currentTimeMillis() + BASIC_AUTH_EXPIRY_HOURS.hoursInMillis,
      )
    context.dataStore.edit { pref -> pref[basicAuthStateKey] = basicAuthState.toJson() }
    context.dataStore.edit { pref -> pref[usernameIV] = uIV.encodeToString() }
    context.dataStore.edit { pref -> pref[passwordIV] = pIV.encodeToString() }
  }

  private suspend fun resetBasicAuthCredentials() {
    val basicAuthState = BasicAuthState()
    context.dataStore.edit { pref -> pref[basicAuthStateKey] = basicAuthState.toJson() }
  }

  suspend fun getBasicAuthState(): BasicAuthState {
    val basicAuthStateString = context.dataStore.data.first()[basicAuthStateKey]
    val usernameIV = context.dataStore.data.first()[usernameIV] ?: ""
    val passwordIV = context.dataStore.data.first()[passwordIV] ?: ""
    if (usernameIV.isEmpty() || passwordIV.isEmpty()) {
      return BasicAuthState()
    }
    val basicAuthState = basicAuthStateString?.fromJson<BasicAuthState>() ?: return BasicAuthState()
    val key = KeystoreHelper.getKey()
    return BasicAuthState(
      username =
        EncryptionHelper.decrypt(
          basicAuthState.username,
          key,
          usernameIV.decodeToByteArray(),
        ),
      password =
        EncryptionHelper.decrypt(
          basicAuthState.password,
          key,
          passwordIV.decodeToByteArray(),
        ),
      expiryEpoch = basicAuthState.expiryEpoch,
      authenticated = basicAuthState.authenticated,
    )
  }

  suspend fun incrementFailedAttempts() {
    val newFailedAttemptValue = getFailedAttemptValue() + 1
    updateFailedAttemptValue(newFailedAttemptValue)
    if (newFailedAttemptValue >= MAX_FAILED_ATTEMPTS) {
      val lockoutEndTime = System.currentTimeMillis() + MAX_LOCKOUT_DURATION_MINS.minutesInMillis
      updateLockedOutDuration(lockoutEndTime)
      updateFailedAttemptValue(0)
    }
  }

  suspend fun isLockedOut(): Boolean {
    val lockedOutDuration = getLockOutDurationValue()
    if (lockedOutDuration > System.currentTimeMillis()) return true
    updateLockedOutDuration(System.currentTimeMillis())
    return false
  }

  private suspend fun getFailedAttemptValue(): Int {
    return context.dataStore.data.first()[failedAttemptsKey] ?: 0
  }

  private suspend fun getLockOutDurationValue(): Long {
    return context.dataStore.data.first()[lockOutDurationKey] ?: System.currentTimeMillis()
  }

  private suspend fun updateLockedOutDuration(value: Long) {
    context.dataStore.edit { pref -> pref[lockOutDurationKey] = value }
  }

  private suspend fun updateFailedAttemptValue(value: Int) {
    context.dataStore.edit { pref -> pref[failedAttemptsKey] = value }
  }

  companion object {
    @SuppressLint("StaticFieldLeak") private var INSTANCE: AuthStateManager? = null

    private const val MAX_FAILED_ATTEMPTS = 5
    private const val MAX_LOCKOUT_DURATION_MINS = 5
    private var BASIC_AUTH_EXPIRY_HOURS = 24

    @Synchronized
    fun getInstance(context: Context): AuthStateManager {
      if (INSTANCE == null) {
        INSTANCE = AuthStateManager(context.applicationContext)
        val expiryAuthExpiryHours =
          context.applicationContext.resources.getInteger(R.integer.basic_auth_expiry_hours)
        BASIC_AUTH_EXPIRY_HOURS = expiryAuthExpiryHours
      }
      return INSTANCE as AuthStateManager
    }
  }
}
