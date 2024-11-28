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
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthorizationException
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import org.openmrs.android.fhir.R

class AuthConfiguration private constructor(private val context: Context) {
  private val authConfigKey by lazy { stringPreferencesKey("AuthConfig") }
  var lastException: AuthorizationException? = null
  private val gson = Gson()
  val stored: AuthConfigData
    get() {
      val serializedAuth =
        runBlocking { context.dataStore.data.first()[authConfigKey] } ?: return authConfigData
      return gson.fromJson(serializedAuth, AuthConfigData::class.java)
    }

  suspend fun isNotStored(): Boolean {
    return context.dataStore.data.first()[authConfigKey] == null
  }

  suspend fun save() {
    context.dataStore.edit { pref -> pref[authConfigKey] = gson.toJson(authConfigData) }
  }

  val authConfigData: AuthConfigData by lazy {
    AuthConfigData(
      client_id = context.getString(R.string.auth_client_id),
      redirect_uri =
        context.getString(R.string.auth_redirect_uri_host) +
          ":" +
          context.getString(R.string.auth_redirect_uri_path),
      authorization_scope = context.getString(R.string.auth_authorization_scope),
      discovery_uri = context.getString(R.string.auth_discovery_uri),
      authorization_endpoint_uri = context.getString(R.string.auth_authorization_endpoint_uri),
      registration_endpoint_uri = context.getString(R.string.auth_registration_endpoint_uri),
      token_endpoint_uri = context.getString(R.string.auth_token_endpoint_uri),
      user_info_endpoint_uri = context.getString(R.string.auth_user_info_endpoint_uri),
      end_session_endpoint = context.getString(R.string.auth_end_session_endpoint),
      https_required = context.resources.getBoolean(R.bool.auth_https_required),
      replace_localhost_by_10_0_2_2 =
        context.resources.getBoolean(R.bool.auth_replace_localhost_by_10_0_2_2),
    )
  }
  val clientId: String
    get() = authConfigData.client_id

  val scope: String?
    get() = authConfigData.authorization_scope

  val redirectUri: Uri?
    get() = Uri.parse(authConfigData.redirect_uri)

  val discoveryUri: Uri?
    get() =
      if (authConfigData.discovery_uri.isNullOrBlank()) {
        null
      } else {
        Uri.parse(authConfigData.discovery_uri)
      }

  val authEndpointUri: Uri?
    get() = Uri.parse(authConfigData.authorization_endpoint_uri)

  val tokenEndpointUri: Uri?
    get() = Uri.parse(authConfigData.token_endpoint_uri)

  val registrationEndpointUri: Uri?
    get() = Uri.parse(authConfigData.registration_endpoint_uri)

  val endSessionEndpoint: Uri?
    get() = Uri.parse(authConfigData.end_session_endpoint)

  val connectionBuilder: ConnectionBuilder
    get() =
      if (authConfigData.https_required) {
        DefaultConnectionBuilder.INSTANCE
      } else {
        ConnectionBuilderForTesting.replace_localhost_by_10_0_2_2 =
          authConfigData.replace_localhost_by_10_0_2_2 ?: true
        ConnectionBuilderForTesting
      }

  init {
    AuthConfigUtil.isRequiredConfigString(authConfigData.client_id)
    AuthConfigUtil.isRequiredConfigString(authConfigData.authorization_scope)
    AuthConfigUtil.isRequiredConfigUri(authConfigData.redirect_uri)
    AuthConfigUtil.isRedirectUriRegistered(context, authConfigData.redirect_uri!!)
    if (authConfigData.discovery_uri == null || authConfigData.discovery_uri.isNullOrEmpty()) {
      AuthConfigUtil.isRequiredConfigWebUri(authConfigData.authorization_endpoint_uri)
      AuthConfigUtil.isRequiredConfigWebUri(authConfigData.token_endpoint_uri)
      AuthConfigUtil.isRequiredConfigWebUri(authConfigData.user_info_endpoint_uri)
      AuthConfigUtil.isRequiredConfigWebUri(authConfigData.end_session_endpoint)
    }
  }

  companion object {
    @SuppressLint("StaticFieldLeak") private var INSTANCE: AuthConfiguration? = null

    @Synchronized
    fun getInstance(context: Context): AuthConfiguration {
      if (INSTANCE == null) {
        INSTANCE = AuthConfiguration(context.applicationContext)
      }
      return INSTANCE as AuthConfiguration
    }
  }
}

data class AuthConfigData(
  val client_id: String,
  val authorization_scope: String?,
  val redirect_uri: String?,
  //  val end_session_redirect_uri: String?,
  val discovery_uri: String?,
  val authorization_endpoint_uri: String?,
  val token_endpoint_uri: String?,
  val user_info_endpoint_uri: String?,
  val end_session_endpoint: String?,
  val registration_endpoint_uri: String?,
  val https_required: Boolean,
  val replace_localhost_by_10_0_2_2: Boolean?,
)
