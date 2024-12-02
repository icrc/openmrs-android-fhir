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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthorizationException
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import org.openmrs.android.fhir.R
import org.openmrs.android.fhir.auth.model.AuthConfigData
import org.openmrs.android.fhir.extensions.fromJson
import org.openmrs.android.fhir.extensions.toJson

class AuthConfiguration private constructor(private val context: Context) {
  private val authConfigKey by lazy { stringPreferencesKey("AuthConfig") }
  var lastException: AuthorizationException? = null
  val stored: AuthConfigData
    get() {
      val serializedAuth =
        runBlocking { context.dataStore.data.first()[authConfigKey] } ?: return authConfigData
      return serializedAuth.fromJson<AuthConfigData>()!!
    }

  suspend fun isNotStored(): Boolean {
    return context.dataStore.data.first()[authConfigKey] == null
  }

  suspend fun save() {
    context.dataStore.edit { pref -> pref[authConfigKey] = authConfigData.toJson() }
  }

  val authConfigData: AuthConfigData by lazy {
    AuthConfigData(
      clientId = context.getString(R.string.auth_client_id),
      redirectUri =
        context.getString(R.string.auth_redirect_uri_host) +
          ":" +
          context.getString(R.string.auth_redirect_uri_path),
      authorizationScope = context.getString(R.string.auth_authorization_scope),
      discoveryUri = context.getString(R.string.auth_discovery_uri),
      authorizationEndpointUri = context.getString(R.string.auth_authorization_endpoint_uri),
      registrationEndpointUri = context.getString(R.string.auth_registration_endpoint_uri),
      tokenEndpointUri = context.getString(R.string.auth_token_endpoint_uri),
      userInfoEndpointUri = context.getString(R.string.auth_user_info_endpoint_uri),
      endSessionEndpoint = context.getString(R.string.auth_end_session_endpoint),
      httpsRequired = context.resources.getBoolean(R.bool.auth_https_required),
      replaceLocalhostBy1022 =
        context.resources.getBoolean(R.bool.auth_replace_localhost_by_10_0_2_2),
    )
  }
  val clientId: String
    get() = authConfigData.clientId

  val scope: String?
    get() = authConfigData.authorizationScope

  val redirectUri: Uri?
    get() = Uri.parse(authConfigData.redirectUri)

  val discoveryUri: Uri?
    get() =
      if (authConfigData.discoveryUri.isNullOrBlank()) {
        null
      } else {
        Uri.parse(authConfigData.discoveryUri)
      }

  val authEndpointUri: Uri?
    get() = Uri.parse(authConfigData.authorizationEndpointUri)

  val tokenEndpointUri: Uri?
    get() = Uri.parse(authConfigData.tokenEndpointUri)

  val registrationEndpointUri: Uri?
    get() = Uri.parse(authConfigData.registrationEndpointUri)

  val endSessionEndpoint: Uri?
    get() = Uri.parse(authConfigData.endSessionEndpoint)

  val connectionBuilder: ConnectionBuilder
    get() =
      if (authConfigData.httpsRequired) {
        DefaultConnectionBuilder.INSTANCE
      } else {
        ConnectionBuilderForTesting.replace_localhost_by_10_0_2_2 =
          authConfigData.replaceLocalhostBy1022 ?: true
        ConnectionBuilderForTesting
      }

  init {
    AuthConfigUtil.isRequiredConfigString(authConfigData.clientId)
    AuthConfigUtil.isRequiredConfigString(authConfigData.authorizationScope)
    AuthConfigUtil.isRequiredConfigUri(authConfigData.redirectUri)
    AuthConfigUtil.isRedirectUriRegistered(context, authConfigData.redirectUri!!)
    if (authConfigData.discoveryUri == null || authConfigData.discoveryUri.isNullOrEmpty()) {
      AuthConfigUtil.isRequiredConfigWebUri(authConfigData.authorizationEndpointUri)
      AuthConfigUtil.isRequiredConfigWebUri(authConfigData.tokenEndpointUri)
      AuthConfigUtil.isRequiredConfigWebUri(authConfigData.userInfoEndpointUri)
      AuthConfigUtil.isRequiredConfigWebUri(authConfigData.endSessionEndpoint)
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
